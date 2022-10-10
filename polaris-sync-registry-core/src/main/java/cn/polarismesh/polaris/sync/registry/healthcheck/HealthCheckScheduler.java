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

package cn.polarismesh.polaris.sync.registry.healthcheck;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.extension.config.ConfigListener;
import cn.polarismesh.polaris.sync.extension.registry.Health;
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.HealthCheck;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Task;
import cn.polarismesh.polaris.sync.registry.tasks.NamedRegistryCenter;
import cn.polarismesh.polaris.sync.registry.tasks.TaskEngine;
import cn.polarismesh.polaris.sync.registry.tasks.TaskEngine.RegistrySet;
import cn.polarismesh.polaris.sync.registry.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.registry.utils.DurationUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckScheduler implements ConfigListener {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckScheduler.class);

    private final ScheduledExecutorService healthCheckExecutor = Executors
            .newScheduledThreadPool(1, new NamedThreadFactory("health-check-worker"));

    private final ExecutorService reloadExecutor = Executors.newFixedThreadPool(
            1, new NamedThreadFactory("reload-worker")
    );

    private final Map<String, ScheduledFuture<?>> tasks = new HashMap<>();

    private final AtomicBoolean enable = new AtomicBoolean(false);

    private final Object configLock = new Object();

    private final TaskEngine taskEngine;

    private final StatReportAggregator statReportAggregator;

    private long intervalMilli;

    public HealthCheckScheduler(StatReportAggregator statReportAggregator, TaskEngine taskEngine) {
        this.taskEngine = taskEngine;
        this.statReportAggregator = statReportAggregator;
        this.intervalMilli = DefaultValues.DEFAULT_INTERVAL_MS;
    }

    public void destroy() {
        healthCheckExecutor.shutdown();
    }

    public void init(RegistryProto.Registry config) {
        reload(config);
    }

    private void clearTasks() {
        if (!tasks.isEmpty()) {
            for (Map.Entry<String, ScheduledFuture<?>> entry : tasks.entrySet()) {
                LOG.info("[Health] cancel scheduled health check for task {}", entry.getKey());
                entry.getValue().cancel(true);
            }
            tasks.clear();
        }
    }

    public void reload(Registry registryConfig) {
        if (!ConfigUtils.verifyHealthCheck(registryConfig)) {
            throw new IllegalArgumentException("invalid health check configuration for content " + registryConfig.toString());
        }

        synchronized (configLock) {
            HealthCheck healthCheck = registryConfig.getHealthCheck();
            if (null == healthCheck) {
                enable.set(false);
                clearTasks();
                return;
            }
            boolean enable = healthCheck.getEnable();
            this.enable.set(enable);
            if (!enable) {
                LOG.info("[Health] health check is disabled");
                clearTasks();
                return;
            }
            long newIntervalMilli = DurationUtils
                    .parseDurationMillis(healthCheck.getInterval(), DefaultValues.DEFAULT_INTERVAL_MS);
            if (intervalMilli != newIntervalMilli) {
                clearTasks();
            }
            intervalMilli = newIntervalMilli;
            List<Task> tasksList = registryConfig.getTasksList();
            Set<String> taskNames = new HashSet<>();
            for (Task task : tasksList) {
                if (task.getEnable()) {
                    String taskName = task.getName();
                    taskNames.add(taskName);
                    if (!tasks.containsKey(taskName)) {
                        LOG.info("[Health] schedule health check for task {}", taskName);
                        ScheduledFuture<?> future = scheduleHealthCheckForTask(taskName);
                        tasks.put(taskName, future);
                    }
                }
            }
            for (String name : tasks.keySet()) {
                if (!taskNames.contains(name)) {
                    ScheduledFuture<?> future = tasks.remove(name);
                    LOG.info("[Health] cancel scheduled health check for task {}",name);
                    future.cancel(true);
                }
            }
        }
    }

    private ScheduledFuture<?> scheduleHealthCheckForTask(String taskName) {
        return healthCheckExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                RegistrySet registrySet = taskEngine.getRegistrySet(taskName);
                if (null == registrySet) {
                    LOG.warn("[Health] registry set not found for task {}", taskName);
                    return;
                }
                NamedRegistryCenter srcRegistry = registrySet.getSrcRegistry();
                NamedRegistryCenter dstRegistry = registrySet.getDstRegistry();
                Health srcHealth = srcRegistry.getRegistry().healthCheck();
                Health dstHealth = dstRegistry.getRegistry().healthCheck();
                RegistryHealthStatus srcHealthStatus = toRegistryHealthStatus(srcHealth, srcRegistry);
                RegistryHealthStatus dstHealthStatus = toRegistryHealthStatus(dstHealth, dstRegistry);
                statReportAggregator.reportHealthStatus(srcHealthStatus);
                statReportAggregator.reportHealthStatus(dstHealthStatus);
            }
        }, intervalMilli, intervalMilli, TimeUnit.MILLISECONDS);
    }

    private static RegistryHealthStatus toRegistryHealthStatus(Health health, NamedRegistryCenter registry) {
        return new RegistryHealthStatus(registry.getName(), registry.getRegistry().getType(), registry.getProductName(),
                health.getTotalCount(), health.getErrorCount());
    }

    @Override
    public void onChange(Registry config) {
        reload(config);
    }

    @Override
    public Executor executor() {
        return reloadExecutor;
    }
}
