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

import java.util.Collection;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigFilesResponse {

	private int code;

	private String info;

	Collection<ConfigFile> files;

	public int getCode() {
		return code;
	}

	public String getInfo() {
		return info;
	}

	public Collection<ConfigFile> getFiles() {
		return files;
	}

	public static Builder builder() {
		return new Builder();
	}


	public static final class Builder {
		private int code;
		private String info;
		private Collection<ConfigFile> files;

		private Builder() {
		}

		public Builder code(int code) {
			this.code = code;
			return this;
		}

		public Builder info(String info) {
			this.info = info;
			return this;
		}

		public Builder files(Collection<ConfigFile> files) {
			this.files = files;
			return this;
		}

		public ConfigFilesResponse build() {
			ConfigFilesResponse configFilesResponse = new ConfigFilesResponse();
			configFilesResponse.files = this.files;
			configFilesResponse.info = this.info;
			configFilesResponse.code = this.code;
			return configFilesResponse;
		}
	}
}
