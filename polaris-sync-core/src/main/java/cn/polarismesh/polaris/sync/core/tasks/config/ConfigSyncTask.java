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

package cn.polarismesh.polaris.sync.core.tasks.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cn.polarismesh.polaris.sync.config.pb.ConfigProto;
import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.core.tasks.registry.RegistrySyncTask;
import cn.polarismesh.polaris.sync.extension.Authorization;
import cn.polarismesh.polaris.sync.extension.Database;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigSyncTask implements SyncTask {

	private final ConfigProto.Task pbTask;

	private final ResourceEndpoint source;

	private final ResourceEndpoint destination;

	private final List<Match> matches;

	public ConfigSyncTask(ConfigProto.Task pbTask, ResourceEndpoint source, ResourceEndpoint destination, List<Match> matches) {
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

	private static ResourceType find(ConfigProto.ConfigEndpoint.ConfigType t) {
		if (ConfigProto.ConfigEndpoint.ConfigType.polaris.equals(t)) {
			return ResourceType.POLARIS;
		}
		if (ConfigProto.ConfigEndpoint.ConfigType.nacos.equals(t)) {
			return ResourceType.NACOS;
		}
		if (ConfigProto.ConfigEndpoint.ConfigType.apollo.equals(t)) {
			return ResourceType.APOLLO;
		}

		return ResourceType.UNKNOWN;
	}

	private static ResourceEndpoint parse(ConfigProto.ConfigEndpoint endpoint) {
		ResourceEndpoint.ResourceEndpointBuilder builder = ResourceEndpoint.builder()
				.name(endpoint.getName())
				.productName(endpoint.getProductName())
				.resourceType(find(endpoint.getType()));

		if (Objects.nonNull(endpoint.getServer())) {
			builder.addresses(endpoint.getServer().getAddressesList())
					.authorization(Authorization.builder()
							.username(endpoint.getServer().getUser())
							.password(endpoint.getServer().getPassword())
							.token(endpoint.getServer().getToken())
							.build());
		}
		if (Objects.nonNull(endpoint.getDb())) {
			builder.database(Database.builder()
					.jdbcUrl(endpoint.getDb().getJdbcUrl())
					.username(endpoint.getDb().getUsername())
					.password(endpoint.getDb().getPassword())
					.build());
		}

		return builder.build();
	}


	public static ConfigSyncTask parse(ConfigProto.Task task) {
		ResourceEndpoint source = parse(task.getSource());
		ResourceEndpoint dest = parse(task.getDestination());

		List<Match> matches = new ArrayList<>();
		task.getMatchList().forEach(match -> {
			Match m = new Match();
			m.setNamespace(match.getNamespace());
			m.setName(match.getConfigGroup());
			m.setGroups(match.getGroupsList());

			matches.add(m);
		});

		return new ConfigSyncTask(task, source, dest, matches);
	}

}
