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

package cn.polarismesh.polaris.sync.registry.server;

import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.registry.config.FileListener;
import cn.polarismesh.polaris.sync.registry.config.SyncRegistryProperties;
import cn.polarismesh.polaris.sync.registry.config.WatchManager;
import cn.polarismesh.polaris.sync.registry.tasks.TaskEngine;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;

public class RegistrySyncServer {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrySyncServer.class);

    private final SyncRegistryProperties syncRegistryProperties;

    private final List<RegistryCenter> registryCenters;

    private TaskEngine taskEngine;

    private WatchManager watchManager;

    public RegistrySyncServer(SyncRegistryProperties syncRegistryProperties,
            List<RegistryCenter> registryCenters) {
        this.syncRegistryProperties = syncRegistryProperties;
        this.registryCenters = registryCenters;
    }


    public void init() {
        taskEngine = new TaskEngine(registryCenters);
        List<FileListener> listeners = new ArrayList<>();
        listeners.add(taskEngine);
        watchManager = new WatchManager(listeners);

        String configPath = syncRegistryProperties.getConfigPath();
        String watchPath = syncRegistryProperties.getWatchPath();
        File configFile = new File(configPath);
        File watchFile = new File(watchPath);
        boolean initByWatched = false;
        if (watchFile.exists()) {
            LOG.info("[Core] try to init by watch file {}", watchPath);
            initByWatched = true;
            try {
                taskEngine.init(watchPath);
                LOG.info("[Core] engine init by watch file {}", watchPath);
            } catch (IOException e) {
                LOG.error("[Core] fail to init engine by watch file {}", watchPath, e);
                initByWatched = false;
            }
        }
        if (!initByWatched) {
            LOG.info("[Core] try to init by config file {}", configPath);
            if (!configFile.exists()) {
                throw new RuntimeException("config file not found in " + configPath);
            }
            try {
                taskEngine.init(configPath);
                LOG.info("[Core] engine init by config file {}", configPath);
            } catch (IOException e) {
                LOG.error("[Core] fail to init engine by config file {}", configPath, e);
            }
        } else {
            try {
                FileCopyUtils.copy(watchFile, configFile);
            } catch (IOException e) {
                LOG.error("[Core]fail to copy file from {} to {}", watchPath, configPath, e);
            }
        }
        try {
            watchManager.start(watchPath, configPath);
        } catch (IOException e) {
            throw new RuntimeException("fail to init watch manager", e);
        }


    }

    public void destroy() {
        if (null != taskEngine) {
            taskEngine.destroy();
        }
        if (null != watchManager) {
            watchManager.destroy();
        }
    }
}
