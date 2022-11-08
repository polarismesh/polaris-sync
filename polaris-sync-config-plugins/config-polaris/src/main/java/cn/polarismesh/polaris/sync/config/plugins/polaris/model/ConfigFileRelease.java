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

package cn.polarismesh.polaris.sync.config.plugins.polaris.model;

import java.util.Date;
import java.util.Objects;

import cn.polarismesh.polaris.sync.extension.config.RecordInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigFileRelease implements RecordInfo {

	@JsonProperty("id")
	private long id;

	@JsonProperty("name")
	private String name;

	@JsonProperty("namespace")
	private String namespace;

	@JsonProperty("group")
	private String group;

	@JsonProperty("fileName")
	private String fileName;

	@JsonProperty("content")
	private String content;

	@JsonProperty("md5")
	private String md5;

	@JsonProperty("version")
	private long version;

	@JsonProperty("modifyTime")
	private Date modifyTime;

	private boolean valid = true;

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getNamespace() {
		return namespace;
	}

	public String getGroup() {
		return group;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContent() {
		return content;
	}

	public String getMd5() {
		return md5;
	}

	public long getVersion() {
		return version;
	}

	public Date getModifyTime() {
		return modifyTime;
	}

	@Override
	public String keyInfo() {
		return String.format("%s@@%s@@%s", namespace, group, fileName);
	}

	@Override
	public boolean isValid() {
		return valid;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ConfigFileRelease)) return false;
		ConfigFileRelease that = (ConfigFileRelease) o;
		return id == that.id && version == that.version && Objects.equals(namespace, that.namespace)
				&& Objects.equals(group, that.group) && Objects.equals(fileName, that.fileName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, namespace, group, fileName, version);
	}

	@Override
	public String toString() {
		return "ConfigFileRelease{" +
				"id=" + id +
				", name='" + name + '\'' +
				", namespace='" + namespace + '\'' +
				", group='" + group + '\'' +
				", fileName='" + fileName + '\'' +
				", content='" + content + '\'' +
				", md5='" + md5 + '\'' +
				", version=" + version +
				", modifyTime=" + modifyTime +
				", valid=" + valid +
				'}';
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private long id;
		private String name;
		private String namespace;
		private String group;
		private String fileName;
		private String content;
		private String md5;
		private long version;
		private Date modifyTime;
		private boolean valid;

		private Builder() {
		}

		public Builder id(long id) {
			this.id = id;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder namespace(String namespace) {
			this.namespace = namespace;
			return this;
		}

		public Builder group(String group) {
			this.group = group;
			return this;
		}

		public Builder fileName(String fileName) {
			this.fileName = fileName;
			return this;
		}

		public Builder content(String content) {
			this.content = content;
			return this;
		}

		public Builder md5(String md5) {
			this.md5 = md5;
			return this;
		}

		public Builder version(long version) {
			this.version = version;
			return this;
		}

		public Builder modifyTime(Date modifyTime) {
			this.modifyTime = modifyTime;
			return this;
		}

		public Builder valid(boolean valid) {
			this.valid = valid;
			return this;
		}

		public ConfigFileRelease build() {
			ConfigFileRelease configFileRelease = new ConfigFileRelease();
			configFileRelease.name = this.name;
			configFileRelease.fileName = this.fileName;
			configFileRelease.content = this.content;
			configFileRelease.version = this.version;
			configFileRelease.group = this.group;
			configFileRelease.id = this.id;
			configFileRelease.valid = this.valid;
			configFileRelease.namespace = this.namespace;
			configFileRelease.md5 = this.md5;
			configFileRelease.modifyTime = this.modifyTime;
			return configFileRelease;
		}
	}
}
