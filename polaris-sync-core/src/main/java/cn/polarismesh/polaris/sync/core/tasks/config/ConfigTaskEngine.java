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

package cn.polarismesh.polaris.sync.core.tasks.config;

import java.util.List;

import cn.polarismesh.polaris.sync.core.tasks.AbstractTaskEngine;
import cn.polarismesh.polaris.sync.core.tasks.NamedResourceCenter;
import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.core.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.extension.InitRequest;
import cn.polarismesh.polaris.sync.extension.ResourceCenter;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigInitRequest;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigTaskEngine extends AbstractTaskEngine<ConfigCenter, ConfigSyncTask> {

	private static final Logger LOG = LoggerFactory.getLogger(ConfigTaskEngine.class);

	public ConfigTaskEngine(List<ConfigCenter> configCenters) {
		super("config");
		for (ConfigCenter center : configCenters) {
			typeClassMap.put(center.getType(), center.getClass());
		}
	}

	@Override
	protected Runnable buildPullTask(NamedResourceCenter<ConfigCenter> source, NamedResourceCenter<ConfigCenter> dest, List<SyncTask.Match> matches) {
		PullTask pullTask = new PullTask(
				new NamedConfigCenter(source.getName(), source.getProductName(), source.getCenter()),
				new NamedConfigCenter(dest.getName(), dest.getProductName(), dest.getCenter()),
				matches
		);
		return pullTask;
	}

	@Override
	protected Runnable buildWatchTask(NamedResourceCenter<ConfigCenter> source, NamedResourceCenter<ConfigCenter> dest, SyncTask.Match match) {
		return new WatchTask(
				watchTasks,
				new NamedConfigCenter(source.getName(), source.getProductName(), source.getCenter()),
				new NamedConfigCenter(dest.getName(), dest.getProductName(), dest.getCenter()),
				match,
				commonExecutor,
				watchExecutor
		);
	}

	@Override
	protected Runnable buildUnWatchTask(NamedResourceCenter<ConfigCenter> center, SyncTask.Match match) {
		return new UnwatchTask(
				new NamedConfigCenter(center.getName(), center.getProductName(), center.getCenter()),
				match
		);
	}


	@Override
	protected void verifyTask(List<ConfigSyncTask> tasks, List<ModelProto.Method> methods) {
		if (!ConfigUtils.verifyTasks(tasks, typeClassMap.keySet(), methods)) {
			throw new IllegalArgumentException("invalid configuration content " + tasks);
		}
	}

	@Override
	protected InitRequest buildInitRequest(String sourceName, ResourceType resourceType, ResourceEndpoint endpoint) {
		return new ConfigInitRequest(sourceName, resourceType, endpoint);
	}
}
