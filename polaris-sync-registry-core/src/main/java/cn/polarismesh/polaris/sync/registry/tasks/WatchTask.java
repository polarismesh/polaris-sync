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

import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.WatchEvent;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(WatchTask.class);

    private final NamedRegistryCenter source;

    private final NamedRegistryCenter destination;

    private final Service service;

    private final Collection<Group> groups;

    private final Executor executor;

    private static final Map<Service, Collection<Group>> serviceToGroups = new HashMap<>();

    public WatchTask(NamedRegistryCenter source,
            NamedRegistryCenter destination, RegistryProto.Match match, Executor executor) {
        this.source = source;
        this.destination = destination;
        this.service = new Service(match.getNamespace(), match.getService());
        this.groups = verifyGroups(match.getGroupsList());
        this.executor = executor;
    }

    public Service getService() {
        return service;
    }

    @Override
    public void run() {
        source.getRegistry().watch(service, new ResponseListener());
    }

    private class ResponseListener implements RegistryCenter.ResponseListener {

        @Override
        public void onEvent(WatchEvent watchEvent) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // diff by groups
                    for (Group group : groups) {
                        DiscoverResponse discoverResponse = source.getRegistry().listInstances(service, group);
                        if (discoverResponse.getCode().getValue() != StatusCodes.SUCCESS) {
                            LOG.warn("[Core][Watch] fail to list service in source {}, group {}, code is {}",
                                    source.getName(), group.getName(), discoverResponse.getCode().getValue());
                            return;
                        }
                        List<Instance> instances = discoverResponse.getInstancesList();
                        LOG.info("[Core][Watch]prepare to update group {} instances {}", group.getName(), instances);
                        destination.getRegistry().updateInstances(service, group, instances);
                    }
                }
            });
        }
    }
}
