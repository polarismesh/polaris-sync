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

package cn.polarismesh.polaris.sync.reporter.plugins.file;

import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus;
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus.Dimension;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.extension.report.StatInfo;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.ReportTarget;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.ReportTarget.TargetType;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class FileReportHandler implements ReportHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FileReportHandler.class);

    private static final Logger HEALTH_LOG = LoggerFactory.getLogger("sync-stat-logger");

    @Override
    public TargetType getType() {
        return TargetType.file;
    }

    @Override
    public void init(ReportTarget reportTarget) {
        LOG.info("[Report] FileReportHandler has been initialized, config {}", reportTarget);
    }

    @Override
    public void reportStat(StatInfo statInfo) {
        Collection<RegistryHealthStatus> registryHealthStatusList = statInfo.getRegistryHealthStatusList();
        StringBuilder reportInfoStr = new StringBuilder();
        reportInfoStr.append("Statis ").append(getCurrentDateStr()).append(":");
        if (CollectionUtils.isEmpty(registryHealthStatusList)) {
            reportInfoStr.append("No API Call");
        } else {
            reportInfoStr.append("\n");
            reportInfoStr.append(String
                    .format("%-48s|%12s|%12s|%12s|%12s|\n", "Name", "Type", "Detail", "Total", "Error"));
            for (RegistryHealthStatus registryHealthStatus : registryHealthStatusList) {
                Dimension dimension = registryHealthStatus.getDimension();
                reportInfoStr.append(String
                        .format("%-48s|%12s|%12s|%12s|%12s|\n", dimension.getName(),
                                dimension.getRegistryType().name(), dimension.getProductionName(),
                                registryHealthStatus.getTotalCount(), registryHealthStatus.getErrorCount()));
            }
        }
        HEALTH_LOG.info(reportInfoStr.toString());
    }

    private static String getCurrentDateStr() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(new Date());
    }
}
