package cn.polarismesh.polaris.sync.core.server;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import cn.polarismesh.polaris.sync.core.taskconfig.ConfigProviderManager;
import cn.polarismesh.polaris.sync.core.taskconfig.SyncRegistryProperties;
import cn.polarismesh.polaris.sync.core.tasks.AbstractTaskEngine;
import cn.polarismesh.polaris.sync.core.tasks.registry.RegistrySyncTask;
import cn.polarismesh.polaris.sync.core.tasks.registry.RegistryTaskEngine;
import cn.polarismesh.polaris.sync.extension.registry.RegistryCenter;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.extension.taskconfig.ConfigProviderFactory;
import cn.polarismesh.polaris.sync.model.pb.ModelProto;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RegistrySyncServer extends ResourceSyncServer<RegistrySyncTask, RegistryProto.Registry, SyncRegistryProperties> {

	public RegistrySyncServer(
			SyncRegistryProperties properties,
			List<RegistryCenter> centers,
			List<ReportHandler> reportHandlers) throws Exception {

		List<ConfigProviderFactory> factories = new ArrayList<>();
		ServiceLoader.load(ConfigProviderFactory.class).iterator().forEachRemaining(factory -> factories.add(factory));

		ConfigProviderManager<RegistryProto.Registry, SyncRegistryProperties> manager = new ConfigProviderManager<>(
				factories,
				properties,
				RegistryProto.Registry::newBuilder
				);
		AbstractTaskEngine<RegistrySyncTask> engine = new RegistryTaskEngine(centers);

		initResourceSyncServer(manager, engine, reportHandlers);
	}

	@Override
	protected List<RegistrySyncTask> parseTasks(RegistryProto.Registry registry) {
		return null;
	}

	@Override
	protected List<ModelProto.Method> parseMethods(RegistryProto.Registry registry) {
		return null;
	}

	@Override
	protected ModelProto.HealthCheck parseHealthCheck(RegistryProto.Registry registry) {
		return null;
	}

	@Override
	protected ModelProto.Report parseReport(RegistryProto.Registry registry) {
		return null;
	}
}
