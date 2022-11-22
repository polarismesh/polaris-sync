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
import cn.polarismesh.polaris.sync.config.plugins.polaris.mapper.ConfigFileReleaseMapper;
import cn.polarismesh.polaris.sync.config.plugins.polaris.model.ConfigFileRelease;
import cn.polarismesh.polaris.sync.config.plugins.polaris.model.ConfigFileTemp;
import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import cn.polarismesh.polaris.sync.extension.config.ConfigFilesResponse;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;
import cn.polarismesh.polaris.sync.extension.config.ConfigInitRequest;
import cn.polarismesh.polaris.sync.extension.config.SubscribeDbChangeTask;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
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
	public String getName() {
		return getType().name();
	}

	@Override
	public ResourceType getType() {
		return ResourceType.POLARIS;
	}

	@Override
	public void init(ConfigInitRequest request) {
		this.request = request;
		parseAddresses(request.getResourceEndpoint().getServerAddresses());
		initDatabaseOperator();
	}

	private void parseAddresses(List<String> addresses) {
		for (String address : addresses) {
			if (address.startsWith(PREFIX_HTTP)) {
				httpAddresses.add(address.substring(PREFIX_HTTP.length()));
			}
			else if (address.startsWith(PREFIX_GRPC)) {
				grpcAddresses.add(address.substring(PREFIX_GRPC.length()));
			}
			else {
				httpAddresses.add(address);
			}
		}
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
			String query = ConfigFileReleaseMapper.getInstance().getMoreSqlTemplate(Objects.isNull(date));
			List<ConfigFileRelease> files = Collections.emptyList();

			try {
				if (Objects.isNull(date)) {
					files = databaseOperator.queryList(query, null, ConfigFileReleaseMapper.getInstance());
				}
				else {
					files = databaseOperator.queryList(query, new Object[] {date}, ConfigFileReleaseMapper.getInstance());
				}
			}
			catch (Exception ex) {
				LOG.error("[Polaris][Config] pull config file info from db fail ", ex);
			}

			return files.stream().map(item -> ConfigFile.builder()
					.fileName(item.getFileName())
					.group(item.getGroup())
					.namespace(item.getNamespace())
					.beta(false)
					.content(item.getContent())
					.md5(item.getMd5())
					.modifyTime(item.getModifyTime())
					.labels(item.getLabels())
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
		String query = "SELECT cr.id, cr.name, cr.namespace, cr.`group`, cr.file_name, cr.content, IFNULL(cr.comment, ''), ct.key, ct.value, "
				+ "cr.md5, cr.version, cr.modify_time, cr.flag FROM config_file_release cr LEFT JOIN config_file_tag ct "
				+ "ON cr.namespace = ct.namespace AND cr.`group` = ct.`group` AND cr.file_name = ct.file_name WHERE 1=1 ";

		List<Object> args = new ArrayList<>();
		if (StringUtils.isNotBlank(configGroup.getNamespace())) {
			query += " AND cr.namespace = ?";
			args.add(configGroup.getNamespace());
		}
		if (StringUtils.isNotBlank(configGroup.getName()) && !StringUtils.equals(DefaultValues.MATCH_ALL, configGroup.getName())) {
			query += " AND cr.`group` = ? ";
			args.add(configGroup.getName());
		}

		try {
			// 从对应的数据库中获取
			Collection<ConfigFile> files = databaseOperator.queryList(query, args.toArray(), ConfigFileReleaseMapper.getInstance()).stream().map(releaseFile -> {
				return ConfigFile.builder()
						.namespace(releaseFile.getNamespace())
						.group(releaseFile.getGroup())
						.fileName(releaseFile.getFileName())
						.content(releaseFile.getContent())
						.valid(releaseFile.isValid())
						.beta(false)
						.labels(releaseFile.getLabels())
						.build();
			}).collect(Collectors.toList());

			return ConfigFilesResponse.builder().group(configGroup).files(files).code(StatusCodes.SUCCESS).build();
		}
		catch (Exception ex) {
			LOG.error("[Polaris][Config] list config file info from db fail ", ex);
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

		stream.peek(file -> {
			Map<String, String> labels = file.getLabels();
			labels.put(DefaultValues.META_SYNC, request.getSourceName());
			file.setLabels(labels);
		}).forEach(file -> {
			if (file.isBeta() || !file.isValid()) {
				return;
			}
			if (Objects.equals(file.getLabels().get(DefaultValues.META_SYNC), request.getResourceEndpoint().getName())) {
				return;
			}

			ConfigFileTemp fileTemp = ConfigFileTemp.builder()
					.namespace(file.getNamespace())
					.fileName(file.getFileName())
					.group(file.getGroup())
					.content(file.getContent())
					.tags(file.getLabels().entrySet().stream()
							.map(entry -> new ConfigFileTemp.Tag(entry.getKey(), entry.getValue()))
							.collect(Collectors.toList()))
					.build();

			ConfigFilesResponse resp = PolarisRestUtils.createAndPublishConfigFile(restOperator, httpAddresses,
					request.getResourceEndpoint().getAuthorization().getToken(), fileTemp);
			if (resp.getCode() != StatusCodes.SUCCESS) {
				LOG.error("[Polaris][Config] {} publish config namespace={} group={} name={} error={}",
						request.getSourceName(),
						fileTemp.getNamespace(), fileTemp.getGroup(), file.getFileName(), resp.getInfo());
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
