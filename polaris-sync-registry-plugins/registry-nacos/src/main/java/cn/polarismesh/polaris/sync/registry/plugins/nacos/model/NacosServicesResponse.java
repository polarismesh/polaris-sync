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

package cn.polarismesh.polaris.sync.registry.plugins.nacos.model;

import java.util.List;

public class NacosServicesResponse {

    private int count;

    private List<NacosServiceView> serviceList;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<NacosServiceView> getServiceList() {
        return serviceList;
    }

    public void setServiceList(
            List<NacosServiceView> serviceList) {
        this.serviceList = serviceList;
    }

    @Override
    public String toString() {
        return "NacosServices{" +
                "count=" + count +
                ", serviceList=" + serviceList +
                '}';
    }
}
