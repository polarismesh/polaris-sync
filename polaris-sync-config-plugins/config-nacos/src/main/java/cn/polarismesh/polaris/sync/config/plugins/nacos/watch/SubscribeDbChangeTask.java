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

package cn.polarismesh.polaris.sync.config.plugins.nacos.watch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import cn.polarismesh.polaris.sync.extension.config.RecordInfo;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SubscribeDbChangeTask<T extends RecordInfo> implements Runnable {

	private boolean first = true;

	private Date lastUpdateTime;

	private final Map<String, Object> items = new ConcurrentHashMap<>();

	private final String name;

	private final Function<Date, List<T>> pullDataAction;

	private final Set<Consumer<ChangeEvent<T>>> consumers = new HashSet<>();


	public SubscribeDbChangeTask(String name, Function<Date, List<T>> pullDataAction, Consumer<ChangeEvent<T>> ...consumers) {
		this.name = name;
		this.pullDataAction = pullDataAction;
		this.consumers.addAll(Arrays.asList(consumers));
	}

	@Override
	public void run() {
		if (first) {
			lastUpdateTime = new Date(0);
		}
		first = false;

		try {
			List<T> result = pullDataAction.apply(lastUpdateTime);

			List<T> add = new ArrayList<>();
			List<T> update = new ArrayList<>();
			List<T> remove = new ArrayList<>();

			for (T t : result) {
				String key = t.keyInfo();
				if (!t.isValid()) {
					remove.add(t);
					items.remove(key);
					continue;
				}

				if (!items.containsKey(key)) {
					add.add(t);
				}
				update.add(t);
				items.put(key, t);
			}

			ChangeEvent<T> event = new ChangeEvent<>(add, update, remove);

			for (Consumer<ChangeEvent<T>> consumer : consumers) {
				consumer.accept(event);
			}
		}
		catch (Throwable ex) {
		}
	}

	public static class ChangeEvent<T extends RecordInfo> {
		private List<T> add = Collections.emptyList();
		private List<T> update = Collections.emptyList();
		private List<T> remove = Collections.emptyList();

		ChangeEvent(List<T> add, List<T> update, List<T> remove) {
			this.add = add;
			this.update = update;
			this.remove = remove;
		}

		public List<T> getAdd() {
			return add;
		}

		public List<T> getUpdate() {
			return update;
		}

		public List<T> getRemove() {
			return remove;
		}
	}
}
