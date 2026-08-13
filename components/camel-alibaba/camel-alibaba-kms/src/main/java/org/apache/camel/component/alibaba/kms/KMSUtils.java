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
package org.apache.camel.component.alibaba.kms;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.DecryptResponse;
import com.aliyun.kms20160120.models.DecryptResponseBody;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.EncryptResponseBody;
import com.aliyun.kms20160120.models.GenerateDataKeyResponse;
import com.aliyun.kms20160120.models.GenerateDataKeyResponseBody;
import com.aliyun.teaopenapi.models.Config;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.OpenApiClientSupport;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.kms.constants.KMSProperties;
import org.apache.camel.component.alibaba.kms.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

public final class KMSUtils {

    private KMSUtils() {
    }

    public static Client createClient(KMSEndpoint endpoint) throws Exception {
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

    public static ClientConfigurations createClientConfigurations(KMSEndpoint endpoint, Exchange exchange) {
        ClientConfigurations configuration = new ClientConfigurations();
        configuration
                .setOperation(OpenApiClientSupport.resolveString(exchange, KMSProperties.OPERATION, endpoint.getOperation()));
        configuration.setKeyId(OpenApiClientSupport.resolveString(exchange, KMSProperties.KEY_ID, endpoint.getKeyId()));
        configuration
                .setPlaintext(OpenApiClientSupport.resolveString(exchange, KMSProperties.PLAINTEXT, endpoint.getPlaintext()));
        configuration.setCiphertextBlob(
                OpenApiClientSupport.resolveString(exchange, KMSProperties.CIPHERTEXT_BLOB, endpoint.getCiphertextBlob()));
        configuration.setKeySpec(OpenApiClientSupport.resolveString(exchange, KMSProperties.KEY_SPEC, endpoint.getKeySpec()));
        configuration.setNumberOfBytes(
                OpenApiClientSupport.resolveInteger(exchange, KMSProperties.NUMBER_OF_BYTES, endpoint.getNumberOfBytes()));
        return configuration;
    }

    public static String encodePlaintextForEncrypt(Exchange exchange, ClientConfigurations configuration) throws Exception {
        if (ObjectHelper.isNotEmpty(configuration.getPlaintext())) {
            return Base64.getEncoder().encodeToString(configuration.getPlaintext().getBytes(StandardCharsets.UTF_8));
        }
        Object body = exchange.getMessage().getBody();
        if (body instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (body instanceof String stringBody) {
            return Base64.getEncoder().encodeToString(stringBody.getBytes(StandardCharsets.UTF_8));
        }
        String value = exchange.getMessage().getBody(String.class);
        if (ObjectHelper.isEmpty(value)) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static Map<String, Object> toEncryptMap(EncryptResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", response.getStatusCode());
        EncryptResponseBody body = response.getBody();
        if (body != null) {
            map.put("ciphertextBlob", body.getCiphertextBlob());
            map.put("keyId", body.getKeyId());
            map.put("keyVersionId", body.getKeyVersionId());
            map.put("requestId", body.getRequestId());
        }
        return map;
    }

    public static Map<String, Object> toDecryptMap(DecryptResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", response.getStatusCode());
        DecryptResponseBody body = response.getBody();
        if (body != null) {
            map.put("keyId", body.getKeyId());
            map.put("keyVersionId", body.getKeyVersionId());
            map.put("plaintext", body.getPlaintext());
            map.put("requestId", body.getRequestId());
        }
        return map;
    }

    public static Map<String, Object> toGenerateDataKeyMap(GenerateDataKeyResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put("statusCode", response.getStatusCode());
        GenerateDataKeyResponseBody body = response.getBody();
        if (body != null) {
            map.put("ciphertextBlob", body.getCiphertextBlob());
            map.put("keyId", body.getKeyId());
            map.put("keyVersionId", body.getKeyVersionId());
            map.put("plaintext", body.getPlaintext());
            map.put("requestId", body.getRequestId());
        }
        return map;
    }
}
