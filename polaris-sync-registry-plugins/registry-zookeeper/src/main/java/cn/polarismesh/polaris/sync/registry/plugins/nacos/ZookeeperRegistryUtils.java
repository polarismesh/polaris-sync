package cn.polarismesh.polaris.sync.registry.plugins.nacos;

import cn.polarismesh.polaris.sync.common.rest.RestUtils;
import cn.polarismesh.polaris.sync.extension.registry.AbstractRegistryCenter;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.registry.ServiceAlias;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.MicroService;
import cn.polarismesh.polaris.sync.registry.plugins.nacos.model.MicroServiceInstance;
import com.google.gson.Gson;
import com.tencent.polaris.client.pb.ResponseProto;
import com.tencent.polaris.client.pb.ServiceProto;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.util.*;

import static cn.polarismesh.polaris.sync.common.utils.DefaultValues.META_SYNC;
import static cn.polarismesh.polaris.sync.registry.plugins.nacos.ZookeeperRegistryCenter.zkClient;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 * @date 2023-07-06
 */
public class ZookeeperRegistryUtils {
    static Gson gson = new Gson();

    static String toZookeeperAddr(List<String> serverAddresses) {
        StringBuilder hosts = new StringBuilder();
        for (String addr : serverAddresses) {
            hosts.append(addr).append(",");
        }
        return hosts.substring(0, hosts.length() - 1);
    }

    static List<String> toRootPaths(String... strRootPaths) {
        List<String> ls = new ArrayList<>();
        for (String strRootPath : strRootPaths) {
            if ('/' != strRootPath.charAt(0)) {
                strRootPath = "/" + strRootPath;
            }
            ls.add(strRootPath);
        }
        return ls;
    }

    static Set<String> ignoreSubPaths = new HashSet<>(Arrays.asList("metadata", "config"));
    //all dubbo services under path: /dubbo/
    static Map<String, MicroService> allDubboServices(String dubboRootPath) {
        Map<String, MicroService> dubboServices = new HashMap<>();

        List<String> interfaces = zkClient.getChildren(dubboRootPath);
        for (String inf : interfaces) {
            if (ignoreSubPaths.contains(inf)) {
                continue;
            }
            for (MicroServiceInstance instance : dubboServiceInstances(getPathOfProviders(inf), false).values()) {
                if (instance.getMetadata().containsKey(META_SYNC)) {
                    continue;
                }
                String appName = instance.getMetadata().get("application");
                if (appName != null) {
                    MicroService ds = dubboServices.computeIfAbsent(appName, k -> {
                        Map<String, String> svrMeta = new HashMap<>();
                        return new MicroService(true, svrMeta);
                    });
                    ds.getInterfaces().add(inf);
                    ds.getInstances().add(instance);
                }
            }
        }

        return dubboServices;
    }


    static Map<String, MicroServiceInstance> dubboServiceInstances(String pathOfProviders, boolean ignoreSynced) {
        Map<String, MicroServiceInstance> instances = new HashMap<>();
        List<String> providers = zkClient.getChildren(pathOfProviders);
        for (String provider : providers) {
            MicroServiceInstance instance = parseDubboInstance(provider);
            boolean isSynced = instance.getMetadata().containsKey(META_SYNC);
            if (!ignoreSynced || !isSynced) {
                instances.put(String.format("%s/%s", pathOfProviders, provider), instance);
            }
        }
        return instances;
    }

    static MicroServiceInstance parseDubboInstance(String dubboProvider) {
        String url = RestUtils.urlDecode(dubboProvider);
        UriComponents uri = UriComponentsBuilder.fromUriString(url).build();

        Map<String, String> meta = new HashMap<>();
        meta.put("host", uri.getHost());
        meta.put("port", String.valueOf(uri.getPort()));
        uri.getQueryParams().forEach((key, val) -> {
            meta.put(key, val.get(0));
        });

        return new MicroServiceInstance(uri.getScheme(), meta);
    }

