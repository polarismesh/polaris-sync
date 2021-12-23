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

package local

import (
	"context"
	"sync"
	"time"

	"github.com/chuntaojun/polaris-syncer/api"
	"github.com/chuntaojun/polaris-syncer/common/log"
	"github.com/chuntaojun/polaris-syncer/model"
	"github.com/chuntaojun/polaris-syncer/plugin"
)

type watchFunc func(ctx context.Context, ch chan interface{})

const (
	defaultName string = "LocalSinker"
)

// localSink 本地Sinker通道，利用golang的chan机制
type localSink struct {
	in               chan interface{}
	out              chan interface{}
	convert          api.Transformer
	runMode          api.RunMode
	writer           api.Writer
	reader           api.Reader
	lock             *sync.RWMutex
	services         map[string]*model.Service
	watchTaskCancels map[string]context.CancelFunc

	discoveryTransformer api.Transformer
	configTransformer    api.Transformer
}

// Init
func (sink *localSink) Init(mode api.RunMode, writer api.Writer, reader api.Reader) error {
	sink.runMode = mode
	sink.writer = writer
	sink.reader = reader

	sink.discoveryTransformer = plugin.GetTransformerPlugin("discovery")
	sink.configTransformer = plugin.GetTransformerPlugin("config")
	return nil
}

func (sink *localSink) Run() {
	if sink.runMode == api.RunDiscoveryMode {
		sink.runWithDiscovery()
	} else {
		sink.runWithConfig()
	}
}

func (sink *localSink) runWithDiscovery() {
	ticker := time.NewTicker(time.Duration(30 * time.Second))
	go func() {
		for {
			select {
			case <-ticker.C:
				// 第一步，执行一次 ListService 逻辑
				ret, err := sink.reader.Read(model.NewDiscoveryReadRequest(api.CommonListServices, nil))
				if err != nil {
					sink.OnError(err)
					continue
				}
				services, ok := ret.([]*model.Service)
				if !ok {
					sink.OnError(nil)
					continue
				}

				add, delete := sink.diffServices(services)
				if ok := sink.handlerServiceAdd(add); !ok {

				}
				if ok := sink.handlerServiceRemove(delete); !ok {

				}
			}
		}
	}()
}

func (sink *localSink) runWithConfig() {

}

func (sink *localSink) OnError(err error) {
	log.SinkerLog.Error("sink OnError : %s", err)
}

// Name 返回 Sinker 的名称
func (sink *localSink) Name() string {
	return defaultName
}
