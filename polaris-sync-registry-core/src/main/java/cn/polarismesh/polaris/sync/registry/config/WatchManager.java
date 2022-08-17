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

package cn.polarismesh.polaris.sync.registry.config;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.registry.utils.ConfigUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchManager {

    private static final Logger LOG = LoggerFactory.getLogger(WatchManager.class);

    private final List<FileListener> fileListeners;

    private final ScheduledExecutorService fileWatchService = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("file-watch-worker"));


    public WatchManager(List<FileListener> fileListeners) {
        this.fileListeners = fileListeners;
    }

    public void destroy() {
        fileWatchService.shutdown();
    }

    public void start(String fullFileName, long crcValue, String backupFileName) {
        FileChangeWorker fileChangeWorker = new FileChangeWorker(fullFileName, crcValue, backupFileName);
        fileWatchService.scheduleWithFixedDelay(fileChangeWorker,
                DefaultValues.DEFAULT_FILE_PULL_MS, DefaultValues.DEFAULT_FILE_PULL_MS, TimeUnit.MILLISECONDS);
    }

    private class FileChangeWorker implements Runnable {

        private final String fullFileName;

        private final String backupFileName;

        private long crcValue;

        public FileChangeWorker(String fullFileName, long crcValue, String backupFileName) {
            this.fullFileName = fullFileName;
            this.backupFileName = backupFileName;
            this.crcValue = crcValue;
        }

        @Override
        public void run() {
            File watchFile = new File(fullFileName);
            byte[] strBytes;
            try {
                strBytes = FileUtils.readFileToByteArray(watchFile);
            } catch (IOException e) {
                LOG.error("[Core] fail to read watchFile {}", fullFileName, e);
                return;
            }
            long newCrcValue = ConfigUtils.calcCrc32(strBytes);
            if (newCrcValue == 0 || newCrcValue == crcValue) {
                return;
            }
            crcValue = newCrcValue;
            String content = new String(strBytes, StandardCharsets.UTF_8);
            LOG.info("[Core] config watchFile changed, new content {}", content);
            boolean fileValid = true;
            for (FileListener fileListener : fileListeners) {
                if (!fileListener.onFileChanged(strBytes)) {
                    fileValid = false;
                }
            }
            if (fileValid) {
                File backupFile = new File(backupFileName);
                try {
                    FileUtils.copyFile(watchFile, backupFile);
                } catch (IOException e) {
                    LOG.error("[Core]fail to copy watchFile from {} to {}", fullFileName, backupFileName, e);
                }
            }
        }
    }

}
