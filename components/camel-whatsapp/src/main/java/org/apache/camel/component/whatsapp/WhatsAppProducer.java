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
package org.apache.camel.component.whatsapp;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.whatsapp.model.BaseMessage;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A producer that sends messages to WhatsApp through the Business Cloud API.
 */
public class WhatsAppProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppProducer.class);

    private WhatsAppEndpoint endpoint;

    public WhatsAppProducer(WhatsAppEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (exchange.getIn().getBody() == null) {
            // fail fast
            LOG.debug("Received exchange with empty body, skipping");
            callback.done(true);
            return true;
        }

        // WhatsAppConfiguration config = endpoint.getConfiguration();

        // Tries to get a message in its OutgoingMessage format
        // Automatic conversion applies here
        BaseMessage message = exchange.getIn().getBody(BaseMessage.class);
        ObjectHelper.notNull(message, "message");

        final WhatsAppService service = endpoint.getWhatsappService();

        LOG.debug("Message being sent is: {}", message);
        LOG.debug("Headers of message being sent are: {}", exchange.getIn().getHeaders());

        service.sendMessage(exchange, callback, message);
        return false;
    }
}
