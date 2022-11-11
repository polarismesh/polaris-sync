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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus;
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus.Dimension;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.extension.report.StatInfo;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;

public class StatReportAggregator {

	private static final Logger LOG = LoggerFactory.getLogger(StatReportAggregator.class);

	private ScheduledExecutorService reportExecutor;

	private final Object reloadLock = new Object();

	private final Map<ModelProto.ReportTarget.TargetType, Class<? extends ReportHandler>> reportHandlerTypeMap = new HashMap<>();

	private final Map<ModelProto.ReportTarget.TargetType, ReportHandler> reportHandlerMap = new HashMap<>();

	private final Map<Dimension, RegistryHealthStatus> healthStatusCounts = new ConcurrentHashMap<>();

	private final Object clearLock = new Object();

	private Set<Dimension> dimensionsToClear = new HashSet<>();

	private Collection<ModelProto.ReportTarget> lastReportTargets = new HashSet<>();

	public StatReportAggregator(List<ReportHandler> reportHandlers) {
		for (ReportHandler reportHandler : reportHandlers) {
			reportHandlerTypeMap.put(reportHandler.getType(), reportHandler.getClass());
		}
	}

	public void init(ModelProto.Report report) {
		reload(report);
		reportExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("report-worker"));
		reportExecutor.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				List<ReportHandler> reportHandlers;
				synchronized (reloadLock) {
					if (CollectionUtils.isEmpty(StatReportAggregator.this.reportHandlerMap)) {
						return;
					}
					reportHandlers = new ArrayList<>(reportHandlerMap.values());
				}
				clearUselessDimensions();
				List<RegistryHealthStatus> registryHealthStatuses = new ArrayList<>();
				for (RegistryHealthStatus registryHealthStatus : healthStatusCounts.values()) {
					RegistryHealthStatus newRegistryHealthStatus = new RegistryHealthStatus(
							registryHealthStatus.getDimension(),
							registryHealthStatus.getAndDeleteTotalCount(),
							registryHealthStatus.getAndDeleteErrorCount());
					registryHealthStatuses.add(newRegistryHealthStatus);

				}
				StatInfo statInfo = new StatInfo();
				statInfo.setRegistryHealthStatusList(registryHealthStatuses);
				for (ReportHandler reportHandler : reportHandlers) {
					LOG.debug("[Report] report stat metric to reporter {}", reportHandler.getType());
					reportHandler.reportStat(statInfo);
				}

			}
		}, 60 * 1000, 60 * 1000, TimeUnit.MILLISECONDS);
	}

	public void reload(ModelProto.Report report) {
		Collection<ModelProto.ReportTarget> reportTargets = new HashSet<>();
		if (null != report && !CollectionUtils.isEmpty(report.getTargetsList())) {
			reportTargets.addAll(report.getTargetsList());
		}
		synchronized (reloadLock) {
			if (reportTargets.equals(lastReportTargets)) {
				return;
			}
			lastReportTargets = reportTargets;
			reportHandlerMap.clear();
			for (ModelProto.ReportTarget reportTarget : reportTargets) {
				if (!reportTarget.getEnable()) {
					continue;
				}
				ModelProto.ReportTarget.TargetType type = reportTarget.getType();
				Class<? extends ReportHandler> aClass = reportHandlerTypeMap.get(type);
				if (null == aClass) {
					LOG.error("[Report] report target type {} not found", type);
					continue;
				}
				ReportHandler reportHandler = createReportHandler(aClass);
				if (null != reportHandler) {
					reportHandler.init(reportTarget);
					reportHandlerMap.put(type, reportHandler);
				}
			}
			LOG.info("[Report] success to reload report config, targets {}", reportHandlerMap.keySet());
		}
	}

	private ReportHandler createReportHandler(Class<? extends ReportHandler> aClass) {
		ReportHandler reportHandler;
		try {
			reportHandler = aClass.newInstance();
		}
		catch (Exception e) {
			LOG.error("[Report] fail to create instance for class {}", aClass.getCanonicalName(), e);
			return null;
		}
		return reportHandler;
	}

	public void clearUselessDimensions() {
		// mark and clear dimensions
		Set<Dimension> newDimensionsToClear = new HashSet<>();
		for (Map.Entry<Dimension, RegistryHealthStatus> entry : healthStatusCounts.entrySet()) {
			RegistryHealthStatus value = entry.getValue();
			if (value.getTotalCount() == 0 && value.getErrorCount() == 0) {
				newDimensionsToClear.add(entry.getKey());
			}
		}
		synchronized (clearLock) {
			for (Dimension dimension : newDimensionsToClear) {
				if (dimensionsToClear.contains(dimension)) {
					//重复检查删除
					healthStatusCounts.remove(dimension);
					dimensionsToClear.remove(dimension);
					LOG.info("[Report] report dimension {} has been cleared", dimension);
				}
			}
			dimensionsToClear = newDimensionsToClear;
		}
	}

	public void reportHealthStatus(RegistryHealthStatus registryHealthStatus) {
		RegistryHealthStatus lastHealthStatus = healthStatusCounts
				.putIfAbsent(registryHealthStatus.getDimension(), registryHealthStatus);
		if (null == lastHealthStatus) {
			return;
		}
		synchronized (clearLock) {
			dimensionsToClear.remove(lastHealthStatus.getDimension());
		}
		if (lastHealthStatus == registryHealthStatus) {
			return;
		}
		lastHealthStatus.addValues(registryHealthStatus.getTotalCount(), registryHealthStatus.getErrorCount());
	}

	public void destroy() {
		if (null != reportExecutor) {
			reportExecutor.shutdown();
		}
	}

}
