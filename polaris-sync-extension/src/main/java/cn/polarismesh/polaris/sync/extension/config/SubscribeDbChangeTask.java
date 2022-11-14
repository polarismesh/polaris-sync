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

package cn.polarismesh.polaris.sync.extension.config;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SubscribeDbChangeTask implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(SubscribeDbChangeTask.class);

	private boolean first = true;

	private Date lastUpdateTime;

	private final Map<String, ConfigFile> items = new ConcurrentHashMap<>();

	private final String name;

	private final Function<Date, List<ConfigFile>> pullDataAction;

	private final ScheduledExecutorService executor;

	private final ExecutorService listenerExecutor;

	private final Map<ConfigGroup, Set<ConfigCenter.ResponseListener>> matchGroups = new ConcurrentHashMap<>();

	private final Map<String, WatchEvent> tmp = new HashMap<>();

	private volatile boolean shutdown = false;

	public SubscribeDbChangeTask(String name, Function<Date, List<ConfigFile>> pullDataAction) {
		this.name = name;
		this.pullDataAction = pullDataAction;
		this.executor = Executors.newScheduledThreadPool(1, r -> {
			Thread thread = new Thread(r);
			thread.setName(String.format("sync.config-%s.watch", name));
			return thread;
		});
		this.listenerExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread thread = new Thread(r);
			thread.setName(String.format("sync.config-%s.listener", name));
			return thread;
		});
		this.executor.scheduleAtFixedRate(this, 2, 2, TimeUnit.SECONDS);
	}

	public synchronized void addListener(ConfigGroup group, ConfigCenter.ResponseListener listener) {
		matchGroups.computeIfAbsent(group, k -> new CopyOnWriteArraySet<>());
		matchGroups.get(group).add(listener);
	}

	public void destroy() {
		shutdown = true;
		executor.shutdown();
		listenerExecutor.shutdown();
	}

	@Override
	public void run() {
		if (shutdown) {
			return;
		}

		Date lastUpdateTime = this.lastUpdateTime;
		if (first) {
			lastUpdateTime = null;
		}
		first = false;

		tmp.clear();

		try {
			Date maxUpdateTime = new Date(0);
			List<ConfigFile> result = pullDataAction.apply(lastUpdateTime);

			for (ConfigFile t : result) {
				String groupKey = t.getNamespace() + "@" + t.getGroup();
				tmp.computeIfAbsent(groupKey, k -> WatchEvent.builder()
						.configGroup(ConfigGroup.builder()
								.namespace(t.getNamespace())
								.name(t.getGroup())
								.build())
						.build());

				WatchEvent event = tmp.get(groupKey);

				String key = t.keyInfo();
				if (!t.isValid()) {
					event.appendRemote(t);
					items.remove(key);
					continue;
				}

				if (!items.containsKey(key)) {
					event.appendAdd(t);
				}
				event.appendUpdate(t);
				items.put(key, t);

				if (Objects.isNull(maxUpdateTime)) {
					maxUpdateTime = t.getModifyTime();
					continue;
				}

				if (maxUpdateTime.compareTo(t.getModifyTime()) < 0) {
					maxUpdateTime = t.getModifyTime();
				}
			}

			this.lastUpdateTime = maxUpdateTime;

			tmp.forEach((s, e) -> {
				SubscribeDbChangeTask.this.matchGroups.forEach((g, l) -> {
					if (g.match(e.getConfigGroup())) {
						listenerExecutor.execute(() -> l.forEach(responseListener -> responseListener.onEvent(e)));
					}
				});
			});
		}
		catch (Throwable ex) {
			LOG.error("[Config][Watch] {} watch config file change error ", name, ex);
		}
	}

}
