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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

	public <R> List<R> queryList(String sql, Object[] args, RecordSupplier<R> convert) throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			PreparedStatement statement = connection.prepareStatement(sql);

			if (Objects.nonNull(args)) {
				for (int i = 0; i < args.length; i ++) {
					statement.setObject(i + 1, args[i]);
				}
			}

			ResultSet rs = statement.executeQuery();

			Map<R, R> records = new HashMap<>();

			List<R> list = new ArrayList<>();
			while (rs.next()) {
				R cur = convert.apply(rs);
				R r = cur;
				if (Objects.nonNull(records.get(cur))) {
					r = convert.merge(cur, records.get(cur));
				}
				list.add(r);
				records.put(r, r);
			}

			return list;
		}
	}

	public void destroy() {
	}
}
