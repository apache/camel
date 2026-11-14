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
package org.apache.camel.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An AsyncProducer that breaks the async contract by throwing from process, before or after it called the callback,
 * sent through the producer cache.
 */
public class DefaultProducerCacheProducerThrowsTest extends ContextTestSupport {

    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger sending = new AtomicInteger();
    private final AtomicInteger sent = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.ExchangeSendingEvent) {
                    sending.incrementAndGet();
                } else if (event instanceof CamelEvent.ExchangeSentEvent) {
                    sent.incrementAndGet();
                }
            }
        });
        return camelContext;
    }

    @Test
    public void testCallbackThenThrowCompletesOnce() {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();
        try {
            Exchange exchange = newExchange("after");
            AtomicInteger calls = new AtomicInteger();

            boolean sync = cache.doInAsyncProducer(endpoint(), exchange, doneSync -> calls.incrementAndGet(),
                    (producer, e, callback) -> producer.process(e, callback));

            assertTrue(sync);
            assertEquals(1, calls.get(), "The callback must be called once");
            assertNull(exchange.getException(), "The producer completed the exchange before it threw");
        } finally {
            cache.stop();
        }
    }

    @Test
    public void testThrowBeforeCallbackFailsAndReleasesProducer() {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();
        try {
            Endpoint endpoint = endpoint();
            for (int i = 0; i < 2; i++) {
                Exchange exchange = newExchange("before");
                AtomicInteger calls = new AtomicInteger();

                boolean sync = cache.doInAsyncProducer(endpoint, exchange, doneSync -> calls.incrementAndGet(),
                        (producer, e, callback) -> producer.process(e, callback));

                assertTrue(sync);
                assertEquals(1, calls.get(), "The callback must be called once");
                assertInstanceOf(IllegalStateException.class, exchange.getException());
            }
            // the producer (of an endpoint that is not a singleton producer) is back in the pool after each send
            assertEquals(1, created.get());
        } finally {
            cache.stop();
        }
    }

    @Test
    public void testErrorBeforeCallbackFailsAndReleasesProducer() {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();
        try {
            Endpoint endpoint = endpoint();
            for (int i = 0; i < 2; i++) {
                Exchange exchange = newExchange("error");
                AtomicInteger calls = new AtomicInteger();

                boolean sync = cache.doInAsyncProducer(endpoint, exchange, doneSync -> calls.incrementAndGet(),
                        (producer, e, callback) -> producer.process(e, callback));

                assertTrue(sync);
                assertEquals(1, calls.get(), "The callback must be called once");
                // the exchange keeps an Error wrapped, as for any Error
                assertInstanceOf(NoClassDefFoundError.class, exchange.getException().getCause());
            }
            // the producer is back in the pool after each send
            assertEquals(1, created.get());
        } finally {
            cache.stop();
        }
    }

    @Test
    public void testAsyncSendCallbackThenThrowKeepsTheResult() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();
        try {
            Exchange exchange = newExchange("after");

            CompletableFuture<Exchange> future = cache.asyncSendExchange(endpoint(), null, null, null, exchange, null);

            assertTrue(future.isDone());
            assertNull(future.get(5, TimeUnit.SECONDS).getException(),
                    "The producer completed the exchange before it threw");
            assertEquals("Done", exchange.getMessage().getBody());
        } finally {
            cache.stop();
        }
    }

    @Test
    public void testAsyncSendCallbackThenThrowReleasesTheProducerOnce() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();
        try {
            Endpoint endpoint = endpoint();
            cache.asyncSendExchange(endpoint, null, null, null, newExchange("after"), null).get(5, TimeUnit.SECONDS);

            // the producer is in the pool once: two sends at the same time get two producers
            AsyncProducer first = cache.acquireProducer(endpoint);
            AsyncProducer second = cache.acquireProducer(endpoint);
            assertNotSame(first, second);
            cache.releaseProducer(endpoint, first);
            cache.releaseProducer(endpoint, second);
        } finally {
            cache.stop();
        }
    }

    @Test
    public void testAsyncSendThrowBeforeCallbackPairsTheEvents() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.setEventNotifierEnabled(true);
        cache.start();
        try {
            Exchange exchange = newExchange("before");
            cache.asyncSendExchange(endpoint(), null, null, null, exchange, null).get(5, TimeUnit.SECONDS);

            assertInstanceOf(IllegalStateException.class, exchange.getException());
            assertTrue(sending.get() > 0);
            assertEquals(sending.get(), sent.get(), "each sending event has its sent event");
        } finally {
            cache.stop();
        }
    }

    @Test
    public void testToDCallbackThenThrowRoutesOnce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Done");

        Exchange out = template.send("direct:start", e -> {
            e.getMessage().setHeader("mode", "after");
            e.getMessage().setHeader("target", "producer");
        });

        // the rest of the route runs once, and without the exception the producer threw after it completed
        mock.assertIsSatisfied();
        assertEquals(1, mock.getReceivedCounter());
        assertNull(out.getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addEndpoint("throwing:producer", new ThrowingEndpoint());
                from("direct:start").toD("throwing:${header.target}").to("mock:result");
            }
        };
    }

    private Endpoint endpoint() {
        return context.getEndpoint("throwing:producer");
    }

    private Exchange newExchange(String mode) {
        Exchange exchange = endpoint().createExchange();
        exchange.getMessage().setHeader("mode", mode);
        return exchange;
    }

    private final class ThrowingEndpoint extends DefaultEndpoint {

        ThrowingEndpoint() {
            super("throwing:producer", null);
            setCamelContext(context);
        }

        @Override
        public boolean isSingletonProducer() {
            return false;
        }

        @Override
        public Producer createProducer() {
            created.incrementAndGet();
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    Object mode = exchange.getMessage().getHeader("mode");
                    if ("error".equals(mode)) {
                        // such as a class of the producer that is missing
                        throw new NoClassDefFoundError("Simulated missing class");
                    }
                    if ("after".equals(mode)) {
                        exchange.getMessage().setBody("Done");
                        callback.done(true);
                    }
                    throw new IllegalStateException("Simulated producer that throws");
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }
}
