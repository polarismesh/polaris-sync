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

package cn.polarismesh.polaris.sync.extension.registry;

import java.util.*;

public class Service {

    private final String namespace;

    private final String service;

    private Map<String, String> metadata;

    public Service(String namespace, String service) {
        this.namespace = namespace;
        this.service = service;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getService() {
        return service;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void addMetadata(String k, String v) {
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
        metadata.put(k, v);
    }

    public void addMetadata(Map<String, String> metadata) {
        if (this.metadata == null) {
            this.metadata = new LinkedHashMap<>();
        }
        this.metadata.putAll(metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Service)) {
            return false;
        }
        Service service1 = (Service) o;
        return Objects.equals(namespace, service1.namespace) &&
                Objects.equals(service, service1.service);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, service);
    }

    @Override
    public String toString() {
        return "Service{" +
                "namespace='" + namespace + '\'' +
                ", service='" + service + '\'' +
                '}';
    }
}
