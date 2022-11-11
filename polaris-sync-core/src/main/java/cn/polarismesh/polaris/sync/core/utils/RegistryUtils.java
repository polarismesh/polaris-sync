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

import java.util.List;

import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class RegistryUtils {

	private static final Logger LOG = LoggerFactory.getLogger(RegistryUtils.class);

	public static boolean isEmptyMatch(SyncTask.Match match) {
		return !StringUtils.hasText(match.getNamespace()) && !StringUtils.hasText(match.getName()) && match.getGroups()
				.size() == 0;
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
			List<ModelProto.Group> groups = match.getGroups();
			if (namespace.contains(".")) {
				LOG.error("[Core] match namespace contains DOT, task {}", taskName);
				return false;
			}
			for (ModelProto.Group group : groups) {
				if (group.getName().contains(".")) {
					LOG.error("[Core] match group name contains DOT, task {}", taskName);
					return false;
				}
			}
		}
		return true;
	}

}
