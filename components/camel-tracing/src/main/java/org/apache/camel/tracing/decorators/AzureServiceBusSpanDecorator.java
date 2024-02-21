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

public class AzureServiceBusSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String SERVICEBUS_CONTENT_TYPE = "contentType";
    static final String SERVICEBUS_CORRELATION_ID = "correlationId";
    static final String SERVICEBUS_DELIVERY_COUNT = "deliveryCount";
    static final String SERVICEBUS_ENQUEUED_SEQUENCE_NUMBER = "enqueuedSequenceNumber";
    static final String SERVICEBUS_ENQUEUED_TIME = "enqueuedTime";
    static final String SERVICEBUS_EXPIRES_AT = "expiresAt";
    static final String SERVICEBUS_PARTITION_KEY = "partitionKey";
    static final String SERVICEBUS_REPLY_TO_SESSION_ID = "replyToSessionId";
    static final String SERVICEBUS_SESSION_ID = "sessionId";
    static final String SERVICEBUS_TIME_TO_LIVE = "ttl";

    /**
     * Constants copied from {@link org.apache.camel.component.azure.servicebus.ServiceBusConstants}
     */
    static final String CONTENT_TYPE = "CamelAzureServiceBusContentType";
    static final String CORRELATION_ID = "CamelAzureServiceBusCorrelationId";
    static final String DELIVERY_COUNT = "CamelAzureServiceBusDeliveryCount";
    static final String ENQUEUED_SEQUENCE_NUMBER = "CamelAzureServiceBusEnqueuedSequenceNumber";
    static final String ENQUEUED_TIME = "CamelAzureServiceBusEnqueuedTime";
    static final String EXPIRES_AT = "CamelAzureServiceBusExpiresAt";
    static final String MESSAGE_ID = "CamelAzureServiceBusMessageId";
    static final String SESSION_ID = "CamelAzureServiceBusSessionId";
    static final String REPLY_TO_SESSION_ID = "CamelAzureServiceBusReplyToSessionId";
    static final String PARTITION_KEY = "CamelAzureServiceBusPartitionKey";
    static final String TIME_TO_LIVE = "CamelAzureServiceBusTimeToLive";

    @Override
    public String getComponent() {
        return "azure-servicebus";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.azure.servicebus.ServiceBusComponent";
    }

    @Override
    public void pre(SpanAdapter span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);

        String contentType = exchange.getIn().getHeader(CONTENT_TYPE, String.class);
        if (contentType != null) {
            span.setTag(SERVICEBUS_CONTENT_TYPE, contentType);
        }

        String correlationId = exchange.getIn().getHeader(CORRELATION_ID, String.class);
        if (correlationId != null) {
            span.setTag(SERVICEBUS_CORRELATION_ID, correlationId);
        }

        Long deliveryCount = exchange.getIn().getHeader(DELIVERY_COUNT, Long.class);
        if (deliveryCount != null) {
            span.setTag(SERVICEBUS_DELIVERY_COUNT, deliveryCount);
        }

        Long enqueuedSequenceNumber = exchange.getIn().getHeader(ENQUEUED_SEQUENCE_NUMBER, Long.class);
        if (enqueuedSequenceNumber != null) {
            span.setTag(SERVICEBUS_ENQUEUED_SEQUENCE_NUMBER, enqueuedSequenceNumber);
        }

        OffsetDateTime enqueuedTime = exchange.getIn().getHeader(ENQUEUED_TIME, OffsetDateTime.class);
        if (enqueuedTime != null) {
            span.setTag(SERVICEBUS_ENQUEUED_TIME, enqueuedTime.toString());
        }

        OffsetDateTime expiresAt = exchange.getIn().getHeader(EXPIRES_AT, OffsetDateTime.class);
        if (expiresAt != null) {
            span.setTag(SERVICEBUS_EXPIRES_AT, expiresAt.toString());
        }

        String partitionKey = exchange.getIn().getHeader(PARTITION_KEY, String.class);
        if (partitionKey != null) {
            span.setTag(SERVICEBUS_PARTITION_KEY, partitionKey);
        }

        String replyToSessionId = exchange.getIn().getHeader(REPLY_TO_SESSION_ID, String.class);
        if (replyToSessionId != null) {
            span.setTag(SERVICEBUS_REPLY_TO_SESSION_ID, replyToSessionId);
        }

        String sessionId = exchange.getIn().getHeader(SESSION_ID, String.class);
        if (sessionId != null) {
            span.setTag(SERVICEBUS_SESSION_ID, sessionId);
        }

        Duration timeToLive = exchange.getIn().getHeader(TIME_TO_LIVE, Duration.class);
        if (timeToLive != null) {
            span.setTag(SERVICEBUS_TIME_TO_LIVE, timeToLive.toString());
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(MESSAGE_ID, String.class);
    }

}
