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
package org.apache.camel.component.vertx.websocket;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exchange with no peer to send to is finished by the producer itself, so it has to report that it was done
 * synchronously. Builds the endpoint directly, so nothing reaches a server.
 */
class VertxWebsocketProducerNoPeerTest {

    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private VertxWebsocketProducer producer() throws Exception {
        context = new DefaultCamelContext();

        VertxWebsocketComponent component = new VertxWebsocketComponent();
        component.setCamelContext(context);

        VertxWebsocketEndpoint endpoint
                = (VertxWebsocketEndpoint) component.createEndpoint("vertx-websocket:localhost:1234/test");
        return (VertxWebsocketProducer) endpoint.createProducer();
    }

    @Test
    void anExchangeWithNoPeerIsDoneSynchronously() throws Exception {
        VertxWebsocketProducer producer = producer();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("a message nobody is listening for");
        // broadcasting to an empty host registry is the one path that reaches no peer without opening a connection
        exchange.getIn().setHeader(VertxWebsocketConstants.SEND_TO_ALL, true);

        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<Boolean> doneSync = new AtomicReference<>();

        // the callback used to be completed with doneSync=true while the method returned false, which says the
        // opposite: that the exchange would be finished from a write handler that never runs
        boolean result = producer.process(exchange, sync -> {
            callbacks.incrementAndGet();
            doneSync.set(sync);
        });

        assertTrue(result, "process must report that it finished the exchange itself");
        assertEquals(1, callbacks.get());
        assertTrue(doneSync.get());
        assertNull(exchange.getException());
    }

    @Test
    void anExchangeWithNoBodyIsDoneSynchronously() throws Exception {
        VertxWebsocketProducer producer = producer();

        Exchange exchange = new DefaultExchange(context);

        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<Boolean> doneSync = new AtomicReference<>();
        boolean result = producer.process(exchange, sync -> {
            callbacks.incrementAndGet();
            doneSync.set(sync);
        });

        assertTrue(result, "process must report that it finished the exchange itself");
        // the same triple as the first test: completing the callback twice would also satisfy the other two
        assertEquals(1, callbacks.get());
        assertTrue(doneSync.get());
    }
}
