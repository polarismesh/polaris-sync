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

package cn.polarismesh.polaris.sync.core.tasks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.core.utils.CommonUtils;
import cn.polarismesh.polaris.sync.core.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigListener;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class AbstractTaskEngine implements ConfigListener {

	private static final Logger LOG = LoggerFactory.getLogger(cn.polarismesh.polaris.sync.core.tasks.registry.RegistryTaskEngine.class);

	private RegistryProto.Registry registryConfig;

	protected final ScheduledExecutorService pullExecutor;

	protected final ScheduledExecutorService watchExecutor;

	protected final ExecutorService reloadExecutor;

	private final Object configLock = new Object();

	public AbstractTaskEngine(String name) {
		pullExecutor =
				Executors.newScheduledThreadPool(1, new NamedThreadFactory(name + "-list-worker"));
		watchExecutor = Executors
				.newScheduledThreadPool(1, new NamedThreadFactory(name + "-watch-worker"));
		reloadExecutor = Executors.newFixedThreadPool(
				1, new NamedThreadFactory(name + "-reload-worker"));
	}

	public void init(RegistryProto.Registry config) {
		reload(config);
	}

	public void destroy() {
		watchExecutor.shutdown();
		pullExecutor.shutdown();
	}

	private int[] initTasks(RegistryProto.Registry registryConfig) {
		int watchTasks = 0;
		int pullTasks = 0;
		List<RegistryProto.Task> registryTasks = registryConfig.getTasksList();
		if (CollectionUtils.isEmpty(registryTasks)) {
			LOG.info("[Core] registry task is empty, no task scheduled");
			return new int[] {watchTasks, pullTasks};
		}
		List<RegistryProto.ConfigTask> configTasks = registryConfig.getConfigTasksList();
		if (CollectionUtils.isEmpty(configTasks)) {
			LOG.info("[Core] config task is empty, no task scheduled");
			return new int[] {watchTasks, pullTasks};
		}

		List<RegistryProto.Method> methods = registryConfig.getMethodsList();
		for (RegistryProto.Task task : registryTasks) {
			if (!task.getEnable()) {
				continue;
			}
			int[] counts = addRegistryTask(task, methods);
			watchTasks += counts[0];
			pullTasks += counts[1];
		}

		for (RegistryProto.ConfigTask task : configTasks) {
			if (!task.getEnable()) {
				continue;
			}
			int[] counts = addConfigTask(task, methods);
			watchTasks += counts[0];
			pullTasks += counts[1];
		}

		return new int[] {watchTasks, pullTasks};
	}

	private int[] clearTasks(RegistryProto.Registry registryConfig) {
		int watchTasks = 0;
		int pullTasks = 0;
		List<RegistryProto.Task> tasks = registryConfig.getTasksList();
		if (CollectionUtils.isEmpty(tasks)) {
			LOG.info("[Core] task is empty, no task scheduled");
			return new int[] {watchTasks, pullTasks};
		}
		List<RegistryProto.Method> methods = registryConfig.getMethodsList();
		for (RegistryProto.Task task : tasks) {
			int[] counts = deleteRegistryTask(task, methods);
			watchTasks += counts[0];
			pullTasks += counts[1];
		}

		List<RegistryProto.ConfigTask> configTasks = registryConfig.getConfigTasksList();
		if (CollectionUtils.isEmpty(configTasks)) {
			LOG.info("[Core] config task is empty, no task scheduled");
			return new int[] {watchTasks, pullTasks};
		}
		for (RegistryProto.ConfigTask task : configTasks) {
			int[] counts = deleteConfigTask(task, methods);
			watchTasks += counts[0];
			pullTasks += counts[1];
		}

		return new int[] {watchTasks, pullTasks};
	}

	protected abstract int[] deleteRegistryTask(RegistryProto.Task task, List<RegistryProto.Method> methods);

	protected abstract int[] deleteConfigTask(RegistryProto.ConfigTask task, List<RegistryProto.Method> methods);

	protected abstract int[] addRegistryTask(RegistryProto.Task task, List<RegistryProto.Method> methods);

	protected abstract int[] addConfigTask(RegistryProto.ConfigTask task, List<RegistryProto.Method> methods);

	protected abstract void verifyTask(RegistryProto.Registry registry);

	protected final void reload(RegistryProto.Registry registryConfig) {
		synchronized (configLock) {
			int watchTasksAdded = 0;
			int pullTasksAdded = 0;
			int watchTasksDeleted = 0;
			int pullTasksDeleted = 0;
			RegistryProto.Registry oldRegistryConfig = this.registryConfig;
			this.registryConfig = registryConfig;

			if (Objects.isNull(oldRegistryConfig)) {
				int[] addCounts = initTasks(registryConfig);
				watchTasksAdded += addCounts[0];
				pullTasksAdded += addCounts[1];
				LOG.info(
						"[Core] tasks init, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
						watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
				return;
			}

			if (CommonUtils.methodsChanged(oldRegistryConfig.getMethodsList(), registryConfig.getMethodsList())) {
				// method changed, clear the old tasks before adding new tasks
				LOG.info("[Core] task sync methods changed");
				int[] clearCounts = clearTasks(oldRegistryConfig);
				int[] addCounts = initTasks(registryConfig);
				watchTasksAdded += addCounts[0];
				pullTasksAdded += addCounts[1];
				watchTasksDeleted += clearCounts[0];
				pullTasksDeleted += clearCounts[1];
				LOG.info(
						"[Core] tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
						watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
				return;
			}

			reloadRegistry(registryConfig, oldRegistryConfig);
			reloadConfig(registryConfig, oldRegistryConfig);
		}
	}

	private void reloadRegistry(RegistryProto.Registry newConfig, RegistryProto.Registry oldConfig) {
		int watchTasksAdded = 0;
		int pullTasksAdded = 0;
		int watchTasksDeleted = 0;
		int pullTasksDeleted = 0;
		Map<String, RegistryProto.Task> oldTasks = new HashMap<>();
		Map<String, RegistryProto.Task> newTasks = new HashMap<>();
		for (RegistryProto.Task task : oldConfig.getTasksList()) {
			oldTasks.put(task.getName(), task);
		}
		for (RegistryProto.Task task : newConfig.getTasksList()) {
			newTasks.put(task.getName(), task);
		}
		for (Map.Entry<String, RegistryProto.Task> entry : oldTasks.entrySet()) {
			if (!newTasks.containsKey(entry.getKey())) {
				LOG.info("[Core] registry task {} has been deleted", entry.getKey());
				int[] deleteCounts = deleteRegistryTask(entry.getValue(), oldConfig.getMethodsList());
				watchTasksDeleted += deleteCounts[0];
				pullTasksDeleted += deleteCounts[1];
			}
		}
		for (Map.Entry<String, RegistryProto.Task> entry : newTasks.entrySet()) {
			if (!oldTasks.containsKey(entry.getKey())) {
				LOG.info("[Core] registry task {} has been added", entry.getKey());
				int[] addCounts = addRegistryTask(entry.getValue(), oldConfig.getMethodsList());
				watchTasksAdded += addCounts[0];
				pullTasksAdded += addCounts[1];
			}
			else {
				RegistryProto.Task oldTask = oldTasks.get(entry.getKey());
				RegistryProto.Task newTask = entry.getValue();
				if (oldTask.equals(newTask)) {
					continue;
				}
				LOG.info("[Core] registry task {} has been changed", entry.getKey());

				int[] deleteCounts = deleteRegistryTask(oldTask, oldConfig.getMethodsList());
				watchTasksDeleted += deleteCounts[0];
				pullTasksDeleted += deleteCounts[1];

				int[] addCounts = addRegistryTask(newTask, newConfig.getMethodsList());
				watchTasksAdded += addCounts[0];
				pullTasksAdded += addCounts[1];
			}
		}
		LOG.info(
				"[Core] registry tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
				watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
	}

	private void reloadConfig(RegistryProto.Registry newConfig, RegistryProto.Registry oldConfig) {
		int watchTasksAdded = 0;
		int pullTasksAdded = 0;
		int watchTasksDeleted = 0;
		int pullTasksDeleted = 0;
		Map<String, RegistryProto.ConfigTask> oldTasks = new HashMap<>();
		Map<String, RegistryProto.ConfigTask> newTasks = new HashMap<>();
		for (RegistryProto.ConfigTask task : oldConfig.getConfigTasksList()) {
			oldTasks.put(task.getName(), task);
		}
		for (RegistryProto.ConfigTask task : newConfig.getConfigTasksList()) {
			newTasks.put(task.getName(), task);
		}
		for (Map.Entry<String, RegistryProto.ConfigTask> entry : oldTasks.entrySet()) {
			if (!newTasks.containsKey(entry.getKey())) {
				LOG.info("[Core] config task {} has been deleted", entry.getKey());
				int[] deleteCounts = deleteConfigTask(entry.getValue(), oldConfig.getMethodsList());
				watchTasksDeleted += deleteCounts[0];
				pullTasksDeleted += deleteCounts[1];
			}
		}
		for (Map.Entry<String, RegistryProto.ConfigTask> entry : newTasks.entrySet()) {
			if (!oldTasks.containsKey(entry.getKey())) {
				LOG.info("[Core] config task {} has been added", entry.getKey());
				int[] addCounts = addConfigTask(entry.getValue(), oldConfig.getMethodsList());
				watchTasksAdded += addCounts[0];
				pullTasksAdded += addCounts[1];
			}
			else {
				RegistryProto.ConfigTask oldTask = oldTasks.get(entry.getKey());
				RegistryProto.ConfigTask newTask = entry.getValue();
				if (oldTask.equals(newTask)) {
					continue;
				}
				LOG.info("[Core] config task {} has been changed", entry.getKey());

				int[] deleteCounts = deleteConfigTask(oldTask, oldConfig.getMethodsList());
				watchTasksDeleted += deleteCounts[0];
				pullTasksDeleted += deleteCounts[1];

				int[] addCounts = addConfigTask(newTask, newConfig.getMethodsList());
				watchTasksAdded += addCounts[0];
				pullTasksAdded += addCounts[1];
			}
		}
		LOG.info(
				"[Core] config tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
				watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
	}

	@Override
	public void onChange(RegistryProto.Registry config) {
		reload(config);
	}

	@Override
	public Executor executor() {
		return reloadExecutor;
	}

}
