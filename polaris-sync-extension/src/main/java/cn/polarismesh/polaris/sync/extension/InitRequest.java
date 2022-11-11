package cn.polarismesh.polaris.sync.extension;

import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface InitRequest {

	public ResourceType getSourceType();

	String getSourceName();

	ResourceEndpoint getResourceEndpoint();

}
