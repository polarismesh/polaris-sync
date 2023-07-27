package cn.polarismesh.polaris.sync.extension;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ResourceEndpoint {

	private String name;

	private String productName;

	private ResourceType resourceType;

	private List<String> addresses;

	private Authorization authorization;

	private Database database;

	private Map<String, String> options;


	public String getName() {
		return name;
	}

	public String getProductName() {
		return productName;
	}

	public ResourceType getResourceType() {
		return resourceType;
	}

	public List<String> getServerAddresses() {
		return addresses;
	}

	public Authorization getAuthorization() {
		return authorization;
	}

	public Database getDatabase() {
		return database;
	}

	public Map<String, String> getOptions() {
		return options;
	}

	public static ResourceEndpointBuilder builder() {
		return new ResourceEndpointBuilder();
	}


	public static final class ResourceEndpointBuilder {
		private String name;
		private String productName;
		private ResourceType resourceType;
		private List<String> addresses;
		private Authorization authorization;
		private Database database;

		private Map<String, String> options;

		private ResourceEndpointBuilder() {
		}

		public ResourceEndpointBuilder name(String name) {
			this.name = name;
			return this;
		}

		public ResourceEndpointBuilder productName(String productName) {
			this.productName = productName;
			return this;
		}

		public ResourceEndpointBuilder resourceType(ResourceType resourceType) {
			this.resourceType = resourceType;
			return this;
		}

		public ResourceEndpointBuilder addresses(List<String> addresses) {
			this.addresses = addresses;
			return this;
		}

		public ResourceEndpointBuilder authorization(Authorization authorization) {
			this.authorization = authorization;
			return this;
		}

		public ResourceEndpointBuilder database(Database database) {
			this.database = database;
			return this;
		}

		public ResourceEndpointBuilder options(Map<String, String> options) {
			this.options = options;
			return this;
		}

		public ResourceEndpoint build() {
			ResourceEndpoint resourceEndpoint = new ResourceEndpoint();
			resourceEndpoint.addresses = this.addresses;
			resourceEndpoint.resourceType = this.resourceType;
			resourceEndpoint.name = this.name;
			resourceEndpoint.authorization = this.authorization;
			resourceEndpoint.database = this.database;
			resourceEndpoint.productName = this.productName;
			resourceEndpoint.options = this.options;
			return resourceEndpoint;
		}
	}
}
