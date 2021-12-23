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
	"context"
	"io"

	"github.com/chuntaojun/polaris-syncer/api"
)

type ParamEntry map[string]string

// JobConf 任务配置表
type JobConf struct {
	// 配置唯一标识
	ID string `json:"id" xml:"id"`
	// 任务类型
	JobType string `json:"job_type" xml:"job_type"`
	// 任务运行时态的配置数据
	JobOptions map[string]string `json:"job_options" xml:"job_options"`
	// 数据源
	Source api.ComponentType `json:"source" xml:"source"`
	// 数据源的相关配置信息
	SourceOptions map[string]string `json:"source_options" xml:"source_options"`
	// 数据目标
	Dest api.ComponentType `json:"dest" xml:"dest"`
	// 数据目标相关配置信息
	DestOptions map[string]string `json:"dest_options" xml:"dest_options"`
}

func (jobConf *JobConf) NewJob() *Job {
	return nil
}

// 运行的任务描述
type Job struct {
	io.Closer
	Cancel context.CancelFunc
	// statusCh 任务状态变更 chan 通道
	statusCh chan JobStatus
	// 当前任务状态
	status JobStatus
	// 任务的配置数据信息
	Task JobConf
	// 任务的数据中间流转管道
	Sinker api.Sinker
	// 任务的数据读取源
	Reader api.Reader
	// 任务的数据写入源
	Writer api.Writer
}

func (job *Job) WatchJobStatus() <-chan JobStatus {
	return job.statusCh
}

func (job *Job) JobStatus() JobStatus {
	return job.status
}
