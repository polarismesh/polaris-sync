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


/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class WatchEvent<T> {

	private ConfigGroup configGroup;

	private T event;

	public void setConfigGroup(ConfigGroup configGroup) {
		this.configGroup = configGroup;
	}

	public void setEvent(T event) {
		this.event = event;
	}

	public ConfigGroup getConfigGroup() {
		return configGroup;
	}

	public T getEvent() {
		return event;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder<T> {
		private ConfigGroup configGroup;
		private T event;

		private Builder() {
		}

		public Builder configGroup(ConfigGroup configGroup) {
			this.configGroup = configGroup;
			return this;
		}

		public Builder event(T event) {
			this.event = event;
			return this;
		}

		public WatchEvent build() {
			WatchEvent watchEvent = new WatchEvent();
			watchEvent.event = this.event;
			watchEvent.configGroup = this.configGroup;
			return watchEvent;
		}
	}
}
