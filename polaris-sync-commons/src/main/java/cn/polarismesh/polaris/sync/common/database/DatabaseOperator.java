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

package cn.polarismesh.polaris.sync.common.database;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.sql.DataSource;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DatabaseOperator {

	private final DataSource dataSource;

	public DatabaseOperator(DataSource dataSource) {
		Objects.requireNonNull(dataSource, "datasource");
		this.dataSource = dataSource;
	}

	public <R> List<R> queryList(String sql, Object[] args, Function<Map<String, Object>, R> convert) {
		return null;
	}

	public void destroy() {
	}
}
