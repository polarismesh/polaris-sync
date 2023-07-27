package cn.polarismesh.polaris.sync.extension;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public enum ResourceType {

	UNKNOWN("unknown"),

	POLARIS("polaris"),

	NACOS("nacos"),

	CONSUL("consul"),

	KONG("kong"),

	KUBERNETES("kubernetes"),

	APOLLO("apollo"),

	ZOOKEEPER("zookeeper"),
	;

	private final String name;

	ResourceType(String name) {
		this.name = name;
	}
}
