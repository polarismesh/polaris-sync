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

import cn.polarismesh.polaris.sync.config.pb.ConfigProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigInitRequest {

	private final String sourceName;

	private final ConfigProto.ConfigEndpoint.ConfigType sourceType;

	private final ConfigProto.ConfigEndpoint configEndpoint;

	public ConfigInitRequest(String sourceName, ConfigProto.ConfigEndpoint.ConfigType sourceType,
			ConfigProto.ConfigEndpoint configEndpoint) {
		this.sourceName = sourceName;
		this.sourceType = sourceType;
		this.configEndpoint = configEndpoint;
	}

	public ConfigProto.ConfigEndpoint.ConfigType getSourceType() {
		return sourceType;
	}

	public String getSourceName() {
		return sourceName;
	}

	public ConfigProto.ConfigEndpoint getConfigEndpoint() {
		return configEndpoint;
	}
}
