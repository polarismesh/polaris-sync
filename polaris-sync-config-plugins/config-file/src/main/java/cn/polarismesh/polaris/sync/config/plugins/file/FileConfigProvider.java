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

package cn.polarismesh.polaris.sync.config.plugins.file;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.extension.config.ConfigListener;
import cn.polarismesh.polaris.sync.extension.config.ConfigProvider;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

/**
 * 基于本地文件的配置提供者
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class FileConfigProvider implements ConfigProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FileConfigProvider.class);

    private static final String PLUGIN_NAME = "file";

    private final ScheduledExecutorService fileWatchService = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("file-watch-worker"));

    private final CopyOnWriteArraySet<ConfigListener> listeners = new CopyOnWriteArraySet<>();

    private FileChangeWorker fileChangeWorker;

    @Override
    public void init(Map<String, Object> options) throws Exception {
        Gson gson = new Gson();
        Config param = gson.fromJson(gson.toJson(options), Config.class);

        fileChangeWorker = new FileChangeWorker(param.getWatchFile(), 0);
        fileWatchService.scheduleWithFixedDelay(fileChangeWorker,
                DefaultValues.DEFAULT_FILE_PULL_MS, DefaultValues.DEFAULT_FILE_PULL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void addListener(ConfigListener listener) {
        listeners.add(listener);
    }

    @Override
    public Registry getConfig() {
        return fileChangeWorker.holder.get();
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public void close() {
        fileWatchService.shutdown();
    }

    private class FileChangeWorker implements Runnable {

        private final String fullFileName;

        private long crcValue;

        private AtomicReference<Registry> holder = new AtomicReference<>();

        public FileChangeWorker(String fullFileName, long crcValue) {
            this.fullFileName = fullFileName;
            this.crcValue = crcValue;
            run();
        }

        @Override
        public void run() {
            File watchFile = new File(fullFileName);
            try {
                byte[] strBytes = FileUtils.readFileToByteArray(watchFile);
                long newCrcValue = calcCrc32(strBytes);
                if (newCrcValue == 0 || newCrcValue == crcValue) {
                    return;
                }
                crcValue = newCrcValue;

                Registry config = unmarshal(strBytes);
                holder.set(config);
                for (ConfigListener listener : listeners) {
                    listener.onChange(config);
                }
                String content = new String(strBytes, StandardCharsets.UTF_8);
                LOG.info("[Core] config watchFile changed, new content {}", content);
            } catch (IOException e) {
                LOG.error("[Core] fail to read watchFile {}", fullFileName, e);
            }
        }
    }

    private static long calcCrc32(byte[] strBytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(strBytes);
        return crc32.getValue();
    }
}
