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

package cn.polarismesh.polaris.sync.common.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HostAndPort {

    private static final Logger LOG = LoggerFactory.getLogger(HostAndPort.class);

    private final String host;

    private final int port;

    private HostAndPort(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static HostAndPort build(String address, int defaultPort) {
        String host;
        int port;
        if (address.indexOf(":") > 0) {
            String[] values = address.split(":");
            host = values[0];
            try {
                port = Integer.parseInt(values[1]);
            } catch (NumberFormatException e) {
                LOG.error("fail to parse address {}", address, e);
                port = defaultPort;
            }
        } else {
            host = address;
            port = defaultPort;
        }
        return new HostAndPort(host, port);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "HostAndPort{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
