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

package cn.polarismesh.polaris.sync.core.tasks.registry;

import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;

public class UnwatchTask implements Runnable {

    private final NamedRegistryCenter source;

    private final Service service;

    public UnwatchTask(NamedRegistryCenter source, RegistryProto.Match match) {
        this.source = source;
        this.service = new Service(match.getNamespace(), match.getService());
    }

    @Override
    public void run() {
        this.source.getRegistry().unwatch(service);
    }

    public Service getService() {
        return service;
    }
}
