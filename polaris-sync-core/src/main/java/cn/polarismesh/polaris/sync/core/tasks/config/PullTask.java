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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.core.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.core.utils.TaskUtils;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import cn.polarismesh.polaris.sync.extension.config.ConfigFilesResponse;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class PullTask implements AbstractTask {

	private static final Logger LOG = LoggerFactory.getLogger(cn.polarismesh.polaris.sync.core.tasks.registry.PullTask.class);

	private final Map<ConfigGroup, Collection<ModelProto.Group>> configGroupToMatchGroups = new HashMap<>();

	private final NamedConfigCenter source;

	private final NamedConfigCenter destination;

	public PullTask(NamedConfigCenter source, NamedConfigCenter destination, List<SyncTask.Match> matches) {
		this.source = source;
		this.destination = destination;
		for (SyncTask.Match match : matches) {
			if (ConfigUtils.isEmptyMatch(match)) {
				continue;
			}
			configGroupToMatchGroups.put(
					new ConfigGroup(match.getNamespace(), match.getName()), TaskUtils.verifyGroups(match.getGroups()));
		}
	}


	@Override
	public void run() {
		try {
			// check config_group, add or remove the config_group from destination
			destination.getConfigCenter().updateGroups(configGroupToMatchGroups.keySet());

			// check instances
			for (Map.Entry<ConfigGroup, Collection<ModelProto.Group>> entry : configGroupToMatchGroups.entrySet()) {
				ConfigGroup configGroup = entry.getKey();
				for (ModelProto.Group group : entry.getValue()) {
					ConfigFilesResponse response = source.getConfigCenter().listConfigFile(configGroup);
					if (response.getCode() != StatusCodes.SUCCESS) {
						LOG.warn("[Core][Pull] config fail to list service in source {}, type {}, group {}, code is {}",
								source.getName(), source.getConfigCenter().getType(), group.getName(),
								response.getCode());
						return;
					}
					Collection<ConfigFile> files = handle(source, destination, response.getFiles());
					LOG.debug(
							"[Core][Pull] config prepare to update from registry {}, type {}, service {}, group {}, instances {}",
							source.getName(), source.getConfigCenter().getType(), configGroup, group.getName(), files);
					destination.getConfigCenter().updateConfigFiles(configGroup, files);
				}
			}
		} catch (Throwable e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("[Core] config pull task(source {}) encounter exception {}", source.getName(), sw);
		}
	}

}

