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

import cn.polarismesh.polaris.sync.core.taskconfig.ConfigProviderManager;
import cn.polarismesh.polaris.sync.core.taskconfig.SyncRegistryProperties;
import cn.polarismesh.polaris.sync.core.tasks.registry.RegistrySyncTask;
import cn.polarismesh.polaris.sync.core.tasks.registry.RegistryTaskEngine;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProviderFactory;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RegistrySyncServer extends ResourceSyncServer<RegistryCenter, RegistrySyncTask, RegistryProto.Registry, SyncRegistryProperties> {

	public RegistrySyncServer(SyncRegistryProperties properties, List<RegistryCenter> centers, List<ReportHandler> reportHandlers) throws Exception {

		List<ConfigProviderFactory> factories = new ArrayList<>();
		ServiceLoader.load(ConfigProviderFactory.class).iterator().forEachRemaining(factories::add);

		ConfigProviderManager<RegistryProto.Registry, SyncRegistryProperties> manager = new ConfigProviderManager<>(factories, properties, RegistryProto.Registry::newBuilder);
		RegistryTaskEngine engine = new RegistryTaskEngine(centers);

		initResourceSyncServer(manager, engine, reportHandlers);
	}

	@Override
	protected List<RegistrySyncTask> parseTasks(RegistryProto.Registry registry) {
		List<RegistrySyncTask> tasks = new ArrayList<>();

		List<RegistryProto.Task> pbTasks = registry.getTasksList();

		for (RegistryProto.Task task : pbTasks) {
			tasks.add(RegistrySyncTask.parse(task));
		}

		return tasks;
	}

	@Override
	protected List<ModelProto.Method> parseMethods(RegistryProto.Registry registry) {
		return registry.getMethodsList();
	}

	@Override
	protected ModelProto.HealthCheck parseHealthCheck(RegistryProto.Registry registry) {
		return registry.getHealthCheck();
	}

	@Override
	protected ModelProto.Report parseReport(RegistryProto.Registry registry) {
		return registry.getReport();
	}

	@Override
	public void onChange(RegistryProto.Registry config) {
		List<RegistrySyncTask> tasks = parseTasks(config);
		List<ModelProto.Method> methods = parseMethods(config);
		ModelProto.HealthCheck healthCheck = parseHealthCheck(config);
		ModelProto.Report report = parseReport(config);

		engine.reload(tasks, methods);
		healthCheckReporter.reload(healthCheck);
		statReportAggregator.reload(report);
	}
}
