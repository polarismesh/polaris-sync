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

package cn.polarismesh.polaris.sync.core.healthcheck;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.core.tasks.AbstractTaskEngine;
import cn.polarismesh.polaris.sync.core.tasks.NamedResourceCenter;
import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.core.utils.CommonUtils;
import cn.polarismesh.polaris.sync.core.utils.DurationUtils;
import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(HealthCheckScheduler.class);

	private final ScheduledExecutorService healthCheckExecutor = Executors
			.newScheduledThreadPool(1, new NamedThreadFactory("health-check-worker"));

	private final ExecutorService reloadExecutor = Executors.newFixedThreadPool(
			1, new NamedThreadFactory("reload-worker")
	);

	private final Map<String, ScheduledFuture<?>> tasks = new HashMap<>();

	private final AtomicBoolean enable = new AtomicBoolean(false);

	private final Object configLock = new Object();

	private final AbstractTaskEngine engine;

	private final StatReportAggregator statReportAggregator;

	private long intervalMilli;

	public HealthCheckScheduler(StatReportAggregator statReportAggregator, AbstractTaskEngine engine) {
		this.engine = engine;
		this.statReportAggregator = statReportAggregator;
		this.intervalMilli = DefaultValues.DEFAULT_INTERVAL_MS;
	}

	public void destroy() {
		healthCheckExecutor.shutdown();
	}

	public void init(ModelProto.HealthCheck healthCheck) {
		reload(healthCheck);
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

	public void reload(ModelProto.HealthCheck healthCheck) {
		if (!CommonUtils.verifyHealthCheck(healthCheck)) {
			throw new IllegalArgumentException("invalid health check configuration for content " + healthCheck.toString());
		}

		synchronized (configLock) {
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
			List<SyncTask> tasksList = engine.getTasks();
			Set<String> taskNames = new HashSet<>();
			for (SyncTask task : tasksList) {
				if (task.isEnable()) {
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
					LOG.info("[Health] cancel scheduled health check for task {}", name);
					future.cancel(true);
				}
			}
		}
	}

	private ScheduledFuture<?> scheduleHealthCheckForTask(String taskName) {
		return healthCheckExecutor.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				AbstractTaskEngine.ResourceSet resourceSet = engine.getResource(taskName);
				if (Objects.isNull(resourceSet)) {
					LOG.warn("[Health] registry set not found for task {}", taskName);
					return;
				}
				NamedResourceCenter source = resourceSet.getSource();
				NamedResourceCenter dest = resourceSet.getDest();
				Health srcHealth = source.getCenter().healthCheck();
				Health dstHealth = dest.getCenter().healthCheck();
				RegistryHealthStatus srcHealthStatus = toRegistryHealthStatus(srcHealth, source);
				RegistryHealthStatus dstHealthStatus = toRegistryHealthStatus(dstHealth, dest);
				statReportAggregator.reportHealthStatus(srcHealthStatus);
				statReportAggregator.reportHealthStatus(dstHealthStatus);
			}
		}, intervalMilli, intervalMilli, TimeUnit.MILLISECONDS);
	}

	private static RegistryHealthStatus toRegistryHealthStatus(Health health, NamedResourceCenter registry) {
		return new RegistryHealthStatus(registry.getName(), registry.getCenter().getType(), registry.getProductName(),
				health.getTotalCount(), health.getErrorCount());
	}

}
