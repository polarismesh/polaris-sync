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

import cn.polarismesh.polaris.sync.extension.registry.Health.Status;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Match;
import cn.polarismesh.polaris.sync.registry.tasks.TaskEngine.NamedRegistryCenter;
import cn.polarismesh.polaris.sync.registry.utils.InstancesUtils;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.Builder;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class CommonTask {

    private static final Logger LOG = LoggerFactory.getLogger(CommonTask.class);

    protected final NamedRegistryCenter source;

    protected final NamedRegistryCenter destination;

    protected final RegistryProto.Match match;

    protected final Service service;

    public CommonTask(NamedRegistryCenter source,
            NamedRegistryCenter destination, Match match,
            Service service) {
        this.source = source;
        this.destination = destination;
        this.match = match;
        this.service = service;
    }

    protected void changeInstances(List<Instance> srcFullInstances, List<Instance> dstFullInstancesList) {
        List<Instance> srcInstances = InstancesUtils.filterInstances(match, srcFullInstances);
        List<Instance> dstInstances = InstancesUtils.filterInstances(match, dstFullInstancesList);
        List<Instance> deletedInstances = new ArrayList<>();
        Map<String, Instance> srcInstanceMap = new HashMap<>();
        for (Instance instance : srcInstances) {
            srcInstanceMap.put(instance.getId().getValue(), instance);
        }
        for (Instance instance : dstInstances) {
            if (!srcInstanceMap.containsKey(instance.getId().getValue())) {
                LOG.info("[Core] instance {} is deleted from source {}", instance.getId(), source.getName());
                deletedInstances.add(instance);
            }
        }
        if (!CollectionUtils.isEmpty(deletedInstances)) {
            Builder builder = ResponseUtils
                    .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
            builder.addAllInstances(deletedInstances);
            destination.getRegistry().deregister(source.getName(), builder.build());
        }
        if (!CollectionUtils.isEmpty(srcInstances)) {
            Builder builder = ResponseUtils
                    .toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
            builder.addAllInstances(srcInstances);
            // register to destination
            destination.getRegistry().register(source.getName(), builder.build());
        }
    }
}
