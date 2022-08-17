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

package cn.polarismesh.polaris.sync.registry.plugins.nacos;

import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.common.rest.RestUtils;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.AuthResponse;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.NacosNamespace;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

public class NacosRestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRestUtils.class);

    public static List<NacosNamespace> discoverAllNamespaces(
            RestOperator restOperator, RegistryEndpoint registryEndpoint) {
        AuthResponse authResponse = null;
        if (StringUtils.hasText(registryEndpoint.getUser()) && StringUtils.hasText(registryEndpoint.getPassword())) {
            // 先进行登录
            authResponse = auth(restOperator, registryEndpoint);
            if (null != authResponse) {
                LOG.error("[Nacos] fail to login nacos when discover all namespaces");
                return null;
            }
        }
        String discoverUrl = NacosEndpointUtils.toNamespacesUrl(registryEndpoint.getAddressesList());
        //TODO: support fetch namespaces
        return Collections.emptyList();
    }

    public static AuthResponse auth(RestOperator restOperator, RegistryEndpoint registryEndpoint) {
        String authUrl = NacosEndpointUtils.toAuthUrl(registryEndpoint.getAddressesList());
        String authMessage = String.format(
                "username=%s&password=%s", registryEndpoint.getUser(), registryEndpoint.getPassword());
        HttpMethod method = HttpMethod.GET;
        HttpEntity<String> entity = new HttpEntity<>(authMessage);
        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(authUrl, method, entity, String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos] server error to auth {}, method {}, request {}, reason {}",
                    authUrl, method.name(), authMessage, restResponse.getException().getMessage());
            return null;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos] text error to auth {}, method {}, request {}, code {}, reason {}",
                    authUrl, method.name(), authMessage, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return null;
        }
        ResponseEntity<String> responseEntity = restResponse.getResponseEntity();
        String message = responseEntity.getBody();
        return RestUtils.unmarshalJsonText(message, AuthResponse.class);
    }

    private static <T> HttpEntity<T> getRequestEntity(String token, T object) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(token)) {
            headers.add("X-Nacos-Token", token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<T>(object, headers);
    }
}
