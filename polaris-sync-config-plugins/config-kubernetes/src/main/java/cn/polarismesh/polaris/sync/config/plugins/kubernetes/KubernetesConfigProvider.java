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

package cn.polarismesh.polaris.sync.config.plugins.kubernetes;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.extension.config.ConfigListener;
import cn.polarismesh.polaris.sync.extension.config.ConfigProvider;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 基于 Kubernetes ConfigMap 的配置提供者
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class KubernetesConfigProvider implements ConfigProvider {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesConfigProvider.class);

    private static final String PLUGIN_NAME = "kubernetes";

    private final Set<ConfigListener> listeners = new CopyOnWriteArraySet<>();

    private final ScheduledExecutorService configmapWatchService = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("configmap-watch-worker"));

    private final AtomicReference<Registry> holder = new AtomicReference<>();

    private GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configMapClient;

    private Config config;


    @Override
    public void init(Map<String, Object> options) throws Exception {
        Gson gson = new Gson();
        config = gson.fromJson(gson.toJson(options), Config.class);
        LOG.info("[ConfigProvider][Kubernetes] init options : {}", options);

        ApiClient apiClient = null;
        if (config.hasToken()) {
            LOG.info("[ConfigProvider][Kubernetes] use fromToken to build kubernetes client");
            apiClient = io.kubernetes.client.util.Config.fromToken(getAddress(config.getAddress()), config.getToken(),
                    false);
        } else {
            LOG.info("[ConfigProvider][Kubernetes] use default kubernetes client");
            apiClient = io.kubernetes.client.util.Config.defaultClient();
        }

        configMapClient = new GenericKubernetesApi<>(V1ConfigMap.class, V1ConfigMapList.class, "", "v1", "configmaps",
                apiClient);
        startAndWatch();
    }

    @Override
    public void addListener(ConfigListener listener) {
        listeners.add(listener);
    }

    @Override
    public Registry getConfig() {
        return holder.get();
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public void close() {
        configmapWatchService.shutdown();
    }

    private static String getAddress(String address) {
        if (address.startsWith("http://") || address.startsWith("https://")) {
            return address;
        }
        return String.format("https://%s", address);
    }

    public void startAndWatch() throws Exception {
        KubernetesApiResponse<V1ConfigMap> response = configMapClient.get(config.getNamespace(), config.getDataId());
        handleConfigMap(response.getObject());

        configmapWatchService.execute(() -> {
            Watchable<V1ConfigMap> watchable = null;
            try {
                watchable = configMapClient.watch();
                watchable.forEachRemaining(ret -> handleConfigMap(ret.object));
            } catch (ApiException e) {
                throw new RuntimeException(e);
            } finally {
                IOUtils.closeQuietly(watchable);
            }
        });
    }

    private void handleConfigMap(V1ConfigMap configMap) {
        Map<String, byte[]> data = configMap.getBinaryData();
        if (MapUtils.isEmpty(data)) {
            LOG.error("[ConfigProvider][Kubernetes] namespace: {} name: {} is empty", config.getNamespace(),
                    config.getConfigmapName());
            return;
        }

        byte[] ret = data.get(config.getDataId());
        if (ret == null || ret.length == 0) {
            LOG.error("[ConfigProvider][Kubernetes] namespace: {} name: {} dataId: {} is empty", config.getNamespace(),
                    config.getConfigmapName(), config.getDataId());
            return;
        }

        LOG.info("[ConfigProvider][Kubernetes] receive new config : {}", new String(ret, StandardCharsets.UTF_8));
        try {
            Registry registry = unmarshal(ret);
            holder.set(registry);

            for (ConfigListener listener : listeners) {
                listener.onChange(registry);
            }

        } catch (IOException e) {
            LOG.error("[ConfigProvider][Kubernetes] marshal namespace: {} name: {} dataId: {} ", config.getNamespace(),
                    config.getConfigmapName(), config.getDataId(), e);
        }
    }
}
