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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;

public class GooglePubsubSpanDecorator extends AbstractMessagingSpanDecorator {

    static final String PUBSUB_ORDERING_KEY = "orderingKey";
    static final String PUBSUB_ACK_ID = "ackId";
    static final String PUBSUB_DELIVERY_ATTEMPT = "deliveryAttempt";

    /**
     * Constants copied from {@link org.apache.camel.component.google.pubsub.GooglePubsubConstants}
     */
    static final String MESSAGE_ID = "CamelGooglePubsubMessageId";
    static final String ACK_ID = "CamelGooglePubsubMsgAckId";
    static final String ORDERING_KEY = "CamelGooglePubsubOrderingKey";
    static final String DELIVERY_ATTEMPT = "CamelGooglePubsubDeliveryAttempt";

    @Override
    public String getComponent() {
        return "google-pubsub";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.google.pubsub.GooglePubsubComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String orderingKey = exchange.getIn().getHeader(ORDERING_KEY, String.class);
        if (orderingKey != null) {
            span.setTag(PUBSUB_ORDERING_KEY, orderingKey);
        }

        String ackId = exchange.getIn().getHeader(ACK_ID, String.class);
        if (ackId != null) {
            span.setTag(PUBSUB_ACK_ID, ackId);
        }

        Integer deliveryAttempt = exchange.getIn().getHeader(DELIVERY_ATTEMPT, Integer.class);
        if (deliveryAttempt != null) {
            span.setTag(PUBSUB_DELIVERY_ATTEMPT, deliveryAttempt.toString());
        }
    }

    @Override
    protected String getMessageId(Exchange exchange) {
        return exchange.getIn().getHeader(MESSAGE_ID, String.class);
    }

}
