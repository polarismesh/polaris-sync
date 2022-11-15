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

package cn.polarismesh.polaris.sync.taskconfig.plugins.kubernetes;

import org.apache.commons.lang.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Config {

    private String address;

    private String token;

    private String namespace;

    private String configmapName;

    private String dataId;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getConfigmapName() {
        return configmapName;
    }

    public void setConfigmapName(String configmapName) {
        this.configmapName = configmapName;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public boolean hasToken() {
        return StringUtils.isNotBlank(address) && StringUtils.isNotBlank(token);
    }

    @Override
    public String toString() {
        return "Config{" +
                "address='" + address + '\'' +
                ", token='" + token + '\'' +
                ", namespace='" + namespace + '\'' +
                ", configmapName='" + configmapName + '\'' +
                ", dataId='" + dataId + '\'' +
                '}';
    }
}
