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
import cn.polarismesh.polaris.sync.registry.config.FileListener;
import cn.polarismesh.polaris.sync.registry.config.SyncRegistryProperties;
import cn.polarismesh.polaris.sync.registry.config.WatchManager;
import cn.polarismesh.polaris.sync.registry.healthcheck.HealthCheckScheduler;
import cn.polarismesh.polaris.sync.registry.healthcheck.StatReportAggregator;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.tasks.TaskEngine;
import cn.polarismesh.polaris.sync.registry.utils.ConfigUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistrySyncServer {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrySyncServer.class);

    private final SyncRegistryProperties syncRegistryProperties;

    private final List<RegistryCenter> registryCenters;

    private TaskEngine taskEngine;

    private WatchManager watchManager;

    private HealthCheckScheduler healthCheckReporter;

    private final List<ReportHandler> reportHandlers;

    private StatReportAggregator statReportAggregator;

    public RegistrySyncServer(SyncRegistryProperties syncRegistryProperties,
            List<RegistryCenter> registryCenters, List<ReportHandler> reportHandlers) {
        this.syncRegistryProperties = syncRegistryProperties;
        this.registryCenters = registryCenters;
        this.reportHandlers = reportHandlers;
    }

    public void init() {
        taskEngine = new TaskEngine(registryCenters);
        statReportAggregator = new StatReportAggregator(reportHandlers);
        healthCheckReporter = new HealthCheckScheduler(statReportAggregator, taskEngine);
        List<FileListener> listeners = new ArrayList<>();
        listeners.add(taskEngine);
        listeners.add(healthCheckReporter);
        listeners.add(statReportAggregator);
        watchManager = new WatchManager(listeners);

        String watchPath = syncRegistryProperties.getConfigWatchPath();
        File watchFile = new File(watchPath);
        boolean initByWatched = false;
        long crcValue = 0;
        LOG.info("[Core] try to init by watch file {}", watchPath);
        if (watchFile.exists()) {
            initByWatched = true;
            try {
                byte[] strBytes = FileUtils.readFileToByteArray(watchFile);
                crcValue = ConfigUtils.calcCrc32(strBytes);
                RegistryProto.Registry config = ConfigUtils.parseFromContent(strBytes);
                taskEngine.init(config);
                LOG.info("[Core] engine init by watch file {}", watchPath);
                healthCheckReporter.init(config);
                LOG.info("[Core] health checker init by watch file {}", watchPath);
                statReportAggregator.init(config);
                LOG.info("[Core] stat reporter init by watch file {}", watchPath);
            } catch (Exception e) {
                LOG.error("[Core] fail to init engine by watch file {}", watchPath, e);
                initByWatched = false;
            }
        } else {
            LOG.info("[Core] watch file {} not exists", watchPath);
        }
        String configPath = syncRegistryProperties.getConfigBackupPath();
        File configFile = new File(configPath);
        if (initByWatched) {
            try {
                FileUtils.copyFile(watchFile, configFile);
            } catch (IOException e) {
                LOG.error("[Core]fail to copy watchFile from {} to {}", watchPath, configPath, e);
            }
        } else {
            LOG.info("[Core] try to init by config file {}", configPath);
            if (!configFile.exists()) {
                throw new RuntimeException("config file not found in " + configPath);
            }
            try {
                byte[] strBytes = FileUtils.readFileToByteArray(watchFile);
                RegistryProto.Registry config = ConfigUtils.parseFromContent(strBytes);
                taskEngine.init(config);
                LOG.info("[Core] engine init by config file {}", configPath);
                healthCheckReporter.init(config);
                LOG.info("[Core] health checker init by config file {}", configPath);
                statReportAggregator.init(config);
                LOG.info("[Core] stat reporter init by watch file {}", watchPath);
            } catch (Exception e) {
                LOG.error("[Core] fail to init engine by config file {}", configPath, e);
            }
        }
        watchManager.start(watchPath, crcValue, configPath);
    }

    public void destroy() {
        if (null != taskEngine) {
            taskEngine.destroy();
        }
        if (null != watchManager) {
            watchManager.destroy();
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
