// Tencent is pleased to support the open source community by making polaris-go available.
//
// Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissionsr and limitations under the License.
//

package model

// JobStatus 任务的状态值
type JobStatus int32

const (
	// 未知状态
	JobUnKnown JobStatus = -1
	// 正在运行
	JobRunning JobStatus = 200000
	// 任务等待调度
	JobPending JobStatus = 200003
	// 任务正在构建
	JobBuilding JobStatus = 200006
	// 任务已暂停
	JobStoped JobStatus = 500009
	// 任务奔溃无法恢复
	JobCrash JobStatus = 500012
)

const (
	// 服务列表参数 key
	ServiceListKey string = "serviceList"
	// 实例列表参数 key
	InstanceListKey string = "instanceList"
	// 监听服务参数 key
	WatchServiceKey string = "watchService"

	// 命名空间名称参数 key
	CommonNamespaceKey string = "namespace"
	// 服务名称参数 key
	CommonServiceNameKey string = "service"

	// 北极星实例端口监听协议字段name
	PolarisProtocolKey string = "protocol"
	// 北极星实例版本字段name
	PolarisVersionKey string = "version"

	// Nacos实例单元化信息字段name
	NacosClusterKey string = "cluster"
	// Nacos服务分组字段name
	NacosGroupKey string = "group"
)
