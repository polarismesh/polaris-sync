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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigFile implements RecordInfo {

	private String namespace;

	private String group;

	private String fileName;

	private String content;

	private String version;

	private String md5;

	private boolean beta;

	private String betaIps;

	private boolean valid = true;

	private Map<String, String> labels = new HashMap<>();

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

	public String getVersion() {
		return version;
	}

	public String getMd5() {
		return md5;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ConfigFile)) return false;
		ConfigFile that = (ConfigFile) o;
		return beta == that.beta && Objects.equals(getNamespace(), that.getNamespace()) &&
				Objects.equals(getGroup(), that.getGroup()) && Objects.equals(getFileName(), that.getFileName()) &&
				Objects.equals(getContent(), that.getContent()) && Objects.equals(betaIps, that.betaIps) &&
				Objects.equals(getLabels(), that.getLabels());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getNamespace(), getGroup(), getFileName(), getContent(), beta, betaIps, getLabels());
	}

	@Override
	public String toString() {
		return "ConfigFile{" +
				"namespace='" + namespace + '\'' +
				", group='" + group + '\'' +
				", fileName='" + fileName + '\'' +
				", content='" + content + '\'' +
				", version='" + version + '\'' +
				", md5='" + md5 + '\'' +
				", beta=" + beta +
				", betaIps='" + betaIps + '\'' +
				", labels=" + labels +
				'}';
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String keyInfo() {
		return String.format("%s@@%s@@%s", namespace, group, fileName);
	}

	@Override
	public boolean isValid() {
		return valid;
	}

	public static final class Builder {
		private String namespace;
		private String group;
		private String fileName;
		private String content;
		private String version;
		private String md5;
		private boolean beta;
		private String betaIps;
		private boolean valid = true;
		private Map<String, String> labels;

		private Builder() {
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

		public Builder version(String version) {
			this.version = version;
			return this;
		}

		public Builder md5(String md5) {
			this.md5 = md5;
			return this;
		}

		public Builder beta(boolean beta) {
			this.beta = beta;
			return this;
		}

		public Builder betaIps(String betaIps) {
			this.betaIps = betaIps;
			return this;
		}

		public Builder valid(boolean valid) {
			this.valid = valid;
			return this;
		}

		public Builder labels(Map<String, String> labels) {
			this.labels = labels;
			return this;
		}

		public ConfigFile build() {
			ConfigFile configFile = new ConfigFile();
			configFile.version = this.version;
			configFile.betaIps = this.betaIps;
			configFile.valid = this.valid;
			configFile.group = this.group;
			configFile.fileName = this.fileName;
			configFile.content = this.content;
			configFile.md5 = this.md5;
			configFile.labels = this.labels;
			configFile.beta = this.beta;
			configFile.namespace = this.namespace;
			return configFile;
		}
	}
}
