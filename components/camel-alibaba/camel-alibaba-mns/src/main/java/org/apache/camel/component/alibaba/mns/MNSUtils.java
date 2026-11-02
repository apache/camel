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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.client.MNSClientBuilder;
import com.aliyun.mns.model.BaseMessage;
import com.aliyun.mns.model.Message;
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

    /**
     * Sets the body of the exchange as the body of the MNS message to send or publish. Text is sent as it is. Bytes, a
     * stream or a file are sent as their bytes, so that binary data is not changed. A body that has no bytes, such as a
     * number, a Map or a POJO, is sent as its text.
     */
    public static void setMessageBody(Exchange exchange, BaseMessage message) {
        Object body = exchange.getMessage().getBody();
        if (body != null && !(body instanceof String)) {
            byte[] bytes = exchange.getMessage().getBody(byte[].class);
            if (bytes != null) {
                message.setMessageBody(bytes);
                return;
            }
        }
        message.setMessageBody(resolveMessageBody(exchange));
    }

    /**
     * The body of a received MNS message: its text when its bytes are UTF-8 text, else its bytes. The text of binary
     * data, such as the bytes of a file a producer sent, would replace the bytes that are not UTF-8.
     */
    public static Object resolveReceivedBody(Message message) {
        byte[] bytes = message.getMessageBodyAsBytes();
        if (bytes == null) {
            return null;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return bytes;
        }
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
