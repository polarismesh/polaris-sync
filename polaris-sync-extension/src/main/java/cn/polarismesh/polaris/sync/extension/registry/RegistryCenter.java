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

import cn.polarismesh.polaris.sync.extension.ResourceCenter;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import java.util.Collection;

public interface RegistryCenter extends ResourceCenter<RegistryInitRequest> {

    /**
     * list the discovery namespaces
     *
     * @return instances
     */
    DiscoverResponse listNamespaces();

    /**
     * list the discovery services
     *
     * @param namespace namespace names
     * @return instances
     */
    DiscoverResponse listServices(String namespace);

    /**
     * list the discovery instances
     *
     * @param service service to list
     * @return instances
     */
    DiscoverResponse listInstances(Service service, ModelProto.Group group);

    /**
     * watch the instances changed
     *
     * @param service service to watch
     * @param eventListener listener callback
     */
    boolean watch(Service service, ResponseListener eventListener);

    /**
     * unwatch the instance changed
     *
     * @param service service to watch
     */
    void unwatch(Service service);

    /**
     * update the services to destinations
     *
     * @param services services
     */
    void updateServices(Collection<Service> services);

    /**
     * register the service group
     *
     * @param service service
     * @param groups groups name
     */
    void updateGroups(Service service, Collection<ModelProto.Group> groups);

    /**
     * update the instances to destinations
     *
     * @param service service instances
     * @param group service group
     * @param instances service instances
     */
    void updateInstances(Service service, ModelProto.Group group, Collection<Instance> instances);

    /**
     * listener to watch the instance change events
     */
    interface ResponseListener {

        /**
         * called when response event received
         *
         * @param watchEvent instances event
         */
        void onEvent(WatchEvent watchEvent);
    }


}
