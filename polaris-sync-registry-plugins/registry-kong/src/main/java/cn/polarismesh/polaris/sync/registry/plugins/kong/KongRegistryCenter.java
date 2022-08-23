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

package cn.polarismesh.polaris.sync.registry.plugins.kong;

import static cn.polarismesh.polaris.sync.common.rest.RestOperator.pickAddress;

import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.common.rest.RestUtils;
import cn.polarismesh.polaris.sync.extension.registry.AbstractRegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import cn.polarismesh.polaris.sync.registry.plugins.kong.model.ServiceObject;
import cn.polarismesh.polaris.sync.registry.plugins.kong.model.ServiceObjectList;
import cn.polarismesh.polaris.sync.registry.plugins.kong.model.TargetObject;
import cn.polarismesh.polaris.sync.registry.plugins.kong.model.TargetObjectList;
import cn.polarismesh.polaris.sync.registry.plugins.kong.model.UpstreamObject;
import cn.polarismesh.polaris.sync.registry.plugins.kong.model.UpstreamObjectList;
import com.google.protobuf.ProtocolStringList;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
public class KongRegistryCenter extends AbstractRegistryCenter {

    private static final Logger LOG = LoggerFactory.getLogger(KongRegistryCenter.class);

    private RegistryInitRequest registryInitRequest;

    private String token;

    private RestOperator restOperator;

    @Override
    public RegistryType getType() {
        return RegistryType.kong;
    }

    @Override
    public void init(RegistryInitRequest registryInitRequest) {
        Assert.hasText(registryInitRequest.getSourceName(), "source registry for kong is empty");
        this.registryInitRequest = registryInitRequest;
        this.token = registryInitRequest.getRegistryEndpoint().getToken();
        restOperator = new RestOperator();
    }

    @Override
    public void destroy() {

    }

    private void processHealthCheck(RestResponse<?> restResponse) {
        if (restResponse.hasServerError()) {
            serverErrorCount.addAndGet(1);
        }
        totalCount.addAndGet(1);
    }

    @Override
    public DiscoverResponse listInstances(Service service, Group group) {
        throw new UnsupportedOperationException("listInstances is not supported in kong");
    }

    @Override
    public boolean watch(Service service, ResponseListener eventListener) {
        throw new UnsupportedOperationException("watch is not supported in kong");
    }

    @Override
    public void unwatch(Service service) {
        throw new UnsupportedOperationException("unwatch is not supported in kong");
    }

