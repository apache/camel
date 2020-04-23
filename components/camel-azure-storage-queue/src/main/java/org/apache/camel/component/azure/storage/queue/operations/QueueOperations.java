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
import com.azure.storage.queue.models.SendMessageResult;
import com.azure.storage.queue.models.UpdateMessageResult;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueConstants;
import org.apache.camel.component.azure.storage.queue.QueueExchangeHeaders;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related to {@link com.azure.storage.queue.QueueClient}. This is at the queue level
 */
public class QueueOperations {

    private final QueueConfiguration configuration;
    private final QueueClientWrapper client;

    public QueueOperations(final QueueConfiguration configuration, final QueueClientWrapper client) {
        ObjectHelper.notNull(client, "client can not be null.");

        this.configuration = configuration;
        this.client = client;
    }

    public QueueOperationResponse createQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.create(null, configuration.getTimeout()));
        }

        final Map<String, String> metadata = QueueExchangeHeaders.getMetadataFromHeaders(exchange);
        final Duration timeout = getTimeout(exchange);

        return buildResponseWithEmptyBody(client.create(metadata, timeout));
    }

    public QueueOperationResponse clearQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.clearMessages(configuration.getTimeout()));
        }

        final Duration timeout = getTimeout(exchange);

        return buildResponseWithEmptyBody(client.clearMessages(timeout));
    }

    public QueueOperationResponse deleteQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.delete(configuration.getTimeout()));
        }

        final Duration timeout = getTimeout(exchange);

        return buildResponseWithEmptyBody(client.delete(timeout));
    }

    public QueueOperationResponse sendMessage(final Exchange exchange) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");

        final boolean queueCreated = QueueExchangeHeaders.getQueueCreatedFlagFromHeaders(exchange);

        if (!queueCreated) {
            createQueue(exchange);
        }

        final String text = QueueExchangeHeaders.getMessageTextFromHeaders(exchange);
        final Duration visibilityTimeout = getVisibilityTimeout(exchange);
        final Duration timeToLive = getTimeToLive(exchange);
        final Duration timeout = getTimeout(exchange);

        return buildResponseWithEmptyBody(client.sendMessage(text, visibilityTimeout, timeToLive, timeout));
    }

    public QueueOperationResponse deleteMessage(final Exchange exchange) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");

        final String messageId = QueueExchangeHeaders.getMessageIdFromHeaders(exchange);
        final String popReceipt = QueueExchangeHeaders.getPopReceiptFromHeaders(exchange);
        final Duration timeout = getTimeout(exchange);

        if (ObjectHelper.isEmpty(messageId)) {
            throw new IllegalArgumentException(String.format("Message ID must be specified in camel headers '%s' for deleteMessage "
                    + "operation.", QueueConstants.MESSAGE_ID));
        }

        if (ObjectHelper.isEmpty(popReceipt)) {
            throw new IllegalArgumentException(String.format("Message Pop Receipt must be specified in camel headers '%s' for deleteMessage "
                    + "operation.", QueueConstants.POP_RECEIPT));
        }

        return buildResponseWithEmptyBody(client.deleteMessage(messageId, popReceipt, timeout));
    }

    public QueueOperationResponse receiveMessages(final Exchange exchange) {
        if (exchange == null) {
            return new QueueOperationResponse(client.receiveMessages(configuration.getMaxMessages(), configuration.getVisibilityTimeout(), configuration.getTimeout()));
        }

        final Integer maxMessages = getMaxMessages(exchange);
        final Duration visibilityTimeout = getVisibilityTimeout(exchange);
        final Duration timeout = getTimeout(exchange);

        return new QueueOperationResponse(client.receiveMessages(maxMessages, visibilityTimeout, timeout));
    }

    public QueueOperationResponse peekMessages(final Exchange exchange) {
        if (exchange == null) {
            return new QueueOperationResponse(client.peekMessages(configuration.getMaxMessages(), configuration.getTimeout()));
        }

        final Integer maxMessages = getMaxMessages(exchange);
        final Duration timeout = getTimeout(exchange);

        return new QueueOperationResponse(client.peekMessages(maxMessages, timeout));
    }

    public QueueOperationResponse updateMessage(final Exchange exchange) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");

        final String updatedText = QueueExchangeHeaders.getMessageTextFromHeaders(exchange);
        final String messageId = QueueExchangeHeaders.getMessageIdFromHeaders(exchange);
        final String popReceipt = QueueExchangeHeaders.getPopReceiptFromHeaders(exchange);
        final Duration visibilityTimeout = getVisibilityTimeout(exchange);
        final Duration timeout = getTimeout(exchange);

        if (ObjectHelper.isEmpty(messageId)) {
            throw new IllegalArgumentException(String.format("Message ID must be specified in camel headers '%s' for updateMessage "
                    + "operation.", QueueConstants.MESSAGE_ID));
        }

        if (ObjectHelper.isEmpty(popReceipt)) {
            throw new IllegalArgumentException(String.format("Message Pop Receipt must be specified in camel headers '%s' for updateMessage "
                    + "operation.", QueueConstants.POP_RECEIPT));
        }

        if (ObjectHelper.isEmpty(visibilityTimeout)) {
            throw new IllegalArgumentException(String.format("Visibility Timeout must be specified in camel headers '%s' for updateMessage "
                    + "operation.", QueueConstants.VISIBILITY_TIMEOUT));
        }

        final Response<UpdateMessageResult> response = client.updateMessage(messageId, popReceipt, updatedText, visibilityTimeout, timeout);
        final QueueExchangeHeaders headers = new QueueExchangeHeaders()
                .timeNextVisible(response.getValue().getTimeNextVisible())
                .popReceipt(response.getValue().getPopReceipt())
                .httpHeaders(response.getHeaders());

        return new QueueOperationResponse(true, headers.toMap());
    }

    @SuppressWarnings("rawtypes")
    private QueueOperationResponse buildResponseWithEmptyBody(final Response response) {
        return buildResponse(response, true);
    }

    @SuppressWarnings("rawtypes")
    private QueueOperationResponse buildResponse(final Response response, final boolean emptyBody) {
        final Object body = emptyBody ? true : response.getValue();
        QueueExchangeHeaders exchangeHeaders;

        if (response.getValue() instanceof SendMessageResult) {
            exchangeHeaders = QueueExchangeHeaders.createQueueExchangeHeadersFromSendMessageResult((SendMessageResult) response.getValue());
        } else {
            exchangeHeaders = new QueueExchangeHeaders();
        }

        exchangeHeaders.httpHeaders(response.getHeaders());

        return new QueueOperationResponse(body, exchangeHeaders.toMap());
    }

    private Duration getVisibilityTimeout(final Exchange exchange) {
        return ObjectHelper.isEmpty(QueueExchangeHeaders.getVisibilityTimeout(exchange)) ? configuration.getVisibilityTimeout()
                : QueueExchangeHeaders.getVisibilityTimeout(exchange);
    }

    private Duration getTimeToLive(final Exchange exchange) {
        return ObjectHelper.isEmpty(QueueExchangeHeaders.getTimeToLiveFromHeaders(exchange)) ? configuration.getTimeToLive()
                : QueueExchangeHeaders.getTimeToLiveFromHeaders(exchange);
    }

    private Duration getTimeout(final Exchange exchange) {
        return ObjectHelper.isEmpty(QueueExchangeHeaders.getTimeoutFromHeaders(exchange)) ? configuration.getTimeout()
                : QueueExchangeHeaders.getTimeoutFromHeaders(exchange);
    }

    private Integer getMaxMessages(final Exchange exchange) {
        return ObjectHelper.isEmpty(QueueExchangeHeaders.getMaxMessagesFromHeaders(exchange)) ? configuration.getMaxMessages()
                : QueueExchangeHeaders.getMaxMessagesFromHeaders(exchange);
    }

}
