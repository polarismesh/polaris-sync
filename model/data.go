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

import (
	"github.com/chuntaojun/polaris-syncer/api"
)

func NewDiscoveryReadRequest(action api.Action, filter map[string]interface{}) api.ReadRequest {
	if filter == nil {
		filter = make(map[string]interface{})
	}
	return &DiscoveryReadRequest{
		action: action,
		query:  filter,
	}
}

type DiscoveryReadRequest struct {
	action api.Action
	query  map[string]interface{}
}

func (req *DiscoveryReadRequest) Get(key string) interface{} {
	return req.query[key]
}

func (req *DiscoveryReadRequest) Action() api.Action {
	return req.action
}

func NewDiscoveryWriteRequest(action api.Action, data map[string]interface{}) api.WriteRequest {
	if data == nil {
		data = make(map[string]interface{})
	}
	return &DiscoveryWriteRequest{
		action: action,
		data:   data,
	}
}

type DiscoveryWriteRequest struct {
	action api.Action
	data   map[string]interface{}
}

//
func (req *DiscoveryWriteRequest) GetAction() api.Action {
	return req.action
}

// key 传入 key 获取对应的 value

func (req *DiscoveryWriteRequest) Value(key string) interface{} {
	return req.data[key]
}

func NewDiscoveryWatchRequest(data map[string]interface{}) api.WatchRequest {
	if data == nil {
		data = make(map[string]interface{})
	}
	return &DiscoveryWatchRequest{
		data: data,
	}
}

type DiscoveryWatchRequest struct {
	data map[string]interface{}
}

// key 传入 key 获取对应的 value

func (req *DiscoveryWatchRequest) Value(key string) interface{} {
	return req.data[key]
}

type ConfigReadRequest struct {
}

type ConfigWriteRequest struct {
	action api.Action
	data   map[string]interface{}
}

func (pwr *ConfigWriteRequest) GetAction() api.Action {
	return pwr.action
}

func (pwr *ConfigWriteRequest) Value(key string) interface{} {
	return pwr.data
}
