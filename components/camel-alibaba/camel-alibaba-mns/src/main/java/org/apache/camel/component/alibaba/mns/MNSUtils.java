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
package org.apache.camel.component.alibaba.mns;

import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.client.MNSClientBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.component.alibaba.mns.constants.MNSProperties;
import org.apache.camel.component.alibaba.mns.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

public final class MNSUtils {

    private static final String TOPIC_PATH_PREFIX = "topic:";

    private MNSUtils() {
    }

    public static void configurePath(String remaining, MNSEndpoint endpoint) {
        if (remaining != null && remaining.startsWith(TOPIC_PATH_PREFIX)) {
            endpoint.setTopicEndpoint(true);
            endpoint.setTopicName(remaining.substring(TOPIC_PATH_PREFIX.length()));
            endpoint.setQueueName(remaining.substring(TOPIC_PATH_PREFIX.length()));
        } else {
            endpoint.setQueueName(remaining);
        }
    }

    public static MNSClient createClient(MNSEndpoint endpoint) {
        String accessKey = resolveAccessKey(endpoint);
        String secretKey = resolveSecretKey(endpoint);

        if (ObjectHelper.isEmpty(endpoint.getAccountEndpoint())) {
            throw new IllegalArgumentException("accountEndpoint is required");
        }
        if (ObjectHelper.isEmpty(endpoint.getRegion())) {
            throw new IllegalArgumentException("region is required");
        }

        return MNSClientBuilder.create()
                .accessKeyId(accessKey)
                .accessKeySecret(secretKey)
                .accountEndpoint(endpoint.getAccountEndpoint())
                .region(endpoint.getRegion())
                .build();
    }

    public static ClientConfigurations createClientConfigurations(MNSEndpoint endpoint, Exchange exchange) {
        return new ClientConfigurations(
                resolveOperation(endpoint, exchange),
                resolveAccessKey(endpoint),
                resolveSecretKey(endpoint),
                endpoint.getRegion(),
                endpoint.getAccountEndpoint(),
                resolveQueueName(endpoint, exchange),
                resolveTopicName(endpoint, exchange));
    }

    public static String resolveOperation(MNSEndpoint endpoint, Exchange exchange) {
        String operation = exchange.getProperty(MNSProperties.OPERATION, String.class);
        if (ObjectHelper.isEmpty(operation)) {
            operation = exchange.getIn().getHeader(MNSProperties.OPERATION, String.class);
        }
        if (ObjectHelper.isEmpty(operation)) {
            operation = endpoint.resolveOperation();
        }
        return operation;
    }

    public static String resolveQueueName(MNSEndpoint endpoint, Exchange exchange) {
        String queueName = exchange.getProperty(MNSProperties.QUEUE_NAME, String.class);
        if (ObjectHelper.isEmpty(queueName)) {
            queueName = endpoint.getQueueName();
        }
        return queueName;
    }

    public static String resolveTopicName(MNSEndpoint endpoint, Exchange exchange) {
        String topicName = exchange.getProperty(MNSProperties.TOPIC_NAME, String.class);
        if (ObjectHelper.isEmpty(topicName)) {
            topicName = exchange.getIn().getHeader(MNSProperties.TOPIC_NAME, String.class);
        }
        if (ObjectHelper.isEmpty(topicName)) {
            topicName = endpoint.resolveTopicName();
        }
        return topicName;
    }

    public static String resolveReceiptHandle(Exchange exchange) {
        String receiptHandle = exchange.getIn().getHeader(MNSHeaders.RECEIPT_HANDLE, String.class);
        if (ObjectHelper.isEmpty(receiptHandle)) {
            receiptHandle = exchange.getProperty(MNSHeaders.RECEIPT_HANDLE, String.class);
        }
        return receiptHandle;
    }

    public static String resolveMessageBody(Exchange exchange) {
        Object body = exchange.getMessage().getBody();
        if (body == null) {
            throw new IllegalArgumentException("exchange body cannot be null / empty");
        }
        if (body instanceof String stringBody) {
            return stringBody;
        }
        return exchange.getMessage().getBody(String.class);
    }

    private static String resolveAccessKey(MNSEndpoint endpoint) {
        if (ObjectHelper.isNotEmpty(endpoint.getAccessKey())) {
            return endpoint.getAccessKey();
        }
        ServiceKeys serviceKeys = endpoint.getServiceKeys();
        if (serviceKeys != null && ObjectHelper.isNotEmpty(serviceKeys.getAccessKey())) {
            return serviceKeys.getAccessKey();
        }
        throw new IllegalArgumentException("authentication parameter 'access key (AK)' not found");
    }

    private static String resolveSecretKey(MNSEndpoint endpoint) {
        if (ObjectHelper.isNotEmpty(endpoint.getSecretKey())) {
            return endpoint.getSecretKey();
        }
        ServiceKeys serviceKeys = endpoint.getServiceKeys();
        if (serviceKeys != null && ObjectHelper.isNotEmpty(serviceKeys.getSecretKey())) {
            return serviceKeys.getSecretKey();
        }
        throw new IllegalArgumentException("authentication parameter 'secret key (SK)' not found");
    }
}
