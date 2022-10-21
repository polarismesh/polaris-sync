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

package cn.polarismesh.polaris.sync.registry.plugins.polaris;

import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonFormat.Printer;
import com.tencent.polaris.client.pb.RequestProto.DiscoverRequest;
import com.tencent.polaris.client.pb.RequestProto.DiscoverRequest.DiscoverRequestType;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

public class PolarisRestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisRestUtils.class);

    private static int CODE_NOT_FOUND_RESOURCE = 400202;

    public static void createInstances(RestOperator restOperator, Collection<ServiceProto.Instance> instances,
            RegistryEndpoint registryEndpoint, List<String> httpAddresses) {
        String instancesUrl = PolarisEndpointUtils.toInstancesUrl(httpAddresses);
        operateInstances(instancesUrl, HttpMethod.POST, "create", restOperator, instances, registryEndpoint);
    }

    public static void updateInstances(RestOperator restOperator, Collection<ServiceProto.Instance> instances,
            RegistryEndpoint registryEndpoint, List<String> httpAddresses) {
        String instancesUrl = PolarisEndpointUtils.toInstancesUrl(httpAddresses);
        operateInstances(instancesUrl, HttpMethod.PUT, "update", restOperator, instances, registryEndpoint);
    }

    public static void deleteInstances(RestOperator restOperator, Collection<ServiceProto.Instance> instances,
            RegistryEndpoint registryEndpoint, List<String> httpAddresses) {
        String instancesUrl = PolarisEndpointUtils.toInstancesDeleteUrl(httpAddresses);
        operateInstances(instancesUrl, HttpMethod.POST, "delete", restOperator, instances, registryEndpoint);
    }

    private static void operateInstances(String instancesUrl, HttpMethod method, String operation,
            RestOperator restOperator, Collection<ServiceProto.Instance> instances, RegistryEndpoint registryEndpoint) {
        String jsonText = "[]";
        if (null != instances) {
            jsonText = marshalProtoInstancesJsonText(instances);
        }
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                instancesUrl, method, getRequestEntity(registryEndpoint.getToken(), jsonText), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Polaris] server error to {} instances to {}, method {}, request {}",
                    operation, instancesUrl, method.name(), jsonText, restResponse.getException());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Polaris] text error to {} instances to {}, method {}, request {}, code {}, reason {}",
                    operation, instancesUrl, method.name(), jsonText, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return;
        }
        LOG.info("[Polaris] success to {} instances to {}, method {}, request {}", operation, instancesUrl, method.name(),
                jsonText);
    }

    public static DiscoverResponse discoverAllInstances(RestOperator restOperator, Service service,
            RegistryEndpoint registryEndpoint, List<String> httpAddresses, DiscoverResponse.Builder builder) {
        DiscoverRequest.Builder requestBuilder = DiscoverRequest.newBuilder();
        requestBuilder.setType(DiscoverRequestType.INSTANCE);
        ServiceProto.Service requestService = ServiceProto.Service.newBuilder()
                .setNamespace(ResponseUtils.toStringValue(service.getNamespace()))
                .setName(ResponseUtils.toStringValue(service.getService())).build();
        requestBuilder.setService(requestService);
        String jsonText = marshalProtoMessageJsonText(requestBuilder.build());
        String discoverUrl = PolarisEndpointUtils.toDiscoverUrl(httpAddresses);
        HttpMethod method = HttpMethod.POST;
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                discoverUrl, method, getRequestEntity(registryEndpoint.getToken(), jsonText), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Polaris] server error to discover instances to {}, method {}, request {}, reason {}",
                    discoverUrl, method.name(), jsonText, restResponse.getException().getMessage());
            return ResponseUtils.toConnectException(service, DiscoverResponseType.INSTANCE);
        }
        if (restResponse.hasTextError()) {
            if (notFoundService(restResponse.getStatusText())) {
                LOG.info("[Polaris] service not found to discover service {} to {}", service, discoverUrl);
                return null;
            } else {
                LOG.warn("[Polaris] text error to discover instances to {}, method {}, request {}, code {}, reason {}",
                        discoverUrl, method.name(), jsonText, restResponse.getRawStatusCode(),
                        restResponse.getStatusText());
                return ResponseUtils.toDiscoverResponse(service, ResponseUtils.normalizeStatusCode(
                        restResponse.getRawStatusCode()), DiscoverResponseType.INSTANCE).build();
            }
        }
        ResponseEntity<String> responseEntity = restResponse.getResponseEntity();
        String body = responseEntity.getBody();
        boolean result = unmarshalProtoMessage(body, builder);
        if (!result) {
            LOG.error("[Kong] invalid response to query instances from {}", discoverUrl);
            return ResponseUtils.toInvalidResponseException(service, DiscoverResponseType.INSTANCE);
        }
        return null;
    }

    public static boolean notFoundService(String statusText) {
        DiscoverResponse.Builder builder = DiscoverResponse.newBuilder();
        boolean result = unmarshalProtoMessage(statusText, builder);
        if (result) {
            return builder.getCode().getValue() == CODE_NOT_FOUND_RESOURCE;
        }
        return false;
    }

    public static <T> HttpEntity<T> getRequestEntity(String token, T object) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(token)) {
            headers.add("X-Polaris-Token", token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<T>(object, headers);
    }

    public static String marshalProtoInstancesJsonText(Collection<Instance> values) {
        List<String> jsonValues = new ArrayList<>();
        for (ServiceProto.Instance value : values) {
            String jsonText = marshalProtoMessageJsonText(value);
            if (null == jsonText) {
                LOG.error("[Polaris] instance {}:{} marshaled failed, skip next operation",
                        value.getHost().getValue(), value.getPort().getValue());
                continue;
            }
            jsonValues.add(jsonText);
        }
        String text = String.join(",", jsonValues);
        return "[" + text + "]";
    }

    public static String marshalProtoMessageJsonText(Message value) {
        Printer printer = JsonFormat.printer();
        try {
            return printer.print(value);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("[Core] fail to serialize object {}", value, e);
        }
        return null;
    }

    public static boolean unmarshalProtoMessage(String jsonText, Message.Builder builder) {
        Parser parser = JsonFormat.parser().ignoringUnknownFields();
        try {
            parser.merge(jsonText, builder);
            return true;
        } catch (InvalidProtocolBufferException e) {
            LOG.error("[Core] fail to deserialize jsonText {}", jsonText, e);
            return false;
        }
    }
}
