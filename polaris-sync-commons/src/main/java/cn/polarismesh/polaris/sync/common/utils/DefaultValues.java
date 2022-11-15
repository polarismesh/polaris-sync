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

package cn.polarismesh.polaris.sync.common.utils;

public interface DefaultValues {

    long DEFAULT_INTERVAL_MS = 5 * 1000;

    long DEFAULT_FILE_PULL_MS = 30 * 1000;

    long DEFAULT_PULL_INTERVAL_MS = 5 * 60 * 1000;

    String DEFAULT_POLARIS_NAMESPACE = "default";

    String GROUP_NAME_DEFAULT = "default";

    // EMPTY_NAMESPACE_HOLDER 空命名空间占位符
    String EMPTY_NAMESPACE_HOLDER = "empty_ns";

    String META_SYNC = "__sync__";

    String MATCH_ALL = "*";
}
