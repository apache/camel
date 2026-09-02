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
package org.apache.camel.component.alibaba.oss;

import java.io.IOException;
import java.io.InputStream;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.utils.IOUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.oss.constants.OSSConstants;
import org.apache.camel.component.alibaba.oss.constants.OSSHeaders;
import org.apache.camel.component.alibaba.oss.constants.OSSProperties;
import org.apache.camel.component.alibaba.oss.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OSSUtils {
    private static final Logger LOG = LoggerFactory.getLogger(OSSUtils.class);

    private OSSUtils() {
    }

    public static ClientConfigurations createClientConfigurations(OSSEndpoint endpoint, Exchange exchange) {
        String operation = resolveString(exchange, OSSProperties.OPERATION, endpoint.getOperation());
        if (ObjectHelper.isEmpty(operation)) {
            LOG.error("No operation name given. Cannot proceed with OSS operations.");
            throw new IllegalArgumentException("Operation name not found");
        }

        String bucketName = resolveString(exchange, OSSProperties.BUCKET_NAME, endpoint.getBucketName());
        String objectName = resolveString(exchange, OSSProperties.OBJECT_NAME, endpoint.getObjectName());
        String sourceBucketName = resolveString(exchange, OSSProperties.SOURCE_BUCKET_NAME, null);
        String sourceObjectName = resolveString(exchange, OSSProperties.SOURCE_OBJECT_NAME, null);
        String prefix = resolveString(exchange, OSSProperties.PREFIX, endpoint.getPrefix());
        Integer maxKeys = resolveInteger(exchange, OSSProperties.MAX_KEYS, endpoint.getMaxKeys());

        return new ClientConfigurations(
                operation,
                bucketName,
                objectName,
                sourceBucketName,
                sourceObjectName,
                prefix,
                maxKeys);
    }

    private static String resolveString(Exchange exchange, String name, String endpointValue) {
        String value = exchange.getIn().getHeader(name, String.class);
        if (ObjectHelper.isEmpty(value)) {
            value = exchange.getProperty(name, String.class);
        }
        if (ObjectHelper.isEmpty(value)) {
            value = endpointValue;
        }
        return value;
    }

    private static Integer resolveInteger(Exchange exchange, String name, Integer endpointValue) {
        Integer value = exchange.getIn().getHeader(name, Integer.class);
        if (value == null) {
            value = exchange.getProperty(name, Integer.class);
        }
        if (value == null) {
            value = endpointValue;
        }
        return value;
    }

    /**
     * Maps the OSS object along with all its metadata into the exchange
     */
    public static void mapOssObject(Exchange exchange, String bucketName, String objectKey, GetObjectResult result)
            throws IOException {
        Message message = exchange.getIn();

        try (InputStream stream = result.body()) {
            message.setBody(IOUtils.toByteArray(stream));
        }

        message.setHeader(OSSHeaders.BUCKET_NAME, bucketName);
        message.setHeader(OSSHeaders.OBJECT_KEY, objectKey);
        message.setHeader(OSSHeaders.LAST_MODIFIED, result.lastModified());
        message.setHeader(OSSHeaders.CONTENT_LENGTH, result.contentLength());
        message.setHeader(OSSHeaders.CONTENT_TYPE, result.contentType());
        message.setHeader(OSSHeaders.ETAG, result.eTag());
        message.setHeader(OSSHeaders.CONTENT_MD5, result.contentMd5());
        message.setHeader(OSSHeaders.FILE_NAME, objectKey);

        if (objectKey != null && objectKey.endsWith("/")) {
            message.setHeader(OSSHeaders.OBJECT_TYPE, OSSConstants.FOLDER);
        } else {
            message.setHeader(OSSHeaders.OBJECT_TYPE, OSSConstants.FILE);
        }
    }

    public static void mapOssObject(
            Exchange exchange, String bucketName, String objectKey, GetObjectResult result,
            byte[] body) {
        Message message = exchange.getIn();
        message.setBody(body);

        message.setHeader(OSSHeaders.BUCKET_NAME, bucketName);
        message.setHeader(OSSHeaders.OBJECT_KEY, objectKey);
        message.setHeader(OSSHeaders.LAST_MODIFIED, result.lastModified());
        message.setHeader(OSSHeaders.CONTENT_LENGTH, result.contentLength());
        message.setHeader(OSSHeaders.CONTENT_TYPE, result.contentType());
        message.setHeader(OSSHeaders.ETAG, result.eTag());
        message.setHeader(OSSHeaders.CONTENT_MD5, result.contentMd5());
        message.setHeader(OSSHeaders.FILE_NAME, objectKey);

        if (objectKey != null && objectKey.endsWith("/")) {
            message.setHeader(OSSHeaders.OBJECT_TYPE, OSSConstants.FOLDER);
        } else {
            message.setHeader(OSSHeaders.OBJECT_TYPE, OSSConstants.FILE);
        }
    }

    public static RuntimeCamelException wrapIOException(IOException e) {
        return new RuntimeCamelException(e);
    }

    public static OSSClient createClient(OSSEndpoint endpoint) {
        String accessKey = resolveAccessKey(endpoint);
        String secretKey = resolveSecretKey(endpoint);
        String region = endpoint.getRegion();
        String endpointUrl = endpoint.getEndpoint();

        if (ObjectHelper.isEmpty(region) && ObjectHelper.isEmpty(endpointUrl)) {
            throw new IllegalArgumentException("Region/endpoint not found");
        }

        OSSClientBuilder clientBuilder = OSSClient.newBuilder()
                .credentialsProvider(new StaticCredentialsProvider(accessKey, secretKey));

        if (ObjectHelper.isNotEmpty(region)) {
            clientBuilder.region(region);
        }
        if (ObjectHelper.isNotEmpty(endpointUrl)) {
            clientBuilder.endpoint(endpointUrl);
        }

        return clientBuilder.build();
    }

    private static String resolveAccessKey(OSSEndpoint endpoint) {
        if (ObjectHelper.isNotEmpty(endpoint.getAccessKey())) {
            return endpoint.getAccessKey();
        }
        ServiceKeys serviceKeys = endpoint.getServiceKeys();
        if (serviceKeys != null && ObjectHelper.isNotEmpty(serviceKeys.getAccessKey())) {
            return serviceKeys.getAccessKey();
        }
        throw new IllegalArgumentException("Authentication parameter 'access key (AK)' not found");
    }

    private static String resolveSecretKey(OSSEndpoint endpoint) {
        if (ObjectHelper.isNotEmpty(endpoint.getSecretKey())) {
            return endpoint.getSecretKey();
        }
        ServiceKeys serviceKeys = endpoint.getServiceKeys();
        if (serviceKeys != null && ObjectHelper.isNotEmpty(serviceKeys.getSecretKey())) {
            return serviceKeys.getSecretKey();
        }
        throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
    }
}
