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

import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.core.utils.TaskUtils;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.WatchEvent;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ServiceProto.Instance;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(WatchTask.class);

    private final NamedRegistryCenter source;

    private final NamedRegistryCenter destination;

    private final Service service;

    private final Collection<ModelProto.Group> groups;

    private final Executor registerExecutor;

    private final ScheduledExecutorService watchExecutor;

    private final SyncTask.Match serviceWithSource;

    private final Map<SyncTask.Match, Future<?>> watchedServices;

    private final ResponseListener responseListener;

    public WatchTask(Map<SyncTask.Match, Future<?>> watchedServices, NamedRegistryCenter source,
            NamedRegistryCenter destination, SyncTask.Match match, Executor registerExecutor,
            ScheduledExecutorService watchExecutor) {
        this.watchedServices = watchedServices;
        this.source = source;
        this.destination = destination;
        this.service = new Service(match.getNamespace(), match.getName());
        this.groups = TaskUtils.verifyGroups(match.getGroups());
        this.registerExecutor = registerExecutor;
        this.watchExecutor = watchExecutor;
        this.serviceWithSource = match;
        responseListener = new ResponseListener();
    }


    @Override
    public void run() {
        if (source.getRegistry().watch(service, responseListener)) {
            LOG.info("[LOG] success to watch for service {}", serviceWithSource);
            return;
        }
        LOG.info("[LOG] start to retry watch for service {}", serviceWithSource);
        if (!watchedServices.containsKey(serviceWithSource)) {
            return;
        }
        Future<?> submit = watchExecutor.schedule(this, 10, TimeUnit.SECONDS);
        watchedServices.put(serviceWithSource, submit);
        LOG.info("[Core] service {} has been scheduled watched", serviceWithSource);
    }

    private class ResponseListener implements RegistryCenter.ResponseListener {

        @Override
        public void onEvent(WatchEvent watchEvent) {
            registerExecutor.execute(() -> {
                // diff by groups
                for (ModelProto.Group group : groups) {
                    DiscoverResponse discoverResponse = source.getRegistry().listInstances(service, group);
                    if (discoverResponse.getCode().getValue() != StatusCodes.SUCCESS) {
                        LOG.warn("[Core][Watch] fail to list service in source {}, group {}, code is {}",
                                source.getName(), group.getName(), discoverResponse.getCode().getValue());
                        return;
                    }
                    List<Instance> instances = discoverResponse.getInstancesList();
                    LOG.info("[Core][Watch]prepare to update group {} instances {}", group.getName(), instances);
                    destination.getRegistry().updateInstances(service, group, instances);
                }
            });
        }
    }
}
