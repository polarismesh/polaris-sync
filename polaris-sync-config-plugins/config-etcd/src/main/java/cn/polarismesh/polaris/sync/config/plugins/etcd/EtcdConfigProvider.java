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

package cn.polarismesh.polaris.sync.config.plugins.etcd;

import cn.polarismesh.polaris.sync.extension.config.ConfigListener;
import cn.polarismesh.polaris.sync.extension.config.ConfigProvider;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import com.google.gson.Gson;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 etcd 的配置提供者.
 *
 * @author jarvisxiong
 */
@Component
public class EtcdConfigProvider implements ConfigProvider {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdConfigProvider.class);

    private static final String PLUGIN_NAME = "etcd";

    private final Set<ConfigListener> listeners = new CopyOnWriteArraySet<>();

    private final AtomicReference<Registry> holder = new AtomicReference<>();

    private Client client;

    private Config config;

    @Override
    public void init(Map<String, Object> options) throws Exception {
        Gson gson = new Gson();
        config = gson.fromJson(gson.toJson(options), Config.class);
        LOG.info("[ConfigProvider][etcd] init options : {}", options);

        ClientBuilder clientBuilder = Client.builder().endpoints(config.getEndpoints().split(","));
        if (StringUtils.isNotBlank(config.getUser()) && StringUtils.isNotBlank(config.getPassword())) {
            LOG.info("[ConfigProvider][etcd] use user&password to build client");
            clientBuilder
                    .user(getByteSequence(config.getUser()))
                    .password(getByteSequence(config.getPassword()));
        }
        if (config.hasCertFile()) {
            LOG.info("[ConfigProvider][etcd] use TLS to build client");
            clientBuilder
                    .authority(config.getAuthority())
                    .sslContext(openSslContext());
        }
        client = clientBuilder.build();

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
        client.close();
    }

    private SslContext openSslContext() throws FileNotFoundException, SSLException {
        File trustCertCollectionFile = ResourceUtils.getFile(config.getTrustCertCollectionFile());
        File keyCertChainFile = ResourceUtils.getFile(config.getKeyCertChainFile());
        File keyFile = ResourceUtils.getFile(config.getKeyFile());
        ApplicationProtocolConfig alpn = new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1);
        return GrpcSslContexts.forClient()
                .applicationProtocolConfig(alpn)
                .trustManager(trustCertCollectionFile)
                .keyManager(keyCertChainFile, keyFile)
                .sslProvider(SslProvider.OPENSSL)
                .build();
    }

    private void startAndWatch() {
        final ByteSequence dataId = getByteSequence(config.getDataId());
        getKeyValue(dataId);
        watchKeyValue(dataId);
    }

    private void getKeyValue(ByteSequence dataId) {
        GetResponse getResponse;
        try {
            getResponse = client.getKVClient().get(dataId, GetOption.DEFAULT).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("[ConfigProvider][etcd] get KeyValue failed, dataId: {} ", config.getDataId(), e);
            return;
        }
        if (Objects.isNull(getResponse) || getResponse.getCount() <= 0) {
            LOG.error("[ConfigProvider][etcd] dataId: {} is empty", config.getDataId());
            return;
        }
        handleValue(getResponse.getKvs().get(0));
    }

    private void watchKeyValue(ByteSequence dataId) {
        Watch.Listener listener = Watch.listener(
                (response) -> response.getEvents().forEach(watchEvent -> {
                    LOG.info("[ConfigProvider][etcd] watch event: {} dataId: {}", watchEvent.getEventType(),
                            config.getDataId());
                    if (WatchEvent.EventType.PUT.equals(watchEvent.getEventType())) {
                        handleValue(watchEvent.getKeyValue());
                    }
                }),
                (throwable) -> LOG.warn("[ConfigProvider][etcd] watch error, dataId: {}", config.getDataId(),
                        throwable)
        );
        try {
            client.getWatchClient().watch(dataId, listener);
        } catch (Exception e) {
            LOG.error("[ConfigProvider][etcd] watch exception, dataId: {}", config.getDataId(), e);
        }
    }

    private void handleValue(KeyValue keyValue) {
        try {
            String ret = keyValue.getValue().toString(StandardCharsets.UTF_8);

            Registry registry = unmarshal(ret.getBytes());
            holder.set(registry);

            for (ConfigListener listener : listeners) {
                listener.onChange(registry);
            }
        } catch (Exception e) {
            LOG.error("[ConfigProvider][etcd] marshal failed, dataId: {} ", config.getDataId(), e);
        }
    }

    private ByteSequence getByteSequence(String value) {
        return ByteSequence.from(value, StandardCharsets.UTF_8);
    }
}
