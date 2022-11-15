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

package cn.polarismesh.polaris.sync.taskconfig.plugins.etcd;


import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.launcher.EtcdCluster;
import io.etcd.jetcd.launcher.EtcdClusterFactory;
import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslProvider;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * EtcdConfigProvider Test.
 *
 * @author jarvisxiong
 */
public class EtcdConfigProviderTest {

    private final ByteSequence key = ByteSequence.from("/polaris/sync-config.json", StandardCharsets.UTF_8);
    private final ByteSequence value1 = ByteSequence.from("{\"tasks\": [{\"name\": \"ins-3ad0f6e7\", \"enable\": true, \"source\": {\"name\": \"ins-3ad0f6e7\", \"type\": \"nacos\", \"product_name\": \"nacos(ins-3ad0f6e7)\", \"addresses\": [\"183.47.111.41:8848\"] }, \"destination\": {\"name\": \"kong\", \"type\": \"kong\", \"product_name\": \"tse-kong\", \"addresses\": [\"9.134.5.52:8001\"] }, \"match\": [{\"namespace\": \"public\", \"service\": \"DEFAULT_GROUP__nacos.test.3\", \"groups\": [{\"name\": \"version-1\", \"metadata\": {\"version\": \"1.0.0\"} }, {\"name\": \"version-2\", \"metadata\": {\"version\": \"2.0.0\"} } ] } ] } ], \"methods\": [{\"type\": \"watch\", \"enable\": true }, {\"type\": \"pull\", \"enable\": true, \"interval\": \"60s\"} ], \"health_check\": {\"enable\": true }, \"report\": {\"interval\" : \"1m\", \"targets\": [{\"type\": \"file\", \"enable\": true } ] } }", StandardCharsets.UTF_8);
    private final ByteSequence value2 = ByteSequence.from("{\"tasks\": [{\"name\": \"ins-3ad0f6e7-update\", \"enable\": true, \"source\": {\"name\": \"ins-3ad0f6e7\", \"type\": \"nacos\", \"product_name\": \"nacos(ins-3ad0f6e7)\", \"addresses\": [\"183.47.111.41:8848\"] }, \"destination\": {\"name\": \"kong\", \"type\": \"kong\", \"product_name\": \"tse-kong\", \"addresses\": [\"9.134.5.52:8001\"] }, \"match\": [{\"namespace\": \"public\", \"service\": \"DEFAULT_GROUP__nacos.test.3\", \"groups\": [{\"name\": \"version-1\", \"metadata\": {\"version\": \"1.0.0\"} }, {\"name\": \"version-2\", \"metadata\": {\"version\": \"2.0.0\"} } ] } ] } ], \"methods\": [{\"type\": \"pull\", \"enable\": true }, {\"type\": \"pull\", \"enable\": true, \"interval\": \"60s\"} ], \"health_check\": {\"enable\": false }, \"report\": {\"interval\" : \"2m\", \"targets\": [{\"type\": \"file\", \"enable\": true } ] } }", StandardCharsets.UTF_8);

    private final ByteSequence root = ByteSequence.from("root", StandardCharsets.UTF_8);
    private final ByteSequence password = ByteSequence.from("123456", StandardCharsets.UTF_8);

    private final Class<? extends EtcdConfigProviderTest> clazz = getClass();

    private final ApplicationProtocolConfig alpn = new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            ApplicationProtocolNames.HTTP_2,
            ApplicationProtocolNames.HTTP_1_1);

    @Test
    public void testEtcdConfigProvider() throws Exception {
        try (EtcdCluster cluster = EtcdClusterFactory.buildCluster("etcd-ssl-cluster", 1, true)) {

            cluster.start();

            URI endpoint = cluster.getClientEndpoints().get(0);

            initValue(endpoint);

            Map<String, Object> options = new HashMap<>();
            options.put("endpoints", endpoint);
            options.put("user", "root");
            options.put("password", "123456");
            options.put("authority", "etcd0");
            options.put("trustCertCollectionFile", "classpath:ssl/cert/ca.pem");
            options.put("keyCertChainFile", "classpath:ssl/cert/client.pem");
            options.put("keyFile", "classpath:ssl/cert/client.key");
            options.put("dataId", "/polaris/sync-config.json");

            EtcdConfigProvider<RegistryProto.Registry> provider = new EtcdConfigProvider<>();
            provider.init(options, RegistryProto.Registry::newBuilder);

            RegistryProto.Registry registry = provider.getConfig();
            Assert.assertEquals(registry.getTasks(0).getName(), "ins-3ad0f6e7");
            Assert.assertEquals(registry.getMethods(0).getType(), ModelProto.Method.MethodType.watch);
            Assert.assertTrue(registry.getHealthCheck().getEnable());
            Assert.assertEquals(registry.getReport().getInterval(), "1m");

            updateValue(endpoint);
            TimeUnit.MILLISECONDS.sleep(50);

            RegistryProto.Registry updateRegistry = provider.getConfig();
            Assert.assertEquals(updateRegistry.getTasks(0).getName(), "ins-3ad0f6e7-update");
            Assert.assertEquals(updateRegistry.getMethods(0).getType(), ModelProto.Method.MethodType.pull);
            Assert.assertFalse(updateRegistry.getHealthCheck().getEnable());
            Assert.assertEquals(updateRegistry.getReport().getInterval(), "2m");

            provider.close();
        }
    }

    private void initValue(URI endpoint) throws Exception {
        try (InputStream caIs = clazz.getResourceAsStream("/ssl/cert/ca.pem");
             InputStream keyCertChainIs = clazz.getResourceAsStream("/ssl/cert/client.pem");
             InputStream keyIs = clazz.getResourceAsStream("/ssl/cert/client.key");
             Client client = Client.builder()
                     .endpoints(endpoint)
                     .authority("etcd0")
                     .sslContext(GrpcSslContexts.forClient()
                             .applicationProtocolConfig(alpn)
                             .trustManager(caIs)
                             .keyManager(keyCertChainIs, keyIs)
                             .sslProvider(SslProvider.OPENSSL)
                             .build())
                     .build()) {

            client.getKVClient().put(key, value1).join();

            client.getAuthClient().userAdd(root, password).join();
            client.getAuthClient().roleAdd(root).join();
            client.getAuthClient().userGrantRole(root, root).join();
            client.getAuthClient().authEnable().join();
        }
    }

    private void updateValue(URI endpoint) throws Exception {
        try (InputStream caIs = clazz.getResourceAsStream("/ssl/cert/ca.pem");
             InputStream keyCertChainIs = clazz.getResourceAsStream("/ssl/cert/client.pem");
             InputStream keyIs = clazz.getResourceAsStream("/ssl/cert/client.key");
             Client rootClient = Client.builder()
                     .endpoints(endpoint)
                     .authority("etcd0")
                     .user(root)
                     .password(password)
                     .sslContext(GrpcSslContexts.forClient()
                             .applicationProtocolConfig(alpn)
                             .trustManager(caIs)
                             .keyManager(keyCertChainIs, keyIs)
                             .sslProvider(SslProvider.OPENSSL)
                             .build())
                     .build()) {
            rootClient.getKVClient().put(key, value2).join();
        }
    }
}
