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
//@Time: 2021/11/18 02:04

package polaris

import (
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/chuntaojun/polaris-syncer/api"
	"github.com/chuntaojun/polaris-syncer/common"
	"github.com/chuntaojun/polaris-syncer/model"
	polarisApi "github.com/polarismesh/polaris-go/api"
	polarisModel "github.com/polarismesh/polaris-go/pkg/model"
)

const (
	defaultName string = "PolarisWriter"

	polarisConfigFile string = "polaris.yaml"

	useHeartbeatToKeepNew string = "open_polaris_heartbeat"
)

func newPolarisWriter() (api.Writer, error) {
	return &PolarisWriter{
		lock:           &sync.RWMutex{},
		heartbeatWorks: make(map[string]*polarisHeartbeatTask),
		beatTaskWheel: common.NewTimeWheel(func(opts *common.Options) {
			opts.Interval = time.Duration(time.Second)
			opts.SlotNum = 128
		}),
	}, nil
}

type PolarisWriter struct {
	providerApi    polarisApi.ProviderAPI
	lock           *sync.RWMutex
	heartbeatWorks map[string]*polarisHeartbeatTask
	beatTaskWheel  *common.HashTimeWheel
	useHeartbeat   bool
}

func (polarisWriter *PolarisWriter) Init(options map[string]string) error {
	cfgStr, ok := options[polarisConfigFile]
	if !ok {
		return model.ErrorNoFoundPolarisConfigFile
	}

	sdkCtx, err := polarisApi.InitContextByStream([]byte(cfgStr))
	if err != nil {
		return err
	}
	polarisWriter.providerApi = polarisApi.NewProviderAPIByContext(sdkCtx)

	polarisWriter.useHeartbeat = strings.Compare(options[useHeartbeatToKeepNew], "true") == 0

	return nil
}

// 执行写操作
func (polarisWriter *PolarisWriter) Write(arg api.WriteRequest) (bool, error) {
	polarisRegister, ok := arg.Value("registerVal").(*polarisApi.InstanceRegisterRequest)
	if !ok {
		return false, model.ErrorNoPolarisDiscoveryObject
	}
	switch arg.GetAction() {

	// 服务相关逻辑处理
	case api.CommonCreateServices:
		return polarisWriter.onServicesCreate(polarisRegister)
	case api.CommonDeleteServices:
		return polarisWriter.onServicesDelete(polarisRegister)
	case api.CommonUpdateServices:
		return polarisWriter.onServicesUpdate(polarisRegister)

	// 实例相关逻辑处理
	case api.CommonCreateInstances:
		return polarisWriter.onInstancesCreate(arg.Value(model.InstanceListKey))
	case api.CommonUpdateInstances:
		return polarisWriter.onInstancesUpdate(polarisRegister)
	case api.CommonDeleteInstances:
		return polarisWriter.onInstancesDelete(polarisRegister)
	}
	return false, model.ErrorUnkonwAction
}

func (polarisWriter *PolarisWriter) Name() string {
	return defaultName
}

// onServicesCreate 处理 service 的创建
func (polarisWriter *PolarisWriter) onServicesCreate(polarisRegister *polarisApi.InstanceRegisterRequest) (bool, error) {
	return true, nil
}

// onServicesUpdate 处理 service 的更新
func (polarisWriter *PolarisWriter) onServicesUpdate(polarisRegister *polarisApi.InstanceRegisterRequest) (bool, error) {
	return true, nil
}

// onServicesDelete 处理 service 的删除
func (polarisWriter *PolarisWriter) onServicesDelete(polarisRegister *polarisApi.InstanceRegisterRequest) (bool, error) {

	return true, nil
}

// onInstancesCreate 处理 instance 的创建（注册）
func (polarisWriter *PolarisWriter) onInstancesCreate(args interface{}) (bool, error) {
	instances, ok := args.([]*polarisApi.InstanceRegisterRequest)
	if !ok {
		return false, nil
	}

	providerApi := polarisWriter.providerApi
	for i := range instances {
		ins := instances[i]
		resp, err := providerApi.Register(ins)
		if err != nil {
			return false, err
		}

		if resp.Existed {
			return true, nil
		}

		if polarisWriter.useHeartbeat {
			polarisWriter.doHeartbeat(resp.InstanceID, ins)
		}
	}

	return true, nil
}

// onInstancesUpdate 处理 instance 的更新
func (polarisWriter *PolarisWriter) onInstancesUpdate(polarisRegister *polarisApi.InstanceRegisterRequest) (bool, error) {
	return false, model.ErrorNoSupport
}

// onInstancesDelete 处理 instance 的删除（反注册）
func (polarisWriter *PolarisWriter) onInstancesDelete(polarisRegister *polarisApi.InstanceRegisterRequest) (bool, error) {

	return true, nil
}

// createNamespaceIfAbsent 创建 namespace
func (polarisWriter *PolarisWriter) createNamespaceIfAbsent(namespace string) (bool, error) {

	return true, nil
}

// doHeartbeat 执行心跳逻辑动作
func (polarisWriter *PolarisWriter) doHeartbeat(id string, ins *polarisApi.InstanceRegisterRequest) {
	beat := &polarisApi.InstanceHeartbeatRequest{
		InstanceHeartbeatRequest: polarisModel.InstanceHeartbeatRequest{
			Service:    ins.Service,
			Namespace:  ins.Namespace,
			InstanceID: id,
			Host:       ins.Host,
			Port:       ins.Port,
		},
	}

	// 执行心跳任务
	lock := polarisWriter.lock
	lock.Lock()
	defer lock.Unlock()

	key := fmt.Sprintf("%s:%s:%s", ins.Namespace, ins.Service, id)
	if _, exist := polarisWriter.heartbeatWorks[key]; exist {
		return
	}
	task := &polarisHeartbeatTask{
		provider:  polarisWriter.providerApi,
		heartbeat: beat,
	}

	polarisWriter.heartbeatWorks[key] = task
	polarisWriter.beatTaskWheel.ScheduleExec(task, time.Duration(3*time.Second), time.Duration(5*time.Second))
}

type polarisHeartbeatTask struct {
	future    common.Future
	provider  polarisApi.ProviderAPI
	heartbeat *polarisApi.InstanceHeartbeatRequest
}

func (beatTask *polarisHeartbeatTask) Run() {
	if err := beatTask.provider.Heartbeat(beatTask.heartbeat); err != nil {

	}
}
