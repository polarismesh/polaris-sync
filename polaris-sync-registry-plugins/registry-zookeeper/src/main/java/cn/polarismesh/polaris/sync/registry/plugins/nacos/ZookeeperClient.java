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

package cn.polarismesh.polaris.sync.registry.plugins.nacos;

import org.apache.zookeeper.*;
import org.apache.zookeeper.server.DumbWatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.polarismesh.polaris.sync.registry.plugins.nacos.ZookeeperRegistryCenter.dealError;
import static cn.polarismesh.polaris.sync.registry.plugins.nacos.ZookeeperRegistryCenter.halt;
import static org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 */
public class ZookeeperClient {
    static byte[] empty = new byte[0];
    private ZooKeeper zk;

    public ZookeeperClient(String addr) {
        zk = getZooKeeper(addr);
    }

    List<String> getChildren(String path) {
        try {
            return zk.getChildren(path, false);
        } catch (Exception ex) {
            if (ex instanceof KeeperException.NoNodeException) {
                return Collections.emptyList();
            }
            dealError("fail to get children of path {}", path, ex);
            return Collections.emptyList();
        }
    }

    String getData(String path) {
        try {
            byte[] bs = zk.getData(path, false, null);
            return new String(bs);
        } catch (Exception ex) {
            dealError("fail to get data of path {}", path, ex);
            return null;
        }
    }

    void createTempPath(String path) {
        parentPaths(path).forEach(parent -> createPath(parent, CreateMode.PERSISTENT));
        createPath(path, CreateMode.EPHEMERAL);
    }

    void createPath(String path, CreateMode mode) {
        try {
            zk.create(path, empty, OPEN_ACL_UNSAFE, mode);
        } catch (Exception ex) {
            if (!(ex instanceof KeeperException.NodeExistsException)) {
                dealError("fail to create path {}", path, ex);
            }
        }
    }

    static List<String> parentPaths(String path) {
        String[] paths = path.split("/");
        List<String> pathAndParents = new ArrayList<>(paths.length);
        for (int i = paths.length - 1; i > 1; i--) {
            pathAndParents.add(join(i, paths));
        }
        Collections.reverse(pathAndParents);
        return pathAndParents;
    }

    static String join(int limit, String... strings) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append(strings[i]).append("/");
        }
        return sb.substring(0, sb.length() - 1);
    }

    void deletePath(String path) {
        try {
            zk.delete(path, -1);
        } catch (Exception ex) {
            dealError("fail to delete path {}", path, ex);
        }
    }

    void watch(String path, Watcher watcher) {
        try {
            zk.addWatch(path, watcher, AddWatchMode.PERSISTENT);
        } catch (Exception ex) {
            dealError("fail to watch path {}", path, ex);
        }
    }

    void unWatch(String path, Watcher watcher) {
        try {
            zk.removeWatches(path, watcher, Watcher.WatcherType.Any, true);
        } catch (Exception ex) {
            dealError("fail to watch path {}", path, ex);
        }
    }

    public void close() {
        try {
            zk.close(200);
        } catch (InterruptedException ignored) {
        }
    }

    private ZooKeeper getZooKeeper(String address) {
        try {
            return new ZooKeeper(address, 120000, new DumbWatcher());
        } catch (IOException e) {
            halt("connect to {} failed", address, e);
            return null;
        }
    }
}
