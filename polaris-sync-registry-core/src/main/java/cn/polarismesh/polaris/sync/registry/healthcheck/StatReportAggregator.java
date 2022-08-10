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
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus;
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus.Dimension;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.extension.report.StatInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.util.CollectionUtils;

public class StatReportAggregator {

    private final List<ReportHandler> reportHandlers;

    private final ScheduledExecutorService reportExecutor = Executors
            .newSingleThreadScheduledExecutor(new NamedThreadFactory("report-worker"));

    private final Map<Dimension, RegistryHealthStatus> healthStatusCounts = new ConcurrentHashMap<>();

    public StatReportAggregator(List<ReportHandler> reportHandlers) {
        this.reportHandlers = reportHandlers;
        reportExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                if (CollectionUtils.isEmpty(StatReportAggregator.this.reportHandlers)) {
                    return;
                }
                StatInfo statInfo = new StatInfo();
                List<RegistryHealthStatus> registryHealthStatuses = new ArrayList<>(healthStatusCounts.values());
                statInfo.setRegistryHealthStatusList(registryHealthStatuses);
                for (ReportHandler reportHandler : StatReportAggregator.this.reportHandlers) {
                    reportHandler.reportStat(statInfo);
                }
            }
        }, 60 * 1000, 60 * 1000, TimeUnit.MILLISECONDS);
    }

    public void deleteDimensions(Collection<Dimension> dimensions) {
        for (Dimension dimension : dimensions) {
            healthStatusCounts.remove(dimension);
        }
    }

    public void reportHealthStatus(RegistryHealthStatus registryHealthStatus) {
        RegistryHealthStatus lastHealthStatus = healthStatusCounts
                .putIfAbsent(registryHealthStatus.getDimension(), registryHealthStatus);
        if (null == lastHealthStatus || lastHealthStatus == registryHealthStatus) {
            return;
        }
        lastHealthStatus.addValues(registryHealthStatus.getTotalCount(), registryHealthStatus.getErrorCount());
    }

    public void destroy() {
        reportExecutor.shutdown();
    }
}
