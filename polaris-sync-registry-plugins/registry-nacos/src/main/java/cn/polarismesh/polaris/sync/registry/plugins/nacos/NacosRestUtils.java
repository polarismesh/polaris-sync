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
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.AuthResponse;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.NacosConsts;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.NacosNamespace;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.NacosNamespaceResponse;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.NacosServiceView;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.NacosServicesResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class NacosRestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRestUtils.class);

    public static DiscoverResponse discoverAllNamespaces(AuthResponse authResponse,
            RestOperator restOperator, RegistryEndpoint registryEndpoint, List<NacosNamespace> namespaces) {
        String namespacesUrl = NacosEndpointUtils.toNamespacesUrl(registryEndpoint.getAddressesList());
        if (StringUtils.hasText(authResponse.getAccessToken())) {
            namespacesUrl += "?accessToken=" + authResponse.getAccessToken();
        }
        HttpMethod method = HttpMethod.GET;
        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(namespacesUrl, method, new HttpEntity<>(""), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos] server error to get namespaces {}, method {}, reason {}",
                    namespacesUrl, method.name(), restResponse.getException().getMessage());
            return ResponseUtils.toConnectException(null, DiscoverResponseType.NAMESPACES);
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos] text error to get namespaces {}, method {}, code {}, reason {}",
                    namespacesUrl, method.name(), restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return ResponseUtils.toDiscoverResponse(null, ResponseUtils.normalizeStatusCode(
                    restResponse.getRawStatusCode()), DiscoverResponseType.SERVICES).build();
        }
        String jsonText = restResponse.getResponseEntity().getBody();
        NacosNamespaceResponse nacosNamespaceResponse = RestUtils
                .unmarshalJsonText(jsonText, NacosNamespaceResponse.class);
        if (null == nacosNamespaceResponse) {
            LOG.error("[Nacos] invalid response to get namespaces {}, method {}, response {}",
                    namespacesUrl, method.name(), jsonText);
            return null;
        }
        namespaces.addAll(nacosNamespaceResponse.getData());
        return null;
    }

    public static DiscoverResponse discoverAllServices(AuthResponse authResponse, RestOperator restOperator,
            RegistryEndpoint endpoint, Service service, int pageno, Integer restCount, List<NacosServiceView> services) {
        String servicesUrl = NacosEndpointUtils.toServicesUrl(endpoint.getAddressesList());
        List<String> parameters = new ArrayList<>();
        if (StringUtils.hasText(authResponse.getAccessToken())) {
            parameters.add(String.format("accessToken=%s", authResponse.getAccessToken()));
        }
        parameters.add(String.format("namespaceId=%s", service.getNamespace()));
        parameters.add(String.format("pageNo=%d", pageno));
        parameters.add(String.format("pageSize=%d", NacosConsts.DEFAULT_PAGE_SIZE));
        String queryStr = String.join("&", parameters);
        servicesUrl += "?" + queryStr;

        HttpMethod method = HttpMethod.GET;
        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(servicesUrl, method, new HttpEntity<>(""), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos] server error to get services {}, method {}, reason {}",
                    servicesUrl, method.name(), restResponse.getException().getMessage());
            return ResponseUtils.toConnectException(service, DiscoverResponseType.SERVICES);
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos] text error to get services {}, method {}, code {}, reason {}",
                    servicesUrl, method.name(), restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return ResponseUtils.toDiscoverResponse(service, ResponseUtils.normalizeStatusCode(
                    restResponse.getRawStatusCode()), DiscoverResponseType.SERVICES).build();
        }
        String jsonText = restResponse.getResponseEntity().getBody();
        NacosServicesResponse nacosServicesResponse = RestUtils
                .unmarshalJsonText(jsonText, NacosServicesResponse.class);
        if (null == nacosServicesResponse) {
            LOG.error("[Nacos] invalid response to get services {}, method {}, response {}",
                    servicesUrl, method.name(), jsonText);
            return ResponseUtils.toInvalidResponseException(service, DiscoverResponseType.SERVICES);
        }
        int totalCount = nacosServicesResponse.getCount();
        List<NacosServiceView> serviceList = nacosServicesResponse.getServiceList();
        if (totalCount <= 0 || CollectionUtils.isEmpty(serviceList)) {
            return null;
        }
        services.addAll(serviceList);
        if (null == restCount) {
            restCount = totalCount;
        }
        restCount-= serviceList.size();
        if (restCount <= 0) {
            return null;
        }
        pageno++;
        return discoverAllServices(authResponse, restOperator, endpoint, service, pageno, restCount, services);
    }

    public static void createNamespace(AuthResponse authResponse,
            RestOperator restOperator, RegistryEndpoint registryEndpoint, String namespace) {
        String namespacesUrl = NacosEndpointUtils.toNamespacesUrl(registryEndpoint.getAddressesList());
        if (StringUtils.hasText(authResponse.getAccessToken())) {
            namespacesUrl += "?accessToken=" + authResponse.getAccessToken();
        }
        HttpMethod method = HttpMethod.POST;
        String requestText = String.format("customNamespaceId=%s&namespaceName=%s&namespaceDesc=", namespace, namespace);
        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(namespacesUrl, method, new HttpEntity<>(requestText), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos] server error to create namespaces {}, method {}, request {}, reason {}",
                    namespacesUrl, method.name(), requestText, restResponse.getException().getMessage());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos] text error to create namespaces {}, method {}, request {}, code {}, reason {}",
                    namespacesUrl, method.name(), requestText, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return;
        }
        LOG.info("[Nacos] success to create namespaces {}, method {}, request {}", namespacesUrl, method, requestText);
    }

    public static DiscoverResponse auth(RestOperator restOperator,
            RegistryEndpoint registryEndpoint, AuthResponse authResponse, Service service, DiscoverResponseType type) {
        String authUrl = NacosEndpointUtils.toAuthUrl(registryEndpoint.getAddressesList());
        String authMessage = String.format(
                "username=%s&password=%s", registryEndpoint.getUser(), registryEndpoint.getPassword());
        HttpMethod method = HttpMethod.POST;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<String> entity = new HttpEntity<>(authMessage, headers);
        RestResponse<String> restResponse = restOperator
                .curlRemoteEndpoint(authUrl, method, entity, String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Nacos] server error to auth {}, method {}, request {}, reason {}",
                    authUrl, method.name(), authMessage, restResponse.getException().getMessage());
            return ResponseUtils.toConnectException(service, type);
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Nacos] text error to auth {}, method {}, request {}, code {}, reason {}",
                    authUrl, method.name(), authMessage, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return ResponseUtils.toDiscoverResponse(service, ResponseUtils.normalizeStatusCode(
                    restResponse.getRawStatusCode()), type).build();
        }
        ResponseEntity<String> responseEntity = restResponse.getResponseEntity();
        String message = responseEntity.getBody();
        AuthResponse authResponseResp =  RestUtils.unmarshalJsonText(message, AuthResponse.class);
        if (null == authResponseResp) {
            LOG.error("[Nacos] invalid response to auth {}, method {}, response {}", authUrl, method.name(), message);
            return ResponseUtils.toInvalidResponseException(service, type);
        }
        authResponse.setAccessToken(authResponseResp.getAccessToken());
        authResponse.setGlobalAdmin(authResponseResp.isGlobalAdmin());
        return null;
    }



}
