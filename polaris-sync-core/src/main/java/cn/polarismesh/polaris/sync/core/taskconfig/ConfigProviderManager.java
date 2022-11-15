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

package cn.polarismesh.polaris.sync.core.taskconfig;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import cn.polarismesh.polaris.sync.core.utils.CommonUtils;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigListener;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProvider;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProviderFactory;
import com.google.protobuf.Message;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigProviderManager<M extends Message, T extends SyncProperties> {

	private static final Logger LOG = LoggerFactory.getLogger(ConfigProviderManager.class);
	private final T properties;

	private final BackupConfig backupConfig;

	private ConfigProvider<M> provider;

	private Supplier<Message.Builder> supplier;

	public ConfigProviderManager(List<ConfigProviderFactory> factories, T properties, Supplier<Message.Builder> supplier) throws Exception {
		this.properties = properties;
		this.supplier = supplier;
		this.backupConfig = new BackupConfig(properties.getConfigBackupPath());

		init(factories);
	}

	private void init(List<ConfigProviderFactory> factories) throws Exception {
		String providerType = properties.getConfigProvider();
		for (ConfigProviderFactory item : factories) {
			if (Objects.equals(item.name(), providerType)) {
				provider = item.create();
				break;
			}
		}

		Objects.requireNonNull(provider, "ConfigProvider");
		provider.init(properties.getOptions(), supplier);
		provider.addListener(backupConfig);
	}

	public void addListener(ConfigListener listener) {
		provider.addListener(new WrapperConfigListener(listener));
	}

	public M getConfig() {
        M config = provider.getConfig();
		if (config == null) {
			return (M) backupConfig.getBackup();
		}
		return config;
	}

	public void destroy() {
		if (provider != null) {
			provider.close();
		}
	}

	private class BackupConfig<T extends Message> implements ConfigListener<T> {

		private final File backup;

		private BackupConfig(String backup) {
			this.backup = new File(backup);
		}

		@Override
		public void onChange(T registry) {
			try {
				byte[] ret = CommonUtils.marshal(registry);
				FileUtils.writeByteArrayToFile(backup, ret);
			}
			catch (IOException e) {
				LOG.error("[BackupConfig] save backup file", e);
			}
		}

		private T getBackup() {
			try {
				byte[] ret = FileUtils.readFileToByteArray(backup);
				return CommonUtils.parseFromContent(ret, supplier);
			}
			catch (IOException e) {
				LOG.error("[BackupConfig] get backup file", e);
				return null;
			}
		}
	}

	private static class WrapperConfigListener<M extends Message> implements ConfigListener<M> {

		private final AtomicReference<M> lastVal = new AtomicReference<>();

		private final ConfigListener listener;

		private WrapperConfigListener(ConfigListener listener) {
			this.listener = listener;
		}

		@Override
		public void onChange(M registry) {
			try {
				listener.onChange(registry);
				lastVal.set(registry);
			}
			catch (Throwable ex) {
                M old = lastVal.get();
				if (old != null) {
					listener.onChange(old);
				}
			}
		}
	}
}
