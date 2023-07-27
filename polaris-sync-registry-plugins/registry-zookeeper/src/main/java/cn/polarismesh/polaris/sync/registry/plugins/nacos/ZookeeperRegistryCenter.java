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

package cn.polarismesh.polaris.sync.registry.plugins.nacos;

import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.registry.AbstractRegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.WatchEvent;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.MicroService;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.MicroServiceInstance;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ServiceProto;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static cn.polarismesh.polaris.sync.registry.plugins.nacos.ZookeeperRegistryUtils.*;
import static cn.polarismesh.polaris.sync.registry.plugins.nacos.ZookeeperRegistryUtils.toProviderPath;
import static org.apache.zookeeper.Watcher.Event.EventType.*;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 */
@Component
public class ZookeeperRegistryCenter extends AbstractRegistryCenter {
    /*key in options config, the value indicates the root path holds services.
      when has much root path, splits by ","   */
    public static final String OPTIONS_KEY_ZK_ROOT_PATH = "root_paths";

    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperRegistryCenter.class);
    private static final Marker LOG_MARKER = MarkerFactory.getMarker("[ZooKeeper][Registry] ");

    static ZookeeperClient zkClient;

    private List<String> rootPaths;

    //{application} -> DubboService
    private Map<String, MicroService> microServices;


    private Map<String, ZkWatcher> watchers = new HashMap<>();

    public class ZkWatcher implements Watcher {
        String path;
        Service service;
        ResponseListener syncListener;

        public ZkWatcher(String path, Service service, ResponseListener syncListener) {
            this.path = path;
            this.service = service;
            this.syncListener = syncListener;
        }

        @Override
        public void process(WatchedEvent event) {
            if (event.getType() == NodeChildrenChanged) {
                try {
                    MicroService ds = microServices.get(service.getService());
                    Map<String, MicroServiceInstance> instances = dubboServiceInstances(event.getPath(), true);
                    ds.resetInstances(instances.values());
                    DiscoverResponse res = instancesToResponse(service, instances.values());
                    syncListener.onEvent(new WatchEvent(res));
                } finally {
                    zkClient.watch(event.getPath(), this);
                }
            }
        }
    }

    @Override
    public void init(RegistryInitRequest request) {
        ResourceEndpoint endpoint = request.getResourceEndpoint();

        List<String> serverAddresses = endpoint.getServerAddresses();
        if (serverAddresses.isEmpty()) {
            halt("server address is empty");
        }
        zkClient = new ZookeeperClient(toZookeeperAddr(serverAddresses));

        String[] strRootPaths = endpoint.getOptions()
                .getOrDefault(OPTIONS_KEY_ZK_ROOT_PATH, "/dubbo")
                .split(",");
        rootPaths = toRootPaths(strRootPaths);
    }

    @Override
    public DiscoverResponse listNamespaces() {
        return super.listNamespaces();
    }

    @Override
    public DiscoverResponse listServices(String namespace) {
        DiscoverResponse.Builder resBuilder = ResponseUtils.toDiscoverResponse(null,
                StatusCodes.SUCCESS, DiscoverResponse.DiscoverResponseType.SERVICES);
        if (microServices == null) {
            for (String rootPath : rootPaths) {
                boolean isDubbo = isDubboService(rootPath);
                if (isDubbo) {
                    addMicroService(allDubboServices(rootPath));
                } else {
                    //addMicroService(allMicroServices(rootPath));
                }
            }
        }
        if (microServices != null)
            addMicroServiceToResponse(namespace, resBuilder, microServices);
        return resBuilder.build();
    }

    void addMicroService(Map<String, MicroService> services) {
        if (services == null || services.isEmpty())
            return;
        if (microServices == null) {
            microServices = services;
            return;
        }
        for (Map.Entry<String, MicroService> e : services.entrySet()) {
            MicroService ms = microServices.get(e.getKey());
            if (ms != null) {
                //todo 聚合微服务信息。目前不会出现这种情况
            } else {
                microServices.put(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public DiscoverResponse listInstances(Service service, ModelProto.Group group) {
        DiscoverResponse.Builder builder = ResponseUtils.toDiscoverResponse(service, StatusCodes.SUCCESS,
                DiscoverResponse.DiscoverResponseType.INSTANCE);
        MicroService microService = microServices.get(service.getService());
        List<ServiceProto.Instance> instances = microService.getInstances().stream()
                .map(ds -> toProtoInstance(service, ds))
                .collect(Collectors.toList());
        builder.addAllInstances(instances);
        return builder.build();
    }

    private DiscoverResponse instancesToResponse(Service service, Collection<MicroServiceInstance> instances) {
        DiscoverResponse.Builder resBuilder = ResponseUtils.toDiscoverResponse(service, StatusCodes.SUCCESS,
                DiscoverResponse.DiscoverResponseType.SERVICES);
        MicroService ds = microServices.get(service.getService());
        for (MicroServiceInstance instance : ds.getInstances()) {
            resBuilder.addInstances(toProtoInstance(service, instance));
        }
        return resBuilder.build();
    }

    @Override
    public boolean watch(Service service, ResponseListener eventListener) {
        if (DefaultValues.MATCH_ALL.equals(service.getService())) {
            listServices(DefaultValues.DEFAULT_POLARIS_NAMESPACE);
        }
        for (Map.Entry<String, MicroService> e : microServices.entrySet()) {
            String inf = e.getValue().getInterfaces().iterator().next();
            String path = String.format("/dubbo/%s/providers", inf);
            ZkWatcher watcher = new ZkWatcher(path, new Service(service.getNamespace(), e.getKey()), eventListener);
            watchers.put(service.getService(), watcher);
            zkClient.watch(path, watcher); //watch only one provider path
        }
        return true;
    }

    @Override
    public void unwatch(Service service) {
        ZkWatcher watcher = watchers.remove(service.getService());
        if (watcher != null) {
            zkClient.unWatch(watcher.path, watcher);
        }
    }

    @Override
    public void updateServices(Collection<Service> services) {
    }

    @Override
    public void updateGroups(Service service, Collection<ModelProto.Group> groups) {
    }

    @Override
    public void updateInstances(Service service, ModelProto.Group group, Collection<ServiceProto.Instance> instances) {
        String svr = service.getService();
        Map<String, String> providersInZk = new HashMap<>();
        for (Map.Entry<String, MicroServiceInstance> e : dubboServiceInstances(getPathOfProviders(svr), false).entrySet()) {
            String key = String.format("%s:%s", e.getValue().getHost(), e.getValue().getPort());
            providersInZk.put(key, e.getKey());
        }
        Map<String, String> providersNotified = new HashMap<>();
        for (ServiceProto.Instance instance : instances) {
            String key = String.format("%s:%s", instance.getHost().getValue(), instance.getPort().getValue());
            String path = toProviderPath(instance);
            if (path != null) {
                providersNotified.put(key, path);
            }
        }
        List<String> pathsToAdd = new ArrayList<>();
        for (Map.Entry<String, String> e : providersNotified.entrySet()) {
            String pathInZk = providersInZk.remove(e.getKey());
            if (pathInZk == null) {
                pathsToAdd.add(e.getValue());
            }
        }

        for (String path : pathsToAdd) {
            zkClient.createTempPath(path);
        }
        for (String path : providersInZk.values()) {
            zkClient.deletePath(path);
        }
    }

    @Override
    public String getName() {
        return ResourceType.ZOOKEEPER.name();
    }

    @Override
    public ResourceType getType() {
        return ResourceType.ZOOKEEPER;
    }

    @Override
    public void destroy() {
        zkClient.close();
    }


    static void halt(String message, Object... args) {
        dealError("halt by fatal error: " + message, args);
        System.exit(1);
    }
    static void dealError(String message, Object... args) {
        LOG.error(LOG_MARKER, message, args);
    }
}
