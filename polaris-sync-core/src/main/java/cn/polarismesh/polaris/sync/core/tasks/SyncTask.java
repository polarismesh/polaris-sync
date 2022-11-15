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

package cn.polarismesh.polaris.sync.core.tasks;

import java.util.List;

import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface SyncTask {

	String getName();

	boolean isEnable();

	ResourceEndpoint getSource();

	ResourceEndpoint getDestination();

	List<Match> getMatchList();

	public static class Match {

		private String namespace;

		private String name;

		private List<ModelProto.Group> groups;

		public String getNamespace() {
			return namespace;
		}

		public void setNamespace(String namespace) {
			this.namespace = namespace;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<ModelProto.Group> getGroups() {
			return groups;
		}

		public void setGroups(List<ModelProto.Group> groups) {
			this.groups = groups;
		}
	}
}
