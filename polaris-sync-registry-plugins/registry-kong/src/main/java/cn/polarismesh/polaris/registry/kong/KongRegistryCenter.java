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

package cn.polarismesh.polaris.registry.kong;

import cn.polarismesh.polaris.sync.extension.registry.Health;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Match;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class KongRegistryCenter implements RegistryCenter {

    private static final String GROUP_DEFAULT = "default";

    private Map<ServiceGroup, Map<String, String>> groupMetadata;

    private RegistryEndpoint registryEndpoint;

    @Override
    public String getType() {
        return "kong";
    }

    @Override
    public void init(RegistryEndpoint registryEndpoint, List<Match> filters) {
        this.registryEndpoint = registryEndpoint;
        this.groupMetadata = parseFilters(filters);
    }

    private static Map<ServiceGroup, Map<String, String>> parseFilters(List<Match> filters) {
        Map<ServiceGroup, Map<String, String>> metadataMap = new HashMap<>();
        for (Match filter : filters) {
            String namespace = filter.getNamespace();
            String service = filter.getService();
            List<Group> groups = filter.getGroupsList();
            if (CollectionUtils.isEmpty(groups)) {
                metadataMap.put(new ServiceGroup(new Service(namespace, service), GROUP_DEFAULT), new HashMap<>());
                continue;
            }
            for (Group group : groups) {
                Map<String, >
            }
        }
    }

    @Override
    public void destroy() {

    }

    @Override
    public DiscoverResponse listServices(String namespace) {
        return null;
    }

    @Override
    public DiscoverResponse listInstances(Service service) {
        return null;
    }

    @Override
    public void watch(Service service, ResponseListener eventListener) {

    }

    @Override
    public void unwatch(Service service) {

    }

    @Override
    public void register(String sourceName, DiscoverResponse service) {

    }

    @Override
    public void deregister(String sourceName, DiscoverResponse service) {

    }

    @Override
    public Health healthCheck() {
        return null;
    }

    private static class ServiceGroup {
       final Service service;
       final String groupName;

        public ServiceGroup(Service service, String groupName) {
            this.service = service;
            this.groupName = groupName;
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
