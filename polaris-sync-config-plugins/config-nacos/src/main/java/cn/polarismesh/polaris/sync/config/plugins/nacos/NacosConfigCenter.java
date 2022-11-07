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

package cn.polarismesh.polaris.sync.config.plugins.nacos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import cn.polarismesh.polaris.sync.common.database.DatabaseOperator;
import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.config.plugins.nacos.mapper.ConfigFileMapper;
import cn.polarismesh.polaris.sync.config.plugins.nacos.model.AuthResponse;
import cn.polarismesh.polaris.sync.config.plugins.nacos.model.NacosNamespace;
import cn.polarismesh.polaris.sync.extension.config.SubscribeDbChangeTask;
import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import cn.polarismesh.polaris.sync.extension.config.ConfigFilesResponse;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;
import cn.polarismesh.polaris.sync.extension.config.ConfigInitRequest;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.ConfigEndpoint.ConfigType;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.tencent.polaris.client.pb.ResponseProto;
import com.tencent.polaris.client.pb.ServiceProto;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class NacosConfigCenter implements ConfigCenter {

	private static final Logger LOG = LoggerFactory.getLogger(NacosConfigCenter.class);

	private final Map<String, ConfigService> ns2ConfigService = new ConcurrentHashMap<>();

	private final AtomicBoolean destroyed = new AtomicBoolean(false);

	private ConfigInitRequest configInitRequest;

	private final RestOperator restOperator = new RestOperator();

	private DatabaseOperator databaseOperator;

	private HikariDataSource dataSource;

	private Set<SubscribeDbChangeTask<ConfigFile>> watchFileTasks;

	@Override
	public ConfigType getType() {
		return ConfigType.nacos;
	}

	@Override
	public void init(ConfigInitRequest request) {
		configInitRequest = request;
		initDatabaseOperator();
	}

	private void initDatabaseOperator() {
		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
		hikariConfig.setPoolName(configInitRequest.getSourceName());
		hikariConfig.setJdbcUrl(configInitRequest.getConfigEndpoint().getDb().getJdbcUrl());
		hikariConfig.setUsername(configInitRequest.getConfigEndpoint().getDb().getUsername());
		hikariConfig.setPassword(configInitRequest.getConfigEndpoint().getDb().getPassword());
		hikariConfig.setMaximumPoolSize(64);
		hikariConfig.setMinimumIdle(16);
		hikariConfig.setMaxLifetime(10 * 60 * 1000);

		dataSource = new HikariDataSource(hikariConfig);
		databaseOperator = new DatabaseOperator(dataSource);

		buildWatchTask();
	}

	@Override
	public void destroy() {
		if (!destroyed.compareAndSet(false, true)) {
			return;
		}

		ns2ConfigService.forEach((s, configService) -> {
			try {
				configService.shutDown();
			}
			catch (NacosException e) {
				LOG.error("[Nacos][Config] close ConfigsService", e);
			}
		});

		ns2ConfigService.clear();
		databaseOperator.destroy();
		dataSource.close();
	}

	@Override
	public ResponseProto.DiscoverResponse listNamespaces() {
		RegistryProto.ConfigEndpoint configEndpoint = configInitRequest.getConfigEndpoint();
		AuthResponse authResponse = new AuthResponse();
		// 1. 先进行登录
		if (StringUtils.hasText(configEndpoint.getServer().getUser()) && StringUtils.hasText(
				configEndpoint.getServer().getPassword())) {
			ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils.auth(
					restOperator, configEndpoint.getServer(), authResponse, null,
					ResponseProto.DiscoverResponse.DiscoverResponseType.NAMESPACES);
			if (null != discoverResponse) {
				return discoverResponse;
			}
		}
		//2. 查询命名空间是否已经创建
		List<NacosNamespace> nacosNamespaces = new ArrayList<>();
		ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils
				.discoverAllNamespaces(authResponse, restOperator, configEndpoint.getServer(), nacosNamespaces);
		if (null != discoverResponse) {
			return discoverResponse;
		}
		ResponseProto.DiscoverResponse.Builder builder = ResponseUtils
				.toDiscoverResponse(null, StatusCodes.SUCCESS, ResponseProto.DiscoverResponse.DiscoverResponseType.NAMESPACES);
		for (NacosNamespace nacosNamespace : nacosNamespaces) {
			builder.addNamespaces(
					ServiceProto.Namespace.newBuilder().setName(ResponseUtils.toStringValue(nacosNamespace.getNamespace())).build());
		}
		return builder.build();
	}

	@Override
	public ConfigFilesResponse listConfigFile(ConfigGroup configGroup) {
		String query = "SELECT tenant_id, group_id, data_id, content, c_desc, tag_name, gmt_modified "
				+ "FROM config_info ci LEFT JOIN config_tags_relation cr ON ci.tenant_id = cr.tenant_id "
				+ "AND ci.group_id = cr.group_id AND ci.data_id = cr.data_id WHERE tenant_id = ? AND group_id = ?";

		// 从对应的数据库中获取
		Collection<ConfigFile> files = databaseOperator.queryList(query, new Object[] {
				configGroup.getName(), configGroup.getNamespace()
		}, ConfigFileMapper.getInstance());

		return ConfigFilesResponse.builder().files(files).code(StatusCodes.SUCCESS).build();
	}

	@Override
	public boolean watch(ConfigGroup group, ResponseListener eventListener) {
		for (SubscribeDbChangeTask task : watchFileTasks) {
			task.addListener(eventListener);
		}
		return true;
	}

	private void buildWatchTask() {
		watchFileTasks = new HashSet<>();
		SubscribeDbChangeTask<ConfigFile> watchFile = new SubscribeDbChangeTask<>("watchFile", date -> {
			String query = ConfigFileMapper.getInstance().getMoreSqlTemplate(Objects.isNull(date));
			List<ConfigFile> files = Collections.emptyList();
			if (Objects.isNull(date)) {
				files = databaseOperator.queryList(query, null, ConfigFileMapper.getInstance());
			} else {
				files = databaseOperator.queryList(query, new Object[]{date}, ConfigFileMapper.getInstance());
			}
			return files;
		});

		watchFileTasks.add(watchFile);
	}

	@Override
	public void unwatch(ConfigGroup group) {

	}

	@Override
	public void updateGroups(Collection<ConfigGroup> groups) {
		Set<String> namespaceIds = new HashSet<>();
		for (ConfigGroup group : groups) {
			if (group.getNamespace().equals(DefaultValues.EMPTY_NAMESPACE_HOLDER)) {
				continue;
			}
			namespaceIds.add(group.getNamespace());
		}
		if (namespaceIds.isEmpty()) {
			return;
		}

		RegistryProto.ConfigEndpoint.Server server  = configInitRequest.getConfigEndpoint().getServer();
		AuthResponse authResponse = new AuthResponse();
		// 1. 先进行登录
		if (StringUtils.hasText(server.getUser()) && StringUtils.hasText(
				server.getPassword())) {
			ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils.auth(
					restOperator, server, authResponse, null, ResponseProto.DiscoverResponse.DiscoverResponseType.NAMESPACES);
			if (null != discoverResponse) {
				return;
			}
		}
		//2. 查询命名空间是否已经创建
		List<NacosNamespace> nacosNamespaces = new ArrayList<>();
		ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils
				.discoverAllNamespaces(authResponse, restOperator, server, nacosNamespaces);
		if (null == discoverResponse) {
			return;
		}
		for (NacosNamespace nacosNamespace : nacosNamespaces) {
			namespaceIds.remove(nacosNamespace.getNamespace());
		}
		if (CollectionUtils.isEmpty(namespaceIds)) {
			return;
		}
		//3. 新增命名空间
		LOG.info("[Nacos] namespaces to add {}", namespaceIds);
		for (String namespaceId : namespaceIds) {
			NacosRestUtils.createNamespace(authResponse, restOperator, server, namespaceId);
		}
	}

	@Override
	public void updateConfigFiles(ConfigGroup group, Collection<ConfigFile> files) {
		Stream<ConfigFile> stream = null;
		if (files.size() < 128) {
			stream = files.stream();
		} else {
			stream = files.parallelStream();
		}

		stream.forEach(file -> {
			String ns = group.getNamespace();
			ConfigService configService = getOrCreateConfigService(ns);
			if (null == configService) {
				LOG.error("[Nacos] fail to lookup configService for group {}, config {}",
						group, configInitRequest.getSourceName());
				return;
			}
			try {
				boolean ok = configService.publishConfig(file.getFileName(), group.getName(),
						file.getContent());
			}
			catch (NacosException e) {
				LOG.error("[Nacos][Config] {} publish config namespace={} group={} name={} ",
						configInitRequest.getSourceName(),
						group.getNamespace(), group.getName(), file.getFileName(), e);
			}
		});
	}

	private ConfigService getOrCreateConfigService(String namespace) {
		ConfigService configService = ns2ConfigService.get(namespace);
		if (null != configService) {
			return configService;
		}
		synchronized (this) {
			RegistryProto.ConfigEndpoint.Server server = configInitRequest.getConfigEndpoint().getServer();
			configService = ns2ConfigService.get(namespace);
			if (null != configService) {
				return configService;
			}
			String address = String.join(",", server.getAddressesList());
			Properties properties = new Properties();
			properties.setProperty("serverAddr", address);
			properties.setProperty("namespace", toNamespaceId(namespace));
			if (StringUtils.hasText(server.getUser())) {
				properties.setProperty("username", server.getUser());
			}
			if (StringUtils.hasText(server.getPassword())) {
				properties.setProperty("password", server.getPassword());
			}
			try {
				configService = NacosFactory.createConfigService(properties);
			} catch (NacosException e) {
				LOG.error("[Nacos] fail to create naming service to {}, namespace {}", address, namespace, e);
				return null;
			}
			try {
				//等待nacos连接建立完成
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			ns2ConfigService.put(namespace, configService);
			return configService;
		}
	}

	@Override
	public Health healthCheck() {
		return new Health(0, 0);
	}

	private static String toNamespaceId(String namespace) {
		if (DefaultValues.EMPTY_NAMESPACE_HOLDER.equals(namespace)) {
			return "";
		}
		return namespace;
	}
}
