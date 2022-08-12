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

package cn.polarismesh.polaris.sync.extension.report;

import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.RegistryEndpoint.RegistryType;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class RegistryHealthStatus {

    private final Dimension dimension;

    private final AtomicInteger totalCount;

    private final AtomicInteger errorCount;

    public RegistryHealthStatus(String name,
            RegistryType registryType, String productionName, int totalCount, int errorCount) {
        this.dimension = new Dimension(name, registryType, productionName);
        this.totalCount = new AtomicInteger(totalCount);
        this.errorCount = new AtomicInteger(errorCount);
    }

    public RegistryHealthStatus(Dimension dimension, int totalCount, int errorCount) {
        this.dimension = dimension;
        this.totalCount = new AtomicInteger(totalCount);
        this.errorCount = new AtomicInteger(errorCount);
    }

    public Dimension getDimension() {
        return dimension;
    }

    public int getTotalCount() {
        return totalCount.get();
    }

    public int getAndDeleteTotalCount() {
        int value = totalCount.get();
        totalCount.addAndGet(-value);
        return value;
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public int getAndDeleteErrorCount() {
        int value = errorCount.get();
        errorCount.addAndGet(-value);
        return value;
    }

    public void addValues(int total, int error) {
        totalCount.addAndGet(total);
        errorCount.addAndGet(error);
    }

    @Override
    public String toString() {
        return "RegistryHealthStatus{" +
                "dimension=" + dimension +
                ", totalCount=" + totalCount +
                ", errorCount=" + errorCount +
                '}';
    }

    public static class Dimension {

        private final String name;

        private final RegistryType registryType;

        private final String productionName;

        public Dimension(String name,
                RegistryType registryType, String productionName) {
            this.name = name;
            this.registryType = registryType;
            this.productionName = productionName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Dimension)) {
                return false;
            }
            Dimension dimension = (Dimension) o;
            return Objects.equals(name, dimension.name) &&
                    registryType == dimension.registryType &&
                    Objects.equals(productionName, dimension.productionName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, registryType, productionName);
        }

        public String getName() {
            return name;
        }

        public RegistryType getRegistryType() {
            return registryType;
        }

        public String getProductionName() {
            return productionName;
        }
    }
}
