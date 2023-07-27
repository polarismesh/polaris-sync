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

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.core.utils.CommonUtils;
import cn.polarismesh.polaris.sync.core.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.core.utils.DurationUtils;
import cn.polarismesh.polaris.sync.extension.InitRequest;
import cn.polarismesh.polaris.sync.extension.ResourceCenter;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class AbstractTaskEngine<C extends ResourceCenter, T extends SyncTask> {

	private static final Logger LOG = LoggerFactory.getLogger(cn.polarismesh.polaris.sync.core.tasks.registry.RegistryTaskEngine.class);

	protected T config;

	protected final ScheduledExecutorService pullExecutor;

	protected final ScheduledExecutorService watchExecutor;

	protected final ScheduledExecutorService commonExecutor;

	protected final ExecutorService reloadExecutor;

	protected final Map<SyncTask.Match, Future<?>> watchTasks = new ConcurrentHashMap<>();

	protected final Map<String, ScheduledFuture<?>> pulledTasks = new HashMap<>();

	private List<T> tasks;

	private List<ModelProto.Method> methods;

	private final Map<String, ResourceSet<C>> resources = new HashMap<>();

	protected final Map<ResourceType, Class<? extends ResourceCenter>> typeClassMap = new HashMap<ResourceType, Class<? extends ResourceCenter>>() {
		@Override
		public Class<? extends ResourceCenter> put(ResourceType key, Class<? extends ResourceCenter> value) {
			return super.put(key, value);
		}
	};

	private final Object configLock = new Object();

	public AbstractTaskEngine(String name) {
		pullExecutor =
				Executors.newScheduledThreadPool(1, new NamedThreadFactory(name + "-list-worker"));
		watchExecutor = Executors
				.newScheduledThreadPool(1, new NamedThreadFactory(name + "-watch-worker"));
		reloadExecutor = Executors.newFixedThreadPool(
				1, new NamedThreadFactory(name + "-reload-worker"));
		commonExecutor = Executors.newScheduledThreadPool(
				Runtime.getRuntime().availableProcessors(), new NamedThreadFactory(name + "-common-worker"));
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
		ResourceSet<C> resourceSet = getOrCreateResourceSet(task);
		if (Objects.isNull(resourceSet)) {
			LOG.error("[Core] registry adding pull task {}, fail to init registry", task.getName());
			return;
		}
		NamedResourceCenter<C> source = resourceSet.getSource();
		NamedResourceCenter<C> dest = resourceSet.getDest();
		Runnable pull = buildPullTask(source, dest, task.getMatchList());

		if (resourceSet.getSource().getCenter().getType() == ResourceType.KUBERNETES
				&& intervalMilli > DefaultValues.DEFAULT_INTERVAL_MS) {
			// 目前kubernetes还没实现watch的模式，因此 pull 的时间要尽可能短
			intervalMilli =  DefaultValues.DEFAULT_INTERVAL_MS;
		}
		ScheduledFuture<?> future = pullExecutor
				.scheduleWithFixedDelay(pull, 0, intervalMilli, TimeUnit.MILLISECONDS);
		pulledTasks.put(task.getName(), future);
		LOG.info("[Core] registry task {} has been scheduled pulled", task.getName());
	}

	protected abstract Runnable buildPullTask(NamedResourceCenter<C> source, NamedResourceCenter<C> dest, List<SyncTask.Match> matches);

	private void addWatchTask(T task) {
		ResourceSet<C> resourceSet = getOrCreateResourceSet(task);
		if (Objects.isNull(resourceSet)) {
			LOG.error("[Core] registry adding watch task {}, fail to init registry", task.getName());
			return;
		}
		NamedResourceCenter<C> source = resourceSet.getSource();
		NamedResourceCenter<C> dest = resourceSet.getDest();
		for (SyncTask.Match match : task.getMatchList()) {
			if (ConfigUtils.isEmptyMatch(match)) {
				continue;
			}
			Runnable watchTask = buildWatchTask(source, dest, match);
			Future<?> submit = watchExecutor.schedule(watchTask, 1, TimeUnit.SECONDS);
			watchTasks.put(match, submit);
			LOG.info("[Core] service {} has been scheduled watched", match);
		}
	}

	protected abstract Runnable buildWatchTask(NamedResourceCenter<C> source, NamedResourceCenter<C> dest, SyncTask.Match match);

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
		NamedResourceCenter<C> center = getResource(task.getName()).getSource();
		if (Objects.isNull(center)) {
			for (SyncTask.Match match : task.getMatchList()) {
				if (ConfigUtils.isEmptyMatch(match)) {
					continue;
				}
				Runnable unwatchTask = buildUnWatchTask(center, match);
				Future<?> future = watchTasks.remove(match);
				if (null != future) {
					future.cancel(true);
				}
				unwatchTask.run();
				LOG.info("[Core] service {} has been cancel watched", match);
			}
		}
	}

	protected abstract Runnable buildUnWatchTask(NamedResourceCenter<C> center, SyncTask.Match match);

	protected abstract void verifyTask(List<T> tasks , List<ModelProto.Method> methods);

	public final void reload(List<T> tasks, List<ModelProto.Method> methods) {
		verifyTask(tasks, methods);

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

	private ResourceSet<C> getOrCreateResourceSet(SyncTask task) {
		ResourceSet<C> resourceSet = resources.get(task.getName());
		if (Objects.nonNull(resourceSet)) {
			return resourceSet;
		}
		ResourceEndpoint source = task.getSource();
		ResourceEndpoint destination = task.getDestination();
		C sourceCenter = createResource(source.getResourceType());
		if (null == sourceCenter) {
			return null;
		}
		C destinationCenter = createResource(destination.getResourceType());
		if (null == destinationCenter) {
			return null;
		}
		sourceCenter.init(buildInitRequest("", ResourceType.UNKNOWN, source));
		destinationCenter.init(buildInitRequest(source.getName(), source.getResourceType(), destination));
		resourceSet = new ResourceSet<>(new NamedResourceCenter<C>(
				source.getName(), source.getProductName(), sourceCenter),
				new NamedResourceCenter<C>(destination.getName(), destination.getProductName(), destinationCenter));
		resources.put(task.getName(), resourceSet);
		return resourceSet;
	}

	private C createResource(ResourceType resourceType) {
		Class<? extends ResourceCenter> registryClazz = typeClassMap.get(resourceType);
		C center;
		try {
			center = (C) registryClazz.newInstance();
		} catch (Exception e) {
			LOG.error("[Core] fail to create instance for class {}", registryClazz.getCanonicalName(), e);
			return null;
		}
		return (C) center;
	}

	public ResourceSet<C> getResource(String taskName) {
		synchronized (configLock) {
			return resources.get(taskName);
		}
	}

	public NamedResourceCenter<C> getResourceCenter(String taskName, String name) {
		ResourceSet<C> resource = getResource(taskName);
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

	public static class ResourceSet<C extends ResourceCenter> {

		private final NamedResourceCenter<C> source;

		private final NamedResourceCenter<C> dest;

		public ResourceSet(NamedResourceCenter<C> source, NamedResourceCenter<C> dest) {
			this.source = source;
			this.dest = dest;
		}

		public NamedResourceCenter<C> getSource() {
			return source;
		}

		public NamedResourceCenter<C> getDest() {
			return dest;
		}

		public void destroy() {
			source.getCenter().destroy();
			dest.getCenter().destroy();
		}
	}


}
