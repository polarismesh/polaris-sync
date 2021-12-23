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

package transform

import (
	"fmt"

	"github.com/chuntaojun/polaris-syncer/api"
	"github.com/chuntaojun/polaris-syncer/model"
	"github.com/chuntaojun/polaris-syncer/plugin"
	polarisApi "github.com/polarismesh/polaris-go/api"
	polarisModel "github.com/polarismesh/polaris-go/pkg/model"
)

type Apply func(source interface{}) (interface{}, error)

func init() {
	plugin.RegisterTransform(newDiscoveryTransform())
}

// DiscoveryObjectToPolaris 服务对象转换为 Polaris 所需要的对象
type discoveryObjectTansform struct {
	applyFc map[string]Apply
}

// newDiscoveryTransform 命名空间的数据转换操作，当前仅支持多服务注册中心数据同步到polaris中
func newDiscoveryTransform() api.Transformer {
	return &discoveryObjectTansform{
		applyFc: map[string]Apply{
			fmt.Sprintf("%s=>%s", api.ComponentForNacos, api.ComponentForPolarisMesh):  convertNacosToPolaris,
			fmt.Sprintf("%s=>%s", api.ComponentForEureka, api.ComponentForPolarisMesh): convertEurekaToPolaris,
			fmt.Sprintf("%s=>%s", api.ComponentForConsul, api.ComponentForPolarisMesh): convertConsulToPolaris,
			fmt.Sprintf("%s=>%s", api.ComponentForDubbo, api.ComponentForPolarisMesh):  convertDubboZkToPolaris,
		},
	}
}

// Convert 执行数据转换动作
func (convert *discoveryObjectTansform) Convert(param api.TransformParam) (interface{}, error) {
	key := fmt.Sprintf("%s=>%s", param.Source, param.Target)

	transformer, ok := convert.applyFc[key]
	if !ok {
		return nil, model.ErrorNoFoundTargetTransformer
	}

	ret, err := transformer(param.Object)
	if err != nil {
		return nil, err
	}

	return ret, nil
}

// Name 插件的名字
func (convert *discoveryObjectTansform) Name() string {
	return "DiscoveryObjectTansform"
}

// convertNacosToPolaris 注册对象转换为 Polaris 的注册对象
func convertNacosToPolaris(source interface{}) (interface{}, error) {
	if nacosSvc, ok := source.(*model.Service); ok {
		fmt.Printf("%#v\n", nacosSvc)
	}
	if nacosIns, ok := source.(*model.Instance); ok {
		weight, _ := nacosIns.Property["weight"].(int)
		ttl := 5
		polarisIns := &polarisApi.InstanceRegisterRequest{
			InstanceRegisterRequest: polarisModel.InstanceRegisterRequest{
				Service:   nacosIns.Property["serviceName"].(string),
				Namespace: nacosIns.Property["namespace"].(string),
				Host:      nacosIns.Host,
				Port:      int(nacosIns.Port),
				Weight:    &weight,
				Metadata:  nacosIns.Metadata,
				TTL:       &ttl,
			},
		}

		polarisIns.SetHealthy(nacosIns.Property["healthy"].(bool))
		polarisIns.SetIsolate(!nacosIns.Property["enabled"].(bool))

		return polarisIns, nil
	}

	if nacosInsList, ok := source.([]*model.Instance); ok {
		polarisInsList := make([]polarisApi.InstanceRegisterRequest, len(nacosInsList))
		for i := range nacosInsList {
			nacosIns := nacosInsList[i]
			weight, _ := nacosIns.Property["weight"].(int)
			ttl := 5
			polarisIns := &polarisApi.InstanceRegisterRequest{
				InstanceRegisterRequest: polarisModel.InstanceRegisterRequest{
					Service:   nacosIns.Property["serviceName"].(string),
					Namespace: nacosIns.Property["namespace"].(string),
					Host:      nacosIns.Host,
					Port:      int(nacosIns.Port),
					Weight:    &weight,
					Metadata:  nacosIns.Metadata,
					TTL:       &ttl,
				},
			}
			polarisIns.SetHealthy(nacosIns.Property["healthy"].(bool))
			polarisIns.SetIsolate(!nacosIns.Property["enabled"].(bool))

			polarisInsList[i] = *polarisIns
		}

		return polarisInsList, nil
	}

	return nil, model.ErrorNoNacosDiscoveryObject
}

func convertConsulToPolaris(source interface{}) (interface{}, error) {
	return nil, nil
}

func convertEurekaToPolaris(source interface{}) (interface{}, error) {
	return nil, nil
}

func convertDubboZkToPolaris(source interface{}) (interface{}, error) {
	return nil, nil
}

// ConfigObjectToPolaris 配置对象转换为 Polaris 所需的对象
type ConfigObjectToPolaris struct {
}

func (convert *ConfigObjectToPolaris) Convert(param api.TransformParam) (interface{}, error) {

	return nil, nil
}

func (convert *ConfigObjectToPolaris) Name() string {
	return "ConfigObjectToPolaris"
}
