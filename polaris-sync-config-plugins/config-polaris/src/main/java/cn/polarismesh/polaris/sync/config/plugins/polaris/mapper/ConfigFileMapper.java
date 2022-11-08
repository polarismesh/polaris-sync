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

package cn.polarismesh.polaris.sync.config.plugins.polaris.mapper;

import java.sql.ResultSet;
import java.util.Date;

import cn.polarismesh.polaris.sync.common.database.RecordSupplier;
import cn.polarismesh.polaris.sync.config.plugins.polaris.model.ConfigFileRelease;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;

/**
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigFileMapper implements RecordSupplier<ConfigFile> {

	private static final ConfigFileMapper INSTANCE = new ConfigFileMapper();

	public static ConfigFileMapper getInstance() {
		return INSTANCE;
	}

	public String getMoreSqlTemplate(boolean first) {
		String query = "SELECT tenant_id, group_id, data_id, content, md5, encrypted_data_key, gmt_modified FROM config_info ";

		if (!first) {
			query += " WHERE gmt_modified >= ? ";
		}

		return query;
	}

	@Override
	public ConfigFile apply(ResultSet row) throws Exception {
		return ConfigFile.builder()
				.namespace(row.getString("namespace"))
				.group(row.getString("group"))
				.fileName(row.getString("file_name"))
				.content(row.getString("content"))
				.modifyTime(row.getDate("modify_time"))
				.md5(row.getString("md5"))
				.valid(1 == row.getInt("flag"))
				.build();
	}
}
