/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package cn.polarismesh.polaris.sync.reporter.plugins.barad;

import cn.polarismesh.polaris.sync.common.rest.RestOperator;
import cn.polarismesh.polaris.sync.common.rest.RestResponse;
import cn.polarismesh.polaris.sync.common.rest.RestUtils;
import cn.polarismesh.polaris.sync.extension.report.RegistryHealthStatus;
import cn.polarismesh.polaris.sync.extension.report.ReportHandler;
import cn.polarismesh.polaris.sync.extension.report.StatInfo;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.ReportTarget;
import cn.polarismesh.polaris.sync.registry.pb.RegistryProto.ReportTarget.TargetType;
import cn.polarismesh.polaris.sync.reporter.plugins.barad.model.Batch;
import cn.polarismesh.polaris.sync.reporter.plugins.barad.model.Metric;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class BaradReportHandler  implements ReportHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BaradReportHandler.class);

    private String url;

    private String namespace;

    private Map<String, String> options = new HashMap<>();

    private boolean configValid;

    private RestOperator restOperator;

    @Override
    public TargetType getType() {
        return TargetType.balad;
    }

    @Override
    public void init(ReportTarget reportTarget) {
        Map<String, String> optionsMap = reportTarget.getOptionsMap();
        if (optionsMap.containsKey(ReportOptions.KEY_URL)) {
            url = String.format("http://%s", optionsMap.get(ReportOptions.KEY_URL));
        }
        if (!StringUtils.hasText(url)) {
            LOG.error("[Barad] Barad report is inactived due to uri empty");
            configValid = false;
            return;
        }
        if (optionsMap.containsKey(ReportOptions.KEY_NAMESPACE)) {
            namespace = optionsMap.get(ReportOptions.KEY_NAMESPACE);
        }
        if (!StringUtils.hasText(namespace)) {
            LOG.error("[Barad] Barad report is inactived due to namespace empty");
            configValid = false;
            return;
        }
        configValid = true;
        options = new HashMap<>();
        for (Map.Entry<String, String> entry : optionsMap.entrySet()) {
            if (entry.getKey().equals(ReportOptions.KEY_URL) || entry.getKey().equals(ReportOptions.KEY_NAMESPACE)) {
                continue;
            }
            options.put(entry.getKey(), entry.getValue());
        }
        restOperator = new RestOperator();
        LOG.info("[Report] BaradReportHandler has been initialized, config {}", reportTarget);
    }


    @Override
    public void reportStat(StatInfo statInfo) {
        if (!configValid) {
            LOG.warn("[Report][Barad] barad config invalid");
            return;
        }
        long timestamp = buildTimestamp();
        Collection<RegistryHealthStatus> registryHealthStatusList = statInfo.getRegistryHealthStatusList();
        if (CollectionUtils.isEmpty(registryHealthStatusList)) {
            LOG.debug("[Report][Barad] report status info empty");
            return;
        }
        List<Batch> batches = new ArrayList<>();
        for (RegistryHealthStatus registryHealthStatus : registryHealthStatusList) {
            batches.add(buildReportData(registryHealthStatus, timestamp));
        }
        String jsonText = RestUtils.marshalJsonText(batches.toArray(new Batch[0]));
        RestResponse<String> restResponse = restOperator.curlRemoteEndpoint(
                url, HttpMethod.POST, RestUtils.getRequestEntity("", jsonText), String.class);
        if (restResponse.hasNormalResponse()) {
            LOG.info("[Barad] success to report metric to Barad {}", url);
        } else {
            if (restResponse.hasServerError()) {
                LOG.error("[Barad] server error to report metric to {}", url, restResponse.getException());
            } else {
                LOG.error("[Barad] client error to report metric to {}, code {}, info {}", url,
                        restResponse.getRawStatusCode(), restResponse.getStatusText());
            }
        }
    }

    public Batch buildReportData(RegistryHealthStatus registryHealthStatus, long timestamp) {
        Batch batch = new Batch();
        batch.setNamespace(namespace);

        batch.setTimestamp(timestamp);

        Map<String, String> dimension = new HashMap<>(options);
        dimension.put(ReportOptions.KEY_REGISTRY, registryHealthStatus.getDimension().getProductionName());
        batch.setDimension(dimension);

        List<Metric> metrics = new ArrayList<>();
        Metric totalMetric = new Metric();
        totalMetric.setName(ReportOptions.METRIC_KEY_TOTAL);
        totalMetric.setValue(registryHealthStatus.getTotalCount());
        metrics.add(totalMetric);
        Metric errorMetric = new Metric();
        errorMetric.setName(ReportOptions.METRIC_KEY_ERROR);
        errorMetric.setValue(registryHealthStatus.getErrorCount());
        metrics.add(errorMetric);
        Metric successMetric = new Metric();
        successMetric.setName(ReportOptions.METRIC_KEY_SUCCESS);
        successMetric.setValue(registryHealthStatus.getTotalCount() - registryHealthStatus.getErrorCount());
        metrics.add(successMetric);
        batch.setBatch(metrics);
        return batch;
    }

    private long buildTimestamp() {
        long curTimeSec = System.currentTimeMillis()/1000;
        long rest = curTimeSec % 60;
        if (rest < 10) { //超过整分
            return curTimeSec - rest;
        }
        if (rest > 50) { //小于整分
            return curTimeSec + (60 - rest);
        }
        return curTimeSec;
    }
}
