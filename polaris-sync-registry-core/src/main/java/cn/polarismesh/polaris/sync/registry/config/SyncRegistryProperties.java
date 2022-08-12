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

import javax.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("polaris.sync.registry")
@Validated
public class SyncRegistryProperties {

    @NotEmpty
    private String configBackupPath;

    @NotEmpty
    private String configWatchPath;

    public String getConfigBackupPath() {
        return configBackupPath;
    }

    public void setConfigBackupPath(String configBackupPath) {
        this.configBackupPath = configBackupPath;
    }

    public String getConfigWatchPath() {
        return configWatchPath;
    }

    public void setConfigWatchPath(String configWatchPath) {
        this.configWatchPath = configWatchPath;
    }

    @Override
    public String toString() {
        return "SyncRegistryProperties{" +
                "configBackupPath='" + configBackupPath + '\'' +
                ", configWatchPath='" + configWatchPath + '\'' +
                '}';
    }
}
