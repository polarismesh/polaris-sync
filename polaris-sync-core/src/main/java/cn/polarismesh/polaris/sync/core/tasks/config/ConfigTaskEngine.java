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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cn.polarismesh.polaris.sync.common.pool.NamedThreadFactory;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.core.tasks.AbstractTaskEngine;
import cn.polarismesh.polaris.sync.core.utils.ConfigUtils;
import cn.polarismesh.polaris.sync.core.utils.DurationUtils;
import cn.polarismesh.polaris.sync.extension.config.ConfigCenter;
import cn.polarismesh.polaris.sync.extension.config.ConfigGroup;
import cn.polarismesh.polaris.sync.extension.config.ConfigInitRequest;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigTaskEngine extends AbstractTaskEngine {

	private static final Logger LOG = LoggerFactory.getLogger(ConfigTaskEngine.class);

	private final ExecutorService publishExecutor =
			Executors.newCachedThreadPool(new NamedThreadFactory("publish-worker"));

	private final Map<ConfigTaskEngine.ConfigGroupWithSource, Future<?>> watchedConfigs = new ConcurrentHashMap<>();

	private final Map<String, ScheduledFuture<?>> pulledConfigs = new HashMap<>();

	private final Object configLock = new Object();

	private final Map<String, ConfigTaskEngine.ConfigSet> taskRegistryMap = new HashMap<>();

	protected final Map<RegistryProto.ConfigEndpoint.ConfigType, Class<? extends ConfigCenter>> configTypeMap = new HashMap<>();

	public ConfigTaskEngine(List<ConfigCenter> configCenters) {
		super("config");
		for (ConfigCenter center : configCenters) {
			configTypeMap.put(center.getType(), center.getClass());
		}
	}

	@Override
	protected void verifyTask(RegistryProto.Registry registryConfig) {
		if (!ConfigUtils.verifyTasks(registryConfig, configTypeMap.keySet())) {
			throw new IllegalArgumentException("invalid configuration content " + registryConfig.toString());
		}
	}

	@Override
	protected int[] deleteRegistryTask(RegistryProto.Task task, List<RegistryProto.Method> methods) {
		return new int[]{0,0};
	}

	@Override
	protected int[] deleteConfigTask(RegistryProto.ConfigTask task, List<RegistryProto.Method> methods) {
		int watchTasks = 0;
		int pullTasks = 0;
		if (CollectionUtils.isEmpty(methods)) {
			return new int[]{watchTasks, pullTasks};
		}
		if (!task.getEnable()) {
			return new int[]{watchTasks, pullTasks};
		}
		for (RegistryProto.Method method : methods) {
			if (!method.getEnable()) {
				continue;
			}
			LOG.info("[Core] config start to delete task {}, method {}", task, method);
			if (RegistryProto.Method.MethodType.pull.equals(method.getType())) {
				deletePullTask(task);
				pullTasks++;
			} else if (RegistryProto.Method.MethodType.watch.equals(method.getType())) {
				deleteWatchTask(task);
				watchTasks++;
			}
		}
		ConfigSet configSet = taskRegistryMap.remove(task.getName());
		if (null != configSet) {
			configSet.destroy();
		}
		return new int[]{watchTasks, pullTasks};
	}

	@Override
	protected int[] addRegistryTask(RegistryProto.Task task, List<RegistryProto.Method> methods) {
		return new int[]{0,0};
	}

	@Override
	protected int[] addConfigTask(RegistryProto.ConfigTask task, List<RegistryProto.Method> methods) {
		int watchTasks = 0;
		int pullTasks = 0;
		if (CollectionUtils.isEmpty(methods)) {
			return new int[]{watchTasks, pullTasks};
		}
		if (!task.getEnable()) {
			return new int[]{watchTasks, pullTasks};
		}
		for (RegistryProto.Method method : methods) {
			if (!method.getEnable()) {
				continue;
			}
			LOG.info("[Core] config start to add task {}, method {}", task, method);
			if (RegistryProto.Method.MethodType.pull.equals(method.getType())) {
				long pullInterval = DurationUtils.parseDurationMillis(
						method.getInterval(), DefaultValues.DEFAULT_PULL_INTERVAL_MS);
				addPullTask(task, pullInterval);
				pullTasks++;
			} else if (RegistryProto.Method.MethodType.watch.equals(method.getType())) {
				addWatchTask(task);
				watchTasks++;
			}
		}
		return new int[]{watchTasks, pullTasks};
	}

	protected void deletePullTask(RegistryProto.ConfigTask task) {
		ScheduledFuture<?> future = pulledConfigs.remove(task.getName());
		if (null != future) {
			future.cancel(true);
		}
		LOG.info("[Core] config task {} has been cancel pulled", task.getName());
	}

	private void deleteWatchTask(RegistryProto.ConfigTask task) {
		RegistryProto.ConfigEndpoint source = task.getSource();
		NamedConfigCenter sourceCenter = getSource(task.getName());
		if (null != sourceCenter) {
			for (RegistryProto.ConfigMatch match : task.getMatchList()) {
				if (ConfigUtils.isEmptyMatch(match)) {
					continue;
				}
				UnwatchTask unwatchTask = new UnwatchTask(sourceCenter, match);
				ConfigGroupWithSource serviceWithSource = new ConfigGroupWithSource(source.getName(), unwatchTask.getConfigGroup());
				Future<?> future = watchedConfigs.remove(serviceWithSource);
				if (null != future) {
					future.cancel(true);
				}
				unwatchTask.run();
				LOG.info("[Core] config {} has been cancel watched", serviceWithSource);
			}
		}
	}


	private void addPullTask(RegistryProto.ConfigTask task, long intervalMilli) {
		ConfigTaskEngine.ConfigSet configSet = getOrCreateConfigSet(task);
		if (null == configSet) {
			LOG.error("[Core] config adding pull task {}, fail to init config", task.getName());
			return;
		}
		NamedConfigCenter source = configSet.getSource();
		NamedConfigCenter target = configSet.getTarget();
		PullTask pullTask = new PullTask(source, target, task.getMatchList());
		ScheduledFuture<?> future = pullExecutor
				.scheduleWithFixedDelay(pullTask, 0, intervalMilli, TimeUnit.MILLISECONDS);
		pulledConfigs.put(task.getName(), future);
		LOG.info("[Core] config task {} has been scheduled pulled", task.getName());
	}

	private void addWatchTask(RegistryProto.ConfigTask task) {
		ConfigTaskEngine.ConfigSet registrySet = getOrCreateConfigSet(task);
		if (null == registrySet) {
			LOG.error("[Core] config adding watch task {}, fail to init config", task.getName());
			return;
		}
		NamedConfigCenter sourceRegistry = registrySet.getSource();
		NamedConfigCenter destRegistry = registrySet.getTarget();
		for (RegistryProto.ConfigMatch match : task.getMatchList()) {
			if (ConfigUtils.isEmptyMatch(match)) {
				continue;
			}
			WatchTask watchTask = new WatchTask(watchedConfigs, sourceRegistry, destRegistry, match,
					publishExecutor, watchExecutor);
			Future<?> submit = watchExecutor.schedule(watchTask, 1, TimeUnit.SECONDS);
			ConfigTaskEngine.ConfigGroupWithSource configGroupWithSource = watchTask.getGroupWithSource();
			watchedConfigs.put(configGroupWithSource, submit);
			LOG.info("[Core] config {} has been scheduled watched", configGroupWithSource);
		}
	}


	private NamedConfigCenter getSource(String taskName) {
		ConfigTaskEngine.ConfigSet configSet = taskRegistryMap.get(taskName);
		if (null == configSet) {
			return null;
		}
		return configSet.getSource();
	}

	private ConfigTaskEngine.ConfigSet getOrCreateConfigSet(RegistryProto.ConfigTask task) {
		ConfigTaskEngine.ConfigSet registrySet = taskRegistryMap.get(task.getName());
		if (null != registrySet) {
			return registrySet;
		}
		RegistryProto.ConfigEndpoint source = task.getSource();
		RegistryProto.ConfigEndpoint destination = task.getDestination();
		ConfigCenter sourceCenter = createConfig(source.getType());
		if (null == sourceCenter) {
			return null;
		}
		ConfigCenter destinationCenter = createConfig(destination.getType());
		if (null == destinationCenter) {
			return null;
		}
		sourceCenter.init(new ConfigInitRequest("", RegistryProto.ConfigEndpoint.ConfigType.unknown, source));
		destinationCenter.init(new ConfigInitRequest(source.getName(), source.getType(), destination));
		registrySet = new ConfigTaskEngine.ConfigSet(new NamedConfigCenter(
				source.getName(), source.getProductName(), sourceCenter),
				new NamedConfigCenter(destination.getName(), destination.getProductName(), destinationCenter));
		taskRegistryMap.put(task.getName(), registrySet);
		return registrySet;
	}

	private ConfigCenter createConfig(RegistryProto.ConfigEndpoint.ConfigType type) {
		Class<? extends ConfigCenter> cls = configTypeMap.get(type);
		ConfigCenter center;
		try {
			center = cls.newInstance();
		} catch (Exception e) {
			LOG.error("[Core] config fail to create config for class {}", cls.getCanonicalName(), e);
			return null;
		}
		return center;
	}

	public ConfigTaskEngine.ConfigSet getConfigSet(String taskName) {
		synchronized (configLock) {
			return taskRegistryMap.get(taskName);
		}
	}
	
	public static class ConfigGroupWithSource {

		private final String sourceName;

		private final ConfigGroup group;

		public ConfigGroupWithSource(String sourceName, ConfigGroup group) {
			this.sourceName = sourceName;
			this.group = group;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof ConfigGroupWithSource)) {
				return false;
			}
			ConfigGroupWithSource that = (ConfigGroupWithSource) o;
			return Objects.equals(sourceName, that.sourceName) &&
					Objects.equals(group, that.group);
		}

		@Override
		public int hashCode() {
			return Objects.hash(sourceName, group);
		}

		@Override
		public String toString() {
			return "ConfigGroupWithSource{" +
					"sourceName='" + sourceName + '\'' +
					", group=" + group +
					'}';
		}
	}

	public static class ConfigSet {

		private final NamedConfigCenter source;

		private final NamedConfigCenter target;

		public ConfigSet(NamedConfigCenter source,
				NamedConfigCenter target) {
			this.source = source;
			this.target = target;
		}

		public NamedConfigCenter getSource() {
			return source;
		}

		public NamedConfigCenter getTarget() {
			return target;
		}

		public void destroy() {
			source.getConfigCenter().destroy();
			target.getConfigCenter().destroy();
		}
	}
}
