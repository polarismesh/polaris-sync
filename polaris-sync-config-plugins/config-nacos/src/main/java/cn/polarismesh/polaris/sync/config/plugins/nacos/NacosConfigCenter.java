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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.polarismesh.polaris.sync.common.database.DatabaseOperator;
import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.config.plugins.nacos.mapper.ConfigFileMapper;
import cn.polarismesh.polaris.sync.config.plugins.nacos.model.AuthResponse;
import cn.polarismesh.polaris.sync.config.plugins.nacos.model.NacosNamespace;
import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import cn.polarismesh.polaris.sync.extension.config.ConfigFilesResponse;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;
import cn.polarismesh.polaris.sync.extension.config.ConfigInitRequest;
import cn.polarismesh.polaris.sync.extension.config.SubscribeDbChangeTask;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import com.alibaba.nacos.common.utils.StringUtils;
import com.tencent.polaris.client.pb.ResponseProto;
import com.tencent.polaris.client.pb.ServiceProto;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class NacosConfigCenter implements ConfigCenter {

	private static final Logger LOG = LoggerFactory.getLogger(NacosConfigCenter.class);

	private final AtomicBoolean destroyed = new AtomicBoolean(false);

	private ConfigInitRequest request;

	private final RestOperator restOperator = new RestOperator();

	private DatabaseOperator databaseOperator;

	private HikariDataSource dataSource;

	private Set<SubscribeDbChangeTask> watchFileTasks = new HashSet<>();

	@Override
	public String getName() {
		return getType().name();
	}

	@Override
	public ResourceType getType() {
		return ResourceType.NACOS;
	}

	@Override
	public void init(ConfigInitRequest request) {
		this.request = request;
		initDatabaseOperator();
	}

	private void initDatabaseOperator() {
		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
		hikariConfig.setPoolName(request.getSourceName());
		hikariConfig.setJdbcUrl(request.getResourceEndpoint().getDatabase().getJdbcUrl());
		hikariConfig.setUsername(request.getResourceEndpoint().getDatabase().getUsername());
		hikariConfig.setPassword(request.getResourceEndpoint().getDatabase().getPassword());
		hikariConfig.setMaximumPoolSize(64);
		hikariConfig.setMinimumIdle(16);
		hikariConfig.setMaxLifetime(10 * 60 * 1000);

		dataSource = new HikariDataSource(hikariConfig);
		databaseOperator = new DatabaseOperator(dataSource);

		buildWatchTask();
	}

	private void buildWatchTask() {
		watchFileTasks = new HashSet<>();

		SubscribeDbChangeTask watchFile = new SubscribeDbChangeTask(request.getSourceName(), date -> {
			String query = ConfigFileMapper.getInstance().getMoreSqlTemplate(Objects.isNull(date));
			List<ConfigFile> files = Collections.emptyList();

			try {
				if (Objects.isNull(date)) {
					files = databaseOperator.queryList(query, null, ConfigFileMapper.getInstance());
				}
				else {
					files = databaseOperator.queryList(query, new Object[] {date}, ConfigFileMapper.getInstance());
				}
			}
			catch (Exception ex) {
				LOG.error("[Nacos][Config] pull config file info from db fail ", ex);
			}
			return files;
		});
		watchFileTasks.add(watchFile);
	}


	@Override
	public void destroy() {
		if (!destroyed.compareAndSet(false, true)) {
			return;
		}
		databaseOperator.destroy();
		dataSource.close();
	}

	@Override
	public ResponseProto.DiscoverResponse listNamespaces() {
		ResourceEndpoint endpoint = request.getResourceEndpoint();
		AuthResponse authResponse = new AuthResponse();
		// 1. 先进行登录
		if (StringUtils.isNotBlank(request.getResourceEndpoint().getAuthorization()
				.getUsername()) && StringUtils.isNotBlank(
				endpoint.getAuthorization().getPassword())) {
			ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils.auth(
					restOperator, endpoint, authResponse, null,
					ResponseProto.DiscoverResponse.DiscoverResponseType.NAMESPACES);
			if (null != discoverResponse) {
				return discoverResponse;
			}
		}
		//2. 查询命名空间是否已经创建
		List<NacosNamespace> nacosNamespaces = new ArrayList<>();
		ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils
				.discoverAllNamespaces(authResponse, restOperator, endpoint, nacosNamespaces);
		if (null != discoverResponse) {
			return discoverResponse;
		}
		ResponseProto.DiscoverResponse.Builder builder = ResponseUtils
				.toDiscoverResponse(null, StatusCodes.SUCCESS, ResponseProto.DiscoverResponse.DiscoverResponseType.NAMESPACES);
		for (NacosNamespace nacosNamespace : nacosNamespaces) {
			builder.addNamespaces(
					ServiceProto.Namespace.newBuilder()
							.setName(ResponseUtils.toStringValue(nacosNamespace.getNamespace())).build());
		}
		return builder.build();
	}

	@Override
	public ConfigFilesResponse listConfigFile(ConfigGroup configGroup) {
		String query = "SELECT ci.tenant_id, ci.group_id, ci.data_id, ci.content, ci.c_desc, IFNULL(cr.tag_name, '') as tag_name, ci.md5, ci.gmt_modified "
				+ "FROM config_info ci LEFT JOIN config_tags_relation cr ON ci.tenant_id = cr.tenant_id "
				+ "AND ci.group_id = cr.group_id AND ci.data_id = cr.data_id WHERE 1=1 ";

		List<Object> args = new ArrayList<>();
		if (StringUtils.isNotBlank(configGroup.getNamespace())) {
			query += " AND ci.tenant_id = ?";
			String tenant = configGroup.getNamespace();
			if (Objects.equals(DefaultValues.EMPTY_NAMESPACE_HOLDER, configGroup.getNamespace())) {
				tenant = "";
			}
			args.add(tenant);
		}
		if (StringUtils.isNotBlank(configGroup.getName()) && !StringUtils.equals("*", configGroup.getName())) {
			query += " AND ci.group_id = ? ";
			args.add(configGroup.getName());
		}

		try {
			// 从对应的数据库中获取
			Collection<ConfigFile> files = databaseOperator.queryList(query, args.toArray(), ConfigFileMapper.getInstance())
					.stream().peek(file -> {
						Map<String, String> labels = file.getLabels();
						labels.put(DefaultValues.META_SYNC, request.getSourceName());
						file.setLabels(labels);
					}).collect(Collectors.toList());

			return ConfigFilesResponse.builder().files(files).code(StatusCodes.SUCCESS).build();
		}
		catch (Exception ex) {
			LOG.error("[Nacos][Config] list config file info from db fail ", ex);
			return ConfigFilesResponse.builder().code(StatusCodes.STORE_LAYER_EXCEPTION).info(ex.getMessage()).build();
		}
	}

	@Override
	public boolean watch(ConfigGroup group, ResponseListener eventListener) {
		for (SubscribeDbChangeTask task : watchFileTasks) {
			task.addListener(group, eventListener);
		}
		return true;
	}

	@Override
	public void unwatch(ConfigGroup group) {

	}

	@Override
	public void updateGroups(Collection<ConfigGroup> groups) {
		Set<String> namespaceIds = new HashSet<>();
		for (ConfigGroup group : groups) {
			if (DefaultValues.EMPTY_NAMESPACE_HOLDER.equals(group.getNamespace())) {
				continue;
			}
			namespaceIds.add(group.getNamespace());
		}
		if (namespaceIds.isEmpty()) {
			return;
		}
		ResourceEndpoint endpoint = request.getResourceEndpoint();
		AuthResponse authResponse = new AuthResponse();
		// 1. 先进行登录
		if (StringUtils.hasText(endpoint.getAuthorization().getUsername()) && StringUtils.hasText(
				endpoint.getAuthorization().getPassword())) {
			ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils.auth(
					restOperator, endpoint, authResponse, null, ResponseProto.DiscoverResponse.DiscoverResponseType.NAMESPACES);
			if (null != discoverResponse) {
				return;
			}
		}
		if (!authResponse.isGlobalAdmin()) {
			LOG.warn("[Nacos][Config] current user is not nacos global admin, ignore create nacos namespace, {}",
					endpoint.getAuthorization());
			return;
		}
		//2. 查询命名空间是否已经创建
		List<NacosNamespace> nacosNamespaces = new ArrayList<>();
		ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils
				.discoverAllNamespaces(authResponse, restOperator, endpoint, nacosNamespaces);
		if (Objects.nonNull(discoverResponse)) {
			return;
		}
		for (NacosNamespace nacosNamespace : nacosNamespaces) {
			namespaceIds.remove(nacosNamespace.getNamespace());
		}
		if (CollectionUtils.isEmpty(namespaceIds)) {
			return;
		}
		//3. 新增命名空间
		LOG.info("[Nacos][Config] namespaces to add {}", namespaceIds);
		for (String namespaceId : namespaceIds) {
			NacosRestUtils.createNamespace(authResponse, restOperator, endpoint, namespaceId);
		}
	}

	@Override
	public void updateConfigFiles(ConfigGroup group, Collection<ConfigFile> files) {
		Stream<ConfigFile> stream = null;
		if (files.size() < 128) {
			stream = files.stream();
		}
		else {
			stream = files.parallelStream();
		}

		ResourceEndpoint endpoint = request.getResourceEndpoint();
		AuthResponse authResponse = new AuthResponse();
		// 1. 先进行登录
		if (StringUtils.hasText(endpoint.getAuthorization().getUsername()) && StringUtils.hasText(
				endpoint.getAuthorization().getPassword())) {
			ResponseProto.DiscoverResponse discoverResponse = NacosRestUtils.auth(
					restOperator, endpoint, authResponse, null, ResponseProto.DiscoverResponse.DiscoverResponseType.NAMESPACES);
			if (null != discoverResponse) {
				return;
			}
		}

		stream.peek(file -> {
			Map<String, String> labels = file.getLabels();
			labels.put(DefaultValues.META_SYNC, request.getSourceName());
			file.setLabels(labels);
		}).forEach(file -> {
			if (file.isBeta() || !file.isValid()) {
				return;
			}
			file.setNamespace(toNamespaceId(file.getNamespace()));
			if (Objects.equals(file.getLabels().get(DefaultValues.META_SYNC), request.getResourceEndpoint()
					.getName())) {
				return;
			}

			try {
				boolean ok = NacosRestUtils.publishConfig(authResponse, restOperator, endpoint, file);
				if (!ok) {
					LOG.warn("[Nacos][Config] {} publish config not success namespace={} group={} name={} ",
							request.getSourceName(),
							file.getNamespace(), file.getGroup(), file.getFileName());
				}
			}
			catch (Exception e) {
				LOG.error("[Nacos][Config] {} publish config namespace={} group={} name={} ",
						request.getSourceName(),
						file.getNamespace(), file.getGroup(), file.getFileName(), e);
			}
		});
	}

	@Override
	public Health healthCheck() {
		String address = RestOperator.pickAddress(request.getResourceEndpoint().getServerAddresses());
		String url = String.format("http://%s/", address);
		RestResponse<String> response = restOperator
				.curlRemoteEndpoint(url, HttpMethod.GET, new HttpEntity<>(""), String.class);
		int totalCount = 0;
		int errorCount = 0;
		if (response.hasServerError()) {
			errorCount++;
		}
		totalCount++;
		return new Health(totalCount, errorCount);
	}

	private static String toNamespaceId(String namespace) {
		if (DefaultValues.EMPTY_NAMESPACE_HOLDER.equals(namespace)) {
			return "";
		}
		return namespace;
	}
}
