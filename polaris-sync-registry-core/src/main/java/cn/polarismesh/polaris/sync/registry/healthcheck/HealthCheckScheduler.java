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
import cn.polarismesh.polaris.sync.extension.registry.Health;
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus;
import cn.polarismesh.polaris.sync.extension.utils.DefaultValues;
import cn.polarismesh.polaris.sync.registry.config.FileListener;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.HealthCheck;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Task;
import cn.polarismesh.polaris.sync.registry.tasks.NamedRegistryCenter;
import cn.polarismesh.polaris.sync.registry.tasks.TaskEngine;
import cn.polarismesh.polaris.sync.registry.tasks.TaskEngine.RegistrySet;
import cn.polarismesh.polaris.sync.registry.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.registry.utils.DurationUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckScheduler implements FileListener {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckScheduler.class);

    private final ScheduledExecutorService healthCheckExecutor = Executors
            .newScheduledThreadPool(1, new NamedThreadFactory("health-check-worker"));

    private Set<String> tasks = new HashSet<>();

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
        if (!ConfigUtils.verifyHealthCheck(config)) {
            throw new IllegalArgumentException("invalid health check configuration for content " + config.toString());
        }
        reload(config);
    }

    public void reload(Registry registryConfig) {
        synchronized (configLock) {
            HealthCheck healthCheck = registryConfig.getHealthCheck();
            if (null == healthCheck) {
                enable.set(false);
                tasks.clear();
                return;
            }
            boolean enable = healthCheck.getEnable();
            this.enable.set(enable);
            if (!enable) {
                tasks.clear();
                LOG.info("[Health] health check is disabled");
                return;
            }
            Set<String> newTasks = new HashSet<>();
            List<Task> tasksList = registryConfig.getTasksList();
            for (Task task : tasksList) {
                if (task.getEnable()) {
                    newTasks.add(task.getName());
                }
            }
            //compare the new add tasks
            for (String newTask : newTasks) {
                if (!tasks.contains(newTask)) {
                    LOG.info("[Health] schedule health check for task {}", newTask);
                    scheduleHealthCheckForTask(newTask);
                }
            }
            tasks = newTasks;
            intervalMilli = DurationUtils
                    .parseDurationMillis(healthCheck.getInterval(), DefaultValues.DEFAULT_INTERVAL_MS);
        }
    }

    private void scheduleHealthCheckForTask(String taskName) {
        healthCheckExecutor.schedule(new Runnable() {
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
                synchronized (configLock) {
                    boolean scheduleNext = tasks.contains(taskName);
                    if (scheduleNext) {
                        healthCheckExecutor.schedule(this, intervalMilli, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }, intervalMilli, TimeUnit.MILLISECONDS);
    }

    private static RegistryHealthStatus toRegistryHealthStatus(Health health, NamedRegistryCenter registry) {
        return new RegistryHealthStatus(registry.getName(), registry.getRegistry().getType(), registry.getProductName(),
                health.getTotalCount(), health.getErrorCount());
    }

    @Override
    public boolean onFileChanged(byte[] strBytes) {
        RegistryProto.Registry config;
        try {
            config = ConfigUtils.parseFromContent(strBytes);
        } catch (IOException e) {
            LOG.error("[Health] fail to parse to config proto, content {}", new String(strBytes, StandardCharsets.UTF_8), e);
            return false;
        }
        reload(config);
        return true;
    }
}
