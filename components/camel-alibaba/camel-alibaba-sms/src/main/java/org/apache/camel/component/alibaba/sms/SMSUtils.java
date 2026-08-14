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
package org.apache.camel.component.alibaba.sms;

import java.util.HashMap;
import java.util.Map;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.OpenApiClientSupport;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.sms.constants.SMSProperties;
import org.apache.camel.component.alibaba.sms.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

public final class SMSUtils {

    private SMSUtils() {
    }

    public static Client createClient(SMSEndpoint endpoint) throws Exception {
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

    public static ClientConfigurations createClientConfigurations(SMSEndpoint endpoint, Exchange exchange) {
        ClientConfigurations configuration = new ClientConfigurations();
        configuration
                .setOperation(OpenApiClientSupport.resolveString(exchange, SMSProperties.OPERATION, endpoint.getOperation()));
        configuration.setPhoneNumbers(
                OpenApiClientSupport.resolveString(exchange, SMSProperties.PHONE_NUMBERS, endpoint.getPhoneNumbers()));
        configuration
                .setSignName(OpenApiClientSupport.resolveString(exchange, SMSProperties.SIGN_NAME, endpoint.getSignName()));
        configuration.setTemplateCode(
                OpenApiClientSupport.resolveString(exchange, SMSProperties.TEMPLATE_CODE, endpoint.getTemplateCode()));
        configuration.setTemplateParam(
                OpenApiClientSupport.resolveString(exchange, SMSProperties.TEMPLATE_PARAM, endpoint.getTemplateParam()));
        configuration.setOutId(OpenApiClientSupport.resolveString(exchange, SMSProperties.OUT_ID, endpoint.getOutId()));
        return configuration;
    }

    public static Map<String, Object> toSendSmsMap(SendSmsResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", response.getStatusCode());
        SendSmsResponseBody body = response.getBody();
        if (body != null) {
            map.put("code", body.getCode());
            map.put("message", body.getMessage());
            map.put("bizId", body.getBizId());
            map.put("requestId", body.getRequestId());
        }
        return map;
    }
}
