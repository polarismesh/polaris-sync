package cn.polarismesh.polaris.sync.extension;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Authorization {

	private String username;

	private String password;

	private String token;

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getToken() {
		return token;
	}

	@Override
	public String toString() {
		return "Authorization{" +
				"username='" + username + '\'' +
				", password='" + password + '\'' +
				", token='" + token + '\'' +
				'}';
	}

	public static AuthorizationBuilder builder() {
		return new AuthorizationBuilder();
	}

	public static final class AuthorizationBuilder {
		private String username;
		private String password;
		private String token;

		private AuthorizationBuilder() {
		}

		public AuthorizationBuilder username(String username) {
			this.username = username;
			return this;
		}

		public AuthorizationBuilder password(String password) {
			this.password = password;
			return this;
		}

		public AuthorizationBuilder token(String token) {
			this.token = token;
			return this;
		}

		public Authorization build() {
			Authorization authorization = new Authorization();
			authorization.password = this.password;
			authorization.token = this.token;
			authorization.username = this.username;
			return authorization;
		}
	}
}
