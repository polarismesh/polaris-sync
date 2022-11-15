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

package cn.polarismesh.polaris.sync.core.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class ConfigUtils {

	private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);

	public static boolean isEmptyMatch(SyncTask.Match match) {
		return !StringUtils.hasText(match.getNamespace()) && !StringUtils.hasText(match.getName()) && match.getGroups()
				.size() == 0;
	}

	public static <T extends SyncTask> boolean verifyTasks(
			List<T> tasks,
			Set<ResourceType> types,
			List<ModelProto.Method> methods,
			Function<T, Throwable>... filters) {

		LOG.info("[Core] start to verify config tasks config {}", tasks);

		Collection<Function<T, Throwable>> functions = Arrays.asList(filters);

		Set<String> taskNames = new HashSet<>();
		boolean hasTask = false;
		for (T task : tasks) {
			if (!task.isEnable()) {
				continue;
			}
			hasTask = true;
			String name = task.getName();
			if (!StringUtils.hasText(name)) {
				LOG.error("[Core] task name is empty");
				return false;
			}
			if (taskNames.contains(name)) {
				LOG.error("[Core] duplicate task name {}", name);
				return false;
			}
			taskNames.add(name);
			ResourceEndpoint source = task.getSource();
			if (!verifyEndpoint(source, name, types)) {
				return false;
			}
			ResourceEndpoint destination = task.getDestination();
			if (!verifyEndpoint(destination, name, types)) {
				return false;
			}
			List<SyncTask.Match> matchList = task.getMatchList();
			if (!verifyMatch(matchList, name)) {
				return false;
			}

			for (Function<T, Throwable> function : functions) {
				Throwable ex = function.apply(task);
				if (Objects.nonNull(ex)) {
					return false;
				}
			}
		}
		return CommonUtils.verifyMethod(hasTask, methods);
	}

	public static boolean verifyMatch(List<SyncTask.Match> matches, String taskName) {
		if (CollectionUtils.isEmpty(matches)) {
			return true;
		}
		for (SyncTask.Match match : matches) {
			if (isEmptyMatch(match)) {
				continue;
			}
			String namespace = match.getNamespace();
			String name = match.getName();
			List<ModelProto.Group> groups = match.getGroups();
			if (!StringUtils.hasText(namespace)) {
				LOG.error("[Core] match namespace is empty, task {}", taskName);
				return false;
			}
			if (!StringUtils.hasText(name)) {
				LOG.error("[Core] match config group is empty, task {}", taskName);
				return false;
			}
			if (CollectionUtils.isEmpty(groups)) {
				continue;
			}
			for (ModelProto.Group group : groups) {
				if (!StringUtils.hasText(group.getName())) {
					LOG.error("[Core] match group name is invalid, task {}", taskName);
					return false;
				}
				Map<String, String> metadataMap = group.getMetadataMap();
				if (metadataMap.size() > 0) {
					for (Map.Entry<String, String> entry : metadataMap.entrySet()) {
						if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
							LOG.error("[Core] match group {} metadata is invalid, task {}", group.getName(), taskName);
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	private static boolean verifyEndpoint(
			ResourceEndpoint endpoint, String taskName, Set<ResourceType> types) {
		String name = endpoint.getName();
		if (!StringUtils.hasText(name)) {
			LOG.error("[Core] endpoint name is empty, task {}", taskName);
			return false;
		}
		List<String> addressesList = endpoint.getServerAddresses();
		if (CollectionUtils.isEmpty(addressesList)) {
			LOG.error("[Core] addresses is empty for endpoint {}, task {}", name, taskName);
			return false;
		}
		ResourceType type = endpoint.getResourceType();
		if (ResourceType.UNKNOWN.equals(type)) {
			LOG.error("[Core] unknown endpoint type for {}, task {}", name, taskName);
			return false;
		}
		if (!types.contains(type)) {
			LOG.error("[Core] unsupported endpoint type {} for {}, task {}", type.name(), name, taskName);
			return false;
		}
		return true;
	}

}
