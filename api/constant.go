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
//@Author: springliao
//@Description:
//@Time: 2021/11/18 01:59

package api

type ComponentType string

const (
	// Nacos 组件
	ComponentForNacos       ComponentType = "nacos"
	ComponentForZookeeper   ComponentType = "zookeeper"
	ComponentForDubbo       ComponentType = "dubbo"
	ComponentForPolarisMesh ComponentType = "polarismesh"
	ComponentForEureka      ComponentType = "eureka"
	ComponentForConsul      ComponentType = "consul"
	ComponentForApollo      ComponentType = "apollo"
)

// RunMode Sinker 的运行模式，是处理服务注册数据的同步，还是处理配置数据的同步
type RunMode int16

const (
	RunDiscoveryMode RunMode = iota
	RunConfigMode
)

type Action int32

const (
	// 通用的查询服务列表动作
	CommonListServices Action = iota
	// 通用的创建服务列表动作
	CommonCreateServices
	// 通用的更新服务列表动作
	CommonUpdateServices
	// 通用的删除服务列表动作
	CommonDeleteServices

	// 通用的创建服务实例列表动作
	CommonCreateInstances
	// 通用的更新服务实例列表动作
	CommonUpdateInstances
	// 通用的删除服务实例列表动作
	CommonDeleteInstances
	// 通用的查询服务实例列表动作
	CommonListInstances
	// 通用的监听服务变更动作
	CommonWatchService
)
