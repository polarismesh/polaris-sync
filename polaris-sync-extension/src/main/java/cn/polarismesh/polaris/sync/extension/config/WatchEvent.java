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


package cn.polarismesh.polaris.sync.extension.config;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class WatchEvent {

	private ConfigGroup configGroup;

	private List<ConfigFile> add = new ArrayList<>();

	private List<ConfigFile> update = new ArrayList<>();

	private List<ConfigFile> remove = new ArrayList<>();

	public void appendAdd(ConfigFile file) {
		add.add(file);
	}

	public void appendUpdate(ConfigFile file) {
		update.add(file);
	}

	public void appendRemote(ConfigFile file) {
		remove.add(file);
	}

	public ConfigGroup getConfigGroup() {
		return configGroup;
	}

	public void setConfigGroup(ConfigGroup configGroup) {
		this.configGroup = configGroup;
	}

	public List<ConfigFile> getAdd() {
		return Collections.unmodifiableList(add);
	}

	public void setAdd(List<ConfigFile> add) {
		this.add = add;
	}

	public List<ConfigFile> getUpdate() {
		return Collections.unmodifiableList(update);
	}

	public void setUpdate(List<ConfigFile> update) {
		this.update = update;
	}

	public List<ConfigFile> getRemove() {
		return Collections.unmodifiableList(remove);
	}

	public void setRemove(List<ConfigFile> remove) {
		this.remove = remove;
	}
}