    private boolean resolveAllServices(String address, String nextUrl, List<ServiceObject> services) {
        String servicesUrl;
        if (StringUtils.hasText(nextUrl)) {
            servicesUrl = nextUrl;
        } else {
            servicesUrl = KongEndpointUtils.toServicesUrl(address);
        }
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                servicesUrl, HttpMethod.GET, RestUtils.getRequestEntity(token, null), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to query services {}, reason {}", servicesUrl, restResponse.getException().getMessage());
            return false;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Kong] text error to query services {}, code {}, reason {}",
                    servicesUrl, restResponse.getRawStatusCode(), restResponse.getStatusText());
            return false;
        }
        ResponseEntity<String> queryEntity = restResponse.getResponseEntity();
        ServiceObjectList serviceObjectList = RestUtils.unmarshalJsonText(queryEntity.getBody(), ServiceObjectList.class);
        if (null == serviceObjectList) {
            LOG.error("[Kong] invalid response to query services from {}, reason {}", servicesUrl,
                    queryEntity.getBody());
            return false;
        }
        services.addAll(serviceObjectList.getData());
        if (StringUtils.hasText(serviceObjectList.getNext())) {
            nextUrl = replaceHostPort(serviceObjectList.getNext(), address);
            if (!StringUtils.hasText(nextUrl)) {
                return false;
            }
            return resolveAllServices(address, nextUrl, services);
        }
        return true;
    }

    @Override
    public void updateServices(Collection<Service> services) {
        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
        ProtocolStringList addressesList = registryEndpoint.getAddressesList();
        String address = pickAddress(addressesList);
        //query all services in the source
        List<ServiceObject> serviceObjects = new ArrayList<>();
        if (!resolveAllServices(address, "", serviceObjects)) {
            LOG.error("[Kong] fail to query all services, registry {}, address {}", registryEndpoint, address);
            return;
        }
        ServiceObjectList serviceObjectList = new ServiceObjectList();
        serviceObjectList.setData(serviceObjects);
        String sourceName = registryInitRequest.getSourceName();
        RegistryType sourceType = registryInitRequest.getSourceType();
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
        if (!servicesToCreate.isEmpty()) {
            LOG.info("[Kong] services(source {}) pending to create are {}", sourceName, servicesToCreate);
            String servicesUrl = KongEndpointUtils.toServicesUrl(address);
            for (ServiceObject serviceObject : servicesToCreate) {
                processServiceRequest(servicesUrl, HttpMethod.POST, serviceObject, "create");
                serviceAddCount++;
            }
        }
        if (!servicesToDelete.isEmpty()) {
            LOG.info("[Kong] services(source {})  pending to delete are {}", sourceName, servicesToDelete);
            for (ServiceObject serviceObject : servicesToDelete) {
                String serviceUrl = KongEndpointUtils.toServiceUrl(addressesList, serviceObject.getName());
                processServiceRequest(serviceUrl, HttpMethod.DELETE, null, "delete");
                serviceDeleteCount++;
            }
        }
        LOG.info("[Kong] success to update services(source {}), add {}, delete {}", sourceName, serviceAddCount, serviceDeleteCount);
    }

    private static final String SCHEME = "http://";

    private String replaceHostPort(String url, String address) {
        if (!url.startsWith(SCHEME)) {
            LOG.error("[Kong] invalid next url {}", url);
            return "";
        }
        String rest = url.substring(SCHEME.length());
        rest = rest.substring(rest.indexOf("/"));
        return SCHEME + address + rest;
    }

    private boolean resolveAllUpstreams(String address, String nextUrl, List<UpstreamObject> upstreams) {
        String upstreamsUrl;
        if (StringUtils.hasText(nextUrl)) {
            upstreamsUrl = nextUrl;
        } else {
            upstreamsUrl = KongEndpointUtils.toUpstreamsUrl(address);
        }
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                upstreamsUrl, HttpMethod.GET, RestUtils.getRequestEntity(token, null), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to query upstreams {}, reason {}",
                    upstreamsUrl, restResponse.getException().getMessage());
            return false;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Kong] text error to query upstreams {}, code {}, reason {}",
                    upstreamsUrl, restResponse.getRawStatusCode(), restResponse.getStatusText());
            return false;
        }

        ResponseEntity<String> queryEntity = restResponse.getResponseEntity();
        UpstreamObjectList upstreamObjectList = RestUtils.unmarshalJsonText(queryEntity.getBody(), UpstreamObjectList.class);
        if (null == upstreamObjectList) {
            LOG.error("[Kong] invalid response to query upstreams from {}, reason {}", upstreamsUrl,
                    queryEntity.getBody());
            return false;
        }
        upstreams.addAll(upstreamObjectList.getData());
        if (StringUtils.hasText(upstreamObjectList.getNext())) {
            nextUrl = replaceHostPort(upstreamObjectList.getNext(), address);
            if (!StringUtils.hasText(nextUrl)) {
                return false;
            }
            return resolveAllUpstreams(address, nextUrl, upstreams);
        }
        return true;
    }

    @Override
    public void updateGroups(Service service, Collection<Group> groups) {
        ProtocolStringList addressesList = registryInitRequest.getRegistryEndpoint().getAddressesList();
        String address = pickAddress(addressesList);
        //query all upstreams in the source
        List<UpstreamObject> upstreams = new ArrayList<>();
        if (!resolveAllUpstreams(address, "", upstreams)) {
            LOG.error("[Kong] fail to query all upstreams for service {}, address {}", service, address);
            return;
        }
        UpstreamObjectList upstreamObjectList = new UpstreamObjectList();
        upstreamObjectList.setData(upstreams);
        String sourceName = registryInitRequest.getSourceName();
        RegistryType sourceType = registryInitRequest.getSourceType();
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
        if (!upstreamsToCreate.isEmpty()) {
            LOG.info("[Kong] upstreams(source {}) pending to create are {}", sourceName, upstreamsToCreate);
            String upstreamsUrl = KongEndpointUtils.toUpstreamsUrl(address);
            for (UpstreamObject upstreamObject : upstreamsToCreate) {
                processUpstreamRequest(upstreamsUrl, HttpMethod.POST, upstreamObject, "create");
                upstreamAddCount++;
            }
        }
        if (!upstreamsToDelete.isEmpty()) {
            LOG.info("[Kong] upstreams(source {}) pending to delete are {}", sourceName, upstreamsToDelete);
            for (UpstreamObject upstreamObject : upstreamsToDelete) {
                String upstreamUrl = KongEndpointUtils.toUpstreamUrl(addressesList, upstreamObject.getName());
                processUpstreamRequest(upstreamUrl, HttpMethod.DELETE, null, "delete");
                upstreamDeleteCount++;
            }
        }
        LOG.info("[Kong] success to update upstreams(source {}) for service {}, add {}, delete {}",
                sourceName, service,  upstreamAddCount, upstreamDeleteCount);
    }

    @Override
    public void updateInstances(Service service, Group group, Collection<Instance> instances) {
        String sourceName = registryInitRequest.getSourceName();
        LOG.info("[Kong] instances to update instances(source {}) group {}, service {}, is {}, ",
                sourceName, group.getName(), service, instances);
        String upstreamName = ConversionUtils.getUpstreamName(service, group.getName(), sourceName);
        ProtocolStringList addressesList = registryInitRequest.getRegistryEndpoint().getAddressesList();
        String targetReadUrl = KongEndpointUtils.toTargetsReadUrl(addressesList, upstreamName);

        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                targetReadUrl, HttpMethod.GET, RestUtils.getRequestEntity(token, null), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to query targets {}, reason {}",
                    targetReadUrl, restResponse.getException().getMessage());
            return;
        }
        if (restResponse.hasTextError() && restResponse.getRawStatusCode() != 404) {
            LOG.warn("[Kong] text error to query targets {}, code {}, reason {}",
                    targetReadUrl, restResponse.getRawStatusCode(), restResponse.getStatusText());
            return;
        }
        TargetObjectList targetObjectList;
        if (restResponse.hasNormalResponse()) {
            ResponseEntity<String> strEntity = restResponse.getResponseEntity();
            targetObjectList = RestUtils.unmarshalJsonText(strEntity.getBody(), TargetObjectList.class);
            if (null == targetObjectList) {
                LOG.error("[Kong] invalid response to query targets {}, text {}", targetReadUrl, strEntity.getBody());
                return;
            }
        } else {
            targetObjectList = new TargetObjectList();
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
        if (!targetsToCreate.isEmpty()) {
            LOG.info("[Kong] targets(source {}) pending to create are {}, upstream {}", sourceName, targetsToCreate, upstreamName);
            String targetWriteUrl = KongEndpointUtils.toTargetsWriteUrl(addressesList, upstreamName);
            for (TargetObject targetObject : targetsToCreate) {
                processTargetRequest(targetWriteUrl, HttpMethod.POST, targetObject, "create");
                targetAddCount++;
            }
        }
        if (!targetsToUpdate.isEmpty()) {
            LOG.info("[Kong] targets(source {}) pending to update are {}, upstream {}", sourceName, targetsToUpdate, upstreamName);
            for (TargetObject targetObject : targetsToUpdate) {
                String targetUrl = KongEndpointUtils.toTargetUrl(addressesList, upstreamName, targetObject.getTarget());
                processTargetRequest(targetUrl, HttpMethod.PUT, targetObject, "update");
                targetPatchCount++;
            }
        }
        if (!targetsToDelete.isEmpty()) {
            LOG.info("[Kong] targets(source {}) pending to delete are {}, upstream {}", sourceName, targetsToDelete, upstreamName);
            for (TargetObject targetObject : targetsToDelete) {
                String targetUrl = KongEndpointUtils.toTargetUrl(addressesList, upstreamName, targetObject.getTarget());
                processTargetRequest(targetUrl, HttpMethod.DELETE, null, "delete");
                targetDeleteCount++;
            }
        }
        LOG.info("[Kong] success to update targets(source {}), add {}, patch {}, delete {}", sourceName,
                targetAddCount, targetPatchCount, targetDeleteCount);
    }

    private <T> void commonCreateOrUpdateRequest(
            String name, String serviceUrl, HttpMethod method, T serviceObject, String operation) {
        String jsonText = "";
        if (null != serviceObject) {
            jsonText = RestUtils.marshalJsonText(serviceObject);
        }
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                serviceUrl, method, RestUtils.getRequestEntity(token, jsonText), String.class);
        processHealthCheck(restResponse);
        if (restResponse.hasServerError()) {
            LOG.error("[Kong] server error to {} {} to {}, method {}, request {}, reason {}",
                    operation, name, serviceUrl, method.name(), jsonText, restResponse.getException().getMessage());
            return;
        }
        if (restResponse.hasTextError()) {
            LOG.warn("[Kong] text error to {} {} to {}, method {}, request {}, code {}, reason {}",
                    operation, name, serviceUrl, method.name(), jsonText, restResponse.getRawStatusCode(),
                    restResponse.getStatusText());
            return;
        }
        LOG.info("[Kong] success to {} {} to {}, method {}, request {}", operation, name, serviceUrl, method.name(),
                jsonText);
    }

    private void processServiceRequest(
            String serviceUrl, HttpMethod method, ServiceObject serviceObject, String operation) {
        commonCreateOrUpdateRequest("service", serviceUrl, method, serviceObject, operation);
    }

    private void processUpstreamRequest(
            String upstreamUrl, HttpMethod method, UpstreamObject upstreamObject, String operation) {
        commonCreateOrUpdateRequest("upstream", upstreamUrl, method, upstreamObject, operation);
    }

    private void processTargetRequest(String targetUrl, HttpMethod method, TargetObject targetObject,
            String operation) {
        commonCreateOrUpdateRequest("target", targetUrl, method, targetObject, operation);
    }

}
