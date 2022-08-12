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


package cn.polarismesh.polaris.sync.registry.plugins.kong;

import static cn.polarismesh.polaris.sync.common.rest.RestOperator.pickAddress;

import java.util.List;

public class KongEndpointUtils {

    public static String toUpstreamsUrl(String address) {
        return String.format("http://%s/upstreams", address);
    }

    public static String toUpstreamUrl(List<String> addresses, String upstreamName) {
        String address = pickAddress(addresses);
        return String.format("http://%s/upstreams/%s", address, upstreamName);
    }

    public static String toTargetsWriteUrl(List<String> addresses, String upstreamName) {
        String address = pickAddress(addresses);
        return String.format("http://%s/upstreams/%s/targets", address, upstreamName);
    }

    public static String toTargetsReadUrl(List<String> addresses, String upstreamName) {
        String address = pickAddress(addresses);
        return String.format("http://%s/upstreams/%s/targets/all", address, upstreamName);
    }

    public static String toTargetUrl(List<String> addresses, String upstreamName, String target) {
        String address = pickAddress(addresses);
        return String.format("http://%s/upstreams/%s/targets/%s", address, upstreamName, target);
    }

    public static String toServicesUrl(String address) {
        return String.format("http://%s/services", address);
    }

    public static String toServiceUrl(List<String> addresses, String serviceName) {
        String address = pickAddress(addresses);
        return String.format("http://%s/services/%s", address, serviceName);
    }
}
