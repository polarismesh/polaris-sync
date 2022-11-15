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
import java.util.HashMap;
import java.util.Map;

import cn.polarismesh.polaris.sync.common.database.RecordSupplier;
import cn.polarismesh.polaris.sync.config.plugins.polaris.model.ConfigFileRelease;
import org.apache.commons.lang.StringUtils;

/**
 * CREATE TABLE `config_file_release`
 * (
 *     `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
 *     `name`        varchar(128)             DEFAULT NULL COMMENT '发布标题',
 *     `namespace`   varchar(64)     NOT NULL COMMENT '所属的namespace',
 *     `group`       varchar(128)    NOT NULL COMMENT '所属的文件组',
 *     `file_name`   varchar(128)    NOT NULL COMMENT '配置文件名',
 *     `content`     longtext        NOT NULL COMMENT '文件内容',
 *     `comment`     varchar(512)             DEFAULT NULL COMMENT '备注信息',
 *     `md5`         varchar(128)    NOT NULL COMMENT 'content的md5值',
 *     `version`     int(11)         NOT NULL COMMENT '版本号，每次发布自增1',
 *     `flag`        tinyint(4)      NOT NULL DEFAULT '0' COMMENT '是否被删除',
 *     `create_time` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 *     `create_by`   varchar(32)              DEFAULT NULL COMMENT '创建人',
 *     `modify_time` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
 *     `modify_by`   varchar(32)              DEFAULT NULL COMMENT '最后更新人',
 *     PRIMARY KEY (`id`),
 *     UNIQUE KEY `uk_file` (`namespace`, `group`, `file_name`),
 *     KEY `idx_modify_time` (`modify_time`)
 * ) ENGINE = InnoDB
 *   AUTO_INCREMENT = 1 COMMENT = '配置文件发布表';
 *
 * CREATE TABLE `config_file_tag`
 * (
 *     `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
 *     `key`         varchar(128)    NOT NULL COMMENT 'tag 的键',
 *     `Value`       varchar(128)    NOT NULL COMMENT 'tag 的值',
 *     `namespace`   varchar(64)     NOT NULL COMMENT '所属的namespace',
 *     `group`       varchar(128)    NOT NULL DEFAULT '' COMMENT '所属的文件组',
 *     `file_name`   varchar(128)    NOT NULL COMMENT '配置文件名',
 *     `create_time` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 *     `create_by`   varchar(32)              DEFAULT NULL COMMENT '创建人',
 *     `modify_time` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
 *     `modify_by`   varchar(32)              DEFAULT NULL COMMENT '最后更新人',
 *     PRIMARY KEY (`id`),
 *     UNIQUE KEY `uk_tag` (
 *                          `key`,
 *                          `Value`,
 *                          `namespace`,
 *                          `group`,
 *                          `file_name`
 *         ),
 *     KEY `idx_file` (`namespace`, `group`, `file_name`)
 * ) ENGINE = InnoDB
 *   AUTO_INCREMENT = 1 COMMENT = '配置文件标签表';
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigFileReleaseMapper implements RecordSupplier<ConfigFileRelease> {

	private static final ConfigFileReleaseMapper INSTANCE = new ConfigFileReleaseMapper();

	public static ConfigFileReleaseMapper getInstance() {
		return INSTANCE;
	}

	@Override
	public String getMoreSqlTemplate(boolean first) {
		String query = "SELECT cr.id, cr.name, cr.namespace, cr.`group`, cr.file_name, cr.content, IFNULL(cr.comment, ''), ct.key, ct.value, "
				+ "cr.md5, cr.version, cr.modify_time, cr.flag FROM config_file_release cr LEFT JOIN config_file_tag ct "
				+ "ON cr.namespace = ct.namespace AND cr.`group` = ct.`group` AND cr.file_name = ct.file_name ";
		if (!first) {
			query += " WHERE cr.modify_time > FROM_UNIXTIME(?)";
		}

		return query;
	}

	@Override
	public ConfigFileRelease apply(ResultSet t) throws Exception {
		Map<String, String> labels = new HashMap<>();

		String key = t.getString("key");
		String value = t.getString("value");

		if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
			labels.put(t.getString("key"), t.getString("value"));
		}

		return ConfigFileRelease.builder()
				.id(t.getLong("id"))
				.namespace(t.getString("namespace"))
				.group(t.getString("group"))
				.fileName(t.getString("file_name"))
				.content(t.getString("content"))
				.modifyTime(t.getDate("modify_time"))
				.md5(t.getString("md5"))
				.version(t.getLong("version"))
				.valid(0 == t.getInt("flag"))
				.labels(labels)
				.build();
	}

	@Override
	public ConfigFileRelease merge(ConfigFileRelease cur, ConfigFileRelease pre) {
		Map<String, String> curLabels = cur.getLabels();
		Map<String, String> preLabels = pre.getLabels();
		preLabels.putAll(curLabels);
		pre.setLabels(preLabels);
		return pre;
	}
}
