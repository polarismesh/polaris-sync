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

package cn.polarismesh.polaris.sync.registry.tasks;

import cn.polarismesh.polaris.sync.registry.config.Match;
import cn.polarismesh.polaris.sync.registry.extensions.Registry;
import cn.polarismesh.polaris.sync.registry.extensions.Service;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import java.util.concurrent.Executor;

public class WatchTask implements Runnable {

    private final Registry source;

    private final Registry destination;

    private final Executor executor;

    private final Match match;

    private final Service service;

    public WatchTask(Registry source, Registry destination, Match match, Executor executor) {
        this.source = source;
        this.destination = destination;
        this.executor = executor;
        this.match = match;
        this.service = new Service(match.getNamespace(), match.getService());
    }

    public Service getService() {
        return service;
    }

    @Override
    public void run() {
        source.watch(new Service(match.getNamespace(), match.getService()), new ResponseListener());
    }

    private class ResponseListener implements Registry.ResponseListener {

        @Override
        public void onResponse(DiscoverResponse response) {
            WatchTask.this.executor.execute(new Runnable() {
                @Override
                public void run() {
                    WatchTask.this.destination.register(WatchTask.this.source.getName(), match.getGroups(), response);
                }
            });

        }

    }
}
