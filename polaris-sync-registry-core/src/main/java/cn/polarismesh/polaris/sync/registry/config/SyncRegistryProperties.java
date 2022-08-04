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

package cn.polarismesh.polaris.sync.registry.config;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "polaris.sync.registry")
public class SyncRegistryProperties {

    private List<SyncTaskConfig> tasks;

    private List<SyncMethod> methods;

    public List<SyncTaskConfig> getTasks() {
        return tasks;
    }

    public void setTasks(List<SyncTaskConfig> tasks) {
        this.tasks = tasks;
    }

    public List<SyncMethod> getMethods() {
        return methods;
    }

    public void setMethods(List<SyncMethod> methods) {
        this.methods = methods;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SyncRegistryProperties)) {
            return false;
        }
        SyncRegistryProperties that = (SyncRegistryProperties) o;
        return Objects.equals(tasks, that.tasks) &&
                Objects.equals(methods, that.methods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tasks, methods);
    }

    @Override
    public String toString() {
        return "SyncRegistryProperties{" +
                "tasks=" + tasks +
                ", methods=" + methods +
                '}';
    }
}
