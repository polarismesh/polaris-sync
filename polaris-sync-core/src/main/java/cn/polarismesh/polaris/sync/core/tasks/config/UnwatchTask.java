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

import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class UnwatchTask implements Runnable {
	private final NamedConfigCenter source;

	private final ConfigGroup configGroup;

	public UnwatchTask(NamedConfigCenter source, SyncTask.Match match) {
		this.source = source;
		this.configGroup = new ConfigGroup(match.getNamespace(), match.getName());
	}

	@Override
	public void run() {
		this.source.getConfigCenter().unwatch(configGroup);
	}

	public ConfigGroup getConfigGroup() {
		return configGroup;
	}
}
