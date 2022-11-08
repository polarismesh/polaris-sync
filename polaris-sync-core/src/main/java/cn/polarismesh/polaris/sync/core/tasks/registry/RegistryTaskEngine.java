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

package cn.polarismesh.polaris.sync.core.tasks.registry;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.core.tasks.AbstractTaskEngine;
import cn.polarismesh.polaris.sync.core.utils.DurationUtils;
import cn.polarismesh.polaris.sync.core.utils.RegistryUtils;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class RegistryTaskEngine extends AbstractTaskEngine {

    private static final Logger LOG = LoggerFactory.getLogger(RegistryTaskEngine.class);

    private final ExecutorService registerExecutor =
            Executors.newCachedThreadPool(new NamedThreadFactory("regis-worker"));

    private final Map<ServiceWithSource, Future<?>> watchedServices = new ConcurrentHashMap<>();

    private final Map<String, ScheduledFuture<?>> pulledServices = new HashMap<>();

    private final Object configLock = new Object();

    private final Map<String, RegistrySet> taskRegistryMap = new HashMap<>();

    protected final Map<RegistryProto.RegistryEndpoint.RegistryType, Class<? extends RegistryCenter>> registryTypeMap = new HashMap<>();

    public RegistryTaskEngine(List<RegistryCenter> registries) {
        super("registry");
        for (RegistryCenter registry : registries) {
            registryTypeMap.put(registry.getType(), registry.getClass());
        }
    }

    private void addPullTask(Task syncTask, long intervalMilli) {
        RegistrySet registrySet = getOrCreateRegistrySet(syncTask);
        if (null == registrySet) {
            LOG.error("[Core] registry adding pull task {}, fail to init registry", syncTask.getName());
            return;
        }
        NamedRegistryCenter sourceRegistry = registrySet.getSrcRegistry();
        NamedRegistryCenter destRegistry = registrySet.getDstRegistry();
        PullTask pullTask = new PullTask(sourceRegistry, destRegistry, syncTask.getMatchList());
        ScheduledFuture<?> future = pullExecutor
                .scheduleWithFixedDelay(pullTask, 0, intervalMilli, TimeUnit.MILLISECONDS);
        pulledServices.put(syncTask.getName(), future);
        LOG.info("[Core] registry task {} has been scheduled pulled", syncTask.getName());
    }

    private void addWatchTask(Task syncTask) {
        RegistrySet registrySet = getOrCreateRegistrySet(syncTask);
        if (null == registrySet) {
            LOG.error("[Core] registry adding watch task {}, fail to init registry", syncTask.getName());
            return;
        }
        NamedRegistryCenter sourceRegistry = registrySet.getSrcRegistry();
        NamedRegistryCenter destRegistry = registrySet.getDstRegistry();
        for (RegistryProto.Match match : syncTask.getMatchList()) {
            if (RegistryUtils.isEmptyMatch(match)) {
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


    @Override
    protected int[] deleteRegistryTask(Task task, List<Method> methods) {
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
            LOG.info("[Core] registry start to delete task {}, method {}", task, method);
            if (MethodType.pull.equals(method.getType())) {
                deletePullTask(task);
                pullTasks++;
            } else if (MethodType.watch.equals(method.getType())) {
                deleteWatchTask(task);
                watchTasks++;
            }
        }
        RegistrySet registrySet = taskRegistryMap.remove(task.getName());
        if (null != registrySet) {
            registrySet.destroy();
        }
        return new int[]{watchTasks, pullTasks};
    }

    @Override
    protected int[] deleteConfigTask(RegistryProto.ConfigTask task, List<Method> methods) {
        return new int[]{0,0};
    }


    @Override
    protected int[] addRegistryTask(Task task, List<Method> methods) {
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
            LOG.info("[Core] registry start to add task {}, method {}", task, method);
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

    @Override
    protected int[] addConfigTask(RegistryProto.ConfigTask task, List<Method> methods) {
        return new int[]{0,0};
    }

    @Override
    protected void verifyTask(Registry registryConfig) {
        if (!RegistryUtils.verifyTasks(registryConfig, registryTypeMap.keySet())) {
            throw new IllegalArgumentException("invalid configuration content " + registryConfig.toString());
        }
    }

    protected void deletePullTask(Task syncTask) {
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
                if (RegistryUtils.isEmptyMatch(match)) {
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

    @Override
    public Executor executor() {
        return reloadExecutor;
    }
}
