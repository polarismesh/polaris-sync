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

import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.extension.Authorization;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RegistrySyncTask implements SyncTask {

	private final RegistryProto.Task pbTask;

	private final ResourceEndpoint source;

	private final ResourceEndpoint destination;

	private final List<Match> matches;

	public RegistrySyncTask(RegistryProto.Task pbTask, ResourceEndpoint source, ResourceEndpoint destination, List<Match> matches) {
		this.pbTask = pbTask;
		this.source = source;
		this.destination = destination;
		this.matches = matches;
	}


	@Override
	public String getName() {
		return pbTask.getName();
	}

	@Override
	public boolean isEnable() {
		return pbTask.getEnable();
	}

	@Override
	public ResourceEndpoint getSource() {
		return source;
	}

	@Override
	public ResourceEndpoint getDestination() {
		return destination;
	}

	@Override
	public List<Match> getMatchList() {
		return matches;
	}

	private static ResourceType find(RegistryProto.RegistryEndpoint.RegistryType t) {
		if (RegistryProto.RegistryEndpoint.RegistryType.polaris.equals(t)) {
			return ResourceType.POLARIS;
		}
		if (RegistryProto.RegistryEndpoint.RegistryType.nacos.equals(t)) {
			return ResourceType.NACOS;
		}
		if (RegistryProto.RegistryEndpoint.RegistryType.consul.equals(t)) {
			return ResourceType.CONSUL;
		}
		if (RegistryProto.RegistryEndpoint.RegistryType.kong.equals(t)) {
			return ResourceType.KONG;
		}
		if (RegistryProto.RegistryEndpoint.RegistryType.kubernetes.equals(t)) {
			return ResourceType.KUBERNETES;
		}
		if (RegistryProto.RegistryEndpoint.RegistryType.zookeeper.equals(t)) {
			return ResourceType.ZOOKEEPER;
		}
		
		return ResourceType.UNKNOWN;
	}
	
	private static ResourceEndpoint parse(RegistryProto.RegistryEndpoint endpoint) {
		ResourceEndpoint source = ResourceEndpoint.builder()
				.addresses(endpoint.getAddressesList())
				.options(endpoint.getOptionsMap())
				.name(endpoint.getName())
				.productName(endpoint.getProductName())
				.resourceType(find(endpoint.getType()))
				.authorization(Authorization.builder()
						.username(endpoint.getUser())
						.password(endpoint.getPassword())
						.token(endpoint.getToken())
						.build())
				.build();

		return source;
	}


	public static RegistrySyncTask parse(RegistryProto.Task task) {
		ResourceEndpoint source = parse(task.getSource());
		ResourceEndpoint dest = parse(task.getDestination());

		List<Match> matches = new ArrayList<>();
		task.getMatchList().forEach(match -> {
			Match m = new Match();
			m.setNamespace(match.getNamespace());
			m.setName(match.getService());
			m.setGroups(match.getGroupsList());

			matches.add(m);
		});

		return new RegistrySyncTask(task, source, dest, matches);
	}

}
