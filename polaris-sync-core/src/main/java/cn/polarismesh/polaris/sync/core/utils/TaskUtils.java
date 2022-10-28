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

package cn.polarismesh.polaris.sync.core.utils;

import cn.polarismesh.polaris.sync.common.utils.CommonUtils;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TaskUtils {


    public static List<Instance> filterInstances(Group group, List<Instance> instances) {
        Map<String, String> filters = group.getMetadataMap();
        List<Instance> outInstances = new ArrayList<>();
        for (Instance instance : instances) {
            Map<String, String> metadataMap = instance.getMetadataMap();
            boolean matched = CommonUtils.matchMetadata(metadataMap,filters);
            if (matched) {
                outInstances.add(instance);
                break;
            }
        }
        return outInstances;
    }

    public static Collection<Group> verifyGroups(Collection<Group> groups) {
        boolean hasDefault = false;
        for (Group group : groups) {
            if (group.getName().equals(DefaultValues.GROUP_NAME_DEFAULT)) {
                hasDefault = true;
            }
        }
        if (hasDefault) {
            return groups;
        }
        List<Group> outGroups = new ArrayList<>();
        outGroups.add(Group.newBuilder().setName(DefaultValues.GROUP_NAME_DEFAULT).build());
        outGroups.addAll(groups);
        return outGroups;
    }
}
