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

import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;

public class RegistryInitRequest {

    private final String sourceName;

    private final RegistryType sourceType;

    private final RegistryEndpoint registryEndpoint;

    public RegistryInitRequest(String sourceName,RegistryType sourceType,
            RegistryEndpoint registryEndpoint) {
        this.sourceName = sourceName;
        this.sourceType = sourceType;
        this.registryEndpoint = registryEndpoint;
    }

    public RegistryType getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public RegistryEndpoint getRegistryEndpoint() {
        return registryEndpoint;
    }

}
