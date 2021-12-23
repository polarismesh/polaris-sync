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
//@Author: chuntaojun <liaochuntao@live.com>
//@Description:
//@Time: 2021/12/22 02:05

package log

var (
	CoreLog   Logger = NewConsoleLogger("core")
	WriterLog Logger = NewConsoleLogger("writer")
	ReaderLog Logger = NewConsoleLogger("reader")
	SinkerLog Logger = NewConsoleLogger("sinker")
)
