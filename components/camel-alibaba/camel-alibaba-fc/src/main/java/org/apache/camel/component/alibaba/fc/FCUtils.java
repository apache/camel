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
package org.apache.camel.component.alibaba.fc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.aliyun.fc_open20210406.Client;
import com.aliyun.fc_open20210406.models.GetFunctionResponseBody;
import com.aliyun.fc_open20210406.models.InvokeFunctionResponse;
import com.aliyun.teaopenapi.models.Config;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.OpenApiClientSupport;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.fc.constants.FCProperties;
import org.apache.camel.component.alibaba.fc.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

public final class FCUtils {

    private FCUtils() {
    }

    public static Client createClient(FCEndpoint endpoint) throws Exception {
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

    public static ClientConfigurations createClientConfigurations(FCEndpoint endpoint, Exchange exchange) {
        return new ClientConfigurations(
                OpenApiClientSupport.resolveString(exchange, FCProperties.OPERATION, endpoint.getOperation()),
                OpenApiClientSupport.resolveString(exchange, FCProperties.SERVICE_NAME, endpoint.getServiceName()),
                OpenApiClientSupport.resolveString(exchange, FCProperties.FUNCTION_NAME, endpoint.getFunctionName()),
                OpenApiClientSupport.resolveString(exchange, FCProperties.QUALIFIER, endpoint.getQualifier()));
    }

    public static byte[] resolvePayload(Exchange exchange) throws Exception {
        Object body = exchange.getMessage().getBody();
        if (body == null) {
            return new byte[0];
        }
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        if (body instanceof String stringBody) {
            return stringBody.getBytes(StandardCharsets.UTF_8);
        }
        return exchange.getMessage().getMandatoryBody(String.class).getBytes(StandardCharsets.UTF_8);
    }

    public static Map<String, Object> toInvokeFunctionMap(InvokeFunctionResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", response.getStatusCode());
        map.put("headers", response.getHeaders());
        if (response.getBody() != null) {
            map.put("body", response.getBody());
        }
        return map;
    }

    public static Map<String, Object> toGetFunctionMap(GetFunctionResponseBody body) {
        Map<String, Object> map = new HashMap<>();
        if (body == null) {
            return map;
        }
        map.put("functionId", body.getFunctionId());
        map.put("functionName", body.getFunctionName());
        map.put("description", body.getDescription());
        map.put("runtime", body.getRuntime());
        map.put("handler", body.getHandler());
        map.put("timeout", body.getTimeout());
        map.put("memorySize", body.getMemorySize());
        map.put("codeSize", body.getCodeSize());
        map.put("createdTime", body.getCreatedTime());
        map.put("lastModifiedTime", body.getLastModifiedTime());
        map.put("environmentVariables", body.getEnvironmentVariables());
        return map;
    }
}
