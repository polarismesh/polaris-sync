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

package cn.polarismesh.polaris.sync.reporter.plugins.balad.model;

import java.util.List;
import java.util.Map;

public class Batch {

    private String namespace;

    private long timestamp;

    private Map<String, String> dimension;

    private List<Metric> batch;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getDimension() {
        return dimension;
    }

    public void setDimension(Map<String, String> dimension) {
        this.dimension = dimension;
    }

    public List<Metric> getBatch() {
        return batch;
    }

    public void setBatch(List<Metric> batch) {
        this.batch = batch;
    }

    @Override
    public String toString() {
        return "Batch{" +
                "namespace='" + namespace + '\'' +
                ", timestamp=" + timestamp +
                ", dimension=" + dimension +
                ", batch=" + batch +
                '}';
    }
}
