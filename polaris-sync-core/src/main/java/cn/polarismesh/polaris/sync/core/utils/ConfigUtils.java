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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.ConfigEndpoint.ConfigType;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class ConfigUtils {

	private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);

	public static boolean isEmptyMatch(RegistryProto.ConfigMatch match) {
		return !StringUtils.hasText(match.getNamespace()) && !StringUtils.hasText(match.getConfigGroup()) && match.getGroupsCount() == 0;
	}

	public static boolean verifyTasks(RegistryProto.Registry registry, Set<ConfigType> registryTypes) {
		LOG.info("[Core] start to verify config tasks config {}", registry);
		List<RegistryProto.ConfigTask> tasks = registry.getConfigTasksList();
		Set<String> taskNames = new HashSet<>();
		boolean hasTask = false;
		for (RegistryProto.ConfigTask task : tasks) {
			if (!task.getEnable()) {
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
			RegistryProto.ConfigEndpoint source = task.getSource();
			if (!verifyEndpoint(source, name, registryTypes)) {
				return false;
			}
			RegistryProto.ConfigEndpoint destination = task.getDestination();
			if (!verifyEndpoint(destination, name, registryTypes)) {
				return false;
			}
			List<RegistryProto.ConfigMatch> matchList = task.getMatchList();
			if (!verifyMatch(matchList, name)) {
				return false;
			}
		}
		return CommonUtils.verifyMethod(hasTask, registry);
	}

	private static boolean verifyMatch(List<RegistryProto.ConfigMatch> matches, String taskName) {
		if (CollectionUtils.isEmpty(matches)) {
			return true;
		}
		for (RegistryProto.ConfigMatch match : matches) {
			if (isEmptyMatch(match)) {
				continue;
			}
			String namespace = match.getNamespace();
			String configGroup = match.getConfigGroup();
			List<Group> groups = match.getGroupsList();
			if (!StringUtils.hasText(namespace)) {
				LOG.error("[Core] match namespace is empty, task {}", taskName);
				return false;
			}
			if (!StringUtils.hasText(configGroup)) {
				LOG.error("[Core] match config group is empty, task {}", taskName);
				return false;
			}
			if (CollectionUtils.isEmpty(groups)) {
				continue;
			}
			for (Group group : groups) {
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
			RegistryProto.ConfigEndpoint endpoint, String taskName, Set<ConfigType> supportedTypes) {
		String name = endpoint.getName();
		if (!StringUtils.hasText(name)) {
			LOG.error("[Core] endpoint name is empty, task {}", taskName);
			return false;
		}
		List<String> addressesList = endpoint.getServer().getAddressesList();
		if (CollectionUtils.isEmpty(addressesList)) {
			LOG.error("[Core] addresses is empty for endpoint {}, task {}", name, taskName);
			return false;
		}
		ConfigType type = endpoint.getType();
		if (ConfigType.unknown.equals(type)) {
			LOG.error("[Core] unknown endpoint type for {}, task {}", name, taskName);
			return false;
		}
		if (!supportedTypes.contains(type)) {
			LOG.error("[Core] unsupported endpoint type {} for {}, task {}", type.name(), name, taskName);
			return false;
		}
		return true;
	}

}
