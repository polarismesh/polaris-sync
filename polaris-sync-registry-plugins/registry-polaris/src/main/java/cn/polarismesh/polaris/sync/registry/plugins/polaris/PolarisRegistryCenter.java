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
import cn.polarismesh.polaris.sync.extension.registry.Health;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.CommonUtils;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import com.google.protobuf.ProtocolStringList;
import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceChangeEvent;
import com.tencent.polaris.api.rpc.GetAllInstancesRequest;
import com.tencent.polaris.api.rpc.InstancesResponse;
import com.tencent.polaris.api.rpc.UnWatchServiceRequest.UnWatchServiceRequestBuilder;
import com.tencent.polaris.api.rpc.WatchServiceRequest;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto;
import com.tencent.polaris.client.pb.ServiceProto.Instance.Builder;
import com.tencent.polaris.factory.api.DiscoveryAPIFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class PolarisRegistryCenter implements RegistryCenter {

    private static final Logger LOG = LoggerFactory.getLogger(PolarisRegistryCenter.class);

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private RegistryInitRequest registryInitRequest;

    private ConsumerAPI consumerAPI;

    private final Object lock = new Object();

    private RestOperator restOperator;

    @Override
    public RegistryType getType() {
        return RegistryType.polaris;
    }

    @Override
    public void init(RegistryInitRequest request) {
        this.registryInitRequest = request;
        restOperator = new RestOperator();
    }

    @Override
    public void destroy() {
        destroyed.set(true);
        if (null != consumerAPI) {
            consumerAPI.destroy();
        }
    }

    private ConsumerAPI getConsumerAPI() {
        synchronized (lock) {
            if (null != consumerAPI) {
                return consumerAPI;
            }
            ProtocolStringList addressesList = registryInitRequest.getRegistryEndpoint().getAddressesList();
            try {
                consumerAPI = DiscoveryAPIFactory.createConsumerAPIByAddress(addressesList);
            } catch (PolarisException e) {
                LOG.error("[Polaris] fail to create consumer API by {}", addressesList, e);
                return null;
            }
            return consumerAPI;
        }
    }

    @Override
    public DiscoverResponse listInstances(Service service, Group group) {
        ConsumerAPI consumerAPI = getConsumerAPI();
        if (null == consumerAPI) {
            LOG.error("[Polaris] fail to lookup ConsumerAPI for service {}, registry {}",
                    service, registryInitRequest.getRegistryEndpoint().getName());
            return ResponseUtils.toRegistryCenterException(service);
        }
        GetAllInstancesRequest request = new GetAllInstancesRequest();
        request.setNamespace(service.getNamespace());
        request.setService(service.getService());
        InstancesResponse allInstance;
        try {
            allInstance = consumerAPI.getAllInstance(request);
        } catch (PolarisException e) {
            LOG.error("[Polaris] fail to getAllInstances for service {}, registry {}",
                    service, registryInitRequest.getRegistryEndpoint().getName(), e);
            return ResponseUtils.toRegistryClientException(service);
        }
        List<ServiceProto.Instance> outInstances = convertPolarisInstances(allInstance, group);
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
        builder.addAllInstances(outInstances);
        return builder.build();
    }

    private List<ServiceProto.Instance> convertPolarisInstances(InstancesResponse allInstance, Group group) {
        Instance[] instances = allInstance.getInstances();
        Map<String, String> filters = (null == group ? null : group.getMetadataMap());
        List<ServiceProto.Instance> polarisInstances = new ArrayList<>();
        if (null == instances) {
            return polarisInstances;
        }
        for (Instance instance : instances) {
            String instanceId = instance.getId();
            Map<String, String> metadata = instance.getMetadata();
            boolean matched = CommonUtils.matchMetadata(metadata, filters);
            if (!matched) {
                continue;
            }
            String ip = instance.getHost();
            int port = instance.getPort();
            Builder builder = ServiceProto.Instance.newBuilder();
            builder.setId(ResponseUtils.toStringValue(instanceId));
            builder.setWeight(ResponseUtils.toUInt32Value(instance.getWeight()));
            builder.putAllMetadata(metadata);
            builder.setHost(ResponseUtils.toStringValue(ip));
            builder.setPort(ResponseUtils.toUInt32Value(port));
            builder.setIsolate(ResponseUtils.toBooleanValue(instance.isIsolated()));
            builder.setHealthy(ResponseUtils.toBooleanValue(instance.isHealthy()));
            polarisInstances.add(builder.build());
        }
        return polarisInstances;
    }

    @Override
    public boolean watch(Service service, ResponseListener eventListener) {
        ConsumerAPI consumerAPI = getConsumerAPI();
        if (null == consumerAPI) {
            LOG.error("[Polaris] fail to lookup ConsumerAPI for service {}, registry {}",
                    service, registryInitRequest.getRegistryEndpoint().getName());
            return false;
        }
        WatchServiceRequest watchServiceRequest = new WatchServiceRequest();
        watchServiceRequest.setNamespace(service.getNamespace());
        watchServiceRequest.setService(service.getService());
        ServiceListener serviceListener = new ServiceListener() {
            @Override
            public void onEvent(ServiceChangeEvent event) {
                //TODO:
                //  eventListener.onEvent();
            }
        };
        watchServiceRequest.setListeners(Collections.singletonList(serviceListener));
        consumerAPI.watchService(watchServiceRequest);
        return true;
    }

    @Override
    public void unwatch(Service service) {
        ConsumerAPI consumerAPI = getConsumerAPI();
        if (null == consumerAPI) {
            LOG.error("[Polaris] fail to lookup ConsumerAPI for service {}, registry {}",
                    service, registryInitRequest.getRegistryEndpoint().getName());
            return;
        }
        UnWatchServiceRequestBuilder builder = UnWatchServiceRequestBuilder.anUnWatchServiceRequest();
        builder.namespace(service.getNamespace()).service(service.getService()).removeAll(true);
        consumerAPI.unWatchService(builder.build());
    }

    @Override
    public void updateServices(Collection<Service> services) {
        throw new UnsupportedOperationException("updateServices not supported in polaris");
    }

    @Override
    public void updateGroups(Service service, Collection<Group> groups) {
        throw new UnsupportedOperationException("updateGroups not supported in polaris");
    }

    @Override
    public void updateInstances(Service service, Group group, Collection<ServiceProto.Instance> instances) {
        throw new UnsupportedOperationException("updateInstances not supported in polaris");
    }

    public static <T> HttpEntity<T> getRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        return new HttpEntity<T>(headers);
    }

    @Override
    public Health healthCheck() {
        String address = RestOperator.pickAddress(registryInitRequest.getRegistryEndpoint().getAddressesList());
        String url = String.format("http://%s/", address);
        RestResponse<String> stringRestResponse = restOperator
                .curlRemoteEndpoint(url, HttpMethod.GET, getRequestEntity(), String.class);
        int totalCount = 0;
        int errorCount = 0;
        if (stringRestResponse.hasServerError()) {
            errorCount++;
        }
        totalCount++;
        return new Health(totalCount, errorCount);
    }
}
