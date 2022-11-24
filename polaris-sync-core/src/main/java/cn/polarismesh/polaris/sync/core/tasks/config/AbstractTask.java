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

package cn.polarismesh.polaris.sync.core.tasks.config;

import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface AbstractTask extends Runnable {

    /**
     * 如果是从 polaris -> nacos, 则需要将 polaris 的默认命名空间 default 转为 nacos 的默认命名空间
     * 如果是从 nacos -> polaris, 则需要将 nacos 的默认命名空间转为 polaris 的默认命名空间 default
     *
     * @param files
     * @return
     */
    default Collection<ConfigFile> handle(NamedConfigCenter source, NamedConfigCenter destination,
            Collection<ConfigFile> files) {
        if (ResourceType.POLARIS.equals(destination.getConfigCenter().getType()) && ResourceType.NACOS.equals(
                source.getConfigCenter().getType())) {
            return files.stream().peek(file -> {
                if (Objects.equals("", file.getNamespace()) || Objects.equals(DefaultValues.EMPTY_NAMESPACE_HOLDER,
                        file.getNamespace())) {
                    file.setNamespace(DefaultValues.DEFAULT_POLARIS_NAMESPACE);
                }
            }).collect(Collectors.toList());
        }

        if (ResourceType.POLARIS.equals(source.getConfigCenter().getType()) && ResourceType.NACOS.equals(
                destination.getConfigCenter().getType())) {
            return files.stream().peek(file -> {
                if (Objects.equals(DefaultValues.DEFAULT_POLARIS_NAMESPACE, file.getNamespace())) {
                    file.setNamespace(DefaultValues.EMPTY_NAMESPACE_HOLDER);
                }
            }).collect(Collectors.toList());
        }

        return files;
    }

}
