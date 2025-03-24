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
package org.apache.camel.tracing.decorators;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.tracing.SpanAdapter;

public class AzureStorageQueueSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String STORAGE_QUEUE_INSERTION_TIME = "insertionTime";
    static final String STORAGE_QUEUE_EXPIRATION_TIME = "expirationTime";
    static final String STORAGE_QUEUE_TIME_NEXT_VISIBLE = "timeNextVisible";
    static final String STORAGE_QUEUE_DEQUEUE_COUNT = "dequeueCount";
    static final String STORAGE_QUEUE_NAME = "name";
    static final String STORAGE_QUEUE_VISIBILITY_TIMEOUT = "visibilityTimeout";
    static final String STORAGE_QUEUE_TIME_TO_LIVE = "ttl";

    /**
     * Constants copied from {@link org.apache.camel.component.azure.storage.queue.QueueConstants}
     */
    static final String MESSAGE_ID = "CamelAzureStorageQueueMessageId";
    static final String INSERTION_TIME = "CamelAzureStorageQueueInsertionTime";
    static final String EXPIRATION_TIME = "CamelAzureStorageQueueExpirationTime";
    static final String TIME_NEXT_VISIBLE = "CamelAzureStorageQueueTimeNextVisible";
    static final String DEQUEUE_COUNT = "CamelAzureStorageQueueDequeueCount";
    static final String NAME = "CamelAzureStorageQueueName";
    static final String VISIBILITY_TIMEOUT = "CamelAzureStorageQueueVisibilityTimeout";
    static final String TIME_TO_LIVE = "CamelAzureStorageQueueTimeToLive";

    @Override
    public String getComponent() {
        return "azure-storage-queue";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.azure.storage.queue.QueueComponent";
    }

    @Override
    public void pre(SpanAdapter span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);

        OffsetDateTime insertionTime = exchange.getIn().getHeader(INSERTION_TIME, OffsetDateTime.class);
        if (insertionTime != null) {
            span.setTag(STORAGE_QUEUE_INSERTION_TIME, insertionTime.toString());
        }

        OffsetDateTime expirationTime = exchange.getIn().getHeader(EXPIRATION_TIME, OffsetDateTime.class);
        if (expirationTime != null) {
            span.setTag(STORAGE_QUEUE_EXPIRATION_TIME, expirationTime.toString());
        }

        OffsetDateTime timeNextVisible = exchange.getIn().getHeader(TIME_NEXT_VISIBLE, OffsetDateTime.class);
        if (timeNextVisible != null) {
            span.setTag(STORAGE_QUEUE_TIME_NEXT_VISIBLE, timeNextVisible.toString());
        }

        Long dequeueCount = exchange.getIn().getHeader(DEQUEUE_COUNT, Long.class);
        if (dequeueCount != null) {
            span.setTag(STORAGE_QUEUE_DEQUEUE_COUNT, dequeueCount);
        }

        String name = exchange.getIn().getHeader(NAME, String.class);
        if (name != null) {
            span.setTag(STORAGE_QUEUE_NAME, name);
        }

        Duration visibilityTimeout = exchange.getIn().getHeader(VISIBILITY_TIMEOUT, Duration.class);
        if (visibilityTimeout != null) {
            span.setTag(STORAGE_QUEUE_VISIBILITY_TIMEOUT, visibilityTimeout.toString());
        }

        Duration timeToLive = exchange.getIn().getHeader(TIME_TO_LIVE, Duration.class);
        if (timeToLive != null) {
            span.setTag(STORAGE_QUEUE_TIME_TO_LIVE, timeToLive.toString());
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(MESSAGE_ID, String.class);
    }
}
