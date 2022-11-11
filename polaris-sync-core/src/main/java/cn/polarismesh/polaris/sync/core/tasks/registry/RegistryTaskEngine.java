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

package cn.polarismesh.polaris.sync.core.tasks.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

import cn.polarismesh.polaris.sync.core.tasks.AbstractTaskEngine;
import cn.polarismesh.polaris.sync.core.tasks.NamedResourceCenter;
import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.core.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.core.utils.RegistryUtils;
import cn.polarismesh.polaris.sync.extension.InitRequest;
import cn.polarismesh.polaris.sync.extension.ResourceCenter;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistryTaskEngine extends AbstractTaskEngine<RegistrySyncTask> {

	private static final Logger LOG = LoggerFactory.getLogger(RegistryTaskEngine.class);

	protected final Map<RegistryProto.RegistryEndpoint.RegistryType, Class<? extends RegistryCenter>> registryTypeMap = new HashMap<>();

	public RegistryTaskEngine(List<RegistryCenter> registries) {
		super("registry");
		for (RegistryCenter registry : registries) {
            typeClassMap.put(registry.getType(), registry.getClass());
        }
	}

	@Override
	protected Runnable buildPullTask(NamedResourceCenter source, NamedResourceCenter dest, List<SyncTask.Match> matches) {
		return null;
	}

	@Override
	protected Runnable buildWatchTask(NamedResourceCenter source, NamedResourceCenter dest, SyncTask.Match match) {
		return null;
	}

	@Override
	protected Runnable buildUnWatchTask(ResourceCenter center, SyncTask.Match match) {
		return null;
	}

	@Override
	protected void verifyTask(List<RegistrySyncTask> tasks, List<ModelProto.Method> methods) {
		Function<RegistrySyncTask, Throwable> filter = task -> {
			if (!RegistryUtils.verifyMatch(task.getMatchList(), task.getName())) {
				return new IllegalArgumentException();
			}
			return null;
		};

		if (!ConfigUtils.verifyTasks(tasks, typeClassMap.keySet(), methods, filter)) {
			throw new IllegalArgumentException("invalid configuration content " + tasks);
		}
	}

	@Override
	protected InitRequest buildInitRequest(String sourceName, ResourceType resourceType, ResourceEndpoint endpoint) {
		return null;
	}
}
