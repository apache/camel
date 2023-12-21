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
package org.apache.camel.component.azure.storage.queue.operations;

import java.time.Duration;
import java.util.Map;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.UpdateMessageResult;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueConfigurationOptionsProxy;
import org.apache.camel.component.azure.storage.queue.QueueConstants;
import org.apache.camel.component.azure.storage.queue.QueueExchangeHeaders;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related to {@link com.azure.storage.queue.QueueClient}. This is at the queue level
 */
public class QueueOperations {

    public static final String MISSING_EXCHANGE = "exchange cannot be null";
    private final QueueConfigurationOptionsProxy configurationOptionsProxy;
    private final QueueClientWrapper client;

    public QueueOperations(final QueueConfiguration configuration, final QueueClientWrapper client) {
        ObjectHelper.notNull(client, "client can not be null.");

        this.client = client;
        this.configurationOptionsProxy = new QueueConfigurationOptionsProxy(configuration);
    }

    public QueueOperationResponse createQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.create(null, configurationOptionsProxy.getTimeout(null)));
        }

        final Map<String, String> metadata = configurationOptionsProxy.getMetadata(exchange);
        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        return buildResponseWithEmptyBody(client.create(metadata, timeout));
    }

    public QueueOperationResponse clearQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.clearMessages(configurationOptionsProxy.getTimeout(null)));
        }

        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        return buildResponseWithEmptyBody(client.clearMessages(timeout));
    }

    public QueueOperationResponse deleteQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.delete(configurationOptionsProxy.getTimeout(null)));
        }

        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        return buildResponseWithEmptyBody(client.delete(timeout));
    }

    public QueueOperationResponse sendMessage(final Exchange exchange) {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final boolean queueCreated = configurationOptionsProxy.isCreateQueue(exchange);

        if (queueCreated) {
            createQueue(exchange);
        }

        final String text = exchange.getIn().getBody(String.class);
        final Duration visibilityTimeout = configurationOptionsProxy.getVisibilityTimeout(exchange);
        final Duration timeToLive = configurationOptionsProxy.getTimeToLive(exchange);
        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        return buildResponseWithEmptyBody(client.sendMessage(text, visibilityTimeout, timeToLive, timeout));
    }

    public QueueOperationResponse deleteMessage(final Exchange exchange) {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final String messageId = configurationOptionsProxy.getMessageId(exchange);
        final String popReceipt = configurationOptionsProxy.getPopReceipt(exchange);
        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        if (ObjectHelper.isEmpty(messageId)) {
            throw new IllegalArgumentException(
                    String.format("Message ID must be specified in camel headers '%s' for deleteMessage "
                                  + "operation.",
                            QueueConstants.MESSAGE_ID));
        }

        if (ObjectHelper.isEmpty(popReceipt)) {
            throw new IllegalArgumentException(
                    String.format("Message Pop Receipt must be specified in camel headers '%s' for deleteMessage "
                                  + "operation.",
                            QueueConstants.POP_RECEIPT));
        }

        return buildResponseWithEmptyBody(client.deleteMessage(messageId, popReceipt, timeout));
    }

    public QueueOperationResponse receiveMessages(final Exchange exchange) {
        if (exchange == null) {
            return QueueOperationResponse.create(
                    client.receiveMessages(configurationOptionsProxy.getMaxMessages(null),
                            configurationOptionsProxy.getVisibilityTimeout(null),
                            configurationOptionsProxy.getTimeout(null)));
        }

        final Integer maxMessages = configurationOptionsProxy.getMaxMessages(exchange);
        final Duration visibilityTimeout = configurationOptionsProxy.getVisibilityTimeout(exchange);
        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        return QueueOperationResponse.create(client.receiveMessages(maxMessages, visibilityTimeout, timeout));
    }

    public QueueOperationResponse peekMessages(final Exchange exchange) {
        if (exchange == null) {
            return QueueOperationResponse.create(
                    client.peekMessages(configurationOptionsProxy.getMaxMessages(null),
                            configurationOptionsProxy.getTimeout(null)));
        }

        final Integer maxMessages = configurationOptionsProxy.getMaxMessages(exchange);
        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        return QueueOperationResponse.create(client.peekMessages(maxMessages, timeout));
    }

    public QueueOperationResponse updateMessage(final Exchange exchange) {
        ObjectHelper.notNull(exchange, MISSING_EXCHANGE);

        final String updatedText = exchange.getIn().getBody(String.class);
        final String messageId = configurationOptionsProxy.getMessageId(exchange);
        final String popReceipt = configurationOptionsProxy.getPopReceipt(exchange);
        final Duration visibilityTimeout = configurationOptionsProxy.getVisibilityTimeout(exchange);
        final Duration timeout = configurationOptionsProxy.getTimeout(exchange);

        if (ObjectHelper.isEmpty(messageId)) {
            throw new IllegalArgumentException(
                    String.format("Message ID must be specified in camel headers '%s' for updateMessage "
                                  + "operation.",
                            QueueConstants.MESSAGE_ID));
        }

        if (ObjectHelper.isEmpty(popReceipt)) {
            throw new IllegalArgumentException(
                    String.format("Message Pop Receipt must be specified in camel headers '%s' for updateMessage "
                                  + "operation.",
                            QueueConstants.POP_RECEIPT));
        }

        if (ObjectHelper.isEmpty(visibilityTimeout)) {
            throw new IllegalArgumentException(
                    String.format("Visibility Timeout must be specified in camel headers '%s' for updateMessage "
                                  + "operation.",
                            QueueConstants.VISIBILITY_TIMEOUT));
        }

        final Response<UpdateMessageResult> response
                = client.updateMessage(messageId, popReceipt, updatedText, visibilityTimeout, timeout);
        final QueueExchangeHeaders headers = new QueueExchangeHeaders()
                .timeNextVisible(response.getValue().getTimeNextVisible())
                .popReceipt(response.getValue().getPopReceipt())
                .httpHeaders(response.getHeaders());

        return QueueOperationResponse.createWithEmptyBody(headers.toMap());
    }

    @SuppressWarnings("rawtypes")
    private QueueOperationResponse buildResponseWithEmptyBody(final Response response) {
        return QueueOperationResponse.createWithEmptyBody(response);
    }
}
