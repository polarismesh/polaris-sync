package cn.polarismesh.polaris.sync.extension;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Database {

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


	public static DatabaseBuilder builder() {
		return new DatabaseBuilder();
	}

	public static final class DatabaseBuilder {
		private String jdbcUrl;
		private String username;
		private String password;

		private DatabaseBuilder() {
		}


		public DatabaseBuilder jdbcUrl(String jdbcUrl) {
			this.jdbcUrl = jdbcUrl;
			return this;
		}

		public DatabaseBuilder username(String username) {
			this.username = username;
			return this;
		}

		public DatabaseBuilder password(String password) {
			this.password = password;
			return this;
		}

		public Database build() {
			Database database = new Database();
			database.setJdbcUrl(jdbcUrl);
			database.setUsername(username);
			database.setPassword(password);
			return database;
		}
	}
}
