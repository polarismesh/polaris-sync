package cn.polarismesh.polaris.sync.config.plugins.kubernetes;

import org.apache.commons.lang.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Config {

    private String address;

    private String token;

    private String namespace;

    private String configmapName;

    private String dataId;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getConfigmapName() {
        return configmapName;
    }

    public void setConfigmapName(String configmapName) {
        this.configmapName = configmapName;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public boolean hasToken() {
        return StringUtils.isNotBlank(address) && StringUtils.isNotBlank(token);
    }
}
