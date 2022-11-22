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

package cn.polarismesh.polaris.sync.config.plugins.nacos.mapper;

import java.sql.ResultSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import cn.polarismesh.polaris.sync.common.database.RecordSupplier;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import org.apache.commons.lang.StringUtils;

/**
 * 对应的 Nacos 配置表结构信息
 * <p>
 * CREATE TABLE `config_info` (
 * `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
 * `data_id` varchar(255) NOT NULL COMMENT 'data_id',
 * `group_id` varchar(128) DEFAULT NULL,
 * `content` longtext NOT NULL COMMENT 'content',
 * `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
 * `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 * `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
 * `src_user` text COMMENT 'source user',
 * `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
 * `app_name` varchar(128) DEFAULT NULL,
 * `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
 * `c_desc` varchar(256) DEFAULT NULL,
 * `c_use` varchar(64) DEFAULT NULL,
 * `effect` varchar(64) DEFAULT NULL,
 * `type` varchar(64) DEFAULT NULL,
 * `c_schema` text,
 * `encrypted_data_key` text NOT NULL COMMENT '秘钥',
 * PRIMARY KEY (`id`),
 * UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info';
 * <p>
 * <p>
 * CREATE TABLE `config_tags_relation` (
 * `id` bigint(20) NOT NULL COMMENT 'id',
 * `tag_name` varchar(128) NOT NULL COMMENT 'tag_name',
 * `tag_type` varchar(64) DEFAULT NULL COMMENT 'tag_type',
 * `data_id` varchar(255) NOT NULL COMMENT 'data_id',
 * `group_id` varchar(128) NOT NULL COMMENT 'group_id',
 * `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
 * `nid` bigint(20) NOT NULL AUTO_INCREMENT,
 * PRIMARY KEY (`nid`),
 * UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
 * KEY `idx_tenant_id` (`tenant_id`)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_tag_relation';
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigFileMapper implements RecordSupplier<ConfigFile> {

	private static final ConfigFileMapper INSTANCE = new ConfigFileMapper();

	public static final ConfigFileMapper getInstance() {
		return INSTANCE;
	}

	public String getMoreSqlTemplate(boolean first) {
		String query = "SELECT ci.tenant_id, ci.group_id, ci.data_id, ci.content, ci.c_desc, IFNULL(cr.tag_name, '') as tag_name, ci.md5, ci.gmt_modified "
				+ "FROM config_info ci LEFT JOIN config_tags_relation cr ON ci.tenant_id = cr.tenant_id "
				+ "AND ci.group_id = cr.group_id AND ci.data_id = cr.data_id ";

		if (!first) {
			query += " WHERE gmt_modified >= ? ";
		}

		return query;
	}

	@Override
	public ConfigFile merge(ConfigFile cur, ConfigFile pre) {
		Map<String, String> curLabels = cur.getLabels();
		Map<String, String> preLabels = pre.getLabels();
		preLabels.putAll(curLabels);
		pre.setLabels(preLabels);
		return pre;
	}

	@Override
	public ConfigFile apply(ResultSet row) throws Exception {
		Map<String, String> labels = new HashMap<>();
		String tag = row.getString("tag_name");
		if (StringUtils.isNotBlank(tag)) {
			if (StringUtils.contains(tag, "=")) {
				String[] kv = StringUtils.split(tag, "=");
				labels.put(kv[0], kv[1]);
			}
			else {
				labels.put(tag, tag);
			}
		}

		return ConfigFile.builder()
				.namespace((String) row.getString("tenant_id"))
				.group((String) row.getString("group_id"))
				.fileName((String) row.getString("data_id"))
				.beta(false)
				.content((String) row.getString("content"))
				.valid(true)
				.md5((String) row.getString("md5"))
				.modifyTime((Date) row.getDate("gmt_modified"))
				.labels(labels)
				.build();
	}
}
