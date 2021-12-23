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

package core

import (
	"context"

	"github.com/chuntaojun/polaris-syncer/model"
	"github.com/chuntaojun/polaris-syncer/store"
)

type Executor interface {
	Init()

	Exec(ctx context.Context, job *model.Job)
}

type baseExecutor struct {
	storeOp store.SyncStore
}

// DoExec 真正执行任务处理
//  @receiver executor
//  @param ctx
//  @param job
func (executor *baseExecutor) DoExec(ctx context.Context, job *model.Job) {
	go func(ctx context.Context) {
		for {
			select {
			case <-ctx.Done():
				// job stop
			case st := <-job.WatchJobStatus():
				// 更新任务的状态
				executor.storeOp.UpdateTask()

				if st == model.JobStoped {
					job.Cancel()
				}
			}
		}
	}(ctx)
}

type namingExecutor struct {
	*baseExecutor
}

// DoPre 前置操作
//  @receiver executor
//  @param ctx
//  @param job
func (executor *namingExecutor) DoPre(ctx context.Context, job *model.Job) {

}

func (executor *namingExecutor) Exec(ctx context.Context, job *model.Job) {
	executor.DoPre(ctx, job)
	executor.DoExec(ctx, job)
}

type configExecutor struct {
	*baseExecutor
}

// DoPre 前置操作
//  @receiver executor
//  @param ctx
//  @param job
func (executor *configExecutor) DoPre(ctx context.Context, job *model.Job) {

}

func (executor *configExecutor) Exec(ctx context.Context, job *model.Job) {
	executor.DoPre(ctx, job)
	executor.DoExec(ctx, job)
}
