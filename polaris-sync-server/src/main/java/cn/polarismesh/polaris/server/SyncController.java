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

package cn.polarismesh.polaris.server;

import cn.polarismesh.polaris.sync.core.server.ResourceSyncServer;
import cn.polarismesh.polaris.sync.core.tasks.registry.NamedRegistryCenter;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sync")
public class SyncController {

    private static final Logger LOG = LoggerFactory.getLogger(SyncController.class);

    @Autowired
    private ResourceSyncServer resourceSyncServer;

    /**
     * health check.
     * @return health check info
     */
    @GetMapping("/health")
    public String healthCheck() {
        return "pk ok";
    }


    /**
     * 获取命名空间
     * @return namespaces
     */
    @GetMapping("/maintain/v1/namespaces")
    public ResponseEntity<String> maintainV1Namespaces(
            @RequestParam("task") String taskName, @RequestParam("registry") String registryName) {
        NamedRegistryCenter registry = resourceSyncServer.getRegistryTaskEngine().getRegistry(taskName, registryName);
        if (null == registry) {
            return ResponseEntity.notFound().build();
        }
        DiscoverResponse discoverResponse = registry.getRegistry().listNamespaces();
        String jsonText = "{}";
        try {
            jsonText = JsonFormat.printer().print(discoverResponse);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("[Server] fail to marshall response {}", discoverResponse, e);
        }
        return new ResponseEntity<>(jsonText, HttpStatus.valueOf(ResponseUtils.getHttpStatusCode(discoverResponse)));
    }

    /**
     * 根据命名空间查询服务列表
     * @return services
     */
    @GetMapping("/maintain/v1/services")
    public ResponseEntity<String> maintainV1Services(@RequestParam("task") String taskName,
            @RequestParam("registry") String registryName, @RequestParam("namespace") String namespace) {
        NamedRegistryCenter registry = resourceSyncServer.getRegistryTaskEngine().getRegistry(taskName, registryName);
        if (null == registry) {
            return ResponseEntity.notFound().build();
        }
        DiscoverResponse discoverResponse = registry.getRegistry().listServices(namespace);
        String jsonText = "{}";
        try {
            jsonText = JsonFormat.printer().print(discoverResponse);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("[Server] fail to marshall response {}", discoverResponse, e);
        }
        return new ResponseEntity<>(jsonText, HttpStatus.valueOf(ResponseUtils.getHttpStatusCode(discoverResponse)));
    }
}
