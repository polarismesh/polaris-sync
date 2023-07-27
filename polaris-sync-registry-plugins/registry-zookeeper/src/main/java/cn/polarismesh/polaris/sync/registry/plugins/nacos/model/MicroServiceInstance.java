package cn.polarismesh.polaris.sync.registry.plugins.nacos.model;

import java.util.Map;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 */
public class MicroServiceInstance {
    private Map<String, String> metadata;

    private String protocol;
    private String host;

    private int port;

    public MicroServiceInstance(String protocol, Map<String, String> metadata) {
        this.protocol = protocol;
        metadata.remove("interface");
        metadata.remove("methods");
        this.metadata = metadata;
        this.host = metadata.get("host");
        this.port = Integer.parseInt(metadata.get("port"));
    }

    public MicroServiceInstance(String protocol, String host, int port, Map<String, String> metadata) {
        this.host = host;
        this.port = port;
        this.metadata = metadata;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public int hashCode() {
        return host.hashCode() * 31 + port;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MicroServiceInstance)) {
            return false;
        }
        MicroServiceInstance ds = (MicroServiceInstance) obj;
        return this.host.equals(ds.host) && this.port == ds.port;
    }
}
