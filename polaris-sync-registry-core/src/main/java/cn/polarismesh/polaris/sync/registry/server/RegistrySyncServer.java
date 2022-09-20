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

package cn.polarismesh.polaris.sync.registry.server;

import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.registry.config.ConfigProviderManager;
import cn.polarismesh.polaris.sync.registry.healthcheck.HealthCheckScheduler;
import cn.polarismesh.polaris.sync.registry.healthcheck.StatReportAggregator;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.tasks.TaskEngine;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistrySyncServer {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrySyncServer.class);

    private final ConfigProviderManager providerManager;

    private final List<RegistryCenter> registryCenters;

    private TaskEngine taskEngine;

    private HealthCheckScheduler healthCheckReporter;

    private final List<ReportHandler> reportHandlers;

    private StatReportAggregator statReportAggregator;

    public RegistrySyncServer(ConfigProviderManager providerManager,
            List<RegistryCenter> registryCenters, List<ReportHandler> reportHandlers) {
        this.providerManager = providerManager;
        this.registryCenters = registryCenters;
        this.reportHandlers = reportHandlers;
    }

    public void init() {
        taskEngine = new TaskEngine(registryCenters);
        statReportAggregator = new StatReportAggregator(reportHandlers);
        healthCheckReporter = new HealthCheckScheduler(statReportAggregator, taskEngine);
        providerManager.addListener(taskEngine);
        providerManager.addListener(healthCheckReporter);
        providerManager.addListener(statReportAggregator);

        try {
            RegistryProto.Registry config = providerManager.getConfig();
            taskEngine.init(config);
            healthCheckReporter.init(config);
            statReportAggregator.init(config);
        } catch (Exception e) {
            LOG.error("[Core] fail to init engine", e);
        }
    }

    public void destroy() {
        if (null != taskEngine) {
            taskEngine.destroy();
        }
        if (null != providerManager) {
            providerManager.destroy();
        }
        if (null != healthCheckReporter) {
            healthCheckReporter.destroy();
        }
        if (null != statReportAggregator) {
            statReportAggregator.destroy();
        }
    }

    public TaskEngine getTaskEngine() {
        return taskEngine;
    }
}
