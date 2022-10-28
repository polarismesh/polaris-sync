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

package cn.polarismesh.polaris.sync.core.utils;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 解析时间格式的工具类
 *
 * @author andrewshan
 */
public class DurationUtils {

    private static final Pattern PATTERN_DIGITAL =
            Pattern.compile("^[0-9]+$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_SECOND =
            Pattern.compile("^[0-9]+s$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_HOUR =
            Pattern.compile("^[0-9]+h$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_MINUTE =
            Pattern.compile("^[0-9]+m$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_MILLIS =
            Pattern.compile("^[0-9]+ms$",
                    Pattern.CASE_INSENSITIVE);

    /**
     * 将字符串转换为毫秒值
     *
     * @param value 字符串的时间间隔
     * @return long, 毫秒
     */
    public static long parseDurationMillis(String value, long defaultValue) {
        if (PATTERN_DIGITAL.matcher(value).matches()) {
            //纯数字，直接返回毫秒
            return Long.parseLong(value);
        }
        if (PATTERN_SECOND.matcher(value).matches()
                || PATTERN_HOUR.matcher(value).matches()
                || PATTERN_MINUTE.matcher(value).matches()) {
            return Duration.parse(String.format("pt%s", value)).toMillis();
        }
        if (PATTERN_MILLIS.matcher(value).matches()) {
            String digitalValue = value.substring(0, value.length() - 2);
            return Long.parseLong(digitalValue);
        }
        return defaultValue;
    }

}
