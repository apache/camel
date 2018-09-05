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
package org.apache.camel.component.iec60870.client;

import java.time.Instant;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultMessage;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ClientConsumer.class);

    private final ClientConnection connection;
    private final ClientEndpoint endpoint;

    public ClientConsumer(final ClientEndpoint endpoint, final Processor processor, final ClientConnection connection) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.connection = connection;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.connection.setListener(this.endpoint.getAddress(), this::updateValue);
    }

    @Override
    protected void doStop() throws Exception {
        this.connection.setListener(this.endpoint.getAddress(), null);
        super.doStop();
    }

    private void updateValue(final ObjectAddress address, final Value<?> value) {
        // Note: we hold the sync lock for the connection
        try {
            final Exchange exchange = getEndpoint().createExchange();
            exchange.setIn(mapMessage(value));
            getAsyncProcessor().process(exchange);
        } catch (final Exception e) {
            LOG.debug("Failed to process message", e);
        }
    }

    private Message mapMessage(final Value<?> value) {
        final DefaultMessage message = new DefaultMessage(this.endpoint.getCamelContext());

        message.setBody(value);

        message.setHeader("value", value.getValue());
        message.setHeader("timestamp", Instant.ofEpochMilli(value.getTimestamp()));
        message.setHeader("quality", value.getQualityInformation());
        message.setHeader("overflow", value.isOverflow());

        return message;
    }
}
