/**
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
package org.apache.camel.component.ignite.messaging;

import java.util.Collection;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.component.ignite.IgniteHelper;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteMessaging;

/**
 * Ignite Messaging producer.
 */
public class IgniteMessagingProducer extends DefaultAsyncProducer {

    private IgniteMessagingEndpoint endpoint;
    private IgniteMessaging messaging;

    public IgniteMessagingProducer(IgniteMessagingEndpoint endpoint, Ignite ignite, IgniteMessaging messaging) {
        super(endpoint);
        this.endpoint = endpoint;
        this.messaging = messaging;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message in = exchange.getIn();
        Message out = exchange.getOut();
        MessageHelper.copyHeaders(exchange.getIn(), out, true);

        Object body = in.getBody();

        if (endpoint.getSendMode() == IgniteMessagingSendMode.UNORDERED) {
            if (body instanceof Collection<?> && !endpoint.isTreatCollectionsAsCacheObjects()) {
                messaging.send(topicFor(exchange), (Collection<?>) body);
            } else {
                messaging.send(topicFor(exchange), body);
            }
        } else {
            messaging.sendOrdered(topicFor(exchange), body, endpoint.getTimeout());
        }

        IgniteHelper.maybePropagateIncomingBody(endpoint, in, out);

        return true;
    }

    private String topicFor(Exchange exchange) {
        return exchange.getIn().getHeader(IgniteConstants.IGNITE_MESSAGING_TOPIC, endpoint.getTopic(), String.class);
    }

}
