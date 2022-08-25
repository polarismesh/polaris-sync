/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package cn.polarismesh.polaris.sync.registry.tasks;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.extension.config.ConfigListener;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Method;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Method.MethodType;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Task;
import cn.polarismesh.polaris.sync.registry.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.registry.utils.DurationUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class TaskEngine implements ConfigListener {

    private static final Logger LOG = LoggerFactory.getLogger(TaskEngine.class);

    private RegistryProto.Registry registryConfig;

    private final ScheduledExecutorService pullExecutor =
            Executors.newScheduledThreadPool(1, new NamedThreadFactory("list-worker"));

    private final ScheduledExecutorService watchExecutor = Executors
            .newScheduledThreadPool(1, new NamedThreadFactory("watch-worker"));

    private final ExecutorService registerExecutor =
            Executors.newCachedThreadPool(new NamedThreadFactory("regis-worker"));

    private final Map<ServiceWithSource, Future<?>> watchedServices = new ConcurrentHashMap<>();

    private final Map<String, ScheduledFuture<?>> pulledServices = new HashMap<>();

    private final Object configLock = new Object();

    private final Map<RegistryType, Class<? extends RegistryCenter>> registryTypeMap = new HashMap<>();

    private final Map<String, RegistrySet> taskRegistryMap = new HashMap<>();

    public TaskEngine(List<RegistryCenter> registries) {
        for (RegistryCenter registry : registries) {
            registryTypeMap.put(registry.getType(), registry.getClass());
        }
        registryConfig = RegistryProto.Registry.newBuilder().build();
    }

    public void init(RegistryProto.Registry config) {
        reload(config);
    }

    public void destroy() {
        watchExecutor.shutdown();
        registerExecutor.shutdown();
        pullExecutor.shutdown();
    }

    private int[] deleteTask(Task syncTask, List<Method> methods) {
        int watchTasks = 0;
        int pullTasks = 0;
        if (CollectionUtils.isEmpty(methods)) {
            return new int[]{watchTasks, pullTasks};
        }
        if (!syncTask.getEnable()) {
            return new int[]{watchTasks, pullTasks};
        }
        for (Method method : methods) {
            if (!method.getEnable()) {
                continue;
            }
            LOG.info("[Core] start to delete task {}, method {}", syncTask, method);
            if (MethodType.pull.equals(method.getType())) {
                deletePullTask(syncTask);
                pullTasks++;
            } else if (MethodType.watch.equals(method.getType())) {
                deleteWatchTask(syncTask);
                watchTasks++;
            }
        }
        RegistrySet registrySet = taskRegistryMap.remove(syncTask.getName());
        if (null != registrySet) {
            registrySet.destroy();
        }
        return new int[]{watchTasks, pullTasks};
    }

    private void addPullTask(Task syncTask, long intervalMilli) {
        RegistrySet registrySet = getOrCreateRegistrySet(syncTask);
        if (null == registrySet) {
            LOG.error("[Core] adding pull task {}, fail to init registry", syncTask.getName());
            return;
        }
        NamedRegistryCenter sourceRegistry = registrySet.getSrcRegistry();
        NamedRegistryCenter destRegistry = registrySet.getDstRegistry();
        PullTask pullTask = new PullTask(sourceRegistry, destRegistry, syncTask.getMatchList());
        ScheduledFuture<?> future = pullExecutor
                .scheduleWithFixedDelay(pullTask, 0, intervalMilli, TimeUnit.MILLISECONDS);
        pulledServices.put(syncTask.getName(), future);
        LOG.info("[Core] task {} has been scheduled pulled", syncTask.getName());
    }

    private void addWatchTask(Task syncTask) {
        RegistrySet registrySet = getOrCreateRegistrySet(syncTask);
        if (null == registrySet) {
            LOG.error("[Core] adding watch task {}, fail to init registry", syncTask.getName());
            return;
        }
        NamedRegistryCenter sourceRegistry = registrySet.getSrcRegistry();
        NamedRegistryCenter destRegistry = registrySet.getDstRegistry();
        for (RegistryProto.Match match : syncTask.getMatchList()) {
            if (ConfigUtils.isEmptyMatch(match)) {
                continue;
            }
            WatchTask watchTask = new WatchTask(watchedServices, sourceRegistry, destRegistry, match,
                    registerExecutor, watchExecutor);
            Future<?> submit = watchExecutor.schedule(watchTask, 1, TimeUnit.SECONDS);
            ServiceWithSource serviceWithSource = watchTask.getService();
            watchedServices.put(serviceWithSource, submit);
            LOG.info("[Core] service {} has been scheduled watched", serviceWithSource);
        }
    }

    private void deletePullTask(Task syncTask) {
        ScheduledFuture<?> future = pulledServices.remove(syncTask.getName());
        if (null != future) {
            future.cancel(true);
        }
        LOG.info("[Core] task {} has been cancel pulled", syncTask.getName());
    }

    private void deleteWatchTask(Task syncTask) {
        RegistryEndpoint source = syncTask.getSource();
        NamedRegistryCenter sourceRegistry = getSrcRegistry(syncTask.getName());
        if (null != sourceRegistry) {
            for (RegistryProto.Match match : syncTask.getMatchList()) {
                if (ConfigUtils.isEmptyMatch(match)) {
                    continue;
                }
                UnwatchTask unwatchTask = new UnwatchTask(sourceRegistry, match);
                ServiceWithSource serviceWithSource = new ServiceWithSource(source.getName(), unwatchTask.getService());
                Future<?> future = watchedServices.remove(serviceWithSource);
                if (null != future) {
                    future.cancel(true);
                }
                unwatchTask.run();
                LOG.info("[Core] service {} has been cancel watched", serviceWithSource);
            }
        }
    }

    private int[] addTask(Task task, List<Method> methods) {
        int watchTasks = 0;
        int pullTasks = 0;
        if (CollectionUtils.isEmpty(methods)) {
            return new int[]{watchTasks, pullTasks};
        }
        if (!task.getEnable()) {
            return new int[]{watchTasks, pullTasks};
        }
        for (Method method : methods) {
            if (!method.getEnable()) {
                continue;
            }
            LOG.info("[Core] start to add task {}, method {}", task, method);
            if (MethodType.pull.equals(method.getType())) {
                long pullInterval = DurationUtils.parseDurationMillis(
                        method.getInterval(), DefaultValues.DEFAULT_PULL_INTERVAL_MS);
                addPullTask(task, pullInterval);
                pullTasks++;
            } else if (MethodType.watch.equals(method.getType())) {
                addWatchTask(task);
                watchTasks++;
            }
        }
        return new int[]{watchTasks, pullTasks};
    }

    private int[] initTasks(Registry registryConfig) {
        int watchTasks = 0;
        int pullTasks = 0;
        List<Task> tasks = registryConfig.getTasksList();
        if (CollectionUtils.isEmpty(tasks)) {
            LOG.info("[Core] task is empty, no task scheduled");
            return new int[]{watchTasks, pullTasks};
        }
        List<Method> methods = registryConfig.getMethodsList();
        for (Task task : tasks) {
            if (!task.getEnable()) {
                continue;
            }
            int[] counts = addTask(task, methods);
            watchTasks += counts[0];
            pullTasks += counts[1];
        }
        return new int[]{watchTasks, pullTasks};
    }

    private int[] clearTasks(Registry registryConfig) {
        int watchTasks = 0;
        int pullTasks = 0;
        List<Task> tasks = registryConfig.getTasksList();
        if (CollectionUtils.isEmpty(tasks)) {
            LOG.info("[Core] task is empty, no task scheduled");
            return new int[]{watchTasks, pullTasks};
        }
        List<Method> methods = registryConfig.getMethodsList();
        for (Task task : tasks) {
            int[] counts = deleteTask(task, methods);
            watchTasks += counts[0];
            pullTasks += counts[1];
        }
        return new int[]{watchTasks, pullTasks};
    }

    private void reload(Registry registryConfig) {
        if (!ConfigUtils.verifyTasks(registryConfig, registryTypeMap.keySet())) {
            throw new IllegalArgumentException("invalid configuration content " + registryConfig.toString());
        }

        synchronized (configLock) {
            int watchTasksAdded = 0;
            int pullTasksAdded = 0;
            int watchTasksDeleted = 0;
            int pullTasksDeleted = 0;
            RegistryProto.Registry oldRegistryConfig = this.registryConfig;
            this.registryConfig = registryConfig;
            if (ConfigUtils.methodsChanged(oldRegistryConfig.getMethodsList(), registryConfig.getMethodsList())) {
                // method changed, clear the old tasks before adding new tasks
                LOG.info("[Core] task sync methods changed");
                int[] clearCounts = clearTasks(oldRegistryConfig);
                int[] addCounts = initTasks(registryConfig);
                watchTasksAdded += addCounts[0];
                pullTasksAdded += addCounts[1];
                watchTasksDeleted += clearCounts[0];
                pullTasksDeleted += clearCounts[1];
                LOG.info(
                        "[Core] tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
                        watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
                return;
            }

            Map<String, Task> oldTasks = new HashMap<>();
            Map<String, Task> newTasks = new HashMap<>();
            for (Task task : oldRegistryConfig.getTasksList()) {
                oldTasks.put(task.getName(), task);
            }
            for (Task task : registryConfig.getTasksList()) {
                newTasks.put(task.getName(), task);
            }
            for (Map.Entry<String, Task> entry : oldTasks.entrySet()) {
                if (!newTasks.containsKey(entry.getKey())) {
                    LOG.info("[Core] task {} has been deleted", entry.getKey());
                    int[] deleteCounts = deleteTask(entry.getValue(), oldRegistryConfig.getMethodsList());
                    watchTasksDeleted += deleteCounts[0];
                    pullTasksDeleted += deleteCounts[1];
                }
            }
            for (Map.Entry<String, Task> entry : newTasks.entrySet()) {
                if (!oldTasks.containsKey(entry.getKey())) {
                    LOG.info("[Core] task {} has been added", entry.getKey());
                    int[] addCounts = addTask(entry.getValue(), oldRegistryConfig.getMethodsList());
                    watchTasksAdded += addCounts[0];
                    pullTasksAdded += addCounts[1];
                } else {
                    Task oldTask = oldTasks.get(entry.getKey());
                    Task newTask = entry.getValue();
                    if (oldTask.equals(newTask)) {
                        continue;
                    }
                    LOG.info("[Core] task {} has been changed", entry.getKey());
                    int[] deleteCounts = deleteTask(oldTask, oldRegistryConfig.getMethodsList());
                    int[] addCounts = addTask(newTask, registryConfig.getMethodsList());
                    watchTasksDeleted += deleteCounts[0];
                    pullTasksDeleted += deleteCounts[1];
                    watchTasksAdded += addCounts[0];
                    pullTasksAdded += addCounts[1];
                }
            }
            LOG.info(
                    "[Core] tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
                    watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
        }
    }

    private NamedRegistryCenter getSrcRegistry(String taskName) {
        RegistrySet registrySet = taskRegistryMap.get(taskName);
        if (null == registrySet) {
            return null;
        }
        return registrySet.getSrcRegistry();
    }

    private RegistrySet getOrCreateRegistrySet(Task task) {
        RegistrySet registrySet = taskRegistryMap.get(task.getName());
        if (null != registrySet) {
            return registrySet;
        }
        RegistryEndpoint source = task.getSource();
        RegistryEndpoint destination = task.getDestination();
        RegistryCenter sourceCenter = createRegistry(source.getType());
        if (null == sourceCenter) {
            return null;
        }
        RegistryCenter destinationCenter = createRegistry(destination.getType());
        if (null == destinationCenter) {
            return null;
        }
        sourceCenter.init(new RegistryInitRequest("", RegistryType.unknown, source));
        destinationCenter.init(new RegistryInitRequest(source.getName(), source.getType(), destination));
        registrySet = new RegistrySet(new NamedRegistryCenter(
                source.getName(), source.getProductName(), sourceCenter),
                new NamedRegistryCenter(destination.getName(), destination.getProductName(), destinationCenter));
        taskRegistryMap.put(task.getName(), registrySet);
        return registrySet;
    }

    private RegistryCenter createRegistry(RegistryType registryType) {
        Class<? extends RegistryCenter> registryClazz = registryTypeMap.get(registryType);
        RegistryCenter registry;
        try {
            registry = registryClazz.newInstance();
        } catch (Exception e) {
            LOG.error("[Core] fail to create instance for class {}", registryClazz.getCanonicalName(), e);
            return null;
        }
        return registry;
    }

    public RegistrySet getRegistrySet(String taskName) {
        synchronized (configLock) {
            return taskRegistryMap.get(taskName);
        }
    }

    public NamedRegistryCenter getRegistry(String taskName, String registryName) {
        RegistrySet registrySet = getRegistrySet(taskName);
        if (null == registrySet) {
            return null;
        }
        if (StringUtils.equals(registrySet.getSrcRegistry().getName(), registryName)) {
            return registrySet.getSrcRegistry();
        }
        if (StringUtils.equals(registrySet.getDstRegistry().getName(), registryName)) {
            return registrySet.getDstRegistry();
        }
        return null;
    }

    @Override
    public void onChange(Registry config) {
        reload(config);
    }

    public static class ServiceWithSource {

        private final String sourceName;

        private final Service service;

        public ServiceWithSource(String sourceName, Service service) {
            this.sourceName = sourceName;
            this.service = service;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ServiceWithSource)) {
                return false;
            }
            ServiceWithSource that = (ServiceWithSource) o;
            return Objects.equals(sourceName, that.sourceName) &&
                    Objects.equals(service, that.service);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceName, service);
        }

        @Override
        public String toString() {
            return "ServiceWithSource{" +
                    "sourceName='" + sourceName + '\'' +
                    ", service=" + service +
                    '}';
        }
    }

    public static class RegistrySet {

        private final NamedRegistryCenter srcRegistry;

        private final NamedRegistryCenter dstRegistry;

        public RegistrySet(NamedRegistryCenter srcRegistry,
                NamedRegistryCenter dstRegistry) {
            this.srcRegistry = srcRegistry;
            this.dstRegistry = dstRegistry;
        }

        public NamedRegistryCenter getSrcRegistry() {
            return srcRegistry;
        }

        public NamedRegistryCenter getDstRegistry() {
            return dstRegistry;
        }

        public void destroy() {
            srcRegistry.getRegistry().destroy();
            dstRegistry.getRegistry().destroy();
        }
    }
}
