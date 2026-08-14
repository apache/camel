/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.alibaba.sls;

import java.util.HashMap;
import java.util.Map;

import com.aliyun.sls20201230.Client;
import com.aliyun.sls20201230.models.GetLogsRequest;
import com.aliyun.sls20201230.models.GetLogsResponse;
import com.aliyun.sls20201230.models.ListLogStoresRequest;
import com.aliyun.sls20201230.models.ListLogStoresResponse;
import com.aliyun.sls20201230.models.ListLogStoresResponseBody;
import com.aliyun.sls20201230.models.LogGroup;
import com.aliyun.sls20201230.models.PutLogsRequest;
import com.aliyun.sls20201230.models.PutLogsResponse;
import com.aliyun.teaopenapi.models.Config;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.OpenApiClientSupport;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.sls.constants.SLSProperties;
import org.apache.camel.component.alibaba.sls.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

public final class SLSUtils {

    private SLSUtils() {
    }

    public static Client createClient(SLSEndpoint endpoint) throws Exception {
        if (ObjectHelper.isEmpty(endpoint.getRegion()) && ObjectHelper.isEmpty(endpoint.getEndpoint())) {
            throw new IllegalArgumentException("Region or endpoint is required");
        }

        return new Client(
                createTeaConfig(
                        endpoint.getAccessKey(),
                        endpoint.getSecretKey(),
                        endpoint.getServiceKeys(),
                        endpoint.getRegion(),
                        endpoint.getEndpoint()));
    }

    private static Config createTeaConfig(
            String accessKey, String secretKey, ServiceKeys serviceKeys, String region, String endpoint) {
        Config config = new Config();
        config.setAccessKeyId(OpenApiClientSupport.resolveAccessKey(accessKey, serviceKeys));
        config.setAccessKeySecret(OpenApiClientSupport.resolveSecretKey(secretKey, serviceKeys));
        if (ObjectHelper.isNotEmpty(region)) {
            config.setRegionId(region);
        }
        if (ObjectHelper.isNotEmpty(endpoint)) {
            config.setEndpoint(endpoint);
        }
        return config;
    }

    public static ClientConfigurations createClientConfigurations(SLSEndpoint endpoint, Exchange exchange) {
        ClientConfigurations configuration = new ClientConfigurations();
        configuration
                .setOperation(OpenApiClientSupport.resolveString(exchange, SLSProperties.OPERATION, endpoint.getOperation()));
        configuration.setProject(
                OpenApiClientSupport.resolveString(exchange, SLSProperties.PROJECT, endpoint.getProject()));
        configuration.setLogStoreName(
                OpenApiClientSupport.resolveString(exchange, SLSProperties.LOG_STORE_NAME, endpoint.getLogStoreName()));
        configuration.setQuery(OpenApiClientSupport.resolveString(exchange, SLSProperties.QUERY, endpoint.getQuery()));
        configuration.setFrom(OpenApiClientSupport.resolveInteger(exchange, SLSProperties.FROM, endpoint.getFrom()));
        configuration.setTo(OpenApiClientSupport.resolveInteger(exchange, SLSProperties.TO, endpoint.getTo()));
        configuration.setLine(resolveLong(exchange, SLSProperties.LINE, endpoint.getLine()));
        configuration.setOffset(resolveLong(exchange, SLSProperties.OFFSET, endpoint.getOffset()));
        configuration.setTopic(OpenApiClientSupport.resolveString(exchange, SLSProperties.TOPIC, endpoint.getTopic()));
        configuration.setReverse(resolveBoolean(exchange, SLSProperties.REVERSE, endpoint.getReverse()));
        configuration.setLogstoreName(
                OpenApiClientSupport.resolveString(exchange, SLSProperties.LOGSTORE_NAME, null));
        configuration.setMode(OpenApiClientSupport.resolveString(exchange, SLSProperties.MODE, null));
        configuration.setListOffset(OpenApiClientSupport.resolveInteger(exchange, SLSProperties.OFFSET, null));
        configuration.setSize(OpenApiClientSupport.resolveInteger(exchange, SLSProperties.SIZE, null));
        configuration.setTelemetryType(
                OpenApiClientSupport.resolveString(exchange, SLSProperties.TELEMETRY_TYPE, null));
        return configuration;
    }

