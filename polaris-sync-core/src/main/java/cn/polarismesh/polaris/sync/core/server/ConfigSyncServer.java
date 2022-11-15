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

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import cn.polarismesh.polaris.sync.config.pb.ConfigProto;
import cn.polarismesh.polaris.sync.core.taskconfig.ConfigProviderManager;
import cn.polarismesh.polaris.sync.core.taskconfig.SyncConfigProperties;
import cn.polarismesh.polaris.sync.core.tasks.config.ConfigSyncTask;
import cn.polarismesh.polaris.sync.core.tasks.config.ConfigTaskEngine;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProviderFactory;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigSyncServer extends ResourceSyncServer<ConfigCenter, ConfigSyncTask, ConfigProto.Config, SyncConfigProperties> {

	public ConfigSyncServer(SyncConfigProperties properties, List<ConfigCenter> centers, List<ReportHandler> reportHandlers) throws Exception {

		List<ConfigProviderFactory> factories = new ArrayList<>();
		ServiceLoader.load(ConfigProviderFactory.class).iterator().forEachRemaining(factories::add);

		ConfigProviderManager<ConfigProto.Config, SyncConfigProperties> manager = new ConfigProviderManager<>(factories, properties, ConfigProto.Config::newBuilder);
		ConfigTaskEngine engine = new ConfigTaskEngine(centers);

		initResourceSyncServer(manager, engine, reportHandlers);
	}


	@Override
	protected List<ConfigSyncTask> parseTasks(ConfigProto.Config config) {
		List<ConfigSyncTask> tasks = new ArrayList<>();
		List<ConfigProto.Task> pbTasks = config.getTasksList();

		for (ConfigProto.Task task : pbTasks) {
			tasks.add(ConfigSyncTask.parse(task));
		}

		return tasks;
	}

	@Override
	protected List<ModelProto.Method> parseMethods(ConfigProto.Config config) {
		return config.getMethodsList();
	}

	@Override
	protected ModelProto.HealthCheck parseHealthCheck(ConfigProto.Config config) {
		return config.getHealthCheck();
	}

	@Override
	protected ModelProto.Report parseReport(ConfigProto.Config config) {
		return config.getReport();
	}

	@Override
	public void onChange(ConfigProto.Config config) {
		List<ConfigSyncTask> tasks = parseTasks(config); List<ModelProto.Method> methods = parseMethods(config);
		ModelProto.HealthCheck healthCheck = parseHealthCheck(config); ModelProto.Report report = parseReport(config);

		engine.reload(tasks, methods); healthCheckReporter.reload(healthCheck); statReportAggregator.reload(report);
	}
}
