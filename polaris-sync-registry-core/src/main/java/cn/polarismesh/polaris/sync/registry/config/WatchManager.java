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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;

public class WatchManager {

    private static final Logger LOG = LoggerFactory.getLogger(WatchManager.class);

    private final List<FileListener> fileListeners;

    private final AtomicBoolean stop = new AtomicBoolean(false);

    public WatchManager(List<FileListener> fileListeners) {
        this.fileListeners = fileListeners;
    }

    public void destroy() {
        stop.set(true);
    }

    private void run(WatchService watchService, String fileName, String backupFileName) {
        boolean poll = true;
        while (poll && !stop.get()) {
            WatchKey key = null;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                LOG.error("[Core] interrupted signal received in watch manager, ignore");
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                Path path  = (Path) event.context();
                if (!fileName.equals(path.getFileName().toFile().getName())) {
                    continue;
                }
                if (ENTRY_DELETE.equals(event.kind())) {
                    LOG.warn("[Core] config file has been deleted");
                    continue;
                }
                LOG.info("[Core] config file {} changed, event kind {}", fileName, event.kind());
                File configFile = path.toFile();
                boolean fileValid = true;
                for (FileListener fileListener : fileListeners) {
                    if (!fileListener.onFileChanged(configFile)) {
                        fileValid = false;
                    }
                }
                if (fileValid) {
                    try {
                        FileCopyUtils.copy(configFile, new File(backupFileName));
                    } catch (IOException e) {
                        LOG.error("[Core]fail to copy file from {} to {}",
                                configFile.getAbsolutePath(), backupFileName, e);
                    }
                }
            }
            poll = key.reset();
        }
        try {
            watchService.close();
        } catch (IOException e) {
            LOG.error("[Core] fail to close watch service", e);
        }

    }

    public void start(String fullFileName, String backupFileName) throws IOException {
        File file = new File(fullFileName);
        String folder = file.getParent();
        WatchService watchService = null;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(folder);
            path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        } catch (IOException e) {
            LOG.error("[Core] watch file {} encounter exception", file.getAbsolutePath(), e);
            if (null != watchService) {
                try {
                    watchService.close();
                } catch (IOException e1) {
                    LOG.error("[Core] fail to close watch service", e1);
                }
            }
            throw e;
        }
        final WatchService wService = watchService;
        String fileName = file.getName();
        Thread thread = new Thread(()-> {
            run(wService, fileName, backupFileName);
        });
        thread.setName("sync-config-file-watcher");
        thread.setDaemon(true);
        thread.start();
    }

}
