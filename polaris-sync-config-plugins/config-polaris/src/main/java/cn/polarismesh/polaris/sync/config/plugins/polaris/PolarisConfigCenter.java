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

package cn.polarismesh.polaris.sync.config.plugins.polaris;

import java.util.Collection;

import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import cn.polarismesh.polaris.sync.extension.config.ConfigFilesResponse;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;
import cn.polarismesh.polaris.sync.extension.config.ConfigInitRequest;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import com.tencent.polaris.client.pb.ResponseProto;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class PolarisConfigCenter  implements ConfigCenter {
	@Override
	public RegistryProto.ConfigEndpoint.ConfigType getType() {
		return null;
	}

	@Override
	public void init(ConfigInitRequest request) {

	}

	@Override
	public void destroy() {

	}

	@Override
	public ResponseProto.DiscoverResponse listNamespaces() {
		return null;
	}

	@Override
	public ConfigFilesResponse listConfigFile(ConfigGroup configGroup) {
		return null;
	}

	@Override
	public boolean watch(ConfigGroup group, ResponseListener eventListener) {
		return false;
	}

	@Override
	public void unwatch(ConfigGroup group) {

	}

	@Override
	public void updateGroups(Collection<ConfigGroup> group) {

	}

	@Override
	public void updateConfigFiles(ConfigGroup group, Collection<ConfigFile> files) {

	}

	@Override
	public Health healthCheck() {
		return null;
	}
}
