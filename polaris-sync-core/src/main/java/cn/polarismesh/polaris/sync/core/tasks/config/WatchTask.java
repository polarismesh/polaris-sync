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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cn.polarismesh.polaris.sync.core.utils.TaskUtils;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;
import cn.polarismesh.polaris.sync.extension.config.WatchEvent;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import com.tencent.polaris.client.pb.ConfigFileProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class WatchTask implements Runnable{

	private static final Logger LOG = LoggerFactory.getLogger(cn.polarismesh.polaris.sync.core.tasks.registry.WatchTask.class);

	private final NamedConfigCenter source;

	private final NamedConfigCenter destination;

	private final ConfigGroup configGroup;

	private final Collection<RegistryProto.Group> groups;

	private final Executor executor;

	private final ScheduledExecutorService watchExecutor;

	private final ConfigTaskEngine.ConfigGroupWithSource groupWithSource;

	private final Map<ConfigTaskEngine.ConfigGroupWithSource, Future<?>> watchedGroups;

	private final ResponseListener responseListener;

	public WatchTask(Map<ConfigTaskEngine.ConfigGroupWithSource, Future<?>> watchedGroups, NamedConfigCenter source,
			NamedConfigCenter destination, RegistryProto.ConfigMatch match, Executor executor,
			ScheduledExecutorService watchExecutor) {
		this.watchedGroups = watchedGroups;
		this.source = source;
		this.destination = destination;
		this.configGroup = ConfigGroup.builder().namespace(match.getNamespace()).name(match.getConfigGroup()).build();
		this.executor = executor;
		this.watchExecutor = watchExecutor;
		this.groups = TaskUtils.verifyGroups(match.getGroupsList());
		this.groupWithSource = new ConfigTaskEngine.ConfigGroupWithSource(source.getName(), this.configGroup);
		responseListener = new WatchTask.ResponseListener();
	}

	public ConfigTaskEngine.ConfigGroupWithSource getGroupWithSource() {
		return groupWithSource;
	}

	@Override
	public void run() {
		if (source.getConfigCenter().watch(configGroup, responseListener)) {
			LOG.info("[LOG] success to watch for service {}", groupWithSource);
			return;
		}
		LOG.info("[LOG] start to retry watch for service {}", groupWithSource);
		if (!watchedGroups.containsKey(groupWithSource)) {
			return;
		}
		Future<?> submit = watchExecutor.schedule(this, 10, TimeUnit.SECONDS);
		watchedGroups.put(groupWithSource, submit);
		LOG.info("[Core] config group {} has been scheduled watched", groupWithSource);
	}

	private class ResponseListener implements ConfigCenter.ResponseListener {

		@Override
		public void onEvent(WatchEvent watchEvent) {
			executor.execute(() -> {
				Collection<ConfigFileProto.ConfigFileDTO> files = watchEvent.getFiles();
				// diff by groups
				for (RegistryProto.Group group : groups) {
					LOG.info("[Core][Watch]prepare to update group {} instances {}", group.getName(), files);
					destination.getConfigCenter().updateConfigFiles(configGroup, files);
				}
			});
		}
	}

}
