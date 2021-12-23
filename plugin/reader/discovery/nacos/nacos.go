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
//@Time: 2021/11/17 23:24

package nacos

import (
	"github.com/chuntaojun/polaris-syncer/api"
)

const (
	defaultName string = "nacos"
)

func newNacosReader() *api.Reader {
	return nil
}

// NacosReader
type NacosReader struct {
	watchChs map[string]chan interface{}
}

func (nacosReader *NacosReader) Init(options map[string]string) error {
	return nil
}

// Read
//  @param query
//  @return interface{}
//  @return error
func (nacosReader *NacosReader) Read(query api.ReadRequest) (interface{}, error) {
	return nil, nil
}

// Watch
func (nacosReader *NacosReader) Watch(req api.WatchRequest) chan interface{} {
	return nil
}

// Name 返回 Reader 的名称
func (nacosReader *NacosReader) Name() string {
	return defaultName
}
