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

package cn.polarismesh.polaris.sync.registry.config;

import cn.polarismesh.polaris.sync.extension.config.ConfigListener;
import cn.polarismesh.polaris.sync.extension.config.ConfigProvider;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import cn.polarismesh.polaris.sync.registry.utils.ConfigUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigProviderManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigProviderManager.class);
    private final SyncRegistryProperties properties;

    private ConfigProvider provider;

    private final BackupConfig backupConfig;

    public ConfigProviderManager(SyncRegistryProperties properties) {
        this.properties = properties;
        this.backupConfig = new BackupConfig(properties.getConfigBackupPath());
    }

    private void init() throws Exception {
        String providerType = properties.getType();
        Iterator<ConfigProvider> iterator = ServiceLoader.load(ConfigProvider.class).iterator();
        while (iterator.hasNext()) {
            ConfigProvider item = iterator.next();
            if (Objects.equals(item.name(), providerType)) {
                provider = item;
                break;
            }
        }

        Objects.requireNonNull(provider, "ConfigProvider");
        provider.init(properties.getOptions());
    }

    public void addListener(ConfigListener listener) {
        provider.addListener(new ConfigListener() {
            private AtomicReference<Registry> lastVal = new AtomicReference<>();

            @Override
            public void onChange(Registry registry) {
                try {
                    listener.onChange(registry);
                    lastVal.set(registry);
                } catch (Throwable ex) {
                    Registry old = lastVal.get();
                    if (old != null) {
                        listener.onChange(old);
                    }
                }
            }
        });
    }

    public Registry getConfig() {
        Registry config = provider.getConfig();
        if (config == null) {
            return backupConfig.getBackup();
        }
        return config;
    }

    public void destroy() {
        if (provider != null) {
            provider.close();
        }
    }

    private class BackupConfig implements ConfigListener {

        private final File backup;

        private BackupConfig(String backup) {
            this.backup = new File(backup);
        }

        @Override
        public void onChange(Registry registry) {
            try {
                byte[] ret = ConfigUtils.marshal(registry);
                FileUtils.writeByteArrayToFile(backup, ret);
            } catch (IOException e) {
                LOG.error("[BackupConfig] save backup file", e);
            }
        }

        private Registry getBackup() {
            try {
                byte[] ret = FileUtils.readFileToByteArray(backup);
                return ConfigUtils.parseFromContent(ret);
            } catch (IOException e) {
                LOG.error("[BackupConfig] get backup file", e);
                return null;
            }
        }
    }
}
