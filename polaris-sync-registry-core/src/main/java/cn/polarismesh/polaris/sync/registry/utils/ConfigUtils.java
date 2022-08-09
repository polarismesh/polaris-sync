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

import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Group;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Match;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Method;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Method.MethodType;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry.Builder;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Task;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class ConfigUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);

    public static RegistryProto.Registry parseFromFile(String fileName) throws IOException {
        File file = new File(fileName);
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file))) {
            Builder builder = Registry.newBuilder();
            Parser parser = JsonFormat.parser();
            parser.merge(reader, builder);
            return builder.build();
        }
    }

    public static boolean verifyTasks(RegistryProto.Registry registry, Set<RegistryType> supportedTypes) {
        LOG.info("[Core] start to verify tasks config {}", registry);
        List<Task> tasks = registry.getTasksList();
        Set<String> taskNames = new HashSet<>();
        boolean hasTask = false;
        for (Task task : tasks) {
            String name = task.getName();
            if (!StringUtils.hasText(name)) {
                LOG.error("[Core] task name is empty");
                return false;
            }
            if (taskNames.contains(name)) {
                LOG.error("[Core] duplicate task name {}", name);
                return false;
            }
            taskNames.add(name);
            RegistryEndpoint source = task.getSource();
            if (null == source) {
                LOG.error("[Core] source is empty for task {}", name);
                return false;
            }
            if (!verifyEndpoint(source, name, supportedTypes)) {
                return false;
            }
            RegistryEndpoint destination = task.getDestination();
            if (null == destination) {
                LOG.error("[Core] destination is empty for task {}", name);
                return false;
            }
            if (!verifyEndpoint(destination, name, supportedTypes)) {
                return false;
            }
            List<Match> matchList = task.getMatchList();
            if (!verifyMatch(matchList, name)) {
                return false;
            }
            if (task.getEnable()) {
                hasTask = true;
            }
        }
        boolean hasMethod = false;
        List<Method> methods = registry.getMethodsList();
        if (!CollectionUtils.isEmpty(methods)) {
            Set<MethodType> methodTypes = new HashSet<>();
            for (Method method : methods) {
                if (MethodType.unknown.equals(method.getType())) {
                    LOG.error("[Core] unknown method type");
                    return false;
                }
                if (methodTypes.contains(method.getType())) {
                    LOG.error("[Core] duplicate method type");
                    return false;
                }
                methodTypes.add(method.getType());
                if (method.getEnable()) {
                    hasMethod = true;
                }
            }
        }
        if (hasTask && !hasMethod) {
            LOG.error("[Core] at least specific one sync method for tasks");
            return false;
        }
        return true;
    }

    private static boolean verifyMatch(List<Match> matches, String taskName) {
        if (CollectionUtils.isEmpty(matches)) {
            return true;
        }
        for (Match match : matches) {
            String namespace = match.getNamespace();
            String service = match.getService();
            List<Group> groups = match.getGroupsList();
            if (!StringUtils.hasText(namespace)) {
                LOG.error("[Core] match namespace is empty, task {}", taskName);
                return false;
            }
            if (namespace.contains(".")) {
                LOG.error("[Core] match namespace contains DOT, task {}", taskName);
                return false;
            }
            if (!StringUtils.hasText(service)) {
                LOG.error("[Core] match service is empty, task {}", taskName);
                return false;
            }
            if (CollectionUtils.isEmpty(groups)) {
                continue;
            }
            for (Group group : groups) {
                if (!StringUtils.hasText(group.getName())) {
                    LOG.error("[Core] match group name is invalid, task {}", taskName);
                    return false;
                }
                if (group.getName().contains(".")) {
                    LOG.error("[Core] match group name contains DOT, task {}", taskName);
                    return false;
                }
                Map<String, String> metadataMap = group.getMetadataMap();
                if (metadataMap.size() > 0) {
                    for (Map.Entry<String, String> entry : metadataMap.entrySet()) {
                        if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                            LOG.error("[Core] match group {} metadata is invalid, task {}", group.getName(), taskName);
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean verifyEndpoint(
            RegistryEndpoint registryEndpoint, String taskName, Set<RegistryType> supportedTypes) {
        String name = registryEndpoint.getName();
        if (!StringUtils.hasText(name)) {
            LOG.error("[Core] endpoint name is empty, task {}", taskName);
            return false;
        }
        List<String> addressesList = registryEndpoint.getAddressesList();
        if (CollectionUtils.isEmpty(addressesList)) {
            LOG.error("[Core] addresses is empty for endpoint {}, task {}", name, taskName);
            return false;
        }
        RegistryType type = registryEndpoint.getType();
        if (RegistryType.unknown.equals(type)) {
            LOG.error("[Core] unknown endpoint type for {}, task {}", name, taskName);
            return false;
        }
        if (!supportedTypes.contains(type)) {
            LOG.error("[Core] unsupported endpoint type {} for {}, task {}", type.name(), name, taskName);
            return false;
        }
        return true;
    }


    public static boolean methodsChanged(List<Method> oldMethods, List<Method> newMethods) {
        if (CollectionUtils.isEmpty(oldMethods)) {
            return !CollectionUtils.isEmpty(newMethods);
        }
        if (CollectionUtils.isEmpty(newMethods)) {
            return !CollectionUtils.isEmpty(oldMethods);
        }
        return !(oldMethods.size() == newMethods.size());
    }


}
