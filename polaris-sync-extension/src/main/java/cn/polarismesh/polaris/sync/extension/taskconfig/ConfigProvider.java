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

package cn.polarismesh.polaris.sync.extension.taskconfig;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface ConfigProvider<T> {

    void init(Map<String, Object> options, Supplier<Message.Builder> supplier) throws Exception;

    void addListener(ConfigListener listener);

    <T> T getConfig();

    void close();

    default T unmarshal(byte[] strBytes, Message.Builder builder) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(strBytes), StandardCharsets.UTF_8)) {
            Parser parser = JsonFormat.parser();
            parser.merge(reader, builder);
            return (T) builder.build();
        }
    }
}
