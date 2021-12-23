//@Author: chuntaojun <liaochuntao@live.com>
//@Description:
//@Time: 2021/12/22 00:54

package local

import (
	"context"

	"github.com/chuntaojun/polaris-syncer/api"
	"github.com/chuntaojun/polaris-syncer/model"
)

// handlerServiceAdd 处理服务的增加
func (sink *localSink) handlerServiceAdd(newServices []*model.Service) bool {
	// 调用 discoveryTransformer 负责将 *model.Service 转换为 writer 所期望的对象数据
	targetObj, err := sink.discoveryTransformer.Convert(api.TransformParam{
		Source: sink.reader.Name(),
		Target: sink.writer.Name(),
		Object: newServices,
	})
	if err != nil {
		sink.OnError(err)
		return false
	}

	if _, err := sink.writer.Write(model.NewDiscoveryWriteRequest(api.CommonCreateServices, map[string]interface{}{
		model.ServiceListKey: targetObj,
	})); err != nil {
		sink.OnError(err)
		return false
	}

	for _, svc := range newServices {
		// 第一步，针对每一个 service 执行一次 ListInstances 的逻辑，进行一次数据的全量倒入动作
		insRet, err := sink.reader.Read(model.NewDiscoveryReadRequest(api.CommonListInstances, map[string]interface{}{
			model.CommonNamespaceKey:   svc.Namespace,
			model.CommonServiceNameKey: svc.Name,
		}))
		if err != nil {
			continue
		}

		sink.handlerInstanceAdd(insRet)
		// 第二步，为每一个 service 开启 watch 的动作（如果支持的话）

		watchCh := sink.reader.Watch(model.NewDiscoveryWatchRequest(map[string]interface{}{
			model.WatchServiceKey: svc,
		}))

		// 不支持 watch 机制
		if watchCh == nil {
			return true
		}

		ctx, cancel := context.WithCancel(context.Background())
		sink.watchTaskCancels[buildServiceKey(svc)] = cancel
		go sink.serviceWatch(ctx, watchCh)
	}
	return true
}

// handlerServiceRemove 处理服务的删除操作
func (sink *localSink) handlerServiceRemove(waitRemove []*model.Service) bool {
	for i := range waitRemove {
		svc := waitRemove[i]
		key := buildServiceKey(svc)

		// 取消watch任务
		if cancel, exist := sink.watchTaskCancels[key]; exist {
			cancel()
		}

	}
	return true
}

// handlerInstanceAdd 处理实例的增加操作
func (sink *localSink) handlerInstanceAdd(insRet interface{}) (bool, error) {
	ins, ok := insRet.([]*model.Instance)
	if !ok {
		return false, model.ErrorNoInstanceListObj
	}

	writeReq := model.NewDiscoveryWriteRequest(api.CommonCreateInstances, map[string]interface{}{
		model.InstanceListKey: ins,
	})

	if _, err := sink.writer.Write(writeReq); err != nil {
		sink.OnError(err)
		return false, err
	}
	return false, nil
}

func (sink *localSink) handlerInstanceRemove(waitRemove []*model.Instance) (bool, error) {
	return false, nil
}

func (sink *localSink) serviceWatch(ctx context.Context, ch chan interface{}) {
	for {
		select {
		case <-ctx.Done():
			return
		case ev := <-ch:
			svcEv, ok := ev.(*model.ServiceChangeEvent)
			if !ok {
				return
			}
			if len(svcEv.AddInstances) != 0 {
				sink.handlerInstanceAdd(svcEv.AddInstances)
			}
			if len(svcEv.DeleteInstances) != 0 {
				sink.handlerInstanceRemove(svcEv.DeleteInstances)
			}

			// 实例更新暂不处理
		}
	}
}

// diffServices 计算 service 的增加以减少情况
func (sink *localSink) diffServices(newServices []*model.Service) ([]*model.Service, []*model.Service) {
	return nil, nil
}

func buildServiceKey(svc *model.Service) string {
	return ""
}
