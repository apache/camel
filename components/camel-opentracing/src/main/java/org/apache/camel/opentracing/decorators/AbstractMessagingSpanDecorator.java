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
package org.apache.camel.opentracing.decorators;

import java.util.Map;

import io.opentracing.Span;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.opentracing.propagation.CamelMessagingHeadersExtractAdapter;
import org.apache.camel.opentracing.propagation.CamelMessagingHeadersInjectAdapter;

public abstract class AbstractMessagingSpanDecorator extends AbstractSpanDecorator {

    public static final String MESSAGE_BUS_ID = "message_bus.id";

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        // Use the destination name
        return getDestination(exchange, endpoint);
    }

    @Override
    public void pre(Span span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);

        span.setTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), getDestination(exchange, endpoint));

        String messageId = getMessageId(exchange);
        if (messageId != null) {
            span.setTag(MESSAGE_BUS_ID, messageId);
        }
    }

    /**
     * This method identifies the destination from the supplied exchange and/or
     * endpoint.
     *
     * @param exchange The exchange
     * @param endpoint The endpoint
     * @return The message bus destination
     */
    protected String getDestination(Exchange exchange, Endpoint endpoint) {
        return stripSchemeAndOptions(endpoint);
    }

    @Override
    public String getInitiatorSpanKind() {
        return "producer";
    }

    @Override
    public String getReceiverSpanKind() {
        return "consumer";
    }

    /**
     * This method identifies the message id for the messaging exchange.
     *
     * @return The message id, or null if no id exists for the exchange
     */
    protected String getMessageId(Exchange exchange) {
        return null;
    }

    @Override
    public TextMap getExtractAdapter(final Map<String, Object> map, final boolean jmsEncoding) {
        return new CamelMessagingHeadersExtractAdapter(map, jmsEncoding);
    }

    @Override
    public TextMap getInjectAdapter(final Map<String, Object> map, final boolean jmsEncoding) {
        return new CamelMessagingHeadersInjectAdapter(map, jmsEncoding);
    }
}
