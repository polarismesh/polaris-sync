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
//@Time: 2021/11/18 02:03

package nacos

import "github.com/chuntaojun/polaris-syncer/api"

// NacosReader
type NacosReader struct {
	v1Reader *NacosV1Reader
	v2Reader *NacosV2Reader
}

// Read
//  @param query
//  @return interface{}
//  @return error
func (nacosReader *NacosReader) Read(query api.ReadRequest) (interface{}, error) {
	return nil, nil
}

// NacosV1Reader
type NacosV1Reader struct {
}

// Read
//  @param query
//  @return interface{}
//  @return error
func (nacosReader *NacosV1Reader) Read(query api.ReadRequest) (interface{}, error) {
	return nil, nil
}

// NacosV2Reader
type NacosV2Reader struct {
}

// Read
//  @param query
//  @return interface{}
//  @return error
func (nacosReader *NacosV2Reader) Read(query api.ReadRequest) (interface{}, error) {
	return nil, nil
}
