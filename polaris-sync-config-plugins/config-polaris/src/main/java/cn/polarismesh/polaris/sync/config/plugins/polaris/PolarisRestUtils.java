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

package cn.polarismesh.polaris.sync.config.plugins.polaris;

import cn.polarismesh.polaris.sync.configfile.pb.ConfigFileProto;
import cn.polarismesh.polaris.sync.extension.config.ConfigFile;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter;


/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class PolarisRestUtils {

	private static final Logger LOG = LoggerFactory.getLogger(PolarisRestUtils.class);

	public static void publishConfigFile(ConfigFile file) {

	}


	private static void createTempConfigFile(ConfigFile file) {

	}

	private static void releaseConfigFile(ConfigFileProto.ConfigFileRelease file) {
		JsonFormat.Printer printer = JsonFormat.printer();
		try {
			String val = printer.print(file);
		}
		catch (InvalidProtocolBufferException e) {
			throw new RuntimeException(e);
		}
	}

}
