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
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.ServiceAlias;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonFormat.Printer;
import com.tencent.polaris.client.pb.RequestProto.DiscoverRequest;
import com.tencent.polaris.client.pb.RequestProto.DiscoverRequest.DiscoverRequestType;
import com.tencent.polaris.client.pb.ResponseProto;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class PolarisRestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisRestUtils.class);

    private static int CODE_NOT_FOUND_RESOURCE = 400202;

    public static void listAllServices(RestOperator restOperator, ResourceEndpoint registryEndpoint,
                                      List<String> httpAddresses, String namespace,
                                      Consumer<ServiceProto.Service> consumer) {
        int limit = 100;
        String allServiceUrl = String.format("%s?namespace=%s&limit=%s",
                PolarisEndpointUtils.toServicesUrl(httpAddresses), namespace, limit);
        int offset = 0;
        while (true) {
            String url = String.format("%s&offset=%s", allServiceUrl, offset);
            RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(url, HttpMethod.GET,
                    getRequestEntity(registryEndpoint.getAuthorization().getToken(), ""), String.class);
            if (!checkResponse(restResponse, "query", "all services", url, HttpMethod.GET, "")) {
                return;
            }
            ResponseProto.BatchQueryResponse.Builder builder = ResponseProto.BatchQueryResponse.newBuilder();
            boolean result = unmarshalProtoMessage(restResponse.getResponseEntity().getBody(), builder);
            if (!result) {
                LOG.error("[Polaris] invalid response to query instances from {}", url);
                return;
            }
            List<ServiceProto.Service> services = builder.build().getServicesList();
            services.forEach(consumer);
            if (services.size() < limit)
                break;
            offset += limit;
        }
    }

    public static void createServices(RestOperator restOperator, Collection<ServiceProto.Service> services,
                                       ResourceEndpoint registryEndpoint, List<String> httpAddresses) {
        String serviceUrl = PolarisEndpointUtils.toServicesUrl(httpAddresses);
        operateServices(serviceUrl, HttpMethod.POST, "create", restOperator, services, registryEndpoint);
    }

    public static void updateServices(RestOperator restOperator, Collection<ServiceProto.Service> services,
                                       ResourceEndpoint registryEndpoint, List<String> httpAddresses) {
        String serviceUrl = PolarisEndpointUtils.toServicesUrl(httpAddresses);
        operateServices(serviceUrl, HttpMethod.PUT, "update", restOperator, services, registryEndpoint);
    }

    public static void deleteServices(RestOperator restOperator, Collection<ServiceProto.Service> services,
                                       ResourceEndpoint registryEndpoint, List<String> httpAddresses) {
        String serviceUrl = PolarisEndpointUtils.toServicesDeleteUrl(httpAddresses);
        /* TODO
             1. remove instances of services firstly
             2. remove serviceAlias referenced by the service
         */
        operateServices(serviceUrl, HttpMethod.POST, "delete", restOperator, services, registryEndpoint);
    }

    public static void createServiceAlias(RestOperator restOperator, Collection<ServiceAlias> services,
                                      ResourceEndpoint registryEndpoint, List<String> httpAddresses) {
        String serviceAliasUrl = PolarisEndpointUtils.toServicesAliasUrl(httpAddresses);
        operateServiceAlias(serviceAliasUrl, HttpMethod.POST, "create", restOperator, services, registryEndpoint);
    }

    public static void createInstances(RestOperator restOperator, Collection<ServiceProto.Instance> instances,
            ResourceEndpoint registryEndpoint, List<String> httpAddresses) {
        String instancesUrl = PolarisEndpointUtils.toInstancesUrl(httpAddresses);
        operateInstances(instancesUrl, HttpMethod.POST, "create", restOperator, instances, registryEndpoint);
    }

    public static void updateInstances(RestOperator restOperator, Collection<ServiceProto.Instance> instances,
            ResourceEndpoint registryEndpoint, List<String> httpAddresses) {
        String instancesUrl = PolarisEndpointUtils.toInstancesUrl(httpAddresses);
        operateInstances(instancesUrl, HttpMethod.PUT, "update", restOperator, instances, registryEndpoint);
    }

    public static void deleteInstances(RestOperator restOperator, Collection<ServiceProto.Instance> instances,
            ResourceEndpoint registryEndpoint, List<String> httpAddresses) {
        String instancesUrl = PolarisEndpointUtils.toInstancesDeleteUrl(httpAddresses);
        operateInstances(instancesUrl, HttpMethod.POST, "delete", restOperator, instances, registryEndpoint);
    }

    private static void operateServices(String servicesUrl, HttpMethod method, String operation,
                                         RestOperator restOperator, Collection<ServiceProto.Service> services, ResourceEndpoint registryEndpoint) {
        String jsonText = "[]";
        if (null != services) {
            jsonText = marshalProtoObjectJsonText(services,
                    service -> String.format("service %s", service.getName().getValue()));
        }
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                servicesUrl, method, getRequestEntity(registryEndpoint.getAuthorization().getToken(), jsonText), String.class);
        if (restResponse.hasServerError()) {
            LOG.error("[Polaris] server error to {} service to {}, method {}, request {}",
                    operation, servicesUrl, method.name(), jsonText, restResponse.getException());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Polaris] text error to {} service to {}, method {}, request {}, code {}, reason {}",
                    operation, servicesUrl, method.name(), jsonText, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return;
        }
        LOG.info("[Polaris] success to {} service to {}, method {}, request {}", operation, servicesUrl, method.name(),
                jsonText);
    }

    private static void operateServiceAlias(String servicesUrl, HttpMethod method, String operation,
                                            RestOperator restOperator, Collection<ServiceAlias> aliases,
                                            ResourceEndpoint registryEndpoint) {
        if (null != aliases) {
            Gson gson = new Gson();
            for (ServiceAlias alias : aliases) {
                //protobuf marshal generate json missing "alias_namespace", use Gson temporarily
                String jsonText = gson.toJson(alias);
                RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(servicesUrl, method,
                        getRequestEntity(registryEndpoint.getAuthorization().getToken(), jsonText), String.class);
                checkResponse(restResponse, operation, "serviceAlias", servicesUrl, method, jsonText);
            }
        }
    }

    private static boolean checkResponse(RestResponse<String> restResponse, String operation, String target,
                                         String servicesUrl, HttpMethod method, String jsonText) {
        if (restResponse.hasServerError()) {
            LOG.error("[Polaris] server error to {} {} to {}, method {}, request {}",
                    operation, target, servicesUrl, method.name(), jsonText, restResponse.getException());
            return false;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Polaris] text error to {} {} serviceAlias to {}, method {}, request {}, code {}, reason {}",
                    operation, target, servicesUrl, method.name(), jsonText, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return false;
        }
        LOG.info("[Polaris] success to {} {} to {}, method {}, request {}", operation, target, servicesUrl, method.name(),
                jsonText);
        return true;
    }

    private static void operateInstances(String instancesUrl, HttpMethod method, String operation,
            RestOperator restOperator, Collection<ServiceProto.Instance> instances, ResourceEndpoint registryEndpoint) {
        String jsonText = "[]";
        if (null != instances) {
            jsonText = marshalProtoObjectJsonText(instances,
                    instance -> String.format("instance %s:%s", instance.getHost().getValue(), instance.getPort().getValue()));
        }
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                instancesUrl, method, getRequestEntity(registryEndpoint.getAuthorization().getToken(), jsonText), String.class);
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
            ResourceEndpoint registryEndpoint, List<String> httpAddresses, DiscoverResponse.Builder builder) {
        DiscoverRequest.Builder requestBuilder = DiscoverRequest.newBuilder();
        requestBuilder.setType(DiscoverRequestType.INSTANCE);
        ServiceProto.Service.Builder requestServiceBuilder = ServiceProto.Service.newBuilder()
                .setNamespace(ResponseUtils.toStringValue(service.getNamespace()))
                .setName(ResponseUtils.toStringValue(service.getService()));
        if (service.getMetadata() != null) {
            requestServiceBuilder.putAllMetadata(service.getMetadata());
        }
        requestBuilder.setService(requestServiceBuilder.build());
        String jsonText = marshalProtoMessageJsonText(requestBuilder.build());
        String discoverUrl = PolarisEndpointUtils.toDiscoverUrl(httpAddresses);
        HttpMethod method = HttpMethod.POST;
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                discoverUrl, method, getRequestEntity(registryEndpoint.getAuthorization().getToken(), jsonText), String.class);
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

    public static <T extends Message> String marshalProtoObjectJsonText(Collection<T> values,
                                                                        Function<T, String> infoResolver) {
        List<String> jsonValues = new ArrayList<>();
        for (T value : values) {
            String jsonText = marshalProtoMessageJsonText(value);
            if (null == jsonText) {
                LOG.error("[Polaris] {} marshaled failed, skip next operation", infoResolver.apply(value));
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
