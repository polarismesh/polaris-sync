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

import cn.polarismesh.polaris.sync.extension.registry.Health;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.WatchEvent;
import cn.polarismesh.polaris.sync.extension.utils.CommonUtils;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
            properties.setProperty("namespace", namespace);
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

    @Override
    public DiscoverResponse listInstances(Service service, Group group) {
        RegistryEndpoint registryEndpoint = registryInitRequest.getRegistryEndpoint();
        NamingService namingService = getOrCreateNamingService(service.getNamespace());
        if (null == namingService) {
            LOG.error("[Nacos] fail to lookup namingService for service {}, registry {}",
                    service, registryEndpoint.getName());
            return ResponseUtils.toRegistryCenterException(service);
        }
        String[] values = parseServiceToGroupService(service.getService());
        String nacosGroup = values[0];
        String serviceName = values[1];
        List<Instance> allInstances;
        try {
            allInstances = namingService.getAllInstances(serviceName, nacosGroup);
        } catch (NacosException e) {
            LOG.error("[Nacos] fail to getAllInstances for service {}, registry {}",
                    service, registryEndpoint.getName(), e);
            return ResponseUtils.toRegistryClientException(service);
        }
        List<ServiceProto.Instance> outInstances = convertNacosInstances(allInstances, group);
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
        builder.addAllInstances(outInstances);
        return builder.build();
    }

    private List<ServiceProto.Instance> convertNacosInstances(List<Instance> instances, Group group) {
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
            builder.setWeight(ResponseUtils.toUInt32Value((int) weight));
            builder.putAllMetadata(metadata);
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
                List<ServiceProto.Instance> polarisInstances = convertNacosInstances(instances, null);
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
        throw new UnsupportedOperationException("updateServices not supported in nacos");
    }

    @Override
    public void updateGroups(Service service, Collection<Group> groups) {
        throw new UnsupportedOperationException("updateGroups not supported in nacos");
    }

    @Override
    public void updateInstances(Service service, Group group, Collection<ServiceProto.Instance> instances) {
        throw new UnsupportedOperationException("updateInstances not supported in nacos");
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
