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

package cn.polarismesh.polaris.sync.extension.utils;

import cn.polarismesh.polaris.sync.extension.registry.Service;
import com.google.protobuf.BoolValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.UInt32Value;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.Builder;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto;

public class ResponseUtils {

    public static DiscoverResponse toRegistryCenterException(Service service) {
        return toDiscoverResponse(service, StatusCodes.SERVER_EXCEPTION, DiscoverResponseType.INSTANCE).build();
    }

    public static DiscoverResponse toRegistryClientException(Service service) {
        return toDiscoverResponse(service, StatusCodes.CLIENT_EXCEPTION, DiscoverResponseType.INSTANCE).build();
    }

    public static DiscoverResponse.Builder toDiscoverResponse(Service service, int code, DiscoverResponseType type) {
        Builder builder = DiscoverResponse.newBuilder();
        builder.setService(
                ServiceProto.Service.newBuilder().setName(toStringValue(service.getService()))
                        .setNamespace(toStringValue(service.getNamespace())).build());
        builder.setCode(toUInt32Value(code));
        builder.setType(type);
        return builder;
    }


    public static StringValue toStringValue(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    public static UInt32Value toUInt32Value(int value) {
        return UInt32Value.newBuilder().setValue(value).build();
    }

    public static BoolValue toBooleanValue(boolean value) {
        return BoolValue.newBuilder().setValue(value).build();
    }
}
