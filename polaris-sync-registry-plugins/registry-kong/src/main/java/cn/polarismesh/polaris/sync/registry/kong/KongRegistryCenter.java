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

package cn.polarismesh.polaris.sync.registry.kong;

import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.extension.registry.Health;
import cn.polarismesh.polaris.sync.extension.registry.Health.Status;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.kong.model.ServiceObject;
import cn.polarismesh.polaris.sync.registry.kong.model.ServiceObjectList;
import cn.polarismesh.polaris.sync.registry.kong.model.TargetObject;
import cn.polarismesh.polaris.sync.registry.kong.model.TargetObjectList;
import cn.polarismesh.polaris.sync.registry.kong.model.UpstreamObject;
import cn.polarismesh.polaris.sync.registry.kong.model.UpstreamObjectList;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import com.google.protobuf.ProtocolStringList;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class KongRegistryCenter implements RegistryCenter {

    private static final Logger LOG = LoggerFactory.getLogger(KongRegistryCenter.class);

    private RegistryInitRequest registryInitRequest;

    @Autowired
    private RestTemplate restTemplate;

    private String token;

    private RestOperator restOperator;

    private final AtomicInteger serverErrorCount = new AtomicInteger(0);

    @Override
    public RegistryType getType() {
        return RegistryType.kong;
    }

    @Override
    public void init(RegistryInitRequest registryInitRequest) {
        this.registryInitRequest = registryInitRequest;
        this.token = registryInitRequest.getRegistryEndpoint().getToken();
        restOperator = new RestOperator(restTemplate);
    }

    @Override
    public void destroy() {

    }

    private void processHealthCheck(RestResponse<?> restResponse) {
        if (restResponse.hasServerError()) {
            serverErrorCount.addAndGet(1);
        } else {
            serverErrorCount.set(0);
        }
    }

    @Override
    public DiscoverResponse listInstances(Service service, Group group) {
        String sourceName = registryInitRequest.getSourceName();
        String upstreamName = ConversionUtils.getUpstreamName(service, group.getName(), sourceName);
        ProtocolStringList addressesList = registryInitRequest.getRegistryEndpoint().getAddressesList();
        String targetsUrl = KongEndpointUtils.toTargetsUrl(addressesList, upstreamName);
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                targetsUrl, HttpMethod.GET, KongEndpointUtils.getRequestEntity(token, null), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to query target {}", targetsUrl, restResponse.getException());
            return ResponseUtils.toRegistryCenterException(service);
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Kong] text error to query target {}, code {}, reason {}",
                    targetsUrl, restResponse.getRawStatusCode(), restResponse.getStatusText());
            if (restResponse.isNotFound()) {
                return ResponseUtils.toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE).build();
            }
            return ResponseUtils.toRegistryClientException(service);
        }
        ResponseEntity<String> strEntity = restResponse.getResponseEntity();
        TargetObjectList targetObjectList = ConversionUtils.unmarshalJsonText(strEntity.getBody(), TargetObjectList.class);
        if (null == targetObjectList) {
            LOG.error("[Kong] invalid response to query target from {}, reason {}", targetsUrl, strEntity.getBody());
            return ResponseUtils.toRegistryClientException(service);
        }
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
        List<TargetObject> data = targetObjectList.getData();
        if (!CollectionUtils.isEmpty(data)) {
            for (TargetObject targetObject : data) {
                builder.addInstances(ConversionUtils.parseTargetToInstance(targetObject));
            }
        }
        return builder.build();
    }

    @Override
    public void watch(Service service, ResponseListener eventListener) {
        throw new UnsupportedOperationException("watch is not supported in kong");
    }

    @Override
    public void unwatch(Service service) {
        throw new UnsupportedOperationException("unwatch is not supported in kong");
    }

    @Override
    public void updateServices(Collection<Service> services) {
        ProtocolStringList addressesList = registryInitRequest.getRegistryEndpoint().getAddressesList();
        //query all services in the source
        String servicesUrl = KongEndpointUtils.toServicesUrl(addressesList);
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                servicesUrl, HttpMethod.GET, KongEndpointUtils.getRequestEntity(token, null), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to query services {}", servicesUrl, restResponse.getException());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Kong] text error to query services {}, code {}, reason {}",
                    servicesUrl, restResponse.getRawStatusCode(), restResponse.getStatusText());
            return;
        }
        ResponseEntity<String> queryEntity = restResponse.getResponseEntity();
        ServiceObjectList serviceObjectList = ConversionUtils
                .unmarshalJsonText(queryEntity.getBody(), ServiceObjectList.class);
        if (null == serviceObjectList) {
            LOG.error("[Kong] invalid response to query services from {}, reason {}", servicesUrl, queryEntity.getBody());
            return;
        }
        String sourceName = registryInitRequest.getSourceName();
        String sourceType = registryInitRequest.getSourceType();
        Map<Service, ServiceObject> serviceObjectMap = ConversionUtils.parseServiceObjects(serviceObjectList,
                sourceName);
        Set<ServiceObject> servicesToCreate = new HashSet<>();
        Set<ServiceObject> servicesToDelete = new HashSet<>();
        Set<Service> processedServices = new HashSet<>();
        for (Service service : services) {
            ServiceObject targetObject = serviceObjectMap.get(service);
            if (null == targetObject) {
                //new add target
                servicesToCreate.add(ConversionUtils.serviceToServiceObject(service, sourceName, sourceType));
            }
            processedServices.add(service);
        }
        for (Map.Entry<Service, ServiceObject> entry : serviceObjectMap.entrySet()) {
            if (!processedServices.contains(entry.getKey())) {
                servicesToDelete.add(entry.getValue());
            }
        }
        // process operation
        int serviceAddCount = 0;
        int serviceDeleteCount = 0;
        for (ServiceObject serviceObject : servicesToCreate) {
            processServiceRequest(servicesUrl, HttpMethod.POST, serviceObject, "create");
            serviceAddCount++;
        }
        for (ServiceObject serviceObject : servicesToDelete) {
            String serviceUrl = KongEndpointUtils.toServiceUrl(addressesList, serviceObject.getName());
            processServiceRequest(serviceUrl, HttpMethod.DELETE, null, "delete");
            serviceDeleteCount++;
        }
        LOG.info("[Core] success to update services, add {}, delete {}", serviceAddCount, serviceDeleteCount);
    }

    @Override
    public void updateGroups(Service service, Collection<Group> groups) {
        ProtocolStringList addressesList = registryInitRequest.getRegistryEndpoint().getAddressesList();
        //query all services in the source
        String upstreamsUrl = KongEndpointUtils.toUpstreamsUrl(addressesList);
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                upstreamsUrl, HttpMethod.GET, KongEndpointUtils.getRequestEntity(token, null), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to query upstreams {}", upstreamsUrl, restResponse.getException());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Kong] text error to query upstreams {}, code {}, reason {}",
                    upstreamsUrl, restResponse.getRawStatusCode(), restResponse.getStatusText());
            return;
        }

        ResponseEntity<String> queryEntity = restResponse.getResponseEntity();
        UpstreamObjectList upstreamObjectList = ConversionUtils
                .unmarshalJsonText(queryEntity.getBody(), UpstreamObjectList.class);
        if (null == upstreamObjectList) {
            LOG.error("[Kong] invalid response to query upstreams from {}, reason {}", upstreamsUrl, queryEntity.getBody());
            return;
        }
        String sourceName = registryInitRequest.getSourceName();
        String sourceType = registryInitRequest.getSourceType();
        Map<String, UpstreamObject> upstreamObjectMap =
                ConversionUtils.parseUpstreamObjects(upstreamObjectList, service, sourceName);
        Set<UpstreamObject> upstreamsToCreate = new HashSet<>();
        Set<UpstreamObject> upstreamsToDelete = new HashSet<>();
        Set<String> processedGroups = new HashSet<>();
        for (Group group : groups) {
            UpstreamObject upstreamObject = upstreamObjectMap.get(group.getName());
            if (null == upstreamObject) {
                //new add target
                upstreamsToCreate.add(
                        ConversionUtils.groupToUpstreamObject(group.getName(), service, sourceName, sourceType));
            }
            processedGroups.add(group.getName());
        }
        for (Map.Entry<String, UpstreamObject> entry : upstreamObjectMap.entrySet()) {
            if (!processedGroups.contains(entry.getKey())) {
                upstreamsToDelete.add(entry.getValue());
            }
        }
        // process operation
        int upstreamAddCount = 0;
        int upstreamDeleteCount = 0;
        for (UpstreamObject upstreamObject : upstreamsToCreate) {
            processUpstreamRequest(upstreamsUrl, HttpMethod.POST, upstreamObject, "create");
            upstreamAddCount++;
        }
        for (UpstreamObject upstreamObject : upstreamsToDelete) {
            String upstreamUrl = KongEndpointUtils.toUpstreamUrl(addressesList, upstreamObject.getName());
            processUpstreamRequest(upstreamUrl, HttpMethod.DELETE, null, "delete");
            upstreamDeleteCount++;
        }
        LOG.info("[Core] success to update upstreams, add {}, delete {}", upstreamAddCount, upstreamDeleteCount);
    }

    private boolean registerGroup(Service service, String group) {
        String sourceName = registryInitRequest.getSourceName();
        String upstreamName = ConversionUtils.getUpstreamName(service, group, sourceName);
        ProtocolStringList addressesList = registryInitRequest.getRegistryEndpoint().getAddressesList();
        String upstreamUrl = KongEndpointUtils.toUpstreamUrl(addressesList, upstreamName);
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                upstreamUrl, HttpMethod.GET, KongEndpointUtils.getRequestEntity(token, null), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to query upstream {}", upstreamUrl, restResponse.getException());
            return false;
        }
        if (restResponse.hasNormalResponse()) {
            // exists, nothing to do yet
            return true;
        }
        if (restResponse.hasTextError()) {
            if (!restResponse.isNotFound()) {
                LOG.warn("[Kong] text error to query upstream {}, code {}, reason {}",
                        upstreamUrl, restResponse.getRawStatusCode(), restResponse.getStatusText());
                return false;
            }
        }
        HttpMethod updateMethod = HttpMethod.POST;
        String createUrl = KongEndpointUtils.toUpstreamsUrl(addressesList);
        UpstreamObject upstreamObject = new UpstreamObject();
        upstreamObject.setName(upstreamName);
        upstreamObject.setTags(Collections.singletonList(group));
        String upstreamText = ConversionUtils.marshalJsonText(upstreamObject);
        restResponse = restOperator.curlRemoteEndpoint(
                createUrl, updateMethod, KongEndpointUtils.getRequestEntity(token, upstreamText), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to create upstream {}, request {}",
                    createUrl, upstreamText, restResponse.getException());
            return false;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Kong] text error to create upstream {}, request {}, code {}, reason {}",
                    createUrl, upstreamText, restResponse.getRawStatusCode(), restResponse.getStatusText());
            return false;
        }
        LOG.error("[Kong] success to create upstream {}, request {}", createUrl, upstreamText);
        return true;
    }

    @Override
    public void updateInstances(Service service, Group group, Collection<Instance> instances) {
        if (!registerGroup(service, group.getName())) {
            LOG.warn("[Kong] updateInstances canceled by fail to regisger group");
            return;
        }
        String sourceName = registryInitRequest.getSourceName();
        String upstreamName = ConversionUtils.getUpstreamName(service, group.getName(), sourceName);
        ProtocolStringList addressesList = registryInitRequest.getRegistryEndpoint().getAddressesList();
        String targetsUrl = KongEndpointUtils.toTargetsUrl(addressesList, upstreamName);

        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                targetsUrl, HttpMethod.GET, KongEndpointUtils.getRequestEntity(token, null), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to query targets {}", targetsUrl, restResponse.getException());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Kong] text error to query targets {}, code {}, reason {}",
                    targetsUrl, restResponse.getRawStatusCode(), restResponse.getStatusText());
            return;
        }
        ResponseEntity<String> strEntity = restResponse.getResponseEntity();
        TargetObjectList targetObjectList = ConversionUtils.unmarshalJsonText(strEntity.getBody(), TargetObjectList.class);
        if (null == targetObjectList) {
            LOG.error("[Kong] invalid response to query targets {}, text {}", targetsUrl, strEntity.getBody());
            return;
        }
        Map<String, TargetObject> targetObjectMap = ConversionUtils.parseTargetObjects(targetObjectList);
        Set<TargetObject> targetsToCreate = new HashSet<>();
        Set<TargetObject> targetsToUpdate = new HashSet<>();
        Set<TargetObject> targetsToDelete = new HashSet<>();
        Set<String> processedAddresses = new HashSet<>();
        for (Instance instance : instances) {
            boolean healthy = instance.getHealthy().getValue();
            boolean isolated = instance.getIsolate().getValue();
            if (!healthy || isolated) {
                continue;
            }
            String address = String.format("%s:%d", instance.getHost().getValue(), instance.getPort().getValue());
            TargetObject targetObject = targetObjectMap.get(address);
            if (null == targetObject) {
                //new add target
                targetsToCreate.add(ConversionUtils.instanceToTargetObject(address, instance));
            } else if (targetObject.getWeight() != instance.getWeight().getValue()) {
                //modify target
                targetsToUpdate.add(ConversionUtils.instanceToTargetObject(address, instance));
            }
            processedAddresses.add(address);
        }
        for (TargetObject targetObject : targetObjectMap.values()) {
            if (!processedAddresses.contains(targetObject.getTarget())) {
                targetsToDelete.add(targetObject);
            }
        }
        // process operation
        int targetAddCount = 0;
        int targetPatchCount = 0;
        int targetDeleteCount = 0;
        for (TargetObject targetObject : targetsToCreate) {
            processTargetRequest(targetsUrl, HttpMethod.POST, targetObject, "create");
            targetAddCount++;
        }
        for (TargetObject targetObject : targetsToUpdate) {
            String targetUrl = KongEndpointUtils.toTargetUrl(addressesList, upstreamName, targetObject.getTarget());
            processTargetRequest(targetUrl, HttpMethod.PATCH, targetObject, "update");
            targetPatchCount++;
        }
        for (TargetObject targetObject : targetsToDelete) {
            String targetUrl = KongEndpointUtils.toTargetUrl(addressesList, upstreamName, targetObject.getTarget());
            processTargetRequest(targetUrl, HttpMethod.DELETE, null, "delete");
            targetDeleteCount++;
        }
        LOG.info("[Core] success to update targets, add {}, patch {}, delete {}",
                targetAddCount, targetPatchCount, targetDeleteCount);
    }

    private <T> void commonCreateOrUpdateRequest(
            String name, String serviceUrl, HttpMethod method, T serviceObject, String operation) {
        String jsonText = "";
        if (null != serviceObject) {
            jsonText = ConversionUtils.marshalJsonText(serviceObject);
        }
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                serviceUrl, method, KongEndpointUtils.getRequestEntity(token, jsonText), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to {} {} to {}, request {}",
                    operation, name, serviceUrl, jsonText, restResponse.getException());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.error("[Kong] server error to {} {} t {}, request {}, code {}, reason {}",
                    operation, name, serviceUrl, jsonText, restResponse.getRawStatusCode(), restResponse.getStatusText());
            return;
        }
        LOG.error("[Kong] success to {} {} to {}, request {}", operation, name, serviceUrl, jsonText);
    }

    private void processServiceRequest(
            String serviceUrl, HttpMethod method, ServiceObject serviceObject, String operation) {
        commonCreateOrUpdateRequest("service", serviceUrl, method, serviceObject, operation);
    }

    private void processUpstreamRequest(
            String upstreamUrl, HttpMethod method, UpstreamObject upstreamObject, String operation) {
        commonCreateOrUpdateRequest("upstream", upstreamUrl, method, upstreamObject, operation);
    }

    private void processTargetRequest(String targetUrl, HttpMethod method, TargetObject targetObject, String operation) {
        commonCreateOrUpdateRequest("target", targetUrl, method, targetObject, operation);
    }

    @Override
    public Health healthCheck() {
        int count = serverErrorCount.get();
        if (count > 0) {
            return new Health(Status.DOWN, -1, "", count);
        }
        return new Health(Status.UP, 0, "");
    }

    public static class ServiceGroup {
       final Service service;
       final String groupName;

        public ServiceGroup(Service service, String groupName) {
            this.service = service;
            this.groupName = groupName;
        }

        public Service getService() {
            return service;
        }

        public String getGroupName() {
            return groupName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ServiceGroup)) {
                return false;
            }
            ServiceGroup that = (ServiceGroup) o;
            return Objects.equals(service, that.service) &&
                    Objects.equals(groupName, that.groupName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(service, groupName);
        }

        @Override
        public String toString() {
            return "ServiceGroup{" +
                    "service=" + service +
                    ", groupName='" + groupName + '\'' +
                    '}';
        }
    }
}
