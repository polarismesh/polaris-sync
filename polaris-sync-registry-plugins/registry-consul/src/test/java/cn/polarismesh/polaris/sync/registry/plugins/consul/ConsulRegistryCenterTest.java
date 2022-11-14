package cn.polarismesh.polaris.sync.registry.plugins.consul;


import com.ecwid.consul.v1.health.model.HealthService.Service;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsulRegistryCenterTest {

    @Test
    public void convertConsulMetadata() {
        Service service = new Service();
        List<String> tags = new ArrayList<>();
        tags.add("localkey=localvalue");
        tags.add("secure=false");
        service.setTags(tags);

        Map<String, String> ret = ConsulRegistryCenter.convertConsulMetadata(service);

        Map<String, String> expect = new HashMap<>();
        expect.put("localkey", "localvalue");
        expect.put("secure", "false");

        Assert.assertEquals(expect, ret);
    }
}