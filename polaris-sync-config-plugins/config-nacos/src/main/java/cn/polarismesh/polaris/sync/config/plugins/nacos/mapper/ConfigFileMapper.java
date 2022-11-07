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

import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import cn.polarismesh.polaris.sync.extension.config.RecordSupplier;

/**
 * 对应的 Nacos 配置表结构信息
 *
 * CREATE TABLE `config_info` (
 *   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
 *   `data_id` varchar(255) NOT NULL COMMENT 'data_id',
 *   `group_id` varchar(128) DEFAULT NULL,
 *   `content` longtext NOT NULL COMMENT 'content',
 *   `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
 *   `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 *   `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
 *   `src_user` text COMMENT 'source user',
 *   `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
 *   `app_name` varchar(128) DEFAULT NULL,
 *   `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
 *   `c_desc` varchar(256) DEFAULT NULL,
 *   `c_use` varchar(64) DEFAULT NULL,
 *   `effect` varchar(64) DEFAULT NULL,
 *   `type` varchar(64) DEFAULT NULL,
 *   `c_schema` text,
 *   `encrypted_data_key` text NOT NULL COMMENT '秘钥',
 *   PRIMARY KEY (`id`),
 *   UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info';
 *
 *
 * CREATE TABLE `config_tags_relation` (
 *   `id` bigint(20) NOT NULL COMMENT 'id',
 *   `tag_name` varchar(128) NOT NULL COMMENT 'tag_name',
 *   `tag_type` varchar(64) DEFAULT NULL COMMENT 'tag_type',
 *   `data_id` varchar(255) NOT NULL COMMENT 'data_id',
 *   `group_id` varchar(128) NOT NULL COMMENT 'group_id',
 *   `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
 *   `nid` bigint(20) NOT NULL AUTO_INCREMENT,
 *   PRIMARY KEY (`nid`),
 *   UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
 *   KEY `idx_tenant_id` (`tenant_id`)
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
		String query = "SELECT tenant_id, group_id, data_id, content, md5, encrypted_data_key, gmt_modified FROM config_info ";

		if (!first) {
			query += " WHERE gmt_modified >= ? ";
		}

		return query;
	}

	@Override
	public ConfigFile apply(Map<String, Object> row) {
		ConfigFile file = ConfigFile.builder()
				.namespace((String) row.get("tenant_id"))
				.group((String) row.get("group_id"))
				.fileName((String) row.get("data_id"))
				.beta(false)
				.content((String) row.get("content"))
				.valid(true)
				.md5((String) row.get("md5"))
				.modifyTime((Date) row.get("gmt_modified"))
				.build();
		return file;
	}
}
