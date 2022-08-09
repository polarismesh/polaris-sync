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

package cn.polarismesh.polaris.sync.common.rest;

import java.util.List;
import java.util.Random;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class RestOperator {

    private final RestTemplate restTemplate;

    public RestOperator(RestTemplate restTemplate) {
        Assert.notNull(restTemplate, "restTemplate should not be null");
        this.restTemplate = restTemplate;
    }

    public static String pickAddress(List<String> addresses) {
        if (addresses.size() == 1) {
            return addresses.get(0);
        }
        Random random = new Random();
        int i = random.nextInt();
        return addresses.get(i % addresses.size());
    }

    public <T> RestResponse<T> curlRemoteEndpoint(String url, HttpMethod method,
            HttpEntity<?> requestEntity, Class<T> clazz) {
        ResponseEntity<T> queryEntity;
        try {
            queryEntity = restTemplate.exchange(url, method, requestEntity, clazz);
        } catch (RestClientException e) {
            return RestResponse.withRestClientException(e);
        }
        return RestResponse.withNormalResponse(queryEntity);
    }
}
