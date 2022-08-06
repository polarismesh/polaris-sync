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

import cn.polarismesh.polaris.sync.registry.config.DefaultValues;
import cn.polarismesh.polaris.sync.registry.config.FileListener;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Method;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Method.MethodType;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Task;
import cn.polarismesh.polaris.sync.registry.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.registry.utils.DurationUtils;
import cn.polarismesh.polaris.sync.registry.utils.NamedThreadFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class TaskEngine implements FileListener {

    private static final Logger LOG = LoggerFactory.getLogger(TaskEngine.class);

    private RegistryProto.Registry registryConfig;

    private final ScheduledExecutorService pullExecutor =
            Executors.newScheduledThreadPool(1, new NamedThreadFactory("list-worker"));

    private final ExecutorService watchExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("watch-worker"));

    private final ExecutorService registerExecutor =
            Executors.newCachedThreadPool(new NamedThreadFactory("register-worker"));

    private final Map<ServiceWithSource, Future<?>> watchedServices = new HashMap<>();

    private final Map<ServiceWithSource, ScheduledFuture<?>> pulledServices = new HashMap<>();

    private final Object configLock = new Object();

    private final Map<String, Class<? extends RegistryCenter>> registryTypeMap = new HashMap<>();

    private final Map<String, NamedRegistryCenter> registryMap = new HashMap<>();

    public TaskEngine(List<RegistryCenter> registries) {
        for (RegistryCenter registry : registries) {
            registryTypeMap.put(registry.getType(), registry.getClass());
        }
        registryConfig = RegistryProto.Registry.newBuilder().build();
    }

    public void init(String fullConfigFile) throws IOException {
        RegistryProto.Registry config = ConfigUtils.parseFromFile(fullConfigFile);
        if (!ConfigUtils.verifyTasks(config, registryMap.keySet())) {
            throw new IllegalArgumentException("invalid configuration for path " + fullConfigFile);
        }
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
            } else if(MethodType.watch.equals(method.getType())) {
                deleteWatchTask(syncTask);
                watchTasks++;
            }
        }
        return new int[]{watchTasks, pullTasks};
    }

    private void addPullTask(Task syncTask, long intervalMilli) {
        RegistryEndpoint source = syncTask.getSource();
        NamedRegistryCenter sourceRegistry= getOrCreateRegistry(source);
        if (null == sourceRegistry) {
            LOG.error("[Core] adding task {}, fail to parse source registry {}", syncTask.getName(), source.getName());
            return;
        }
        RegistryEndpoint destination = syncTask.getDestination();
        NamedRegistryCenter destRegistry = getOrCreateRegistry(destination);
        if (null == destRegistry) {
            LOG.error("[Core] adding task {}, fail to parse destination registry {}",
                    syncTask.getName(), destination.getName());
            return;
        }
        for (RegistryProto.Match match : syncTask.getMatchList()) {
            PullTask pullTask = new PullTask(sourceRegistry, destRegistry, match);
            ScheduledFuture<?> future = pullExecutor
                    .scheduleAtFixedRate(pullTask, 0, intervalMilli, TimeUnit.MILLISECONDS);
            ServiceWithSource serviceWithSource = new ServiceWithSource(source.getName(), pullTask.getService());
            pulledServices.put(serviceWithSource, future);
            LOG.info("[Core] service {} has been scheduled pulled", serviceWithSource);
        }
    }

    private void addWatchTask(Task syncTask) {
        RegistryEndpoint source = syncTask.getSource();
        NamedRegistryCenter sourceRegistry= getOrCreateRegistry(source);
        if (null == sourceRegistry) {
            LOG.error("[Core] adding task {}, fail to parse source registry {}", syncTask.getName(), source.getName());
            return;
        }
        sourceRegistry.acquire();
        RegistryEndpoint destination = syncTask.getDestination();
        NamedRegistryCenter destRegistry = getOrCreateRegistry(destination);
        if (null == destRegistry) {
            LOG.error("[Core] adding task {}, fail to parse destination registry {}",
                    syncTask.getName(), destination.getName());
            return;
        }
        destRegistry.acquire();
        for (RegistryProto.Match match : syncTask.getMatchList()) {
            WatchTask watchTask = new WatchTask(sourceRegistry, destRegistry, match,
                    registerExecutor);
            Future<?> submit = watchExecutor.submit(watchTask);
            ServiceWithSource serviceWithSource = new ServiceWithSource(source.getName(), watchTask.getService());
            watchedServices.put(serviceWithSource, submit);
            LOG.info("[Core] service {} has been scheduled watched", serviceWithSource);
        }
    }

    private void deletePullTask(Task syncTask) {
        RegistryEndpoint destination = syncTask.getDestination();
        NamedRegistryCenter destRegistry = getRegistry(destination);
        if (null != destRegistry) {
            destRegistry.release();
        }
        RegistryEndpoint source = syncTask.getSource();
        NamedRegistryCenter sourceRegistry= getRegistry(source);
        if (null != sourceRegistry) {
            sourceRegistry.release();
        }
        for (RegistryProto.Match match : syncTask.getMatchList()) {
            ServiceWithSource serviceWithSource = new ServiceWithSource(
                    source.getName(), new Service(match.getNamespace(), match.getService()));
            ScheduledFuture<?> future = pulledServices.remove(serviceWithSource);
            if (null != future) {
                future.cancel(true);
            }
            LOG.info("[Core] service {} has been cancel pulled", serviceWithSource);
        }

    }

    private void deleteWatchTask(Task syncTask) {
        RegistryEndpoint destination = syncTask.getDestination();
        NamedRegistryCenter destRegistry = getRegistry(destination);
        if (null != destRegistry) {
            destRegistry.release();
        }
        RegistryEndpoint source = syncTask.getSource();
        NamedRegistryCenter sourceRegistry= getRegistry(source);
        if (null != sourceRegistry) {
            sourceRegistry.release();
            for (RegistryProto.Match match : syncTask.getMatchList()) {
                UnwatchTask unwatchTask = new UnwatchTask(sourceRegistry, match);
                ServiceWithSource serviceWithSource = new ServiceWithSource(source.getName(), unwatchTask.getService());
                Future<?> future = watchedServices.remove(serviceWithSource);
                if (null != future) {
                    future.cancel(true);
                }
                watchExecutor.submit(unwatchTask);
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
            } else if(MethodType.watch.equals(method.getType())) {
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
        for(Task task : tasks) {
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
        for(Task task : tasks) {
            int[] counts = deleteTask(task, methods);
            watchTasks += counts[0];
            pullTasks += counts[1];
        }
        return new int[]{watchTasks, pullTasks};
    }

    private void reload(Registry registryConfig) {
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
                LOG.info("[Core] tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
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
            LOG.info("[Core] tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
                    watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
        }
    }

    private NamedRegistryCenter getRegistry(RegistryEndpoint registryConfig) {
        String key = registryConfig.getName();
        return registryMap.get(key);
    }

    private NamedRegistryCenter getOrCreateRegistry(RegistryEndpoint registryConfig) {
        String key = registryConfig.getName();
        NamedRegistryCenter registryCounter = registryMap.get(key);
        if (null != registryCounter) {
            return registryCounter;
        }
        Class<? extends RegistryCenter> registryClazz = registryTypeMap.get(key);
        RegistryCenter registry = null;
        try {
            registry = registryClazz.newInstance();
        } catch (Exception e) {
            LOG.error("[Core] fail to create instance for class {}", registryClazz.getCanonicalName(), e);
            return null;
        }
        registry.init(registryConfig);
        registryCounter = new NamedRegistryCenter(key, registry);
        registryMap.put(key, registryCounter);
        return registryCounter;
    }

    @Override
    public boolean onFileChanged(File file) {
        RegistryProto.Registry config = null;
        String fullPath = file.getAbsolutePath();
        try {
            config = ConfigUtils.parseFromFile(fullPath);
        } catch (IOException e) {
            LOG.error("[Core] fail to parse file {} to config proto", fullPath, e);
            return false;
        }

        if (!ConfigUtils.verifyTasks(config, registryMap.keySet())) {
            LOG.error("invalid configuration for path {}", fullPath);
            return false;
        }
        reload(config);
        return true;
    }

    private static class ServiceWithSource {
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

    public static class NamedRegistryCenter {

        private final String name;

        private final RegistryCenter registry;

        private final AtomicInteger counter = new AtomicInteger();

        public NamedRegistryCenter(String name, RegistryCenter registry) {
            this.name = name;
            this.registry = registry;
        }

        public String getName() {
            return name;
        }

        public RegistryCenter getRegistry() {
            return registry;
        }

        private void acquire() {
            counter.incrementAndGet();
        }

        private void release() {
            counter.decrementAndGet();
        }
    }
}
