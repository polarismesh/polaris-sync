package cn.polarismesh.polaris.sync.core.tasks.registry;

import java.util.List;

import cn.polarismesh.polaris.sync.core.tasks.SyncTask;
import cn.polarismesh.polaris.sync.extension.ResourceEndpoint;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RegistrySyncTask implements SyncTask {
	@Override
	public String getName() {
		return null;
	}

	@Override
	public boolean isEnable() {
		return false;
	}

	@Override
	public ResourceEndpoint getSource() {
		return null;
	}

	@Override
	public ResourceEndpoint getDestination() {
		return null;
	}

	@Override
	public List<Match> getMatchList() {
		return null;
	}
}
