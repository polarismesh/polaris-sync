package cn.polarismesh.polaris.sync.taskconfig.plugins.etcd;

import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProvider;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProviderFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class EtcdConfigProviderFactory implements ConfigProviderFactory {

	@Override
	public ConfigProvider create() {
		return new EtcdConfigProvider();
	}

	@Override
	public String name() {
		return EtcdConfigProvider.PLUGIN_NAME;
	}
}
