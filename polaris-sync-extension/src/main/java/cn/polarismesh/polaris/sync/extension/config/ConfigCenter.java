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

import cn.polarismesh.polaris.sync.extension.Health;
import com.tencent.polaris.client.pb.ResponseProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.ConfigEndpoint.ConfigType;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface ConfigCenter {


	/**
	 * registry type, such as nacos, kong, consul, etc...
	 *
	 * @return type
	 */
	ConfigType getType();

	/**
	 * initialize registry
	 */
	void init(ConfigInitRequest request);

	/**
	 * destroy registry
	 */
	void destroy();

	/**
	 * list the discovery namespaces
	 *
	 * @return instances
	 */
	ResponseProto.DiscoverResponse listNamespaces();

	/**
	 * list config file by {@link ConfigGroup}
	 *
	 * @param configGroup
	 * @return
	 */
	ConfigFilesResponse listConfigFile(ConfigGroup configGroup);

	/**
	 * watch the instances changed
	 *
	 * @param eventListener listener callback
	 */
	boolean watch(ConfigGroup group, ResponseListener eventListener);

	/**
	 * unwatch the instance changed
	 *
	 */
	void unwatch(ConfigGroup group);

	/**
	 * register the service group
	 *
	 */
	void updateGroups(Collection<ConfigGroup> group);

	/**
	 * update the instances to destinations
	 */
	void updateConfigFiles(ConfigGroup group, Collection<ConfigFile> files);

	/**
	 * listener to watch the instance change events
	 */
	interface ResponseListener {

		/**
		 * called when response event received
		 *
		 * @param watchEvent instances event
		 */
		void onEvent(WatchEvent watchEvent);
	}

	/**
	 * process health checking
	 *
	 * @return check result
	 */
	Health healthCheck();

}
