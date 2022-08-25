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

package cn.polarismesh.polaris.sync.registry.plugins.consul;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.rest.HostAndPort;
import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.extension.registry.AbstractRegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.WatchEvent;
import cn.polarismesh.polaris.sync.common.utils.CommonUtils;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import com.ecwid.consul.ConsulException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.ConsulRawClient;
import com.ecwid.consul.v1.OperationException;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.CatalogServicesRequest;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.HealthServicesRequest.Builder;
import com.ecwid.consul.v1.health.model.HealthService;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import com.tencent.polaris.client.pb.ServiceProto.Namespace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class ConsulRegistryCenter extends AbstractRegistryCenter {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulRegistryCenter.class);

    private RegistryEndpoint registryEndpoint;

    private final ExecutorService longPullExecutor =
            Executors.newCachedThreadPool(new NamedThreadFactory("consul-pull-worker"));

    private final Map<Service, LongPullContext> watchedServices = new HashMap<>();

    private final Object lock = new Object();

    @Override
    public RegistryType getType() {
        return RegistryType.consul;
    }

    @Override
    public void init(RegistryInitRequest request) {
        registryEndpoint = request.getRegistryEndpoint();
    }

    @Override
    public void destroy() {

    }

    @Override
    public DiscoverResponse listNamespaces() {
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(null, StatusCodes.SUCCESS, DiscoverResponseType.SERVICES);
        builder.addNamespaces(Namespace.newBuilder().setName(ResponseUtils.toStringValue("default")).build());
        return builder.build();
    }

    @Override
    public DiscoverResponse listServices(String namespace) {
        String address = RestOperator.pickAddress(registryEndpoint.getAddressesList());
        ConsulClient consulClient = getConsulClient(address);
        Response<Map<String, List<String>>> catalogServices;
        Service service = new Service(namespace, "");
        String registryName = registryEndpoint.getName();
        try {
            catalogServices = consulClient.getCatalogServices(buildCatalogServicesRequest());
        } catch (ConsulException e) {
            if (e instanceof OperationException) {
                LOG.error("[Consul] text error to listInstances by registry {}, address {}",
                        registryName, address, e);
                return ResponseUtils.toDiscoverResponse(service, ResponseUtils.normalizeStatusCode(
                        ((OperationException) e).getStatusCode()), DiscoverResponseType.SERVICES).build();
            } else {
                serverErrorCount.addAndGet(1);
                LOG.error("[Consul] server error to listInstances by registry {}, address {}",
                        registryName, address, e);
                return ResponseUtils.toConnectException(service, DiscoverResponseType.SERVICES);
            }
        } finally {
            totalCount.addAndGet(1);
        }
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.SERVICES);
        Map<String, List<String>> values = catalogServices.getValue();
        for (Map.Entry<String, List<String>> value : values.entrySet()) {
            for (String svcName : value.getValue()) {
                ServiceProto.Service.Builder svcBuilder = ServiceProto.Service.newBuilder();
                svcBuilder.setNamespace(ResponseUtils.toStringValue(namespace));
                svcBuilder.setName(ResponseUtils.toStringValue(svcName));
                builder.addServices(svcBuilder.build());
            }
        }
        return builder.build();
    }

    private CatalogServicesRequest buildCatalogServicesRequest() {
        CatalogServicesRequest.Builder builder = CatalogServicesRequest.newBuilder();
        builder.setDatacenter("dc1");
        if (StringUtils.hasText(registryEndpoint.getToken())) {
            builder.setToken(registryEndpoint.getToken());
        }
        return builder.build();
    }

    private ConsulClient getConsulClient(String address) {
        HostAndPort hostAndPort = HostAndPort.build(address, ConsulRawClient.DEFAULT_PORT);
        return new ConsulClient(hostAndPort.getHost(), hostAndPort.getPort());
    }

    private HealthServicesRequest buildHealthServiceRequest(long index) {
        Builder builder = HealthServicesRequest.newBuilder();
        builder.setDatacenter("dc1").setPassing(true);
        if (StringUtils.hasText(registryEndpoint.getToken())) {
            builder.setToken(registryEndpoint.getToken());
        }
        QueryParams.Builder paramBuilder = QueryParams.Builder.builder();
        paramBuilder.setIndex(index);
        builder.setQueryParams(paramBuilder.build());
        return builder.build();
    }

    @Override
    public DiscoverResponse listInstances(Service service, Group group) {
        String address = RestOperator.pickAddress(registryEndpoint.getAddressesList());
        ConsulClient consulClient = getConsulClient(address);
        Response<List<HealthService>> healthServices;
        String registryName = registryEndpoint.getName();
        try {
            healthServices = consulClient.getHealthServices(service.getService(), buildHealthServiceRequest(0));
            LOG.info("[Consul][List] health services got by registry {}, address {}, service {}, list {}",
                    registryName, address, service, healthServices);
        } catch (ConsulException e) {
            if (e instanceof OperationException) {
                LOG.error("[Consul] text error to listInstances by registry {}, address {}", registryName, address, e);
                return ResponseUtils.toDiscoverResponse(service, ResponseUtils.normalizeStatusCode(
                        ((OperationException) e).getStatusCode()), DiscoverResponseType.INSTANCE).build();
            } else {
                serverErrorCount.addAndGet(1);
                LOG.error("[Consul] server error to listInstances by registry {}, address {}", registryName, address,
                        e);
                return ResponseUtils.toConnectException(service);
            }
        } finally {
            totalCount.addAndGet(1);
        }
        List<HealthService> healthInstances = healthServices.getValue();
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
        builder.addAllInstances(convertConsulInstance(service, healthInstances, group));
        DiscoverResponse discoverResponse = builder.build();
        LOG.info("[Consul][Pull] instances response (registry {}, address {}, group {}) from is {}", registryName,
                address, group, discoverResponse);
        return discoverResponse;
    }

    private List<Instance> convertConsulInstance(Service service, List<HealthService> instances, Group group) {
        List<Instance> outInstances = new ArrayList<>();
        if (CollectionUtils.isEmpty(instances)) {
            return outInstances;
        }
        Map<String, String> filters = (null == group ? null : group.getMetadataMap());
        Set<HostAndPort> processedNodes = new HashSet<>();
        for (HealthService healthService : instances) {
            HealthService.Service instance = healthService.getService();
            HostAndPort hostAndPort = HostAndPort.build(instance.getAddress(), instance.getPort());
            if (processedNodes.contains(hostAndPort)) {
                continue;
            }
            processedNodes.add(hostAndPort);
            Map<String, String> metadata = instance.getMeta();
            boolean matched = CommonUtils.matchMetadata(metadata, filters);
            if (!matched) {
                continue;
            }
            Instance.Builder builder = Instance.newBuilder();
            builder.setNamespace(ResponseUtils.toStringValue(service.getNamespace()));
            builder.setService(ResponseUtils.toStringValue(service.getService()));
            builder.setHost(ResponseUtils.toStringValue(hostAndPort.getHost()));
            builder.setPort(ResponseUtils.toUInt32Value(hostAndPort.getPort()));
            builder.putAllMetadata(convertConsulMetadata(instance));
            builder.setWeight(ResponseUtils.toUInt32Value(100));
            builder.setHealthy(ResponseUtils.toBooleanValue(true));
            builder.setIsolate(ResponseUtils.toBooleanValue(false));
            outInstances.add(builder.build());
        }
        return outInstances;
    }

    private Map<String, String> convertConsulMetadata(HealthService.Service instance) {
        Map<String, String> ret = new HashMap<>(instance.getMeta());

        // 这里主要是处理某些框架利用 Consul 的 tags来实现实例的元数据
        List<String> tags = instance.getTags();
        for (String item : tags) {
            if (item.contains("=")) {
                String[] v = item.split("=");
                ret.put(v[0], v[1]);
            } else {
                ret.put(item, item);
            }
        }

        return ret;
    }

    @Override
    public boolean watch(Service service, ResponseListener eventListener) {
        synchronized (lock) {
            if (watchedServices.containsKey(service)) {
                LOG.warn("[Consul] service {} already watched, registry {}", service, registryEndpoint.getName());
                return true;
            }
            long watchIndex = 0L;
            Future<?> submit = longPullExecutor.submit(new LongPullRunnable(service, eventListener));
            watchedServices.put(service, new LongPullContext(watchIndex, submit));
            return true;
        }
    }

    @Override
    public void unwatch(Service service) {
        synchronized (lock) {
            LongPullContext future = watchedServices.remove(service);
            if (null != future) {
                future.getFuture().cancel(true);
            }
        }
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

    private class LongPullRunnable implements Runnable {

        private final Service service;
        private final ResponseListener eventListener;

        public LongPullRunnable(Service service,
                ResponseListener eventListener) {
            this.service = service;
            this.eventListener = eventListener;
        }

        public boolean processWatch(String address, LongPullContext longPullContext) {
            String registryName = registryEndpoint.getName();
            ConsulClient consulClient = getConsulClient(address);
            Response<List<HealthService>> healthServices;
            try {
                long index = longPullContext.getIndex();
                healthServices = consulClient.getHealthServices(
                        service.getService(), buildHealthServiceRequest(index));
                LOG.info("[Consul][Watch] health services got by registry {}, address {}, service {}, list {}",
                        registryName, address, service, healthServices);
            } catch (ConsulException e) {
                if (e instanceof OperationException) {
                    LOG.error("[Consul] text error to listInstances by registry {}, address {}",
                            registryName, address, e);
                } else {
                    serverErrorCount.addAndGet(1);
                    LOG.error("[Consul] server error to listInstances by registry {}, address {}",
                            registryName, address, e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                    LOG.error("[Consul] sleep interrupted", interruptedException);
                }
                return false;
            } finally {
                totalCount.addAndGet(1);
            }
            Long consulIndex = healthServices.getConsulIndex();
            longPullContext.setIndex(consulIndex);
            List<HealthService> healthInstances = healthServices.getValue();
            DiscoverResponse.Builder builder = ResponseUtils
                    .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
            builder.addAllInstances(convertConsulInstance(service, healthInstances, null));
            eventListener.onEvent(new WatchEvent(builder.build()));
            return true;
        }

        @Override
        public void run() {
            LongPullContext longPullContext;
            boolean watched;
            synchronized (lock) {
                watched = watchedServices.containsKey(service);
                longPullContext = watchedServices.get(service);
            }
            String address = RestOperator.pickAddress(registryEndpoint.getAddressesList());
            while (watched) {
                try {
                    boolean result = processWatch(address, longPullContext);
                    if (!result) {
                        address = RestOperator.pickAddress(registryEndpoint.getAddressesList());
                        longPullContext.setIndex(0L);
                    }
                } catch (Throwable e) {
                    LOG.error("[Consul][Watch] fail to process watch task (registry {})", registryEndpoint.getName(),
                            e);
                }
                synchronized (lock) {
                    watched = watchedServices.containsKey(service);
                }
            }
        }
    }

    private static class LongPullContext {

        private final AtomicLong index = new AtomicLong(0L);

        private final Future<?> future;

        public LongPullContext(long index, Future<?> future) {
            this.index.set(index);
            this.future = future;
        }

        public long getIndex() {
            return index.get();
        }

        public void setIndex(long idx) {
            index.set(idx);
        }

        public Future<?> getFuture() {
            return future;
        }
    }
}
