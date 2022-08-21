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

package cn.polarismesh.polaris.sync.registry.plugins.k8s;

import static cn.polarismesh.polaris.sync.common.rest.RestOperator.pickAddress;

import cn.polarismesh.polaris.sync.extension.registry.AbstractRegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.common.utils.CommonUtils;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class KubernetesRegistryCenter extends AbstractRegistryCenter {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesRegistryCenter.class);

    private RegistryEndpoint registryEndpoint;

    @Override
    public RegistryType getType() {
        return RegistryType.kubernetes;
    }

    @Override
    public void init(RegistryInitRequest request) {
        registryEndpoint = request.getRegistryEndpoint();
    }

    private ApiClient createApiClient(String address) {
        return Config.fromToken(getAddress(address), registryEndpoint.getToken(), false);
    }

    private static String getAddress(String address) {
        if (address.startsWith("http://") || address.startsWith("https://")) {
            return address;
        }
        return String.format("https://%s", address);
    }

    @Override
    public void destroy() {

    }

    @Override
    public DiscoverResponse listInstances(Service service, Group group) {
        String apiServerAddress = pickAddress(registryEndpoint.getAddressesList());
        LOG.info("[Kubernetes] start to list endpoints for service {} from k8s {}", service, apiServerAddress);
        ApiClient apiClient = createApiClient(apiServerAddress);
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        V1EndpointsList v1EndpointsList;
        try {
            v1EndpointsList = coreV1Api.listNamespacedEndpoints(service.getNamespace(),
                    null, null, null, null, null, null,
                    null, null, null, null);
        } catch (ApiException e) {
            serverErrorCount.addAndGet(1);
            LOG.error("[Kubernetes] fail to getAllInstances for service {}, address {}, registry {}",
                    service, apiServerAddress, registryEndpoint.getName(), e);
            return ResponseUtils.toConnectException(service);
        } finally {
            totalCount.addAndGet(1);
        }
        LOG.info("[Kubernetes] endpoints for service {} from k8s is {}", service, v1EndpointsList);
        List<V1Endpoints> endpointsList = v1EndpointsList.getItems();
        V1Endpoints svcEndpoints = null;
        for (V1Endpoints v1Endpoints : endpointsList) {
            //通过name来匹配
            if (null != v1Endpoints.getMetadata() && service.getService().equals(v1Endpoints.getMetadata().getName())) {
                svcEndpoints = v1Endpoints;
                break;
            }
        }
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
        if (null == svcEndpoints) {
            LOG.warn("[Kubernetes] service {} not found in k8s", service);
            return builder.build();
        }
        List<V1EndpointSubset> subsets = svcEndpoints.getSubsets();
        if (CollectionUtils.isEmpty(subsets)) {
            return builder.build();
        }
        Map<String, String> filters = (null == group ? null : group.getMetadataMap());
        List<ServiceProto.Instance> instances = new ArrayList<>();
        for (V1EndpointSubset subset : subsets) {
            List<V1EndpointAddress> addresses = subset.getAddresses();
            if (CollectionUtils.isEmpty(addresses)) {
                continue;
            }
            List<CoreV1EndpointPort> ports = subset.getPorts();
            if (CollectionUtils.isEmpty(ports)) {
                continue;
            }
            for (V1EndpointAddress address : addresses) {
                String ip = address.getIp();
                V1ObjectReference targetRef = address.getTargetRef();
                Map<String, String> metadataMap;
                try {
                    metadataMap = queryMetadata(targetRef, coreV1Api);
                } catch (ApiException e) {
                    return ResponseUtils.toConnectException(service);
                }
                boolean matched = CommonUtils.matchMetadata(metadataMap, filters);
                if (!matched) {
                    continue;
                }
                for (CoreV1EndpointPort endpointPort : ports) {
                    Integer port = endpointPort.getPort();
                    String protocol = endpointPort.getProtocol();
                    if (null == port) {
                        continue;
                    }
                    ServiceProto.Instance.Builder instanceBuilder = ServiceProto.Instance.newBuilder();
                    instanceBuilder.setNamespace(ResponseUtils.toStringValue(service.getNamespace()));
                    instanceBuilder.setService(ResponseUtils.toStringValue(service.getService()));
                    instanceBuilder.setHost(ResponseUtils.toStringValue(ip)).setPort(ResponseUtils.toUInt32Value(port));
                    if (null != protocol) {
                        instanceBuilder.setProtocol(ResponseUtils.toStringValue(protocol));
                    }
                    instanceBuilder.setHealthy(ResponseUtils.toBooleanValue(true));
                    instanceBuilder.setIsolate(ResponseUtils.toBooleanValue(false));
                    instanceBuilder.setWeight(ResponseUtils.toUInt32Value(100));
                    instances.add(instanceBuilder.build());
                }
            }
        }
        builder.addAllInstances(instances);
        return builder.build();
    }

    private Map<String, String> queryMetadata(V1ObjectReference targetRef, CoreV1Api coreV1Api) throws ApiException {
        Map<String, String> metadata = new HashMap<>();
        if (null == targetRef) {
            return metadata;
        }
        LOG.info("[Kubernetes] query metadata for targetRef name {}, kind {}, namespace {}",
                targetRef.getName(), targetRef.getKind(), targetRef.getNamespace());
        if (!StringUtils.hasText(targetRef.getKind()) ||
                !"pod".equals(targetRef.getKind().toLowerCase())) {
            return metadata;
        }
        String podName = targetRef.getName();
        String podNamespace = targetRef.getNamespace();
        V1Pod v1Pod;
        try {
            v1Pod = coreV1Api.readNamespacedPod(podName, podNamespace, null);
        } catch (ApiException e) {
            serverErrorCount.addAndGet(1);
            LOG.error("[Kubernetes] fail to query pod {}, namespace {}",
                    podName, podNamespace, e);
            throw e;
        } finally {
            totalCount.addAndGet(1);
        }
        V1ObjectMeta podMetadata = v1Pod.getMetadata();
        LOG.info("[Kubernetes] pod metadata for name {} and namespace {} is {}", podName, podNamespace, podMetadata);
        if (null == podMetadata) {
            return metadata;
        }
        Map<String, String> labels = podMetadata.getLabels();
        if (null != labels) {
            metadata.putAll(labels);
        }
        return metadata;
    }

    @Override
    public boolean watch(Service service, ResponseListener eventListener) {
        return true;
    }

    @Override
    public void unwatch(Service service) {

    }

    @Override
    public void updateServices(Collection<Service> services) {

    }

    @Override
    public void updateGroups(Service service, Collection<Group> groups) {

    }

    @Override
    public void updateInstances(Service service, Group group, Collection<Instance> instances) {

    }
}

