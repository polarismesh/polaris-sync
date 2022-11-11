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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.core.tasks.registry.NamedRegistryCenter;
import cn.polarismesh.polaris.sync.core.tasks.registry.PullTask;
import cn.polarismesh.polaris.sync.core.tasks.registry.RegistryTaskEngine;
import cn.polarismesh.polaris.sync.core.tasks.registry.UnwatchTask;
import cn.polarismesh.polaris.sync.core.tasks.registry.WatchTask;
import cn.polarismesh.polaris.sync.core.utils.CommonUtils;
import cn.polarismesh.polaris.sync.core.utils.DurationUtils;
import cn.polarismesh.polaris.sync.core.utils.RegistryUtils;
import cn.polarismesh.polaris.sync.extension.InitRequest;
import cn.polarismesh.polaris.sync.extension.ResourceCenter;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class AbstractTaskEngine<T extends SyncTask> {

	private static final Logger LOG = LoggerFactory.getLogger(cn.polarismesh.polaris.sync.core.tasks.registry.RegistryTaskEngine.class);

	protected T config;

	protected final ScheduledExecutorService pullExecutor;

	protected final ScheduledExecutorService watchExecutor;

	protected final ExecutorService reloadExecutor;

	private final Map<SyncTask.Match, Future<?>> watchTasks = new ConcurrentHashMap<>();

	private final Map<String, ScheduledFuture<?>> pulledTasks = new HashMap<>();

	private List<T> tasks;

	private List<ModelProto.Method> methods;

	private final Map<String, ResourceSet> resources = new HashMap<>();

	protected final Map<ResourceType, Class<? extends ResourceCenter>> typeClassMap = new HashMap<>();

	private final Object configLock = new Object();

	public AbstractTaskEngine(String name) {
		pullExecutor =
				Executors.newScheduledThreadPool(1, new NamedThreadFactory(name + "-list-worker"));
		watchExecutor = Executors
				.newScheduledThreadPool(1, new NamedThreadFactory(name + "-watch-worker"));
		reloadExecutor = Executors.newFixedThreadPool(
				1, new NamedThreadFactory(name + "-reload-worker"));
	}

	public void init(List<T> tasks, List<ModelProto.Method> methods) {
		reload(tasks, methods);
	}

	public void destroy() {
		watchExecutor.shutdown();
		pullExecutor.shutdown();
	}

	public List<T> getTasks() {
		return Collections.unmodifiableList(tasks);
	}

	private int[] initTasks(List<T> tasks, List<ModelProto.Method> methods) {
		int watchTasks = 0;
		int pullTasks = 0;
		if (CollectionUtils.isEmpty(tasks)) {
			LOG.info("[Core] registry task is empty, no task scheduled");
			return new int[] {watchTasks, pullTasks};
		}
		for (T task : tasks) {
			if (!task.isEnable()) {
				continue;
			}
			int[] counts = addTask(task, methods);
			watchTasks += counts[0];
			pullTasks += counts[1];
		}
		return new int[] {watchTasks, pullTasks};
	}

	private int[] clearTasks(List<T> tasks, List<ModelProto.Method> methods) {
		int watchTasks = 0;
		int pullTasks = 0;
		if (CollectionUtils.isEmpty(tasks)) {
			LOG.info("[Core] task is empty, no task scheduled");
			return new int[] {watchTasks, pullTasks};
		}
		for (T task : tasks) {
			int[] counts = deleteTask(task, methods);
			watchTasks += counts[0];
			pullTasks += counts[1];
		}
		return new int[] {watchTasks, pullTasks};
	}

	protected int[] addTask(T task, List<ModelProto.Method> methods) {
		int watchTasks = 0;
		int pullTasks = 0;
		if (CollectionUtils.isEmpty(methods)) {
			return new int[]{watchTasks, pullTasks};
		}
		if (!task.isEnable()) {
			return new int[]{watchTasks, pullTasks};
		}
		for (ModelProto.Method method : methods) {
			if (!method.getEnable()) {
				continue;
			}
			LOG.info("[Core] registry start to add task {}, method {}", task, method);
			if (ModelProto.Method.MethodType.pull.equals(method.getType())) {
				long pullInterval = DurationUtils.parseDurationMillis(
						method.getInterval(), DefaultValues.DEFAULT_PULL_INTERVAL_MS);
				addPullTask(task, pullInterval);
				pullTasks++;
			} else if (ModelProto.Method.MethodType.watch.equals(method.getType())) {
				addWatchTask(task);
				watchTasks++;
			}
		}
		return new int[]{watchTasks, pullTasks};
	}

	private void addPullTask(T task, long intervalMilli) {
		ResourceSet resourceSet = getOrCreateResourceSet(task);
		if (Objects.isNull(resourceSet)) {
			LOG.error("[Core] registry adding pull task {}, fail to init registry", task.getName());
			return;
		}
		NamedResourceCenter source = resourceSet.getSource();
		NamedResourceCenter dest = resourceSet.getDest();
		Runnable pull = buildPullTask(source, dest, task.getMatchList());
		ScheduledFuture<?> future = pullExecutor
				.scheduleWithFixedDelay(pull, 0, intervalMilli, TimeUnit.MILLISECONDS);
		pulledTasks.put(task.getName(), future);
		LOG.info("[Core] registry task {} has been scheduled pulled", task.getName());
	}

	protected abstract Runnable buildPullTask(NamedResourceCenter source, NamedResourceCenter dest, List<SyncTask.Match> matches);

	private void addWatchTask(T task) {
		ResourceSet resourceSet = getOrCreateResourceSet(task);
		if (Objects.isNull(resourceSet)) {
			LOG.error("[Core] registry adding watch task {}, fail to init registry", task.getName());
			return;
		}
		NamedResourceCenter source = resourceSet.getSource();
		NamedResourceCenter dest = resourceSet.getDest();
		for (SyncTask.Match match : task.getMatchList()) {
			if (RegistryUtils.isEmptyMatch(match)) {
				continue;
			}
			Runnable watchTask = buildWatchTask(source, dest, match);
			Future<?> submit = watchExecutor.schedule(watchTask, 1, TimeUnit.SECONDS);
			watchTasks.put(match, submit);
			LOG.info("[Core] service {} has been scheduled watched", match);
		}
	}

	protected abstract Runnable buildWatchTask(NamedResourceCenter source, NamedResourceCenter dest, SyncTask.Match match);

	protected int[] deleteTask(T task, List<ModelProto.Method> methods) {
		int watchTasks = 0;
		int pullTasks = 0;
		if (CollectionUtils.isEmpty(methods)) {
			return new int[]{watchTasks, pullTasks};
		}
		if (!task.isEnable()) {
			return new int[]{watchTasks, pullTasks};
		}
		for (ModelProto.Method method : methods) {
			if (!method.getEnable()) {
				continue;
			}
			LOG.info("[Core] registry start to delete task {}, method {}", task, method);
			if (ModelProto.Method.MethodType.pull.equals(method.getType())) {
				deletePullTask(task);
				pullTasks++;
			} else if (ModelProto.Method.MethodType.watch.equals(method.getType())) {
				deleteWatchTask(task);
				watchTasks++;
			}
		}
		ResourceSet resourceSet = resources.remove(task.getName());
		if (Objects.nonNull(resourceSet)) {
			resourceSet.destroy();
		}
		return new int[]{watchTasks, pullTasks};
	}

	protected void deletePullTask(T task) {
		ScheduledFuture<?> future = pulledTasks.remove(task.getName());
		if (null != future) {
			future.cancel(true);
		}
		LOG.info("[Core] task {} has been cancel pulled", task.getName());
	}

	private void deleteWatchTask(T task) {
		ResourceEndpoint source = task.getSource();
		NamedResourceCenter center = getResource(task.getName()).getSource();
		if (Objects.isNull(center)) {
			for (SyncTask.Match match : task.getMatchList()) {
				if (RegistryUtils.isEmptyMatch(match)) {
					continue;
				}
				Runnable unwatchTask = buildUnWatchTask(center.getCenter(), match);
				Future<?> future = watchTasks.remove(match);
				if (null != future) {
					future.cancel(true);
				}
				unwatchTask.run();
				LOG.info("[Core] service {} has been cancel watched", match);
			}
		}
	}

	protected abstract Runnable buildUnWatchTask(ResourceCenter center, SyncTask.Match match);

	protected abstract void verifyTask(List<T> tasks , List<ModelProto.Method> methods);

	protected final void reload(List<T> tasks, List<ModelProto.Method> methods) {
		synchronized (configLock) {
			int watchTasksAdded = 0;
			int pullTasksAdded = 0;
			int watchTasksDeleted = 0;
			int pullTasksDeleted = 0;
			List<T> oldTasks = this.tasks;
			List<ModelProto.Method> oldMethods = this.methods;
			this.tasks = tasks;
			this.methods = methods;

			if (CollectionUtils.isEmpty(oldTasks)) {
				int[] addCounts = initTasks(tasks, methods);
				watchTasksAdded += addCounts[0];
				pullTasksAdded += addCounts[1];
				LOG.info(
						"[Core] tasks init, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
						watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
				return;
			}

			if (CommonUtils.methodsChanged(oldMethods, methods)) {
				// method changed, clear the old tasks before adding new tasks
				LOG.info("[Core] task sync methods changed");
				int[] clearCounts = clearTasks(tasks, methods);
				int[] addCounts = initTasks(tasks, methods);
				watchTasksAdded += addCounts[0];
				pullTasksAdded += addCounts[1];
				watchTasksDeleted += clearCounts[0];
				pullTasksDeleted += clearCounts[1];
				LOG.info(
						"[Core] tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
						watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
				return;
			}

			Map<String, T> oldTasksMap = new HashMap<>();
			Map<String, T> newTasksMap = new HashMap<>();
			for (T task : oldTasks) {
				oldTasksMap.put(task.getName(), task);
			}
			for (T task : this.tasks) {
				newTasksMap.put(task.getName(), task);
			}
			for (Map.Entry<String, T> entry : oldTasksMap.entrySet()) {
				if (!newTasksMap.containsKey(entry.getKey())) {
					LOG.info("[Core] config task {} has been deleted", entry.getKey());
					int[] deleteCounts = deleteTask(entry.getValue(), oldMethods);
					watchTasksDeleted += deleteCounts[0];
					pullTasksDeleted += deleteCounts[1];
				}
			}
			for (Map.Entry<String, T> entry : newTasksMap.entrySet()) {
				if (!oldTasksMap.containsKey(entry.getKey())) {
					LOG.info("[Core] config task {} has been added", entry.getKey());
					int[] addCounts = addTask(entry.getValue(), methods);
					watchTasksAdded += addCounts[0];
					pullTasksAdded += addCounts[1];
				}
				else {
					T oldTask = oldTasksMap.get(entry.getKey());
					T newTask = entry.getValue();
					if (oldTask.equals(newTask)) {
						continue;
					}
					LOG.info("[Core] config task {} has been changed", entry.getKey());

					int[] deleteCounts = deleteTask(oldTask, oldMethods);
					watchTasksDeleted += deleteCounts[0];
					pullTasksDeleted += deleteCounts[1];

					int[] addCounts = addTask(newTask, this.methods);
					watchTasksAdded += addCounts[0];
					pullTasksAdded += addCounts[1];
				}
			}
			LOG.info(
					"[Core] config tasks reloaded, watchTasksAdded {}, pullTasksAdded {}, watchTasksDeleted {}, pullTasksDeleted {}",
					watchTasksAdded, pullTasksAdded, watchTasksDeleted, pullTasksDeleted);
		}
	}

	public Executor executor() {
		return reloadExecutor;
	}


	protected abstract InitRequest buildInitRequest(String sourceName, ResourceType resourceType, ResourceEndpoint endpoint);

	private ResourceSet getOrCreateResourceSet(SyncTask task) {
		ResourceSet resourceSet = resources.get(task.getName());
		if (Objects.nonNull(resourceSet)) {
			return resourceSet;
		}
		ResourceEndpoint source = task.getSource();
		ResourceEndpoint destination = task.getDestination();
		ResourceCenter sourceCenter = createResource(source.getResourceType());
		if (null == sourceCenter) {
			return null;
		}
		ResourceCenter destinationCenter = createResource(destination.getResourceType());
		if (null == destinationCenter) {
			return null;
		}
		sourceCenter.init(buildInitRequest("", ResourceType.UNKNOWN, source));
		destinationCenter.init(buildInitRequest(source.getName(), source.getResourceType(), destination));
		resourceSet = new ResourceSet(new NamedResourceCenter(
				source.getName(), source.getProductName(), sourceCenter),
				new NamedResourceCenter(destination.getName(), destination.getProductName(), destinationCenter));
		resources.put(task.getName(), resourceSet);
		return resourceSet;
	}

	private ResourceCenter createResource(ResourceType resourceType) {
		Class<? extends ResourceCenter> registryClazz = typeClassMap.get(resourceType);
		ResourceCenter center;
		try {
			center = registryClazz.newInstance();
		} catch (Exception e) {
			LOG.error("[Core] fail to create instance for class {}", registryClazz.getCanonicalName(), e);
			return null;
		}
		return center;
	}

	public ResourceSet getResource(String taskName) {
		synchronized (configLock) {
			return resources.get(taskName);
		}
	}

	public NamedResourceCenter getResourceCenter(String taskName, String name) {
		ResourceSet resource = getResource(taskName);
		if (Objects.isNull(resource)) {
			return null;
		}
		if (StringUtils.equals(resource.getSource().getName(), name)) {
			return resource.getSource();
		}
		if (StringUtils.equals(resource.getDest().getName(), name)) {
			return resource.getDest();
		}
		return null;
	}

	public static class Source {

		private final String sourceName;

		private final SyncTask.Match match;

		public Source(String sourceName, SyncTask.Match match) {
			this.sourceName = sourceName;
			this.match = match;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Source)) {
				return false;
			}
			Source that = (Source) o;
			return Objects.equals(sourceName, that.sourceName) &&
					Objects.equals(match, that.match);
		}

		@Override
		public int hashCode() {
			return Objects.hash(sourceName, match);
		}

		@Override
		public String toString() {
			return "ServiceWithSource{" +
					"sourceName='" + sourceName + '\'' +
					", match=" + match +
					'}';
		}
	}

	public static class ResourceSet {

		private final NamedResourceCenter source;

		private final NamedResourceCenter dest;

		public ResourceSet(NamedResourceCenter source, NamedResourceCenter dest) {
			this.source = source;
			this.dest = dest;
		}

		public NamedResourceCenter getSource() {
			return source;
		}

		public NamedResourceCenter getDest() {
			return dest;
		}

		public void destroy() {
			source.getCenter().destroy();
			dest.getCenter().destroy();
		}
	}


}
