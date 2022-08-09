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

package cn.polarismesh.polaris.sync.registry.consul;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.rest.HostAndPort;
import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.extension.registry.Health;
import cn.polarismesh.polaris.sync.extension.registry.Health.Status;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.CommonUtils;
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
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.HealthServicesRequest.Builder;
import com.ecwid.consul.v1.health.model.HealthService;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class ConsulRegistryCenter implements RegistryCenter {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulRegistryCenter.class);

    private RegistryEndpoint registryEndpoint;

    private final AtomicInteger serverErrorCount = new AtomicInteger(0);

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
        if (index >= 0) {
            QueryParams.Builder paramBuilder = QueryParams.Builder.builder();
            paramBuilder.setIndex(index);
            builder.setQueryParams(paramBuilder.build());
        }
        return builder.build();
    }

    @Override
    public DiscoverResponse listInstances(Service service, Group group) {
        String address = RestOperator.pickAddress(registryEndpoint.getAddressesList());
        ConsulClient consulClient = getConsulClient(address);
        Response<List<HealthService>> healthServices;
        try {
            healthServices = consulClient
                    .getHealthServices(service.getService(), buildHealthServiceRequest(-1));
        } catch (ConsulException e) {
            if (e instanceof OperationException) {
                LOG.error("[Consul] text error to listInstances by address {}", address, e);
                return ResponseUtils.toRegistryClientException(service);
            } else {
                serverErrorCount.addAndGet(1);
                LOG.error("[Consul] server error to listInstances by address {}", address, e);
                return ResponseUtils.toRegistryCenterException(service);
            }
        }
        List<HealthService> healthInstances = healthServices.getValue();
        DiscoverResponse.Builder builder = ResponseUtils
                .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
        builder.addAllInstances(convertConsulInstance(healthInstances, group));
        return builder.build();
    }

    private List<Instance> convertConsulInstance(List<HealthService> instances, Group group) {
        List<Instance> outInstances = new ArrayList<>();
        if (CollectionUtils.isEmpty(instances)) {
            return outInstances;
        }
        Map<String, String> filters = (null == group ? null : group.getMetadataMap());
        for (HealthService healthService : instances) {
            HealthService.Service instance = healthService.getService();
            Map<String, String> metadata = instance.getMeta();
            boolean matched = CommonUtils.matchMetadata(metadata, filters);
            if (!matched) {
                continue;
            }
            Instance.Builder builder = Instance.newBuilder();
            builder.setId(ResponseUtils.toStringValue(instance.getId()));
            builder.setHost(ResponseUtils.toStringValue(instance.getAddress()));
            builder.setPort(ResponseUtils.toUInt32Value(instance.getPort()));
            builder.putAllMetadata(instance.getMeta());
            builder.setWeight(ResponseUtils.toUInt32Value(100));
            builder.setHealthy(ResponseUtils.toBooleanValue(true));
            builder.setIsolate(ResponseUtils.toBooleanValue(false));
            outInstances.add(builder.build());
        }
        return outInstances;
    }

    @Override
    public boolean watch(Service service, ResponseListener eventListener) {
        synchronized (lock) {
            if (watchedServices.containsKey(service)) {
                LOG.warn("[Consul] service {} already watched", service);
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

    @Override
    public Health healthCheck() {
        int count = serverErrorCount.get();
        if (count > 0) {
            return new Health(Status.DOWN, -1, "", count);
        }
        return new Health(Status.UP, 0, "");
    }

    private class LongPullRunnable implements Runnable {

        private final Service service;
        private final ResponseListener eventListener;

        public LongPullRunnable(Service service,
                ResponseListener eventListener) {
            this.service = service;
            this.eventListener = eventListener;
        }

        @Override
        public void run() {
            LongPullContext longPullContext;
            boolean watched;
            synchronized (lock) {
                watched = watchedServices.containsKey(service);
                longPullContext = watchedServices.get(service);
            }
            while (watched) {
                String address = RestOperator.pickAddress(registryEndpoint.getAddressesList());
                ConsulClient consulClient = getConsulClient(address);
                Response<List<HealthService>> healthServices;
                try {
                    healthServices = consulClient.getHealthServices(
                            service.getService(), buildHealthServiceRequest(longPullContext.getIndex()));
                } catch (ConsulException e) {
                    if (e instanceof OperationException) {
                        LOG.error("[Consul] text error to listInstances by address {}", address, e);
                    } else {
                        serverErrorCount.addAndGet(1);
                        LOG.error("[Consul] server error to listInstances by address {}", address, e);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        LOG.error("[Consul] sleep interrupted", interruptedException);
                    }
                    continue;
                }
                Long consulIndex = healthServices.getConsulIndex();
                longPullContext.setIndex(consulIndex);
                List<HealthService> healthInstances = healthServices.getValue();
                DiscoverResponse.Builder builder = ResponseUtils
                        .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
                builder.addAllInstances(convertConsulInstance(healthInstances, null));
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
