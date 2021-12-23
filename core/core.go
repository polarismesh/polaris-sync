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
//@Time: 2021/11/18 02:05

package core

import (
	"context"
	"sync"

	"github.com/chuntaojun/polaris-syncer/model"
	"github.com/chuntaojun/polaris-syncer/store"
)

// CoreServer 核心逻辑 server
type CoreServer interface {

	// Init
	Init()

	// Exec
	//  @param job
	//  @return error
	Exec(job *model.Job) error

	// JobStatus
	//  @param id
	//  @return model.JobStatus
	JobStatus(id string) model.JobStatus

	// Destory
	//  @return error
	Destory() error
}

// coreServerImpl
type coreServerImpl struct {
	lock    *sync.RWMutex
	jobs    map[string]*model.Job
	storeOp store.SyncStore
}

func (svr *coreServerImpl) Init() {

}

func (svr *coreServerImpl) Exec(job *model.Job) error {

	// 填充 job 的详细信息
	// 1. 填充 reader 的具体实现
	// 2. 填充 writer 的具体实现
	if err := svr.fullJobDetail(job); err != nil {
		return err
	}

	subCtx, cancel := context.WithCancel(context.Background())
	job.Cancel = cancel
	// 保存当前任务的状态

	executor := svr.findExecutor(job)
	if executor == nil {
		return nil
	}

	executor.Exec(subCtx, job)

	return nil
}

func (svr *coreServerImpl) fullJobDetail(job *model.Job) error {
	return nil
}

func (svr *coreServerImpl) findExecutor(job *model.Job) Executor {
	return nil
}

func (svr *coreServerImpl) JobStatus(id string) model.JobStatus {
	svr.lock.RLock()
	defer svr.lock.RUnlock()

	if job, ok := svr.jobs[id]; ok {
		return job.JobStatus()
	}

	return model.JobUnKnown
}

func (svr *coreServerImpl) Destory() error {
	svr.lock.Lock()
	defer svr.lock.Unlock()

	for _, job := range svr.jobs {
		_ = job.Close()
	}

	return nil
}
