package cn.polarismesh.polaris.sync.taskconfig.plugins.kubernetes;

import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProvider;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProviderFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class KubernetesConfigProviderFactory implements ConfigProviderFactory {
	@Override
	public ConfigProvider create() {
		return new KubernetesConfigProvider();
	}

	@Override
	public String name() {
		return KubernetesConfigProvider.PLUGIN_NAME;
	}
}
