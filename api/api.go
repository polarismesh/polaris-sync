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
//@Time: 2021/11/17 23:19

package api

// ReadRequest
type ReadRequest interface {
	// Action
	Action() Action

	// Get
	Get(key string) interface{}
}

type WatchRequest interface {

	// Value
	//  @param key
	//  @return string
	Value(key string) interface{}
}

// Sinker 数据管道
type Sinker interface {
	// Init
	Init(RunMode, Writer, Reader) error

	// Run
	Run()

	OnError(err error)

	// Name 返回 Sinker 的名称
	Name() string
}

type WriteRequest interface {
	//
	GetAction() Action

	// key 传入 key 获取对应的 value
	Value(key string) interface{}
}

// Writer
type Writer interface {
	Init(options map[string]string) error

	// Write
	//  @param arg
	//  @return bool
	//  @return error
	Write(arg WriteRequest) (bool, error)

	// Name 返回 Writer 的名称
	Name() string
}

// Reader
type Reader interface {
	Init(options map[string]string)

	// Read
	Read(req ReadRequest) (interface{}, error)

	// Watch
	Watch(req WatchRequest) chan interface{}

	// Name 返回 Reader 的名称
	Name() string
}

type TransformParam struct {
	// 原始数据类型
	Source string
	// 目标数据类型
	Target string
	// 原始数据
	Object interface{}
}

// Transformer 数据转换器
type Transformer interface {

	// Convert
	Convert(param TransformParam) (interface{}, error)

	// Name 返回 Transformer 的名称
	Name() string
}
