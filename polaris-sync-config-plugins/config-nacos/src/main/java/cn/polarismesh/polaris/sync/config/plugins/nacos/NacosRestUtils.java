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
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.common.rest.RestUtils;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.config.plugins.nacos.model.AuthResponse;
import cn.polarismesh.polaris.sync.config.plugins.nacos.model.NacosNamespace;
import cn.polarismesh.polaris.sync.config.plugins.nacos.model.NacosNamespaceResponse;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import static cn.polarismesh.polaris.sync.common.rest.RestOperator.pickAddress;

public class NacosRestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRestUtils.class);

    public static DiscoverResponse discoverAllNamespaces(AuthResponse authResponse,
            RestOperator restOperator, ResourceEndpoint endpoint, List<NacosNamespace> namespaces) {
        String namespacesUrl = toNamespacesUrl(endpoint.getServerAddresses());
        if (StringUtils.hasText(authResponse.getAccessToken())) {
            namespacesUrl += "?accessToken=" + authResponse.getAccessToken();
        }
        HttpMethod method = HttpMethod.GET;
        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(namespacesUrl, method, new HttpEntity<>(""), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos][Config] server error to get namespaces {}, method {}, reason {}",
                    namespacesUrl, method.name(), restResponse.getException().getMessage());
            return ResponseUtils.toConnectException(null, DiscoverResponseType.NAMESPACES);
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos][Config] text error to get namespaces {}, method {}, code {}, reason {}",
                    namespacesUrl, method.name(), restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return ResponseUtils.toDiscoverResponse(null, ResponseUtils.normalizeStatusCode(
                    restResponse.getRawStatusCode()), DiscoverResponseType.SERVICES).build();
        }
        String jsonText = restResponse.getResponseEntity().getBody();
        NacosNamespaceResponse nacosNamespaceResponse = RestUtils
                .unmarshalJsonText(jsonText, NacosNamespaceResponse.class);
        if (null == nacosNamespaceResponse) {
            LOG.error("[Nacos][Config] invalid response to get namespaces {}, method {}, response {}",
                    namespacesUrl, method.name(), jsonText);
            return null;
        }
        namespaces.addAll(nacosNamespaceResponse.getData());
        return null;
    }

    public static void createNamespace(AuthResponse authResponse,
            RestOperator restOperator, ResourceEndpoint endpoint, String namespace) {
        String namespacesUrl = toNamespacesUrl(endpoint.getServerAddresses());
        if (StringUtils.hasText(authResponse.getAccessToken())) {
            namespacesUrl += "?accessToken=" + authResponse.getAccessToken();
        }
        HttpMethod method = HttpMethod.POST;
        String requestText = String.format("customNamespaceId=%s&namespaceName=%s&namespaceDesc=", namespace, namespace);
        namespacesUrl += "&" + requestText;
        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(namespacesUrl, method, new HttpEntity<>(requestText), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos][Config] server error to create namespaces {}, method {}, request {}, reason {}",
                    namespacesUrl, method.name(), requestText, restResponse.getException().getMessage());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos][Config] text error to create namespaces {}, method {}, request {}, code {}, reason {}",
                    namespacesUrl, method.name(), requestText, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return;
        }
        LOG.info("[Nacos][Config] success to create namespaces {}, method {}, request {}", namespacesUrl, method, requestText);
    }

    public static DiscoverResponse auth(RestOperator restOperator,
            ResourceEndpoint endpoint, AuthResponse authResponse, Service service, DiscoverResponseType type) {
        String authUrl = toAuthUrl(endpoint.getServerAddresses());
        String authMessage = String.format(
                "username=%s&password=%s", endpoint.getAuthorization().getUsername(), endpoint.getAuthorization().getPassword());
        HttpMethod method = HttpMethod.POST;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<String> entity = new HttpEntity<>(authMessage, headers);
        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(authUrl, method, entity, String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos][Config] server error to auth {}, method {}, request {}, reason {}",
                    authUrl, method.name(), authMessage, restResponse.getException().getMessage());
            return ResponseUtils.toConnectException(service, type);
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos][Config] text error to auth {}, method {}, request {}, code {}, reason {}",
                    authUrl, method.name(), authMessage, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return ResponseUtils.toDiscoverResponse(service, ResponseUtils.normalizeStatusCode(
                    restResponse.getRawStatusCode()), type).build();
        }
        ResponseEntity<String> responseEntity = restResponse.getResponseEntity();
        String message = responseEntity.getBody();
        AuthResponse authResponseResp =  RestUtils.unmarshalJsonText(message, AuthResponse.class);
        if (null == authResponseResp) {
            LOG.error("[Nacos][Config] invalid response to auth {}, method {}, response {}", authUrl, method.name(), message);
            return ResponseUtils.toInvalidResponseException(service, type);
        }
        authResponse.setAccessToken(authResponseResp.getAccessToken());
        authResponse.setGlobalAdmin(authResponseResp.isGlobalAdmin());
        return null;
    }

    public static boolean publishConfig(AuthResponse authResponse,
            RestOperator restOperator, ResourceEndpoint endpoint, ConfigFile file) {
        String namespacesUrl = toPublishConfig(endpoint.getServerAddresses());
        if (StringUtils.hasText(authResponse.getAccessToken())) {
            namespacesUrl += "?accessToken=" + authResponse.getAccessToken();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);

        HttpMethod method = HttpMethod.POST;
        String requestText = "";

        List<String> tags = new ArrayList<>();
        file.getLabels().forEach((key, value) -> tags.add(key + "=" + value));

        if (Objects.equals(file.getNamespace(), "")) {
            requestText = String.format("dataId=%s&group=%s&content=%s&config_tags=%s",
                    file.getFileName(), file.getGroup(), file.getContent(), String.join(",", tags));
        } else {
            requestText = String.format("tenant=%s&dataId=%s&group=%s&content=%s&config_tags=%s",
                    file.getNamespace(), file.getFileName(), file.getGroup(), file.getContent(), String.join(",", tags));
        }

        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(namespacesUrl, method, new HttpEntity<>(requestText, headers), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos][Config] server error to create namespaces {}, method {}, request {}, reason {}",
                    namespacesUrl, method.name(), requestText, restResponse.getException().getMessage());
            return false;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos][Config] text error to create namespaces {}, method {}, request {}, code {}, reason {}",
                    namespacesUrl, method.name(), requestText, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return false;
        }
        LOG.info("[Nacos][Config] success to publish config {}, method {}, request {}", namespacesUrl, method, requestText);
        return true;
    }

    public static String toNamespacesUrl(List<String> addresses) {
        String address = pickAddress(addresses);
        return String.format("http://%s/nacos/v1/console/namespaces", address);
    }

    public static String toAuthUrl(List<String> addresses) {
        String address = pickAddress(addresses);
        return String.format("http://%s/nacos/v1/auth/login", address);
    }

    public static String toPublishConfig(List<String> addresses) {
        String address = pickAddress(addresses);
        return String.format("http://%s/nacos/v1/cs/configs", address);
    }

}
