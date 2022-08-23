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
import cn.polarismesh.polaris.sync.extension.registry.Health;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.WatchEvent;
import cn.polarismesh.polaris.sync.common.utils.CommonUtils;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
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
    public RegistryType getType() {
        return RegistryType.nacos;
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
                        entry.getKey(), registryInitRequest.getRegistryEndpoint().getName(), e);
            }
        }
    }

    @Override
    public DiscoverResponse listNamespaces() {
        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
        AuthResponse authResponse = new AuthResponse();
        // 1. 先进行登录
        if (StringUtils.hasText(registryEndpoint.getUser()) && StringUtils.hasText(
                registryEndpoint.getPassword())) {
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
        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
        AuthResponse authResponse = new AuthResponse();
        // 1. 先进行登录
        if (StringUtils.hasText(registryEndpoint.getUser()) && StringUtils.hasText(
                registryEndpoint.getPassword())) {
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
            svcBuilder.setName(ResponseUtils.toStringValue(
                    nacosServiceView.getGroupName() + GROUP_SEP + nacosServiceView.getName()));
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
            RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
            namingService = ns2NamingService.get(namespace);
            if (null != namingService) {
                return namingService;
            }
            String address = String.join(",", registryEndpoint.getAddressesList());
            Properties properties = new Properties();
            properties.setProperty("serverAddr", address);
            properties.setProperty("namespace", toNamespaceId(namespace));
            if (StringUtils.hasText(registryEndpoint.getUser())) {
                properties.setProperty("username", registryEndpoint.getUser());
            }
            if (StringUtils.hasText(registryEndpoint.getPassword())) {
                properties.setProperty("password", registryEndpoint.getPassword());
            }
            try {
                namingService = NacosFactory.createNamingService(properties);
            } catch (NacosException e) {
                LOG.error("[Nacos] fail to create naming service to {}, namespace {}", address, namespace, e);
                return null;
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
    public DiscoverResponse listInstances(Service service, Group group) {
        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
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

    private List<ServiceProto.Instance> convertNacosInstances(Service service, List<Instance> instances, Group group) {
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
        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
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
        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
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

        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
        AuthResponse authResponse = new AuthResponse();
        // 1. 先进行登录
        if (StringUtils.hasText(registryEndpoint.getUser()) && StringUtils.hasText(
                registryEndpoint.getPassword())) {
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
    public void updateGroups(Service service, Collection<Group> groups) {

    }

    @Override
    public void updateInstances(Service service, Group group, Collection<ServiceProto.Instance> srcInstances) {
        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
        List<Instance> allInstances = queryNacosInstances(service, registryEndpoint.getName());
        if (null == allInstances) {
            LOG.info("[Nacos] cancel update instances for query nacos errors");
            return;
        }
        String sourceName = registryInitRequest.getSourceName();
        Map<HostAndPort, Instance> targetsToCreate = new HashMap<>();
        Map<HostAndPort, Instance> targetsToUpdate = new HashMap<>();
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
                targetsToCreate.put(srcAddress, toNacosInstance(srcInstance, null));
            } else {
                Instance destInstance = instancesMap.get(srcAddress);
                if (!CommonUtils.isSyncedByCurrentSource(
                        destInstance.getMetadata(), sourceName)) {
                    //并非同步实例，可能是目标注册中心新注册的，不处理
                    continue;
                }
                //比较是否存在不一致
                if (!instanceEquals(srcInstance, destInstance)) {
                    targetsToUpdate.put(srcAddress, toNacosInstance(srcInstance, destInstance.getInstanceId()));
                }
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
        if (!targetsToCreate.isEmpty()) {
            LOG.info("[Nacos] targets pending to create are {}, group {}", targetsToCreate.keySet(), group.getName());
            registerInstances("create", namingService, service.getService(), targetsToCreate.values());
            targetAddCount++;
        }
        if (!targetsToUpdate.isEmpty()) {
            LOG.info("[Nacos] targets pending to update are {}, group {}", targetsToUpdate.keySet(), group.getName());
            registerInstances("update", namingService, service.getService(), targetsToUpdate.values());
            targetPatchCount++;
        }
        if (!targetsToDelete.isEmpty()) {
            LOG.info("[Nacos] targets pending to delete are {}, group {}", targetsToDelete.keySet(), group.getName());
            deregisterInstances("delete", namingService, service.getService(), targetsToDelete.values());
            targetDeleteCount++;
        }
        LOG.info("[Nacos] success to update targets, add {}, patch {}, delete {}",
                targetAddCount, targetPatchCount, targetDeleteCount);

    }

    private void registerInstances(String operation, NamingService namingService,
            String svcName, Collection<Instance> instances) {
        for (Instance instance : instances) {
            try {
                namingService.registerInstance(svcName, instance);
            } catch (NacosException e) {
                LOG.error("[Nacos] fail to register instance {} to service {} when {}, reason {}",
                        instance, svcName, operation, e.getMessage());
            }
        }
    }

    private void deregisterInstances(String operation, NamingService namingService,
            String svcName, Collection<Instance> instances) {
        for (Instance instance : instances) {
            try {
                namingService.deregisterInstance(svcName, instance);
            } catch (NacosException e) {
                LOG.error("[Nacos] fail to deregister instance {} to service {} when {}, reason {}",
                        instance, svcName, operation, e.getMessage());
            }
        }
    }

    private Instance toNacosInstance(ServiceProto.Instance instance, String instanceId) {
        Instance outInstance = new Instance();
        if (StringUtils.hasText(instanceId)) {
            outInstance.setInstanceId(instanceId);
        }
        outInstance.setIp(instance.getHost().getValue());
        outInstance.setPort(instance.getPort().getValue());
        outInstance.setMetadata(instance.getMetadataMap());
        outInstance.setEphemeral(false);
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
