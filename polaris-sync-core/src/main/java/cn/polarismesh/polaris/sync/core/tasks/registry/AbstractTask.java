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

package cn.polarismesh.polaris.sync.core.tasks.registry;

import java.util.Objects;

import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.registry.Service;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface AbstractTask extends Runnable {

	/**
	 * 如果是从 polaris -> nacos, 则需要将 polaris 的默认命名空间 default 转为 nacos 的默认命名空间
	 * 如果是从 nacos -> polaris, 则需要将 nacos 的默认命名空间转为 polaris 的默认命名空间 default
	 *
	 * @param service
	 * @return
	 */
	default Service handle(NamedRegistryCenter source, NamedRegistryCenter destination, Service service) {
		if (ResourceType.POLARIS.equals(destination.getRegistry()
				.getType()) && ResourceType.NACOS.equals(source.getRegistry().getType())) {
			String ns = service.getNamespace();
			if (Objects.equals("", service.getNamespace())) {
				ns = DefaultValues.DEFAULT_POLARIS_NAMESPACE;
			}
			return new Service(ns, service.getService());
		}

		if (ResourceType.POLARIS.equals(source.getRegistry()
				.getType()) && ResourceType.NACOS.equals(destination.getRegistry().getType())) {
			String ns = service.getNamespace();
			if (Objects.equals(DefaultValues.DEFAULT_POLARIS_NAMESPACE, service.getNamespace())) {
				ns = DefaultValues.DEFAULT_POLARIS_NAMESPACE;
			}
			return new Service(ns, service.getService());
		}

		return service;
	}

}
