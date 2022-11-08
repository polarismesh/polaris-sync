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

package cn.polarismesh.polaris.sync.config.plugins.polaris;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.polarismesh.polaris.sync.common.database.DatabaseOperator;
import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.config.plugins.polaris.mapper.ConfigFileMapper;
import cn.polarismesh.polaris.sync.config.plugins.polaris.mapper.ConfigFileReleaseMapper;
import cn.polarismesh.polaris.sync.config.plugins.polaris.model.ConfigFileTemp;
import cn.polarismesh.polaris.sync.config.plugins.polaris.model.ConfigFileRelease;
import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import cn.polarismesh.polaris.sync.extension.config.ConfigFilesResponse;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;
import cn.polarismesh.polaris.sync.extension.config.ConfigInitRequest;
import cn.polarismesh.polaris.sync.extension.config.SubscribeDbChangeTask;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import com.tencent.polaris.client.pb.ResponseProto;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class PolarisConfigCenter implements ConfigCenter {

	private static final String PREFIX_HTTP = "http://";

	private static final String PREFIX_GRPC = "grpc://";

	private static final Logger LOG = LoggerFactory.getLogger(PolarisConfigCenter.class);

	private final AtomicBoolean destroyed = new AtomicBoolean(false);

	private ConfigInitRequest request;

	private final RestOperator restOperator = new RestOperator();

	private DatabaseOperator databaseOperator;

	private HikariDataSource dataSource;

	private Set<SubscribeDbChangeTask> watchFileTasks;

	private final List<String> httpAddresses = new ArrayList<>();

	private final List<String> grpcAddresses = new ArrayList<>();

	@Override
	public RegistryProto.ConfigEndpoint.ConfigType getType() {
		return RegistryProto.ConfigEndpoint.ConfigType.polaris;
	}

	@Override
	public void init(ConfigInitRequest request) {
		this.request = request;
		parseAddresses(request.getConfigEndpoint().getServer().getAddressesList());
		initDatabaseOperator();
	}

	private void parseAddresses(List<String> addresses) {
		for (String address : addresses) {
			if (address.startsWith(PREFIX_HTTP)) {
				httpAddresses.add(address.substring(PREFIX_HTTP.length()));
			} else if (address.startsWith(PREFIX_GRPC)) {
				grpcAddresses.add(address.substring(PREFIX_GRPC.length()));
			} else {
				httpAddresses.add(address);
			}
		}
	}

	private void initDatabaseOperator() {
		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
		hikariConfig.setPoolName(request.getSourceName());
		hikariConfig.setJdbcUrl(request.getConfigEndpoint().getDb().getJdbcUrl());
		hikariConfig.setUsername(request.getConfigEndpoint().getDb().getUsername());
		hikariConfig.setPassword(request.getConfigEndpoint().getDb().getPassword());
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
			String query = ConfigFileReleaseMapper.getInstance().getMoreSqlTemplate(Objects.isNull(date));
			List<ConfigFileRelease> files = Collections.emptyList();
			if (Objects.isNull(date)) {
				files = databaseOperator.queryList(query, null, ConfigFileReleaseMapper.getInstance());
			}
			else {
				files = databaseOperator.queryList(query, new Object[] {date}, ConfigFileReleaseMapper.getInstance());
			}
			return files.stream().map(item -> ConfigFile.builder()
					.fileName(item.getFileName())
					.group(item.getGroup())
					.namespace(item.getNamespace())
					.beta(false)
					.content(item.getContent())
					.md5(item.getMd5())
					.modifyTime(item.getModifyTime())
					.build()).collect(Collectors.toList());
		});

		watchFileTasks.add(watchFile);
	}

	@Override
	public void destroy() {
		if (!destroyed.compareAndSet(false, true)) {
			return;
		}

		watchFileTasks.forEach(SubscribeDbChangeTask::destroy);
		databaseOperator.destroy();
		dataSource.close();
	}

	@Override
	public ResponseProto.DiscoverResponse listNamespaces() {
		return ResponseUtils.toDiscoverResponse(
				null, StatusCodes.SUCCESS, ResponseProto.DiscoverResponse.DiscoverResponseType.NAMESPACES).build();
	}

	@Override
	public ConfigFilesResponse listConfigFile(ConfigGroup configGroup) {
		String query = "SELECT id, name, namespace, `group`, file_name, content, IFNULL(comment, ''), md5, version, "
				+ " modify_time, flag FROM config_file_release WHERE 1=1 ";

		if (StringUtils.isNotBlank(configGroup.getNamespace())) {
			query += " AND namespace = ?";
		}
		if (StringUtils.isNotBlank(configGroup.getName())) {
			query += " AND group = ? ";
		}

		// 从对应的数据库中获取
		Collection<ConfigFile> files = databaseOperator.queryList(query, new Object[] {
				configGroup.getNamespace(), configGroup.getName()
		}, ConfigFileMapper.getInstance());

		return ConfigFilesResponse.builder().group(configGroup).files(files).code(StatusCodes.SUCCESS).build();
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
	public void updateGroups(Collection<ConfigGroup> group) {

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

		stream.forEach(file -> {
			if (file.isBeta()) {
				return;
			}
			ConfigFileTemp fileTemp = ConfigFileTemp.builder()
					.fileName(file.getFileName())
					.group(file.getGroup())
					.content(file.getContent())
					.build();

			ConfigFilesResponse resp = PolarisRestUtils.createAndPublishConfigFile(restOperator, httpAddresses,
					request.getConfigEndpoint().getServer().getToken(), fileTemp);
			if (resp.getCode() != StatusCodes.SUCCESS) {
				LOG.error("[Polaris][Config] {} publish config namespace={} group={} name={} error={}",
						request.getSourceName(),
						group.getNamespace(), group.getName(), file.getFileName(), resp.getInfo());
			}
		});
	}

	@Override
	public Health healthCheck() {
		String address = RestOperator.pickAddress(httpAddresses);
		String url = String.format("http://%s/", address);
		RestResponse<String> stringRestResponse = restOperator
				.curlRemoteEndpoint(url, HttpMethod.GET, new HttpEntity<>(""), String.class);
		int totalCount = 0;
		int errorCount = 0;
		if (stringRestResponse.hasServerError()) {
			errorCount++;
		}
		totalCount++;
		return new Health(totalCount, errorCount);
	}
}
