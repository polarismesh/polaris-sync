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

package cn.polarismesh.polaris.sync.registry.tasks;

import static cn.polarismesh.polaris.sync.registry.utils.TaskUtils.verifyGroups;

import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Match;
import cn.polarismesh.polaris.sync.registry.utils.ConfigUtils;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(PullTask.class);

    private final Map<Service, Collection<Group>> serviceToGroups = new HashMap<>();

    private final NamedRegistryCenter source;

    private final NamedRegistryCenter destination;

    public PullTask(NamedRegistryCenter source, NamedRegistryCenter destination, List<Match> matches) {
        this.source = source;
        this.destination = destination;
        for (Match match : matches) {
            if (ConfigUtils.isEmptyMatch(match)) {
                continue;
            }
            serviceToGroups.put(
                    new Service(match.getNamespace(), match.getService()), verifyGroups(match.getGroupsList()));
        }
    }


    @Override
    public void run() {
        try {
            // check services, add or remove the services from destination
            destination.getRegistry().updateServices(serviceToGroups.keySet());

            // check groups
            for (Map.Entry<Service, Collection<Group>> entry : serviceToGroups.entrySet()) {
                destination.getRegistry().updateGroups(entry.getKey(), entry.getValue());
            }

            // check instances
            for (Map.Entry<Service, Collection<Group>> entry : serviceToGroups.entrySet()) {
                Service service = entry.getKey();
                for (Group group : entry.getValue()) {
                    DiscoverResponse srcInstanceResponse = source.getRegistry().listInstances(service, group);
                    if (srcInstanceResponse.getCode().getValue() != StatusCodes.SUCCESS) {
                        LOG.warn("[Core][Pull] fail to list service in source {}, type {}, group {}, code is {}",
                                source.getName(), source.getRegistry().getType(), group.getName(),
                                srcInstanceResponse.getCode().getValue());
                        return;
                    }
                    List<Instance> instances = srcInstanceResponse.getInstancesList();
                    LOG.info(
                            "[Core][Pull]prepare to update from registry {}, type {}, service {}, group {}, instances {}",
                            source.getName(), source.getRegistry().getType(), service, group.getName(), instances);
                    destination.getRegistry().updateInstances(service, group, instances);
                }
            }
        } catch (Throwable e) {
            LOG.error("[Core] pull task(source {}) encounter exception {}", source.getName(), e.getStackTrace());
        }
    }

}
