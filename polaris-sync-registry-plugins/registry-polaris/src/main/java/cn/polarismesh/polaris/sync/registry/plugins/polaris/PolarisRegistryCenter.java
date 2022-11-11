/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package cn.polarismesh.polaris.sync.registry.plugins.polaris;

import cn.polarismesh.polaris.sync.common.rest.HostAndPort;
import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.common.utils.CommonUtils;
import cn.polarismesh.polaris.sync.common.utils.DefaultValues;
import cn.polarismesh.polaris.sync.extension.ResourceType;
import cn.polarismesh.polaris.sync.extension.registry.AbstractRegistryCenter;
import cn.polarismesh.polaris.sync.extension.Health;
import cn.polarismesh.polaris.sync.extension.registry.RegistryInitRequest;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.WatchEvent;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.extension.utils.StatusCodes;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import com.tencent.polaris.api.core.ConsumerAPI;
import com.tencent.polaris.api.exception.PolarisException;
import com.tencent.polaris.api.listener.ServiceListener;
import com.tencent.polaris.api.pojo.ServiceChangeEvent;
import com.tencent.polaris.api.pojo.ServiceInfo;
import com.tencent.polaris.api.rpc.GetAllInstancesRequest;
import com.tencent.polaris.api.rpc.GetInstancesRequest;
import com.tencent.polaris.api.rpc.GetOneInstanceRequest;
import com.tencent.polaris.api.rpc.UnWatchServiceRequest.UnWatchServiceRequestBuilder;
import com.tencent.polaris.api.rpc.WatchServiceRequest;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse;
import com.tencent.polaris.client.pb.ResponseProto.DiscoverResponse.DiscoverResponseType;
import com.tencent.polaris.client.pb.ServiceProto;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import com.tencent.polaris.factory.api.DiscoveryAPIFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class PolarisRegistryCenter extends AbstractRegistryCenter {

	private static final Logger LOG = LoggerFactory.getLogger(PolarisRegistryCenter.class);

	private static final String PREFIX_HTTP = "http://";

	private static final String PREFIX_GRPC = "grpc://";

	private final AtomicBoolean destroyed = new AtomicBoolean(false);

	private RegistryInitRequest registryInitRequest;

	private RestOperator restOperator;

	private final List<String> httpAddresses = new ArrayList<>();

	private final List<String> grpcAddresses = new ArrayList<>();

	private ConsumerAPI consumerAPI;

	private final Object lock = new Object();

	@Override
	public String getName() {
		return getType().name();
	}

	@Override
	public ResourceType getType() {
		return ResourceType.POLARIS;
	}

	@Override
	public void init(RegistryInitRequest request) {
		this.registryInitRequest = request;
		restOperator = new RestOperator();
		parseAddresses(request.getResourceEndpoint().getServerAddresses());
		LOG.info("[Polaris] polaris {} inited, http addresses {}, grpc addresses {}",
				request.getSourceName(), httpAddresses, grpcAddresses);
	}

	private void parseAddresses(List<String> addresses) {
		for (String address : addresses) {
			if (address.startsWith(PREFIX_HTTP)) {
				httpAddresses.add(address.substring(PREFIX_HTTP.length()));
			}
			else if (address.startsWith(PREFIX_GRPC)) {
				grpcAddresses.add(address.substring(PREFIX_GRPC.length()));
			}
			else {
				httpAddresses.add(address);
			}
		}
	}

	private ConsumerAPI getConsumerAPI() {
		synchronized (lock) {
			if (null != consumerAPI) {
				return consumerAPI;
			}
			try {
				consumerAPI = DiscoveryAPIFactory.createConsumerAPIByAddress(grpcAddresses);
			}
			catch (PolarisException e) {
				LOG.error("[Polaris] fail to create consumer API by {}", grpcAddresses, e);
				return null;
			}
			return consumerAPI;
		}
	}

	@Override
	public void destroy() {
		destroyed.set(true);
	}

	@Override
	public DiscoverResponse listInstances(Service service, ModelProto.Group group) {
		DiscoverResponse.Builder builder = DiscoverResponse.newBuilder();
		DiscoverResponse discoverResponse = PolarisRestUtils.discoverAllInstances(
				restOperator, service, registryInitRequest.getResourceEndpoint(), httpAddresses, builder);
		if (null != discoverResponse) {
			return discoverResponse;
		}
		if (DefaultValues.GROUP_NAME_DEFAULT.equals(group.getName())) {
			return builder.build();
		}
		Map<String, String> filters = group.getMetadataMap();
		List<Instance> filteredInstances = new ArrayList<>();
		for (Instance instance : builder.getInstancesList()) {
			if (CommonUtils.matchMetadata(instance.getMetadataMap(), filters)) {
				filteredInstances.add(instance);
			}
		}
		builder.clearInstances();
		builder.addAllInstances(filteredInstances);
		return builder.build();
	}

	@Override
	public boolean watch(Service service, ResponseListener eventListener) {
		if (CollectionUtils.isEmpty(grpcAddresses)) {
			LOG.warn("[Polaris] grpc addresses are empty, watch is disabled");
			return true;
		}
		ConsumerAPI consumerAPI = getConsumerAPI();
		if (null == consumerAPI) {
			LOG.error("[Polaris] fail to lookup ConsumerAPI for service {}, registry {}",
					service, registryInitRequest.getResourceEndpoint().getName());
			return false;
		}
		WatchServiceRequest watchServiceRequest = new WatchServiceRequest();
		watchServiceRequest.setNamespace(service.getNamespace());
		watchServiceRequest.setService(service.getService());
		ServiceListener serviceListener = new ServiceListener() {
			@Override
			public void onEvent(ServiceChangeEvent event) {
				List<com.tencent.polaris.api.pojo.Instance> allInstances = event.getAddInstances();
				List<ServiceProto.Instance> outInstances = convertPolarisInstances(
						allInstances.toArray(new com.tencent.polaris.api.pojo.Instance[0]));
				DiscoverResponse.Builder builder = ResponseUtils
						.toDiscoverResponse(service, StatusCodes.SUCCESS, DiscoverResponseType.INSTANCE);
				builder.addAllInstances(outInstances);
				eventListener.onEvent(new WatchEvent(builder.build()));
			}
		};
		watchServiceRequest.setListeners(Collections.singletonList(serviceListener));
		consumerAPI.watchService(watchServiceRequest);
		return true;
	}

	private List<ServiceProto.Instance> convertPolarisInstances(com.tencent.polaris.api.pojo.Instance[] instances) {
		List<ServiceProto.Instance> polarisInstances = new ArrayList<>();
		if (null == instances) {
			return polarisInstances;
		}
		for (com.tencent.polaris.api.pojo.Instance instance : instances) {
			String instanceId = instance.getId();
			Map<String, String> metadata = instance.getMetadata();
			String ip = instance.getHost();
			int port = instance.getPort();
			ServiceProto.Instance.Builder builder = ServiceProto.Instance.newBuilder();
			builder.setId(ResponseUtils.toStringValue(instanceId));
			builder.setWeight(ResponseUtils.toUInt32Value(instance.getWeight()));
			if (CollectionUtils.isEmpty(metadata)) {
				builder.putAllMetadata(metadata);
			}
			builder.setHost(ResponseUtils.toStringValue(ip));
			builder.setPort(ResponseUtils.toUInt32Value(port));
			builder.setIsolate(ResponseUtils.toBooleanValue(instance.isIsolated()));
			builder.setHealthy(ResponseUtils.toBooleanValue(instance.isHealthy()));
			polarisInstances.add(builder.build());
		}
		return polarisInstances;
	}

	@Override
	public void unwatch(Service service) {
		if (CollectionUtils.isEmpty(grpcAddresses)) {
			LOG.warn("[Polaris] grpc addresses are empty, unwatch is disabled");
			return;
		}
		ConsumerAPI consumerAPI = getConsumerAPI();
		if (null == consumerAPI) {
			LOG.error("[Polaris] fail to lookup ConsumerAPI for service {}, registry {}",
					service, registryInitRequest.getResourceEndpoint().getName());
			return;
		}
		UnWatchServiceRequestBuilder builder = UnWatchServiceRequestBuilder.anUnWatchServiceRequest();
		builder.namespace(service.getNamespace()).service(service.getService()).removeAll(true);
		consumerAPI.unWatchService(builder.build());
	}

	@Override
	public void updateServices(Collection<Service> services) {
	}

	@Override
	public void updateGroups(Service service, Collection<ModelProto.Group> groups) {
	}

	private static ServiceProto.Instance wrapInstanceWithSync(Instance instance, String sourceName) {
		Instance.Builder builder = Instance.newBuilder();
		builder.mergeFrom(instance);
		builder.putMetadata(DefaultValues.META_SYNC, sourceName);
		return builder.build();
	}

	@Override
	public void updateInstances(Service service, ModelProto.Group group, Collection<ServiceProto.Instance> srcInstances) {
		DiscoverResponse.Builder builder = DiscoverResponse.newBuilder();
		DiscoverResponse discoverResponse = PolarisRestUtils.discoverAllInstances(
				restOperator, service, registryInitRequest.getResourceEndpoint(), httpAddresses, builder);
		if (null != discoverResponse) {
			return;
		}
		DiscoverResponse allInstances = builder.build();
		Map<HostAndPort, ServiceProto.Instance> targetsToCreate = new HashMap<>();
		Map<HostAndPort, ServiceProto.Instance> targetsToUpdate = new HashMap<>();
		Map<HostAndPort, ServiceProto.Instance> instancesMap = toInstancesMap(allInstances.getInstancesList());
		Set<HostAndPort> processedAddresses = new HashSet<>();
		String sourceName = registryInitRequest.getSourceName();
		// 比较新增、编辑、删除
		// 新增=源不带同步标签的实例，额外存在了（与当前全部实例作为对比）
		// 编辑=当前实例（sync=sourceName），与源实例做对比，存在不一致
		for (ServiceProto.Instance srcInstance : srcInstances) {
			HostAndPort srcAddress = HostAndPort.build(
					srcInstance.getHost().getValue(), srcInstance.getPort().getValue());
			processedAddresses.add(srcAddress);
			Map<String, String> srcMetadata = srcInstance.getMetadataMap();
			if (srcMetadata.containsKey(DefaultValues.META_SYNC)) {
				continue;
			}
			if (!instancesMap.containsKey(srcAddress)) {
				//不存在则新增
				targetsToCreate.put(srcAddress, wrapInstanceWithSync(srcInstance, sourceName));
			}
			else {
				ServiceProto.Instance destInstance = instancesMap.get(srcAddress);
				if (!CommonUtils.isSyncedByCurrentSource(
						destInstance.getMetadataMap(), sourceName)) {
					//并非同步实例，可能是目标注册中心新注册的，不处理
					continue;
				}
				//比较是否存在不一致
				if (!instanceEquals(srcInstance, destInstance)) {
					targetsToUpdate.put(srcAddress, toUpdateInstance(srcInstance, destInstance.getId().getValue()));
				}
			}
		}
		// 删除=当前实例（sync=sourceName），额外存在了（与源的全量实例作对比）
		Map<HostAndPort, ServiceProto.Instance> targetsToDelete = new HashMap<>();
		for (Map.Entry<HostAndPort, ServiceProto.Instance> instanceEntry : instancesMap.entrySet()) {
			ServiceProto.Instance instance = instanceEntry.getValue();
			if (!CommonUtils.isSyncedByCurrentSource(instance.getMetadataMap(), sourceName)) {
				continue;
			}
			if (!processedAddresses.contains(instanceEntry.getKey())) {
				targetsToDelete.put(instanceEntry.getKey(), instance);
			}
		}
		// process operation
		int targetAddCount = 0;
		int targetPatchCount = 0;
		int targetDeleteCount = 0;
		if (!targetsToCreate.isEmpty()) {
			LOG.info("[Polaris] targets pending to create are {}, group {}", targetsToCreate.keySet(), group.getName());
			PolarisRestUtils.createInstances(
					restOperator, targetsToCreate.values(), registryInitRequest.getResourceEndpoint(), httpAddresses);
			targetAddCount++;
		}
		if (!targetsToUpdate.isEmpty()) {
			LOG.info("[Polaris] targets pending to update are {}, group {}", targetsToUpdate.keySet(), group.getName());
			PolarisRestUtils.updateInstances(
					restOperator, targetsToUpdate.values(), registryInitRequest.getResourceEndpoint(), httpAddresses);
			targetPatchCount++;
		}
		if (!targetsToDelete.isEmpty()) {
			LOG.info("[Polaris] targets pending to delete are {}, group {}", targetsToDelete.keySet(), group.getName());
			PolarisRestUtils.deleteInstances(
					restOperator, targetsToDelete.values(), registryInitRequest.getResourceEndpoint(), httpAddresses);
			targetDeleteCount++;
		}
		LOG.info("[Polaris] success to update targets, add {}, patch {}, delete {}",
				targetAddCount, targetPatchCount, targetDeleteCount);

	}

	private ServiceProto.Instance toUpdateInstance(ServiceProto.Instance instance, String instanceId) {
		ServiceProto.Instance.Builder builder = ServiceProto.Instance.newBuilder().mergeFrom(instance);
		builder.setId(ResponseUtils.toStringValue(instanceId));
		builder.putMetadata(DefaultValues.META_SYNC, registryInitRequest.getSourceName());
		return builder.build();
	}

	private static boolean instanceEquals(ServiceProto.Instance srcInstance, ServiceProto.Instance dstInstance) {
		if (dstInstance.getWeight().getValue() != srcInstance.getWeight().getValue()) {
			return false;
		}
		if (!StringUtils.defaultString(dstInstance.getProtocol().getValue()).equals(
				StringUtils.defaultString(srcInstance.getProtocol().getValue()))) {
			return false;
		}
		if (!StringUtils.defaultString(dstInstance.getVersion().getValue()).equals(
				StringUtils.defaultString(srcInstance.getVersion().getValue()))) {
			return false;
		}
		if (dstInstance.getHealthy().getValue() != srcInstance.getHealthy().getValue()) {
			return false;
		}
		return CommonUtils.metadataEquals(srcInstance.getMetadataMap(), dstInstance.getMetadataMap());
	}

	private static Map<HostAndPort, ServiceProto.Instance> toInstancesMap(List<ServiceProto.Instance> instances) {
		Map<HostAndPort, ServiceProto.Instance> outInstances = new HashMap<>();
		if (CollectionUtils.isEmpty(instances)) {
			return outInstances;
		}
		for (ServiceProto.Instance instance : instances) {
			outInstances.put(HostAndPort.build(instance.getHost().getValue(), instance.getPort().getValue()), instance);
		}
		return outInstances;
	}

	public static <T> HttpEntity<T> getRequestEntity() {
		HttpHeaders headers = new HttpHeaders();
		return new HttpEntity<T>(headers);
	}

	@Override
	public Health healthCheck() {
		String address = RestOperator.pickAddress(httpAddresses);
		String url = String.format("http://%s/", address);
		RestResponse<String> stringRestResponse = restOperator
				.curlRemoteEndpoint(url, HttpMethod.GET, getRequestEntity(), String.class);
		int totalCount = 0;
		int errorCount = 0;
		if (stringRestResponse.hasServerError()) {
			errorCount++;
		}
		totalCount++;
		return new Health(totalCount, errorCount);
	}
}
