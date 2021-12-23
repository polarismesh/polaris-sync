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

package plugin

import "github.com/chuntaojun/polaris-syncer/api"

var (
	writerPluginSolt    map[string]api.Writer
	readerPluginSolt    map[string]api.Reader
	sinkerPluginSolt    map[string]api.Sinker
	transformPluginSolt map[string]api.Transformer
)

func RegisterWriterSupplier(name string, supplier func() api.Writer) (bool, error) {
	return false, nil
}

func RegisterSinkerSupplier(name string, supplier func() api.Sinker) (bool, error) {
	return false, nil
}

func RegisterReaderSupplier(name string, reader func() api.Reader) (bool, error) {
	return false, nil
}

func RegisterTransform(transform api.Transformer) (bool, error) {
	return false, nil
}

func GetWriterPlugin(name string) api.Writer {
	return nil
}

func GetReaderPlugin(name string) api.Reader {
	return nil
}

func GetSinkerPlugin(name string) api.Sinker {
	return nil
}

func GetTransformerPlugin(name string) api.Transformer {
	return nil
}
