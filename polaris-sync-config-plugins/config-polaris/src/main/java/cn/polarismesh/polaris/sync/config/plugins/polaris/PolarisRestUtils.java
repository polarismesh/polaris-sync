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

import java.util.List;

import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.common.rest.RestUtils;
import cn.polarismesh.polaris.sync.config.plugins.polaris.model.ConfigFileTemp;
import cn.polarismesh.polaris.sync.config.plugins.polaris.model.ConfigFileRelease;
import cn.polarismesh.polaris.sync.extension.config.ConfigFilesResponse;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static cn.polarismesh.polaris.sync.common.rest.RestOperator.pickAddress;


/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class PolarisRestUtils {

	private static final Logger LOG = LoggerFactory.getLogger(PolarisRestUtils.class);

	public static ConfigFilesResponse createAndPublishConfigFile(RestOperator restOperator, List<String> addresses,
			String token, ConfigFileTemp file) {

		String address = pickAddress(addresses);
		ConfigFilesResponse resp = createTempConfigFile(restOperator, token, address, file);
		if (resp.getCode() != StatusCodes.SUCCESS) {
			ConfigFilesResponse updateResp = updateAndPublishConfigFile(restOperator, addresses, token, file);
			if (updateResp.getCode() != StatusCodes.SUCCESS) {
				updateResp.setParent(resp);
				return updateResp;
			}
		}

		resp = releaseConfigFile(restOperator, token, address, ConfigFileRelease.builder()
				.namespace(file.getNamespace())
				.group(file.getGroup())
				.fileName(file.getFileName())
				.build());
		return resp;
	}

	public static ConfigFilesResponse updateAndPublishConfigFile(RestOperator restOperator, List<String> addresses,
			String token, ConfigFileTemp file) {

		String address = pickAddress(addresses);
		ConfigFilesResponse resp = updateTempConfigFile(restOperator, token, address, file);
		if (resp.getCode() != StatusCodes.SUCCESS) {
			return resp;
		}

		resp = releaseConfigFile(restOperator, token, address, ConfigFileRelease.builder()
				.namespace(file.getNamespace())
				.group(file.getGroup())
				.fileName(file.getFileName())
				.build());
		return resp;
	}


	/**
	 * {
	 *     "name":"application.properties",
	 *     "namespace":"someNamespace",
	 *     "group":"someGroup",
	 *     "content":"redis.cache.age=10",
	 *     "comment":"第一个配置文件",
	 *     "tags":[{"key":"service", "value":"helloService"}],
	 *     "createBy":"ledou",
	 *     "format":"properties"
	 * }
	 *
	 * 创建一个配置文件
	 *
	 * @param restOperator
	 * @param token
	 * @param address
	 * @param file
	 */
	private static ConfigFilesResponse createTempConfigFile(RestOperator restOperator,
			String token, String address, ConfigFileTemp file) {

		return sendPost(restOperator, HttpMethod.POST, toCreateConfigFileUrl(address),
				token, RestUtils.marshalJsonText(file));
	}

	/**
	 * 更新一个配置文件
	 *
	 * @param restOperator
	 * @param token
	 * @param address
	 * @param file
	 * @return
	 */
	private static ConfigFilesResponse updateTempConfigFile(RestOperator restOperator,
			String token, String address, ConfigFileTemp file) {

		return sendPost(restOperator, HttpMethod.PUT, toCreateConfigFileUrl(address),
				token, RestUtils.marshalJsonText(file));
	}

	/**
	 * {
	 *     "name":"release-002",
	 *     "fileName":"application.properties",
	 *     "namespace":"someNamespace",
	 *     "group":"someGroup",
	 *     "comment":"发布第一个配置文件",
	 *     "createBy":"ledou"
	 * }
	 *
	 * @param restOperator
	 * @param token
	 * @param address
	 * @param file
	 */
	private static ConfigFilesResponse releaseConfigFile(RestOperator restOperator, String token,
			String address, ConfigFileRelease file) {

		return sendPost(restOperator, HttpMethod.POST, toReleaseConfigFileUrl(address),
				token, RestUtils.marshalJsonText(file));
	}

	private static ConfigFilesResponse sendPost(RestOperator restOperator, HttpMethod method,
			String url, String token, String body) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("X-Polaris-Token", token);
		headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

		HttpEntity<String> entity = new HttpEntity<>(body, headers);

		RestResponse<String> restResponse = restOperator
				.curlRemoteEndpoint(url, method, entity, String.class);
		if (restResponse.hasServerError()) {
			LOG.error("[Polaris] server error to send post {}, method {}, reason {}",
					url, method, restResponse.getException().getMessage());
			return ResponseUtils.toConfigFilesResponse(null, StatusCodes.CONNECT_EXCEPTION);
		}
		if (restResponse.hasTextError()) {
			LOG.warn("[Polaris] text error to send post {}, method {}, code {}, reason {}",
					url, method, restResponse.getRawStatusCode(),
					restResponse.getStatusText());
			return ResponseUtils.toConfigFilesResponse(null, ResponseUtils.normalizeStatusCode(
					restResponse.getRawStatusCode()));
		}
		LOG.info("[Polaris] success to send post {}, method {}", url, method);
		return ResponseUtils.toConfigFilesResponse(null, StatusCodes.SUCCESS);
	}

	private static String toCreateConfigFileUrl(String address) {
		return String.format("http://%s/config/v1/configfiles", address);
	}

	private static String toReleaseConfigFileUrl(String address) {
		return String.format("http://%s/config/v1/configfiles/release", address);
	}

}