    static void addMicroServiceToResponse(String namespace, ResponseProto.DiscoverResponse.Builder resBuilder,
                                          Map<String, MicroService> dubboServices) {
        dubboServices.forEach((app, ds) -> {
            ServiceProto.Service.Builder svrBuilder = ServiceProto.Service.newBuilder();
            svrBuilder.setNamespace(ResponseUtils.toStringValue(namespace))
                    .setName(ResponseUtils.toStringValue(app))
                    .putAllMetadata(ds.getMetadata());

            Set<ServiceAlias> aliases = AbstractRegistryCenter.ServiceAlias.get(app);
            for (String inf : ds.getInterfaces()) {
                aliases.add(new ServiceAlias(app, namespace, inf));
            }

            resBuilder.addServices(svrBuilder);
        });
    }

    static Map<String, MicroService> allMicroServices(String zkPath) {//SpringCloud mode
        Map<String, MicroService> microServices = new HashMap<>();

        List<String> services = zkClient.getChildren(zkPath);
        for (String svr : services) {
            List<String> instances = zkClient.getChildren(String.format("%s/%s", zkPath, svr));
            if (instances.isEmpty())
                continue;
            MicroService ms = new MicroService(false, new HashMap<>());
            microServices.put(svr, ms);
            for (String instance : instances) {
                String data = zkClient.getData(String.format("%s/%s/%s", zkPath, svr, instance));
                if (data == null)
                    continue;
                addMicroInstances(ms, gson.fromJson(data, HashMap.class));
            }
        }

        return microServices;
    }

    private static void addMicroInstances(MicroService ms, Map<String, Object> json) {
        String address = (String) json.get("address");
        Number port = (Number) json.get("port");
        Number sslPort = (Number) json.get("null");
        int iPort = 0;
        String protocol = "http";
        if (sslPort != null) {
            protocol = "https";
            iPort = sslPort.intValue();
        } else {
            iPort = port.intValue();
        }

        Map<String, Object> payload = (Map) json.get("payload");
        MicroServiceInstance msi = new MicroServiceInstance(protocol, address, iPort, (Map) payload.get("metadata"));
        ms.getInstances().add(msi);
    }

    static ServiceProto.Instance toProtoInstance(Service service, MicroServiceInstance dsi) {
        ServiceProto.Instance.Builder builder = ServiceProto.Instance.newBuilder();

        builder.setNamespace(ResponseUtils.toStringValue(service.getNamespace()));
        builder.setService(ResponseUtils.toStringValue(service.getService()));
        builder.setProtocol(ResponseUtils.toStringValue(dsi.getProtocol()));
        builder.setHost(ResponseUtils.toStringValue(dsi.getHost()));
        builder.setPort(ResponseUtils.toUInt32Value(dsi.getPort()));
        builder.setIsolate(ResponseUtils.toBooleanValue(false));
        builder.setHealthy(ResponseUtils.toBooleanValue(true));
        builder.putAllMetadata(dsi.getMetadata());

        return builder.build();
    }

    static String getPathOfProviders(String inf) {
        return String.format("/dubbo/%s/providers", inf);
    }

    static boolean isDubboService(String path) {
        return path.contains("dubbo");
    }

    static Set<String> paramsIgnored = new HashSet<>(Arrays.asList("zone", "path", "region", "campus"));
    static String toProviderPath(ServiceProto.Instance instance) {
        String protocol = instance.getProtocol().getValue();
        if (!"dubbo".equals(protocol))
            return null;

        String svr = instance.getService().getValue();
        String host = instance.getHost().getValue();
        int port = instance.getPort().getValue();

        StringBuilder params = new StringBuilder(META_SYNC).append("=");
        for (Map.Entry<String, String> e : instance.getMetadataMap().entrySet()) {
            if (!paramsIgnored.contains(e.getKey())) {
                params.append("&").append(e.getKey()).append("=").append(e.getValue());
            }
        }
        String dubboUrl = String.format("dubbo://%s:%s/%s?%s", host, port, svr, params);
        return String.format("/dubbo/%s/providers/%s", svr, URLEncoder.encode(dubboUrl));
    }
}
