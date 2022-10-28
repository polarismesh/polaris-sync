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

import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigGroup {

	private String namespace;

	private String name;

	public ConfigGroup() {
	}

	public ConfigGroup(String namespace, String name) {
		this.namespace = namespace;
		this.name = name;
	}

	public String getNamespace() {
		return namespace;
	}

	void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getName() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String namespace;
		private String name;

		private Builder() {
		}

		public Builder namespace(String namespace) {
			this.namespace = namespace;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public ConfigGroup build() {
			ConfigGroup configGroup = new ConfigGroup();
			configGroup.setNamespace(namespace);
			configGroup.setName(name);
			return configGroup;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ConfigGroup)) return false;
		ConfigGroup that = (ConfigGroup) o;
		return Objects.equals(getNamespace(), that.getNamespace()) && Objects.equals(getName(), that.getName());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getNamespace(), getName());
	}

	@Override
	public String toString() {
		return "ConfigGroup{" +
				"namespace='" + namespace + '\'' +
				", name='" + name + '\'' +
				'}';
	}
}
