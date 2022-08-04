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

public class SyncTaskConfig {

    private String name;

    private boolean enable;

    private List<Match> match;

    private RegistryConfig source;

    private RegistryConfig destination;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RegistryConfig getSource() {
        return source;
    }

    public void setSource(RegistryConfig source) {
        this.source = source;
    }

    public RegistryConfig getDestination() {
        return destination;
    }

    public void setDestination(RegistryConfig destination) {
        this.destination = destination;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public List<Match> getMatch() {
        return match;
    }

    public void setMatch(List<Match> match) {
        this.match = match;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SyncTaskConfig)) {
            return false;
        }
        SyncTaskConfig that = (SyncTaskConfig) o;
        return enable == that.enable &&
                Objects.equals(name, that.name) &&
                Objects.equals(match, that.match) &&
                Objects.equals(source, that.source) &&
                Objects.equals(destination, that.destination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, enable, match, source, destination);
    }

    @Override
    public String toString() {
        return "SyncTaskConfig{" +
                "name='" + name + '\'' +
                ", enable=" + enable +
                ", match=" + match +
                ", source=" + source +
                ", destination=" + destination +
                '}';
    }
}
