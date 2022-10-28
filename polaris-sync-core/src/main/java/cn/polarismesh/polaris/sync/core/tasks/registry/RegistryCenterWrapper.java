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

package cn.polarismesh.polaris.sync.core.tasks.registry;

import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.ServiceGroup;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class RegistryCenterWrapper implements RegistryCenter {

    private final RegistryCenter registryCenter;

    private final ReentrantLock servicesLock = new ReentrantLock();

    private final Map<Service, ReentrantLock> serviceLock = new ConcurrentHashMap<>();

    private final Map<ServiceGroup, ReentrantLock> serviceGroupLocks = new ConcurrentHashMap<>();

    public RegistryCenterWrapper(RegistryCenter registryCenter) {
        this.registryCenter = registryCenter;
    }

    @Override
    public RegistryType getType() {
        return registryCenter.getType();
    }

    @Override
    public void init(RegistryInitRequest request) {
        registryCenter.init(request);
    }

    @Override
    public void destroy() {
        registryCenter.destroy();
    }

    @Override
    public DiscoverResponse listNamespaces() {
        return registryCenter.listNamespaces();
    }

    @Override
    public DiscoverResponse listServices(String namespace) {
        return registryCenter.listServices(namespace);
    }

    @Override
    public DiscoverResponse listInstances(Service service, Group group) {
        return registryCenter.listInstances(service, group);
    }

    @Override
    public boolean watch(Service service, ResponseListener eventListener) {
        return registryCenter.watch(service, eventListener);
    }

    @Override
    public void unwatch(Service service) {
        registryCenter.unwatch(service);
    }

    @Override
    public void updateServices(Collection<Service> services) {
        servicesLock.lock();
        try {
            registryCenter.updateServices(services);
        } finally {
            servicesLock.unlock();
        }
    }

    @Override
    public void updateGroups(Service service, Collection<Group> groups) {
        ReentrantLock svcLock = serviceLock.computeIfAbsent(service, new Function<Service, ReentrantLock>() {
            @Override
            public ReentrantLock apply(Service service) {
                return new ReentrantLock();
            }
        });
        svcLock.lock();
        try {
            registryCenter.updateGroups(service, groups);
        } finally {
            svcLock.unlock();
        }
    }

    @Override
    public void updateInstances(Service service, Group group, Collection<Instance> instances) {
        ReentrantLock svcLock = serviceGroupLocks.computeIfAbsent(
                new ServiceGroup(service, group.getName()), new Function<ServiceGroup, ReentrantLock>() {
                    @Override
                    public ReentrantLock apply(ServiceGroup serviceGroup) {
                        return new ReentrantLock();
                    }
                });
        svcLock.lock();
        try {
            registryCenter.updateInstances(service, group, instances);
        } finally {
            svcLock.unlock();
        }
    }

    @Override
    public Health healthCheck() {
        return registryCenter.healthCheck();
    }
}
