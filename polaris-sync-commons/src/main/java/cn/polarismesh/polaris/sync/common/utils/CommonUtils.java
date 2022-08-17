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

import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang.StringUtils;

public class CommonUtils {

    public static boolean matchMetadata(Map<String, String> metadataMap, Map<String, String> filters) {
        if (null == filters || filters.size() == 0) {
            return true;
        }
        if (null == metadataMap || metadataMap.size() == 0) {
            return false;
        }
        boolean matched = true;
        for (Map.Entry<String, String> label : filters.entrySet()) {
            String labelKey = label.getKey();
            String labelValue = label.getValue();
            if (!metadataMap.containsKey(labelKey)) {
                matched = false;
                break;
            }
            String metaValue = metadataMap.get(labelKey);
            if (!labelValue.equals(metaValue)) {
                matched = false;
                break;
            }
        }
        return matched;
    }

    public static boolean isSyncedByCurrentSource(Map<String, String> metadataMap, String srcName) {
        String value = metadataMap.get(DefaultValues.META_SYNC);
        return null != value && value.equals(srcName);
    }

    public static Map<String, String> defaultMap(Map<String, String> metadata) {
        if (null == metadata) {
            return Collections.emptyMap();
        }
        return metadata;
    }

    public static boolean metadataEquals(Map<String, String> srcMetadata, Map<String, String> dstMetadata) {
        srcMetadata = defaultMap(srcMetadata);
        dstMetadata = defaultMap(dstMetadata);
        if (srcMetadata.size() != dstMetadata.size()) {
            return false;
        }
        for (Map.Entry<String, String> entry : srcMetadata.entrySet()) {
            String key = entry.getKey();
            if (!dstMetadata.containsKey(key)) {
                return false;
            }
            String srcValue = entry.getValue();
            String dstValue = dstMetadata.get(key);
            if (!StringUtils.equals(srcValue, dstValue)) {
                return false;
            }
        }
        return true;
    }
}
