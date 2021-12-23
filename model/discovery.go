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

// Service 通用的服务数据对象描述
type Service struct {
	Namespace string
	Name      string
	Metadata  map[string]string
	Property  map[string]interface{}
}

// Instance 通用的服务实例数据对象描述
type Instance struct {
	Host     string
	Port     int
	Metadata map[string]string
	Property map[string]interface{}
}

// ServiceChangeEvent 服务变更事件
type ServiceChangeEvent struct {
	Service         *Service
	AddInstances    []*Instance
	DeleteInstances []*Instance
	UpdateInstances []*Instance
}
