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

package cn.polarismesh.polaris.sync.core.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.CRC32;

import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.Registry;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonFormat.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;

public class CommonUtils {

	private static final Logger LOG = LoggerFactory.getLogger(CommonUtils.class);

	public static <T> T parseFromContent(byte[] strBytes, Supplier<Message.Builder> supplier) throws IOException {
		try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(strBytes), StandardCharsets.UTF_8)) {
			Message.Builder builder = supplier.get();
			Parser parser = JsonFormat.parser();
			parser.merge(reader, builder);
			return (T) builder.build();
		}
	}

	public static byte[] marshal(Message config) throws IOException {
		Printer printer = JsonFormat.printer();
		String val = printer.print(config);
		return val.getBytes();
	}

	public static boolean verifyHealthCheck(ModelProto.HealthCheck healthCheck) {
		return true;
	}

	public static boolean verifyReport(Registry registry) {
		ModelProto.Report report = registry.getReport();
		if (null == report) {
			return true;
		}
		List<ModelProto.ReportTarget> targetsList = report.getTargetsList();
		for (ModelProto.ReportTarget reportTarget : targetsList) {
			if (!reportTarget.getEnable()) {
				continue;
			}
			if (reportTarget.getType() == ModelProto.ReportTarget.TargetType.unknown) {
				LOG.error("[Core] target type name is unknown");
				return false;
			}
		}
		return true;
	}

	public static boolean verifyMethod(boolean hasTask, List<ModelProto.Method> methods) {
		boolean hasMethod = false;
		if (!CollectionUtils.isEmpty(methods)) {
			Set<ModelProto.Method.MethodType> methodTypes = new HashSet<>();
			for (ModelProto.Method method : methods) {
				if (ModelProto.Method.MethodType.unknown.equals(method.getType())) {
					LOG.error("[Core] unknown method type");
					return false;
				}
				if (methodTypes.contains(method.getType())) {
					LOG.error("[Core] duplicate method type");
					return false;
				}
				methodTypes.add(method.getType());
				if (method.getEnable()) {
					hasMethod = true;
				}
			}
		}
		if (hasTask && !hasMethod) {
			LOG.error("[Core] at least specific one sync method for tasks");
			return false;
		}

		return true;
	}

	public static boolean methodsChanged(List<ModelProto.Method> oldMethods, List<ModelProto.Method> newMethods) {
		if (CollectionUtils.isEmpty(oldMethods)) {
			return !CollectionUtils.isEmpty(newMethods);
		}
		if (CollectionUtils.isEmpty(newMethods)) {
			return !CollectionUtils.isEmpty(oldMethods);
		}
		return !(oldMethods.size() == newMethods.size());
	}

	public static long calcCrc32(byte[] strBytes) {
		CRC32 crc32 = new CRC32();
		crc32.update(strBytes);
		return crc32.getValue();
	}

}
