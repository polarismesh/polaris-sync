package cn.polarismesh.polaris.sync.core.tasks;

import cn.polarismesh.polaris.sync.extension.ResourceCenter;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NamedResourceCenter {

	private final String name;

	private final String productName;

	private final ResourceCenter center;

	public NamedResourceCenter(String name, String productName,
			ResourceCenter center) {
		this.name = name;
		this.productName = productName;
		this.center = center;
	}

	public String getProductName() {
		return productName;
	}

	public String getName() {
		return name;
	}

	public ResourceCenter getCenter() {
		return center;
	}

}
