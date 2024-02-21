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
package org.apache.camel.component.azure.storage.queue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.queue.models.QueueMessageItem;
import com.azure.storage.queue.models.QueuesSegmentOptions;
import com.azure.storage.queue.models.SendMessageResult;
import org.apache.camel.Exchange;

public class QueueExchangeHeaders {

    private final Map<String, Object> headers = new HashMap<>();

    public static QueueExchangeHeaders createQueueExchangeHeadersFromSendMessageResult(final SendMessageResult result) {
        return new QueueExchangeHeaders()
                .messageId(result.getMessageId())
                .insertionTime(result.getInsertionTime())
                .expirationTime(result.getExpirationTime())
                .popReceipt(result.getPopReceipt())
                .timeNextVisible(result.getTimeNextVisible());
    }

    public static QueueExchangeHeaders createQueueExchangeHeadersFromQueueMessageItem(final QueueMessageItem item) {
        return new QueueExchangeHeaders()
                .messageId(item.getMessageId())
                .insertionTime(item.getInsertionTime())
                .expirationTime(item.getExpirationTime())
                .popReceipt(item.getPopReceipt())
                .timeNextVisible(item.getTimeNextVisible())
                .dequeueCount(item.getDequeueCount());
    }

    public Map<String, Object> toMap() {
        return headers;
    }

    public static QueuesSegmentOptions getQueuesSegmentOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.QUEUES_SEGMENT_OPTIONS, QueuesSegmentOptions.class);
    }

    public static Duration getTimeoutFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.TIMEOUT, Duration.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getMetadataFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.METADATA, Map.class);
    }

    public static Duration getTimeToLiveFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.TIME_TO_LIVE, Duration.class);
    }

    public static Duration getVisibilityTimeout(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.VISIBILITY_TIMEOUT, Duration.class);
    }

    public static Boolean getCreateQueueFlagFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.CREATE_QUEUE, Boolean.class);
    }

    public static String getPopReceiptFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.POP_RECEIPT, String.class);
    }

    public static String getMessageIdFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.MESSAGE_ID, String.class);
    }

    public static Integer getMaxMessagesFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.MAX_MESSAGES, Integer.class);
    }

    public static QueueOperationDefinition getQueueOperationsDefinitionFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.QUEUE_OPERATION, QueueOperationDefinition.class);
    }

    public static String getQueueNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.QUEUE_NAME, String.class);
    }

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return exchange.getIn().getHeader(headerName, classType);
    }

    public QueueExchangeHeaders messageId(final String id) {
        headers.put(QueueConstants.MESSAGE_ID, id);
        return this;
    }

    public QueueExchangeHeaders insertionTime(final OffsetDateTime insertionTime) {
        headers.put(QueueConstants.INSERTION_TIME, insertionTime);
        if (insertionTime != null) {
            long ts = insertionTime.toEpochSecond() * 1000;
            headers.put(Exchange.MESSAGE_TIMESTAMP, ts);
        }
        return this;
    }

    public QueueExchangeHeaders expirationTime(final OffsetDateTime expirationTime) {
        headers.put(QueueConstants.EXPIRATION_TIME, expirationTime);
        return this;
    }

    public QueueExchangeHeaders popReceipt(final String pop) {
        headers.put(QueueConstants.POP_RECEIPT, pop);
        return this;
    }

    public QueueExchangeHeaders timeNextVisible(final OffsetDateTime timeNextVisible) {
        headers.put(QueueConstants.TIME_NEXT_VISIBLE, timeNextVisible);
        return this;
    }

    public QueueExchangeHeaders dequeueCount(final long count) {
        headers.put(QueueConstants.DEQUEUE_COUNT, count);
        return this;
    }

    public QueueExchangeHeaders httpHeaders(final HttpHeaders httpHeaders) {
        headers.put(QueueConstants.RAW_HTTP_HEADERS, httpHeaders);
        return this;
    }
}
