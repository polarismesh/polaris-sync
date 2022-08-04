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
import cn.polarismesh.polaris.sync.registry.config.Match;
import cn.polarismesh.polaris.sync.registry.config.RegistryConfig;
import cn.polarismesh.polaris.sync.registry.config.SyncMethod;
import cn.polarismesh.polaris.sync.registry.config.SyncRegistryProperties;
import cn.polarismesh.polaris.sync.registry.config.SyncTaskConfig;
import cn.polarismesh.polaris.sync.registry.extensions.Registry;
import cn.polarismesh.polaris.sync.registry.extensions.Service;
import cn.polarismesh.polaris.sync.registry.utils.DurationUtils;
import cn.polarismesh.polaris.sync.registry.utils.NamedThreadFactory;
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class TaskEngine {

    private static final Logger LOG = LoggerFactory.getLogger(TaskEngine.class);

    private final SyncRegistryProperties syncRegistryProperties;

    private final ScheduledExecutorService pullExecutor =
            Executors.newScheduledThreadPool(1, new NamedThreadFactory("list-worker"));

    private final ExecutorService watchExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("watch-worker"));

    private final ExecutorService registerExecutor =
            Executors.newCachedThreadPool(new NamedThreadFactory("register-worker"));

    private final Map<ServiceWithSource, Future<?>> watchedServices = new HashMap<>();

    private final Map<ServiceWithSource, ScheduledFuture<?>> pulledServices = new HashMap<>();

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private final Map<String, Class<? extends Registry>> registryTypeMap = new HashMap<>();

    private final Map<String, RegistryCounter> registryMap = new HashMap<>();

    public TaskEngine(SyncRegistryProperties syncRegistryProperties, List<Registry> registries) {
        this.syncRegistryProperties = syncRegistryProperties;
        for (Registry registry : registries) {
            registryTypeMap.put(registry.getType(), registry.getClass());
        }
    }

    public void deleteTask(SyncTaskConfig syncTask, List<SyncMethod> methods) {
        if (CollectionUtils.isEmpty(methods)) {
            return;
        }
        for (SyncMethod method : methods) {
            if (!method.isEnable()) {
                continue;
            }
            LOG.info("[Core] start to delete task {}, method {}", syncTask, method);
            if (DefaultValues.METHOD_PULL.equals(method.getName())) {
                deletePullTask(syncTask);
            } else if(DefaultValues.METHOD_WATCH.equals(method.getName())) {
                deleteWatchTask(syncTask);
            }
        }
    }

    private void addPullTask(SyncTaskConfig syncTask, long intervalMilli) {
        RegistryConfig source = syncTask.getSource();
        RegistryCounter sourceRegistry= getOrCreateRegistry(source);
        if (null == sourceRegistry) {
            LOG.error("[Core] adding task {}, fail to parse source registry {}", syncTask.getName(), source.getName());
            return;
        }
        RegistryConfig destination = syncTask.getDestination();
        RegistryCounter destRegistry = getOrCreateRegistry(destination);
        if (null == destRegistry) {
            LOG.error("[Core] adding task {}, fail to parse destination registry {}",
                    syncTask.getName(), destination.getName());
            return;
        }
        for (Match match : syncTask.getMatch()) {
            PullTask pullTask = new PullTask(sourceRegistry.getRegistry(), destRegistry.getRegistry(), match);
            ScheduledFuture<?> future = pullExecutor
                    .scheduleAtFixedRate(pullTask, 0, intervalMilli, TimeUnit.MILLISECONDS);
            ServiceWithSource serviceWithSource = new ServiceWithSource(source.getName(), pullTask.getService());
            pulledServices.put(serviceWithSource, future);
            LOG.info("[Core] service {} has been scheduled pulled", serviceWithSource);
        }
    }

    private void addWatchTask(SyncTaskConfig syncTask) {
        RegistryConfig source = syncTask.getSource();
        RegistryCounter sourceRegistry= getOrCreateRegistry(source);
        if (null == sourceRegistry) {
            LOG.error("[Core] adding task {}, fail to parse source registry {}", syncTask.getName(), source.getName());
            return;
        }
        sourceRegistry.acquire();
        RegistryConfig destination = syncTask.getDestination();
        RegistryCounter destRegistry = getOrCreateRegistry(destination);
        if (null == destRegistry) {
            LOG.error("[Core] adding task {}, fail to parse destination registry {}",
                    syncTask.getName(), destination.getName());
            return;
        }
        destRegistry.acquire();
        for (Match match : syncTask.getMatch()) {
            WatchTask watchTask = new WatchTask(sourceRegistry.getRegistry(), destRegistry.getRegistry(), match,
                    watchExecutor);
            Future<?> submit = watchExecutor.submit(watchTask);
            ServiceWithSource serviceWithSource = new ServiceWithSource(source.getName(), watchTask.getService());
            watchedServices.put(serviceWithSource, submit);
            LOG.info("[Core] service {} has been scheduled watched", serviceWithSource);
        }
    }

    private void deletePullTask(SyncTaskConfig syncTask) {
        RegistryConfig destination = syncTask.getDestination();
        RegistryCounter destRegistry = getRegistry(destination);
        if (null != destRegistry) {
            destRegistry.release();
        }
        RegistryConfig source = syncTask.getSource();
        RegistryCounter sourceRegistry= getRegistry(source);
        if (null != sourceRegistry) {
            sourceRegistry.release();
        }
        for (Match match : syncTask.getMatch()) {
            ServiceWithSource serviceWithSource = new ServiceWithSource(
                    source.getName(), new Service(match.getNamespace(), match.getService()));
            ScheduledFuture<?> future = pulledServices.remove(serviceWithSource);
            if (null != future) {
                future.cancel(true);
            }
            LOG.info("[Core] service {} has been cancel pulled", serviceWithSource);
        }

    }

    private void deleteWatchTask(SyncTaskConfig syncTask) {
        RegistryConfig destination = syncTask.getDestination();
        RegistryCounter destRegistry = getRegistry(destination);
        if (null != destRegistry) {
            destRegistry.release();
        }
        RegistryConfig source = syncTask.getSource();
        RegistryCounter sourceRegistry= getRegistry(source);
        if (null != sourceRegistry) {
            sourceRegistry.release();
            for (Match match : syncTask.getMatch()) {
                UnwatchTask unwatchTask = new UnwatchTask(sourceRegistry.getRegistry(), match);
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

    private void init(SyncRegistryProperties syncRegistryProperties) {
        List<SyncTaskConfig> tasks = syncRegistryProperties.getTasks();
        if (CollectionUtils.isEmpty(tasks)) {
            LOG.info("[Core] task is empty, no task scheduled");
            return;
        }
        int watchTasks = 0;
        int pullTasks = 0;
        List<SyncMethod> methods = syncRegistryProperties.getMethods();
        for(SyncTaskConfig syncTask : tasks) {
            for (SyncMethod method : methods) {
                if (!method.isEnable()) {
                    continue;
                }
                LOG.info("[Core] start to add task {}, method {}", syncTask, method);
                if (DefaultValues.METHOD_PULL.equals(method.getName())) {
                    long pullInterval = DurationUtils.parseDurationMillis(
                            method.getInterval(), DefaultValues.DEFAULT_PULL_INTERVAL_MS);
                    addPullTask(syncTask, pullInterval);
                    pullTasks++;
                } else if(DefaultValues.METHOD_WATCH.equals(method.getName())) {
                    addWatchTask(syncTask);
                    watchTasks++;
                }
            }
        }
        LOG.info("[Core] sync config initialized, watch tasks {}, pull tasks {}", watchTasks, pullTasks);
    }

    private void reload(SyncRegistryProperties syncRegistryProperties) {

    }

    private RegistryCounter getRegistry(RegistryConfig registryConfig) {
        String key = registryConfig.getName();
        return registryMap.get(key);
    }

    private RegistryCounter getOrCreateRegistry(RegistryConfig registryConfig) {
        String key = registryConfig.getName();
        RegistryCounter registryCounter = registryMap.get(key);
        if (null != registryCounter) {
            return registryCounter;
        }
        Class<? extends Registry> registryClazz = registryTypeMap.get(key);
        if (null == registryClazz) {
            LOG.error("[Core] unknown registry type {}", key);
            return null;
        }
        Registry registry = null;
        try {
            registry = registryClazz.newInstance();
        } catch (Exception e) {
            LOG.error("[Core] fail to create instance for class {}", registryClazz.getCanonicalName(), e);
            return null;
        }
        registry.init(registryConfig);
        registryCounter = new RegistryCounter(registry);
        registryMap.put(key, registryCounter);
        return registryCounter;
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

    public static class RegistryCounter {

        private final Registry registry;

        private final AtomicInteger counter = new AtomicInteger();

        public RegistryCounter(Registry registry) {
            this.registry = registry;
        }

        public Registry getRegistry() {
            return registry;
        }

        public void acquire() {
            counter.incrementAndGet();
        }

        public int release() {
            return counter.decrementAndGet();
        }
    }
}
