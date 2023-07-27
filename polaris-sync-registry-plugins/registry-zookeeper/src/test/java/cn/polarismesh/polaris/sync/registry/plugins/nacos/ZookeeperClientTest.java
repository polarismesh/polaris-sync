package cn.polarismesh.polaris.sync.registry.plugins.nacos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.polarismesh.polaris.sync.registry.plugins.nacos.ZookeeperClient.parentPaths;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 * @date 2023-07-27
 */
class ZookeeperClientTest {

    @Test
    void testParentPaths() {
        String path = "a/b/c/d";
        List<String> leveledPaths = parentPaths(path);
        assertEquals(2, leveledPaths.size());
        assertEquals("a/b", leveledPaths.get(0));
        assertEquals("a/b/c", leveledPaths.get(1));
    }
}