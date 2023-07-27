package cn.polarismesh.polaris.sync.registry.plugins.nacos;

import cn.polarismesh.polaris.sync.common.rest.RestUtils;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.MicroService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static cn.polarismesh.polaris.sync.registry.plugins.nacos.ZookeeperRegistryUtils.allDubboServices;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 * @date 2023-07-07
 */
public class ZookeeperRegistryUtilsTest {

    @BeforeClass
    public static void init() {
        String addr = System.getProperty("zk-addr");
        if (addr != null) {
            ZookeeperRegistryCenter.zkClient = new ZookeeperClient(addr);
        }
    }

    @Test
    public void testDubboService2Interfaces() {
        Map<String, MicroService> serviceMap = allDubboServices("/dubbo");
        assertTrue(serviceMap.size() > 0);
    }

    @Test
    public void testTemp() {
        String json = "{\"a\":12}";
        Map map = RestUtils.unmarshalJsonText(json, HashMap.class);
        System.out.println(map);
    }
}