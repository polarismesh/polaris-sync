# Polaris Sync

[English](./README-zh.md) | 中文

##  介绍

polaris-sync用于北极星和其他注册中心/网关服务之间的数据同步，用于以下2种场景：

- 北极星与其他注册中心的服务数据同步：用于服务实例在注册中心之间平滑迁移。
- 北极星服务数据同步到网关：用于网关无缝对接到注册中心服务发现场景。

### 组件架构

![](https://qcloudimg.tencent-cloud.cn/raw/b42f33820efb7bdde36a0e68c6359bfb.png)

### 架构优势

- 支持任意注册中心之间的双向服务数据同步。
- 支持对接主流网关，无需对网关做任何改动即可让网关对接任意注册中心。
- 基于插件化设计，可方便扩展其他注册中心实现。

## 安装说明

### 在K8S环境下安装

- 执行所有安装之前，需要下载源码包，可以从以下2个地址下载源码包，请选择最新的release版本的源码包：

 - Github下载：[polaris-sync-release](https://github.com/polarismesh/polaris-sync/releases)

- 解压后，执行部署

```
cd deploy/kubernetes
kubectl apply -f polaris-sync-config.yaml
kubectl apply -f polaris-sync.yaml
```

### 在VM下安装

- 执行所有安装之前，需要下载源码包，可以从以下2个地址下载软件包，请选择最新的release版本，并选择polaris-sync-server-*.zip的格式包下载：

 - Github下载：[polaris-sync-release](https://github.com/polarismesh/polaris-sync/releases)

- 解压后，执行部署

````
unzip polaris-sync-server-${VERSION}.zip

// 这里需要修改一下 /bin/application.yaml, conf/sync-registry.json, conf/sync-config.json
bash bin/start.sh
````

日志默认会打印到STDOUT，可以通过重定向的方式来重定向到文件。

## 快速入门

### 相关的环境变量

polaris-sync支持通过环境变量的方式来修改系统的各项参数：

| 变量名                                    | 说明                                                         | 可选值                                                    |
| ----------------------------------------- | ------------------------------------------------------------ | --------------------------------------------------------- |
| POLARIS_SYNC_REGISTRY_CONFIG_PROVIDER     | 同步配置的提供方式，默认是file，即从本地文件获取配置         | file（本地文件），kubernetes（通过读取k8s configmap获取） |
| POLARIS_SYNC_REGISTRY_CONFIG_BACKUP_PATH  | 同步配置的备份路径，容灾用，默认为conf/sync-config-backup.json | 任意可写的本地文件路径                                    |
| POLARIS_SYNC_CONFIG_FILE_WATCH            | 配置提供方式为file时，同步配置通过该路径来监听并获取配置内容，默认为conf/sync-config.json | 任意文件路径                                              |
| POLARIS_SYNC_CONFIG_K8S_ADDRESS           | k8s的APIServer地址，配置提供方式为kubernetes时有用           | ip:port                                                   |
| POLARIS_SYNC_CONFIG_K8S_TOKEN             | k8s的访问token，配置提供方式为kubernetes时有用               | 任意字符串                                                |
| POLARIS_SYNC_CONFIG_K8S_NAMESPACE         | k8s的configmap命名空间，配置提供方式为kubernetes时有用       | 任意字符串                                                |
| POLARIS_SYNC_CONFIG_K8S_CONFIGMAP_NAME    | k8s的配置configmap名称，配置提供方式为kubernetes时有用       | 任意字符串                                                |
| POLARIS_SYNC_CONFIG_K8S_CONFIGMAP_DATA_ID | k8s的配置configmap的配置项ID，配置提供方式为kubernetes时有用 | 任意字符串                                                |

### nacos到北极星的服务数据双向同步

在注册中心迁移的过程中，往往需要双向访问，迁移到新注册中心的服务，需要访问未迁移的服务。同时未迁移的服务，也需要访问已迁移的服务。

为了不对应用程序做任何的改造，注册中心之间需要进行实时的双向数据同步，才能达成热迁移的目标。

下面以nacos到北极星的服务数据迁移为例，讲解如何使用polaris-sync实现注册中心之间的数据双向同步。

我们需要给polaris-sync配置2个同步任务，一个是nacos到北极星的同步任务，另外一个是北极星到nacos的同步任务。

需要修改polaris-sync-config.yaml中的json配置，添加任务的配置。注意，polaris-sync会监听该配置的变更，因此配置修改后，无需重启polaris-sync。

```
{
	"tasks": [
	    //第一个任务是从nacos单向同步到北极星
		{
			"name": "nacos1-to-polaris1",  //任务标识，需唯一
			"enable": true,
			"source": {                    //定义来源注册中心
					"name": "nacos1",      //注册中心名，需唯一
					"type": "nacos",       //注册中心类型
					"addresses": [
						"127.0.0.1:8848"   //nacos地址
					],
					"user": "nacos",       //登录凭据，如果未开启鉴权，可不填
					"password": "nacos"    
			},
			"destination": {              //定义目标注册中心
					"name": "polaris1",
					"type": "polaris",
					"addresses": [
						"127.0.0.1:8090"  //北极星地址，使用HTTP端口
					],
					"token": "123456"     //访问凭据，如果未开启鉴权，可不填
			},
			"match": [                    // 指定哪些服务需要进行同步
				{
					"namespace": "empty_ns",                  //命名空间ID，nacos默认命名空间填empty_ns
					"service": "nacos.test.3", // 需要进行同步的服务名，格式为分组名__服务名，如果不带分组名则为默认分组
				}
			]
		},
		//第二个任务是从北极星单向同步到nacos
		{
			"name": "polaris1-to-nacos1",  //任务标识，需唯一
			"enable": true,
			"source": {                    //定义来源注册中心
					"name": "polaris1",
					"type": "polaris",
					"addresses": [
						"127.0.0.1:8090"  //北极星地址，使用HTTP端口
					],
					"token": "123456"     //访问凭据，如果未开启鉴权，可不填
			},
			"destination": {              //定义目标注册中心
					"name": "nacos1",      //注册中心名，需唯一
					"type": "nacos",       //注册中心类型
					"addresses": [
						"127.0.0.1:8848"   //nacos地址
					],
					"user": "nacos",       //登录凭据，如果未开启鉴权，可不填
					"password": "nacos"
			},
			"match": [                    // 指定哪些服务需要进行同步
				{
					"namespace": "empty_ns",                  //命名空间ID，nacos默认命名空间填empty_ns
					"service": "nacos.test.3", // 需要进行同步的服务名，格式为分组名__服务名
				}
			]
		}
	],
	"methods": [
		{
			"type": "watch",
			"enable": true
		},
		{
			"type": "pull",
			"enable": true,
			"interval": "60s"
		}
	],
	"health_check": {
		"enable": true
	},
	"report": {
		"interval" : "1m",
		"targets": [
			{
				"type": "file",
				"enable": true
			}
		]
	}
}
```

编辑完规则后，直接通过```kubectl apply -f polaris-sync-config.yaml```，polaris-sync监听到配置变更后，会进行任务的热加载，启动同步事项。

### 北极星到kong网关的服务数据单向同步

应用的地址信息往往注册到注册中心，当用户使用网关想直接访问注册中心的地址时，往往有2个方案：

第一种是使用网关插件，对接注册中心的服务发现机制。这种方式，用户需要在网关加载对应的注册中心的插件，存在一定的侵入性，且存在插件冲突管理的问题。

第二种是通过同步工具，将服务数据从注册中心同步到网关，然后走网关原生的路由能力进行寻址。这种方式对网关无侵入，与网关现有的插件不冲突，更符合网关的原生使用场景。

下面讲解如何使用polaris-sync实现北极星的服务数据同步到Kong网关，实现Kong网关直通北极星注册中心的场景。

需要修改polaris-sync-config.yaml中的json配置，添加任务的配置。

```
{
	"tasks": [
	{
			"name": "polaris1",
			"enable": true,
			"source": {
					"name": "ins-3ad0f6e6",
					"type": "polaris",
					"addresses": [
						"127.0.0.1:8090"
					],
					"token": "123456"
			},
			"destination": {
					"name": "kong",
					"type": "kong",
					"addresses": [
						"127.0.0.1:8001"  //这里填写kong的admin地址
					]
			},
			"match": [
				{
					"namespace": "default",
					"service": "test.service.3",
					"groups": [ //需要同步的分组信息，分组会成为kong的upstream，默认会有一个default的upstream，包含全部的实例
						{
							"name": "version-1",    //分组名
							"metadata": {
								"version": "1.0.0" //分组过滤的元数据
							}
						},
						{
							"name": "version-2",
							"metadata": {
								"version": "2.0.0"
							}
						}
					]
				}
			]
		}
	],
	"methods": [
		{
			"type": "watch",
			"enable": true
		},
		{
			"type": "pull",
			"enable": true,
			"interval": "60s"
		}
	],
	"health_check": {
		"enable": true
	},
	"report": {
		"interval" : "1m",
		"targets": [
			{
				"type": "file",
				"enable": true
			}
		]
	}
}
```

编辑完规则后，直接通过```kubectl apply -f polaris-sync-config.yaml```，polaris-sync监听到配置变更后，会进行任务的热加载，启动同步事项。

同步后，在kong中会创建1个service，以及3个upstream（分别是default, version-1, version-2），每个upstream里面的target和对应的过滤后的实例对应。

## 配置说明

polaris-sync是一个配置驱动的同步工具，用户所有的任务下发都通过配置完成。

配置文件的定义可以参考polaris-sync-protobuf工程中的[registry.proto](https://github.com/polarismesh/polaris-sync/blob/main/polaris-sync-protobuf/src/main/proto/registry.proto)的协议定义。

其中根对象为Registry。

