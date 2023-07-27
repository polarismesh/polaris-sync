package cn.polarismesh.polaris.sync.extension.registry;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 */
public class ServiceAlias {
    private final String service;

    private final String namespace;

    private final String alias;
    private final String alias_namespace; //used to generate json

    public ServiceAlias(String service, String namespace, String alias) {
        this.service = service;
        this.namespace = namespace;
        this.alias = alias;
        this.alias_namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getAlias() {
        return alias;
    }

    public String getService() {
        return service;
    }

    public String getAlias_namespace() {
        return alias_namespace;
    }

    @Override
    public int hashCode() {
        return namespace.hashCode() * 31 + alias.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ServiceAlias) {
            ServiceAlias sa = (ServiceAlias) obj;
            return this.namespace.equals(sa.namespace) && this.alias.equals(sa.alias);
        }
        return false;
    }
}
