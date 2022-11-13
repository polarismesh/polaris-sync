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

import cn.polarismesh.polaris.sync.common.rest.HostAndPort;
import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.WatchEvent;
import cn.polarismesh.polaris.sync.common.utils.CommonUtils;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.AuthResponse;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.NacosNamespace;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.NacosServiceView;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto;
import com.tencent.polaris.client.pb.ServiceProto.Instance.Builder;
import com.tencent.polaris.client.pb.ServiceProto.Namespace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class NacosRegistryCenter implements RegistryCenter {

    private static final Logger LOG = LoggerFactory.getLogger(NacosRegistryCenter.class);

    private static final String GROUP_SEP = "__";

    private final Map<String, NamingService> ns2NamingService = new ConcurrentHashMap<>();

    private final Map<Service, EventListener> eventListeners = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private RegistryInitRequest registryInitRequest;

    private final RestOperator restOperator = new RestOperator();

    @Override
    public String getName() {
        return getType().name();
    }

    @Override
    public ResourceType getType() {
        return ResourceType.NACOS;
    }

    @Override
    public void init(RegistryInitRequest registryInitRequest) {
        this.registryInitRequest = registryInitRequest;
    }

    @Override
    public void destroy() {
        destroyed.set(true);
        for (Map.Entry<String, NamingService> entry : ns2NamingService.entrySet()) {
            try {
                entry.getValue().shutDown();
            } catch (NacosException e) {
                LOG.error("[Nacos] fail to shutdown namingService {}, name {}",
                        entry.getKey(), registryInitRequest.getResourceEndpoint().getName(), e);
            }
        }
    }

    @Override
    public DiscoverResponse listNamespaces() {
        ResourceEndpoint registryEndpoint = registryInitRequest.getResourceEndpoint();
        AuthResponse authResponse = new AuthResponse();
        // 1. 先进行登录
        if (StringUtils.hasText(registryEndpoint.getAuthorization().getUsername()) && StringUtils.hasText(
                registryEndpoint.getAuthorization().getPassword())) {
            DiscoverResponse discoverResponse = NacosRestUtils.auth(
                    restOperator, registryEndpoint, authResponse, null, DiscoverResponseType.NAMESPACES);
            if (null != discoverResponse) {
                return discoverResponse;
            }
        }
        //2. 查询命名空间是否已经创建
        List<NacosNamespace> nacosNamespaces = new ArrayList<>();
        DiscoverResponse discoverResponse = NacosRestUtils
                .discoverAllNamespaces(authResponse, restOperator, registryEndpoint, nacosNamespaces);
        if (null != discoverResponse) {
            return discoverResponse;
        }
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(null, StatusCodes.SUCCESS, DiscoverResponseType.NAMESPACES);
        for (NacosNamespace nacosNamespace : nacosNamespaces) {
            builder.addNamespaces(
                    Namespace.newBuilder().setName(ResponseUtils.toStringValue(nacosNamespace.getNamespace())).build());
        }
        return builder.build();
    }

    @Override
    public DiscoverResponse listServices(String namespace) {
        Service service = new Service(namespace, "");
        NamingService namingService = getOrCreateNamingService(namespace);
        if (null == namingService) {
            return ResponseUtils.toConnectException(service, DiscoverResponseType.SERVICES);
        }
        ResourceEndpoint registryEndpoint = registryInitRequest.getResourceEndpoint();
        AuthResponse authResponse = new AuthResponse();
        // 1. 先进行登录
        if (StringUtils.hasText(registryEndpoint.getAuthorization().getUsername()) && StringUtils.hasText(
                registryEndpoint.getAuthorization().getPassword())) {
            DiscoverResponse discoverResponse = NacosRestUtils.auth(
                    restOperator, registryEndpoint, authResponse, service, DiscoverResponseType.SERVICES);
            if (null != discoverResponse) {
                LOG.error("[Nacos] fail to login nacos when discover all services, code {}",
                        discoverResponse.getCode().getValue());
                return discoverResponse;
            }
        }
        List<NacosServiceView> serviceViews = new ArrayList<>();
        DiscoverResponse discoverResponse = NacosRestUtils.discoverAllServices(
                authResponse, restOperator, registryEndpoint, service, 1, null, serviceViews);
        if (null != discoverResponse) {
            return discoverResponse;
        }
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.SERVICES);
        for (NacosServiceView nacosServiceView : serviceViews) {
            ServiceProto.Service.Builder svcBuilder = ServiceProto.Service.newBuilder();
            svcBuilder.setNamespace(ResponseUtils.toStringValue(namespace));
            if (StringUtils.hasText(nacosServiceView.getGroupName())
                    && nacosServiceView.getGroupName().equals(Constants.DEFAULT_GROUP)) {
                svcBuilder.setName(ResponseUtils.toStringValue(
                        nacosServiceView.getGroupName() + GROUP_SEP + nacosServiceView.getName()));
            } else {
                svcBuilder.setName(ResponseUtils.toStringValue(nacosServiceView.getName()));
            }
            builder.addServices(svcBuilder.build());
        }
        return builder.build();
    }


    private static String toNamespaceId(String namespace) {
        if (DefaultValues.EMPTY_NAMESPACE_HOLDER.equals(namespace)) {
            return "";
        }
        return namespace;
    }

    private NamingService getOrCreateNamingService(String namespace) {
        NamingService namingService = ns2NamingService.get(namespace);
        if (null != namingService) {
            return namingService;
        }
        synchronized (lock) {
            ResourceEndpoint registryEndpoint = registryInitRequest.getResourceEndpoint();
            namingService = ns2NamingService.get(namespace);
            if (null != namingService) {
                return namingService;
            }
            String address = String.join(",", registryEndpoint.getServerAddresses());
            Properties properties = new Properties();
            properties.setProperty("serverAddr", address);
            properties.setProperty("namespace", toNamespaceId(namespace));
            if (StringUtils.hasText(registryEndpoint.getAuthorization().getUsername())) {
                properties.setProperty("username", registryEndpoint.getAuthorization().getUsername());
            }
            if (StringUtils.hasText(registryEndpoint.getAuthorization().getPassword())) {
                properties.setProperty("password", registryEndpoint.getAuthorization().getPassword());
            }
            try {
                namingService = NacosFactory.createNamingService(properties);
            } catch (NacosException e) {
                LOG.error("[Nacos] fail to create naming service to {}, namespace {}", address, namespace, e);
                return null;
            }
            try {
                //等待nacos连接建立完成
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ns2NamingService.put(namespace, namingService);
            return namingService;
        }
    }


    private static String[] parseServiceToGroupService(String serviceName) {
        if (serviceName.contains(GROUP_SEP)) {
            return serviceName.split(GROUP_SEP);
        }
        return new String[]{Constants.DEFAULT_GROUP, serviceName};
    }

    private List<Instance> queryNacosInstances(Service service, String registryName) {
        NamingService namingService = getOrCreateNamingService(service.getNamespace());
        if (null == namingService) {
            LOG.error("[Nacos] fail to lookup namingService for service {}, registry {}",
                    service, registryName);
            return null;
        }
        String[] values = parseServiceToGroupService(service.getService());
        String nacosGroup = values[0];
        String serviceName = values[1];
        try {
            return namingService.getAllInstances(serviceName, nacosGroup);
        } catch (NacosException e) {
            LOG.error("[Nacos] fail to getAllInstances for service {}, registry {}",
                    service, registryName, e);
            return null;
        }
    }

    @Override
    public DiscoverResponse listInstances(Service service, ModelProto.Group group) {
        ResourceEndpoint registryEndpoint = registryInitRequest.getResourceEndpoint();
        List<Instance> allInstances = queryNacosInstances(service, registryEndpoint.getName());
        if (null == allInstances) {
            return ResponseUtils.toConnectException(service);
        }
        List<ServiceProto.Instance> outInstances = convertNacosInstances(service, allInstances, group);
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
        builder.addAllInstances(outInstances);
        return builder.build();
    }

    private List<ServiceProto.Instance> convertNacosInstances(Service service, List<Instance> instances, ModelProto.Group group) {
        Map<String, String> filters = (null == group ? null : group.getMetadataMap());
        List<ServiceProto.Instance> polarisInstances = new ArrayList<>();
        for (Instance instance : instances) {
            Map<String, String> metadata = instance.getMetadata();
            boolean matched = CommonUtils.matchMetadata(metadata, filters);
            if (!matched) {
                continue;
            }
            String ip = instance.getIp();
            int port = instance.getPort();
            double weight = instance.getWeight();
            boolean enabled = instance.isEnabled();
            boolean healthy = instance.isHealthy();
            Builder builder = ServiceProto.Instance.newBuilder();
            builder.setNamespace(ResponseUtils.toStringValue(service.getNamespace()));
            builder.setService(ResponseUtils.toStringValue(service.getService()));
            builder.setWeight(ResponseUtils.toUInt32Value((int) weight));
            if (!CollectionUtils.isEmpty(metadata)) {
                builder.putAllMetadata(metadata);
            }
            builder.setHost(ResponseUtils.toStringValue(ip));
            builder.setPort(ResponseUtils.toUInt32Value(port));
            builder.setIsolate(ResponseUtils.toBooleanValue(!enabled));
            builder.setHealthy(ResponseUtils.toBooleanValue(healthy));
            polarisInstances.add(builder.build());
        }
        return polarisInstances;
    }

    @Override
    public boolean watch(Service service, ResponseListener eventListener) {
        ResourceEndpoint registryEndpoint = registryInitRequest.getResourceEndpoint();
        NamingService namingService = getOrCreateNamingService(service.getNamespace());
        if (null == namingService) {
            LOG.error("[Nacos] fail to lookup namingService for service {}, registry {}",
                    service, registryEndpoint.getName());
            return false;
        }
        String[] values = parseServiceToGroupService(service.getService());
        String group = values[0];
        String serviceName = values[1];
        EventListener nacosEventListener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                if (!(event instanceof NamingEvent)) {
                    return;
                }
                NamingEvent namingEvent = (NamingEvent) event;
                List<Instance> instances = namingEvent.getInstances();
                List<ServiceProto.Instance> polarisInstances = convertNacosInstances(service, instances, null);
                DiscoverResponse.Builder builder = ResponseUtils
                        .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
                builder.addAllInstances(polarisInstances);
                eventListener.onEvent(new WatchEvent(builder.build()));

            }
        };
        try {
            namingService.subscribe(serviceName, group, nacosEventListener);
            eventListeners.put(service, nacosEventListener);
            return true;
        } catch (NacosException e) {
            LOG.error("[Nacos] fail to subscribe for service {}, registry {}",
                    service, registryEndpoint.getName(), e);
            return false;
        }
    }

    @Override
    public void unwatch(Service service) {
        ResourceEndpoint registryEndpoint = registryInitRequest.getResourceEndpoint();
        NamingService namingService = getOrCreateNamingService(service.getNamespace());
        if (null == namingService) {
            LOG.error("[Nacos] fail to lookup namingService for service {}, registry {}",
                    service, registryEndpoint.getName());
            return;
        }
        EventListener eventListener = eventListeners.get(service);
        if (null == eventListener) {
            return;
        }
        String[] values = parseServiceToGroupService(service.getService());
        String group = values[0];
        String serviceName = values[1];
        try {
            namingService.unsubscribe(serviceName, group, eventListener);
            eventListeners.remove(service);
        } catch (NacosException e) {
            LOG.error("[Nacos] fail to unsubscribe for service {}, registry {}",
                    service, registryEndpoint.getName(), e);
        }
    }

    @Override
    public void updateServices(Collection<Service> services) {
        Set<String> namespaceIds = new HashSet<>();
        for (Service service : services) {
            if (service.getNamespace().equals(DefaultValues.EMPTY_NAMESPACE_HOLDER)) {
                continue;
            }
            namespaceIds.add(service.getNamespace());
        }
        if (namespaceIds.isEmpty()) {
            return;
        }

        ResourceEndpoint registryEndpoint = registryInitRequest.getResourceEndpoint();
        AuthResponse authResponse = new AuthResponse();
        // 1. 先进行登录
        if (StringUtils.hasText(registryEndpoint.getAuthorization().getUsername()) && StringUtils.hasText(
                registryEndpoint.getAuthorization().getPassword())) {
            DiscoverResponse discoverResponse = NacosRestUtils.auth(
                    restOperator, registryEndpoint, authResponse, null, DiscoverResponseType.NAMESPACES);
            if (null != discoverResponse) {
                return;
            }
        }
        //2. 查询命名空间是否已经创建
        List<NacosNamespace> nacosNamespaces = new ArrayList<>();
        DiscoverResponse discoverResponse = NacosRestUtils
                .discoverAllNamespaces(authResponse, restOperator, registryEndpoint, nacosNamespaces);
        if (null == discoverResponse) {
            return;
        }
        for (NacosNamespace nacosNamespace : nacosNamespaces) {
            namespaceIds.remove(nacosNamespace.getNamespace());
        }
        if (CollectionUtils.isEmpty(namespaceIds)) {
            return;
        }
        //3. 新增命名空间
        LOG.info("[Nacos] namespaces to add {}", namespaceIds);
        for (String namespaceId : namespaceIds) {
            NacosRestUtils.createNamespace(authResponse, restOperator, registryEndpoint, namespaceId);
        }
    }

    @Override
    public void updateGroups(Service service, Collection<ModelProto.Group> groups) {

    }

    @Override
    public void updateInstances(Service service, ModelProto.Group group, Collection<ServiceProto.Instance> srcInstances) {
        ResourceEndpoint registryEndpoint = registryInitRequest.getResourceEndpoint();
        List<Instance> allInstances = queryNacosInstances(service, registryEndpoint.getName());
        if (null == allInstances) {
            LOG.info("[Nacos] cancel update instances for query nacos errors");
            return;
        }
        String sourceName = registryInitRequest.getSourceName();
        Map<HostAndPort, Instance> targetsToCreate = new HashMap<>();
        Map<HostAndPort, Instance> targetsToUpdate = new HashMap<>();
        Map<HostAndPort, Instance> targetsExists = new HashMap<>();
        boolean hasExistsInstances = false;
        Set<HostAndPort> processedAddresses = new HashSet<>();
        // 比较新增、编辑、删除
        // 新增=源不带同步标签的实例，额外存在了（与当前全部实例作为对比）
        // 编辑=当前实例（sync=sourceName），与源实例做对比，存在不一致
        Map<HostAndPort, Instance> instancesMap = toInstancesMap(allInstances);
        for (ServiceProto.Instance srcInstance : srcInstances) {
            HostAndPort srcAddress = HostAndPort.build(
                    srcInstance.getHost().getValue(), srcInstance.getPort().getValue());
            processedAddresses.add(srcAddress);
            Map<String, String> srcMetadata = srcInstance.getMetadataMap();
            if (srcMetadata.containsKey(DefaultValues.META_SYNC)) {
                continue;
            }
            if (!instancesMap.containsKey(srcAddress)) {
                //不存在则新增
                targetsToCreate.put(srcAddress, toNacosInstancePendingSync(srcInstance, null));
            } else {
                Instance destInstance = instancesMap.get(srcAddress);
                if (!CommonUtils.isSyncedByCurrentSource(
                        destInstance.getMetadata(), sourceName)) {
                    //并非同步实例，可能是目标注册中心新注册的，不处理
                    continue;
                }
                hasExistsInstances = true;
                //比较是否存在不一致
                if (!instanceEquals(srcInstance, destInstance)) {
                    targetsToUpdate.put(srcAddress, toNacosInstancePendingSync(srcInstance, destInstance.getInstanceId()));
                }
                targetsExists.put(srcAddress, toNacosInstancePendingSync(srcInstance, destInstance.getInstanceId()));
            }
        }
        // 删除=当前实例（sync=sourceName），额外存在了（与源的全量实例作对比）
        Map<HostAndPort, Instance> targetsToDelete = new HashMap<>();
        for (Map.Entry<HostAndPort, Instance> instanceEntry : instancesMap.entrySet()) {
            Instance instance = instanceEntry.getValue();
            if (!CommonUtils.isSyncedByCurrentSource(instance.getMetadata(), sourceName)) {
                continue;
            }
            if (!processedAddresses.contains(instanceEntry.getKey())) {
                targetsToDelete.put(instanceEntry.getKey(), instance);
            }
        }
        // process operation
        int targetAddCount = 0;
        int targetPatchCount = 0;
        int targetDeleteCount = 0;
        NamingService namingService = ns2NamingService.get(service.getNamespace());
        boolean deleted = false;
        if (!targetsToDelete.isEmpty() || (!targetsToCreate.isEmpty() && hasExistsInstances)) {
            //以下场景需要删除全部
            //1. 有1-N个实例需要删除
            //2. 存在新增+存量
            if (!targetsToDelete.isEmpty()) {
                LOG.info("[Nacos] targets pending to delete are {}, group {}", targetsToDelete.keySet(), group.getName());
                deregisterInstance("delete", namingService, service.getService(), targetsToDelete.values().iterator()
                        .next());
                targetDeleteCount += targetsToDelete.size();
            } else {
                deregisterInstance("delete", namingService, service.getService(), targetsExists.values().iterator()
                        .next());
            }
            deleted = true;
        }
        if (!targetsToCreate.isEmpty()) {
            //新增实例
            LOG.info("[Nacos] targets pending to create are {}, group {}", targetsToCreate.keySet(), group.getName());
            List<Instance> instances = new ArrayList<>(targetsToCreate.values());
            if (hasExistsInstances) {
                //假如有存量，因为之前已经删除了全部，这里要加上去
                instances.addAll(targetsExists.values());
            }
            registerInstances("create", namingService, service.getService(), instances);
            targetAddCount += targetsToCreate.size();
        } else{
            if (deleted) {
                //前面已经删除了，则把存量重新注册一遍
                List<Instance> instances = new ArrayList<>(targetsExists.values());
                LOG.info("[Nacos] targets pending to update are {}, group {}", targetsExists.keySet(), group.getName());
                registerInstances("update", namingService, service.getService(), instances);
                targetPatchCount += targetsExists.size();
            } else if (!targetsToUpdate.isEmpty()) {
                //前面已经删除了，则把存量重新注册一遍
                List<Instance> instances = new ArrayList<>(targetsToUpdate.values());
                LOG.info("[Nacos] targets pending to update are {}, group {}", targetsToUpdate.keySet(), group.getName());
                registerInstances("update", namingService, service.getService(), instances);
                targetPatchCount += targetsToUpdate.size();
            }
        }
        LOG.info("[Nacos] success to update targets, add {}, patch {}, delete {}",
                targetAddCount, targetPatchCount, targetDeleteCount);

    }

    private void registerInstances(String operation, NamingService namingService,
            String svcName, List<Instance> instances) {
        String[] values = parseServiceToGroupService(svcName);
        try {
            namingService.batchRegisterInstance(values[1], values[0], instances);
        } catch (NacosException e) {
            LOG.error("[Nacos] fail to register instances {} to service {} when {}, reason {}",
                    instances, svcName, operation, e.getMessage());
        }
    }

    private void deregisterInstance(String operation, NamingService namingService,
            String svcName, Instance instance) {
        String[] values = parseServiceToGroupService(svcName);
        try {
            namingService.deregisterInstance(values[1], values[0], instance);
        } catch (NacosException e) {
            LOG.error("[Nacos] fail to deregister instance {} to service {} when {}, reason {}",
                    instance, svcName, operation, e.getMessage());
        }
    }

    private Instance toNacosInstancePendingSync(ServiceProto.Instance instance, String instanceId) {
        Instance outInstance = new Instance();
        if (StringUtils.hasText(instanceId)) {
            outInstance.setInstanceId(instanceId);
        }
        outInstance.setIp(instance.getHost().getValue());
        outInstance.setPort(instance.getPort().getValue());
        Map<String, String> metadataMap = new HashMap<>();
        for (Map.Entry<String, String> entry : instance.getMetadataMap().entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue())) {
                metadataMap.put(entry.getKey(), entry.getValue());
            }
        }
        metadataMap.put(DefaultValues.META_SYNC, registryInitRequest.getSourceName());
        outInstance.setMetadata(metadataMap);
        outInstance.setWeight(instance.getWeight().getValue());
        outInstance.setHealthy(instance.getHealthy().getValue());
        outInstance.setEnabled(!instance.getIsolate().getValue());
        return outInstance;
    }

    private static boolean instanceEquals(ServiceProto.Instance srcInstance, Instance dstInstance) {
        if (dstInstance.getWeight() != srcInstance.getWeight().getValue()) {
            return false;
        }
        if (dstInstance.isHealthy() != srcInstance.getHealthy().getValue()) {
            return false;
        }
        if (dstInstance.isEnabled() == (srcInstance.getIsolate().getValue())) {
            return false;
        }
        return CommonUtils.metadataEquals(srcInstance.getMetadataMap(), dstInstance.getMetadata());
    }

    private static Map<HostAndPort, Instance> toInstancesMap(List<Instance> instances) {
        Map<HostAndPort, Instance> outInstances = new HashMap<>();
        for (Instance instance : instances) {
            outInstances.put(HostAndPort.build(instance.getIp(), instance.getPort()), instance);
        }
        return outInstances;
    }

    @Override
    public Health healthCheck() {
        int totalCount = 0;
        int errorCount = 0;
        if (ns2NamingService.isEmpty()) {
            return new Health(totalCount, errorCount);
        }
        for (Entry<String, NamingService> entry : ns2NamingService.entrySet()) {
            String serverStatus = entry.getValue().getServerStatus();
            if (!serverStatus.equals("UP")) {
                errorCount++;
            }
            totalCount++;
        }
        return new Health(totalCount, errorCount);
    }
}
