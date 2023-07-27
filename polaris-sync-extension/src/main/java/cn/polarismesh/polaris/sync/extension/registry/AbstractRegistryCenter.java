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

import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractRegistryCenter implements RegistryCenter {
    public static Map<String, Set<ServiceAlias>> ServiceAlias = new HashMap<String, Set<ServiceAlias>>() {
        @Override
        public Set<ServiceAlias> get(Object key) {
            Set<ServiceAlias> aliases = super.get(key);
            if (aliases == null) {
                put(key.toString(), aliases = new HashSet<>());
            }
            return aliases;
        }
    };

    protected final AtomicInteger serverErrorCount = new AtomicInteger(0);

    protected final AtomicInteger totalCount = new AtomicInteger(0);

    @Override
    public Health healthCheck() {
        int totalCountValue = totalCount.get();
        int errorCountValue = serverErrorCount.get();
        totalCount.addAndGet(-totalCountValue);
        serverErrorCount.addAndGet(-errorCountValue);
        return new Health(totalCountValue, errorCountValue);
    }

    @Override
    public DiscoverResponse listNamespaces() {
        return ResponseUtils.toDiscoverResponse(
                null, StatusCodes.SUCCESS, DiscoverResponseType.NAMESPACES).build();
    }

    @Override
    public DiscoverResponse listServices(String namespace) {
        return ResponseUtils.toDiscoverResponse(
                new Service(namespace, ""), StatusCodes.SUCCESS, DiscoverResponseType.SERVICES).build();
    }
}
