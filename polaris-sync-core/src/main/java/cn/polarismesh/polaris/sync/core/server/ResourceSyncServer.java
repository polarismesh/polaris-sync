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

package cn.polarismesh.polaris.sync.core.server;

import java.util.List;
import java.util.Objects;

import cn.polarismesh.polaris.sync.core.healthcheck.HealthCheckScheduler;
import cn.polarismesh.polaris.sync.core.healthcheck.StatReportAggregator;
import cn.polarismesh.polaris.sync.core.taskconfig.ConfigProviderManager;
import cn.polarismesh.polaris.sync.core.taskconfig.SyncProperties;
import cn.polarismesh.polaris.sync.core.tasks.AbstractTaskEngine;
import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.extension.ResourceCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigListener;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ResourceSyncServer<T extends SyncTask, M extends Message, P extends SyncProperties> implements ConfigListener<M> {

	private static final Logger LOG = LoggerFactory.getLogger(ResourceSyncServer.class);

	private ConfigProviderManager<M, P> configManager;

	private AbstractTaskEngine<T> engine;

	private HealthCheckScheduler healthCheckReporter;

	private List<ReportHandler> reportHandlers;

	private StatReportAggregator statReportAggregator;

	protected void initResourceSyncServer(
			ConfigProviderManager<M, P> manager,
			AbstractTaskEngine<T> engine,
			List<ReportHandler> reportHandlers) throws Exception {

		this.configManager = manager;
		this.engine = engine;
		this.reportHandlers = reportHandlers;

        this.statReportAggregator = new StatReportAggregator(reportHandlers);
        this.healthCheckReporter = new HealthCheckScheduler(statReportAggregator, engine);
	}

	public void init() {
		try {
			M config = configManager.getConfig();
            List<T> tasks = parseTasks(config);
            List<ModelProto.Method> methods = parseMethods(config);
            ModelProto.HealthCheck healthCheck = parseHealthCheck(config);
			ModelProto.Report report = parseReport(config);
			engine.init(tasks, methods);
			healthCheckReporter.init(healthCheck);
			statReportAggregator.init(report);
		}
		catch (Exception e) {
			LOG.error("[Core] fail to init engine", e);
		}
	}

	public void destroy() {
		if (Objects.nonNull(engine)) {
			engine.destroy();
		}
		if (Objects.nonNull(configManager)) {
			configManager.destroy();
		}
		if (null != healthCheckReporter) {
			healthCheckReporter.destroy();
		}
		if (null != statReportAggregator) {
			statReportAggregator.destroy();
		}
	}

    protected abstract List<T> parseTasks(M m);

    protected abstract List<ModelProto.Method> parseMethods(M m);

    protected abstract ModelProto.HealthCheck parseHealthCheck(M m);

	protected abstract ModelProto.Report parseReport(M m);

	public AbstractTaskEngine getEngine() {
		return engine;
	}

    @Override
    public void onChange(M registry) {

    }
}
