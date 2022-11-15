package cn.polarismesh.polaris.sync.extension;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface ResourceCenter<T extends InitRequest> {

	/**
	 *
	 * @return resource center name
	 */
	String getName();


	/**
	 * registry type, such as nacos, kong, consul, etc...
	 *
	 * @return type
	 */
	ResourceType getType();

	/**
	 * initialize registry
	 */
	void init(T request);

	/**
	 * destroy registry
	 */
	void destroy();

	/**
	 * process health checking
	 *
	 * @return check result
	 */
	Health healthCheck();

}
