package cn.polarismesh.polaris.sync.taskconfig.plugins.file;

import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProvider;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProviderFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class FileConfigProviderFactory implements ConfigProviderFactory {
	@Override
	public ConfigProvider create() {
		return new FileConfigProvider();
	}

	@Override
	public String name() {
		return FileConfigProvider.PLUGIN_NAME;
	}
}
