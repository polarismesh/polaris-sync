package cn.polarismesh.polaris.sync.registry.plugins.nacos.model;

import java.util.*;

/**
* @author <a href="mailto:amyson99@foxmail.com">amyson</a>
*/
public class MicroService {
    private Set<String> interfaces;

    //dubbo service is whether registered as traditional dubbo mode
    private boolean dubboMode;

    private Map<String, String> metadata;

    private Set<MicroServiceInstance> instances;

    public MicroService(boolean dubboMode, Map<String, String> metadata) {
        this.metadata = metadata;
        this.interfaces = new HashSet<>();
        this.instances = new HashSet<>();
        this.dubboMode = dubboMode;
    }

    public Set<String> getInterfaces() {
        return interfaces;
    }

    public boolean isDubboMode() {
        return dubboMode;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Set<MicroServiceInstance> getInstances() {
        return instances;
    }

    public void resetInstances(Collection<MicroServiceInstance> instances) {
        this.instances.clear();
        this.instances.addAll(instances);
    }
}