    public static PutLogsRequest resolvePutLogsRequest(Exchange exchange) {
        Object body = exchange.getMessage().getBody();
        if (body instanceof PutLogsRequest putLogsRequest) {
            return putLogsRequest;
        }
        if (body instanceof LogGroup logGroup) {
            return new PutLogsRequest().setBody(logGroup);
        }
        throw new IllegalArgumentException("Body must be LogGroup or PutLogsRequest for putLogs");
    }

    public static GetLogsRequest buildGetLogsRequest(ClientConfigurations configuration) {
        GetLogsRequest request = new GetLogsRequest();
        if (ObjectHelper.isNotEmpty(configuration.getQuery())) {
            request.setQuery(configuration.getQuery());
        }
        if (configuration.getFrom() != null) {
            request.setFrom(configuration.getFrom());
        }
        if (configuration.getTo() != null) {
            request.setTo(configuration.getTo());
        }
        if (configuration.getLine() != null) {
            request.setLine(configuration.getLine());
        }
        if (configuration.getOffset() != null) {
            request.setOffset(configuration.getOffset());
        }
        if (ObjectHelper.isNotEmpty(configuration.getTopic())) {
            request.setTopic(configuration.getTopic());
        }
        if (configuration.getReverse() != null) {
            request.setReverse(configuration.getReverse());
        }
        return request;
    }

    public static ListLogStoresRequest buildListLogStoresRequest(ClientConfigurations configuration) {
        ListLogStoresRequest request = new ListLogStoresRequest();
        if (ObjectHelper.isNotEmpty(configuration.getLogstoreName())) {
            request.setLogstoreName(configuration.getLogstoreName());
        }
        if (ObjectHelper.isNotEmpty(configuration.getMode())) {
            request.setMode(configuration.getMode());
        }
        if (configuration.getListOffset() != null) {
            request.setOffset(configuration.getListOffset());
        }
        if (configuration.getSize() != null) {
            request.setSize(configuration.getSize());
        }
        if (ObjectHelper.isNotEmpty(configuration.getTelemetryType())) {
            request.setTelemetryType(configuration.getTelemetryType());
        }
        return request;
    }

    public static Map<String, Object> toPutLogsMap(PutLogsResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", response.getStatusCode());
        map.put("headers", response.getHeaders());
        return map;
    }

    public static Map<String, Object> toGetLogsMap(GetLogsResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", response.getStatusCode());
        map.put("headers", response.getHeaders());
        map.put("body", response.getBody());
        return map;
    }

    public static Map<String, Object> toListLogStoresMap(ListLogStoresResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", response.getStatusCode());
        map.put("headers", response.getHeaders());
        ListLogStoresResponseBody body = response.getBody();
        if (body != null) {
            map.put("count", body.getCount());
            map.put("logstores", body.getLogstores());
            map.put("total", body.getTotal());
        }
        return map;
    }

    public static String extractRequestId(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        String requestId = headers.get("x-log-requestid");
        if (ObjectHelper.isEmpty(requestId)) {
            requestId = headers.get("x-log-requestId");
        }
        return requestId;
    }

    private static Long resolveLong(Exchange exchange, String name, Long endpointValue) {
        Long value = exchange.getIn().getHeader(name, Long.class);
        if (value == null) {
            value = exchange.getProperty(name, Long.class);
        }
        if (value == null) {
            value = endpointValue;
        }
        return value;
    }

    private static Boolean resolveBoolean(Exchange exchange, String name, Boolean endpointValue) {
        Boolean value = exchange.getIn().getHeader(name, Boolean.class);
        if (value == null) {
            value = exchange.getProperty(name, Boolean.class);
        }
        if (value == null) {
            value = endpointValue;
        }
        return value;
    }
}
