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

package cn.polarismesh.polaris.sync.registry.extensions;

import cn.polarismesh.polaris.sync.registry.config.Group;
import cn.polarismesh.polaris.sync.registry.config.Match;
import cn.polarismesh.polaris.sync.registry.config.RegistryConfig;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import java.util.List;
import java.util.concurrent.Executor;

public interface Registry {

    /**
     * registry name, unique name for registry
     * @return name
     */
    String getName();

    /**
     * registry type, such as nacos, kong, consul, etc...
     * @return type
     */
    String getType();

    /**
     * initialize registry
     */
    void init(RegistryConfig registryConfig);

    /**
     * destroy registry
     */
    void destroy();

    /**
     * list the discovery services
     * @param namespace namespace to list
     * @return services
     */
    DiscoverResponse listServices(String namespace);

    /**
     * list the discovery instances
     * @param service service to list
     * @return instances
     */
    DiscoverResponse listInstances(Service service);

    /**
     * watch the instances changed
     * @param service service to watch
     * @param eventListener listener callback
     */
    void watch(Service service, ResponseListener eventListener);

    /**
     * unwatch the instance changed
     * @param service service to watch
     */
    void unwatch(Service service);

    /**
     * register the service to destinations
     * @param groups groups to register
     * @param sourceName name for source registry
     * @param service service instances
     */
    void register(String sourceName, List<Group> groups, DiscoverResponse service);

    /**
     * listener to watch the instance change events
     */
    interface ResponseListener {

        /**
         * called when response event received
         * @param response instances
         */
        void onResponse(DiscoverResponse response);
    }

}
