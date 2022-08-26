package cn.polarismesh.polaris.sync.registry.plugins.consul;

import static org.junit.jupiter.api.Assertions.*;

import com.ecwid.consul.v1.health.model.HealthService.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConsulRegistryCenterTest {

    @Test
    void convertConsulMetadata() {
        Service service = new Service();
        List<String> tags = new ArrayList<>();
        tags.add("localkey=localvalue");
        tags.add("secure=false");
        service.setTags(tags);

        Map<String, String> ret = ConsulRegistryCenter.convertConsulMetadata(service);

        Map<String, String> expect = new HashMap<>();
        expect.put("localkey", "localvalue");
        expect.put("secure", "false");

        Assertions.assertEquals(expect, ret);
    }
}