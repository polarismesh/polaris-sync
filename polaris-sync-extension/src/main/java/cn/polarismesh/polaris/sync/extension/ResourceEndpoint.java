package cn.polarismesh.polaris.sync.extension;

import java.util.List;

import cn.polarismesh.polaris.sync.extension.ResourceType;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface ResourceEndpoint {

	String getName();

	String getProductName();

	ResourceType getResourceType();

	List<String> getServerAddresses();

	Authorization getAuthorization();

	Database getDatabase();

	public static class Database {

		private String jdbcUrl;

		private String username;

		private String password;

		public String getJdbcUrl() {
			return jdbcUrl;
		}

		public void setJdbcUrl(String jdbcUrl) {
			this.jdbcUrl = jdbcUrl;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}
	}
}
