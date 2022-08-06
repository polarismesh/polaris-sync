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

package cn.polarismesh.polaris.sync.registry.utils;

import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Match;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.Builder;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.CollectionUtils;

public class InstancesUtils {

    public static DiscoverResponse filterInstancesResponse(Match match, DiscoverResponse instancesResponse) {
        List<Instance> instances = instancesResponse.getInstancesList();
        if (instancesResponse.getType() != DiscoverResponseType.INSTANCE || CollectionUtils.isEmpty(instances)) {
            return instancesResponse;
        }
        List<Group> groups = match.getGroupsList();
        if (CollectionUtils.isEmpty(groups)) {
            return instancesResponse;
        }
        Builder builder = DiscoverResponse.newBuilder();
        builder.setService(instancesResponse.getService());
        builder.setType(instancesResponse.getType());
        builder.setCode(instancesResponse.getCode());

        List<Instance> outInstances = filterInstances(match, instances);
        builder.addAllInstances(outInstances);
       return builder.build();
    }

    public static List<Instance> filterInstances(Match match, List<Instance> instances) {
        List<Group> groups = match.getGroupsList();
        if (CollectionUtils.isEmpty(groups)) {
            return instances;
        }
        Map<String, String> filterLabels = new HashMap<>();
        for (Group group : groups) {
            filterLabels.put(group.getKey(), group.getValue());
        }
        List<Instance> outInstances = new ArrayList<>();
        for (Instance instance : instances) {
            for (Map.Entry<String, String> label : filterLabels.entrySet()) {
                String labelKey = label.getKey();
                String labelValue = label.getValue();
                Map<String, String> metadataMap = instance.getMetadataMap();
                if (!metadataMap.containsKey(labelKey)) {
                    continue;
                }
                String metaValue = metadataMap.get(labelKey);
                if (!labelValue.equals(metaValue)) {
                    continue;
                }
                outInstances.add(instance);
            }
        }
        return outInstances;
    }
}
