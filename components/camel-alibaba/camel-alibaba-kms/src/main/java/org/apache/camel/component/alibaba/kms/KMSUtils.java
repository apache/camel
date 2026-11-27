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
import org.apache.camel.component.alibaba.kms.constants.KMSOperations;
import org.apache.camel.component.alibaba.kms.constants.KMSProperties;
import org.apache.camel.component.alibaba.kms.models.ClientConfigurations;
import org.apache.camel.support.BodyFacts;
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
        String operation = OpenApiClientSupport.resolveString(exchange, KMSProperties.OPERATION, endpoint.getOperation());
        return new ClientConfigurations(
                operation,
                OpenApiClientSupport.resolveString(exchange, KMSProperties.KEY_ID, endpoint.getKeyId()),
                resolvePlaintext(exchange, endpoint.getPlaintext()),
                OpenApiClientSupport.resolveString(exchange, KMSProperties.CIPHERTEXT_BLOB, endpoint.getCiphertextBlob()),
                OpenApiClientSupport.resolveString(exchange, KMSProperties.KEY_SPEC, endpoint.getKeySpec()),
                KMSOperations.GENERATE_DATA_KEY.equals(operation)
                        ? resolveNumberOfBytes(exchange, endpoint.getNumberOfBytes()) : null);
    }

    /**
     * The plaintext of the plaintext header, of the exchange property of the same name, or of the plaintext option, in
     * that order. A Map or a POJO fails, as it does in the message body since CAMEL-26258: Camel reads it only as its
     * toString(), and KMS would encrypt that text.
     */
    private static String resolvePlaintext(Exchange exchange, String endpointValue) {
        String plaintext = asText(exchange, exchange.getIn().getHeader(KMSProperties.PLAINTEXT), "header");
        if (ObjectHelper.isEmpty(plaintext)) {
            plaintext = asText(exchange, exchange.getProperty(KMSProperties.PLAINTEXT), "exchange property");
        }
        if (ObjectHelper.isEmpty(plaintext)) {
            plaintext = endpointValue;
        }
        return plaintext;
    }

    /**
     * The value as text, unless the text is no more than its toString(), which is what ToStringTypeConverter, the
     * fallback that is never promoted, gives for an object no other converter reads as text. The message names the
     * class but never the value, which is the plaintext.
     */
    private static String asText(Exchange exchange, Object value, String where) {
        if (value == null) {
            return null;
        }
        String text = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
        if (text != null && BodyFacts.isOnlyToString(value, text)) {
            throw new IllegalArgumentException(
                    "The " + KMSProperties.PLAINTEXT + " " + where + " must be the plaintext as text, but it is a "
                                               + value.getClass().getName()
                                               + ", which Camel reads only as its toString(). Marshal it first, for"
                                               + " example with marshal().json(), or set it to the text");
        }
        return text;
    }

    /**
     * The number of bytes of the numberOfBytes header, of the exchange property of the same name, or of the
     * numberOfBytes option, in that order. Only generateDataKey calls this, so a value that no other operation reads
     * does not fail that operation, and a value that is not a number says so instead of failing the exchange with a
     * TypeConversionException from the conversion of a header.
     */
    private static Integer resolveNumberOfBytes(Exchange exchange, Integer endpointValue) {
        Integer numberOfBytes = toNumberOfBytes(exchange, exchange.getIn().getHeader(KMSProperties.NUMBER_OF_BYTES),
                "header");
        if (numberOfBytes == null) {
            numberOfBytes = toNumberOfBytes(exchange, exchange.getProperty(KMSProperties.NUMBER_OF_BYTES),
                    "exchange property");
        }
        return numberOfBytes != null ? numberOfBytes : endpointValue;
    }

    private static Integer toNumberOfBytes(Exchange exchange, Object value, String where) {
        if (ObjectHelper.isEmpty(value)) {
            return null;
        }
        Integer numberOfBytes = exchange.getContext().getTypeConverter().tryConvertTo(Integer.class, exchange, value);
        if (numberOfBytes == null) {
            throw new IllegalArgumentException(
                    "The " + KMSProperties.NUMBER_OF_BYTES + " " + where
                                               + " must be a number from 1 to 1024, but it is " + value);
        }
        return numberOfBytes;
    }

    /**
     * The Base64 of the plaintext of encrypt: the plaintext option or header in UTF-8, else the body, a String in UTF-8
     * and any other body as its bytes, so a binary stream or file is encrypted unchanged. Null without a body. A body
     * that does not convert to bytes, such as a Map or a POJO, fails with an {@code InvalidPayloadException}.
     */
    public static String encodePlaintextForEncrypt(Exchange exchange, ClientConfigurations configuration) throws Exception {
        if (ObjectHelper.isNotEmpty(configuration.plaintext())) {
            return Base64.getEncoder().encodeToString(configuration.plaintext().getBytes(StandardCharsets.UTF_8));
        }
        Object body = exchange.getMessage().getBody();
        if (body == null) {
            return null;
        }
        if (body instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (body instanceof String stringBody) {
            return Base64.getEncoder().encodeToString(stringBody.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(exchange.getMessage().getMandatoryBody(byte[].class));
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
