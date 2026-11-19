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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An AsyncProducer that breaks the async contract by throwing from process, before or after it called the callback,
 * called through the {@link EventNotifierProducer}, as the producers injected with {@code @EndpointInject} and the
 * {@code @Produce} proxies are (DefaultDeferServiceFactory).
 */
class EventNotifierProducerProducerThrowsTest extends ContextTestSupport {

    @Test
    void testCallbackThenThrowCompletesOnce() throws Exception {
        EventNotifierProducer producer = new EventNotifierProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "after");
            AtomicInteger done = new AtomicInteger();

            boolean sync = producer.process(exchange, doneSync -> done.incrementAndGet());

            // the exchange is completed once, without the exception the producer threw after it completed
            assertEquals(1, done.get());
            assertTrue(sync);
            assertNull(exchange.getException());
            assertEquals("Done", exchange.getMessage().getBody());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    @Test
    void testThrowBeforeCallbackFailsTheExchange() throws Exception {
        EventNotifierProducer producer = new EventNotifierProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "before");
            AtomicInteger done = new AtomicInteger();

            boolean sync = producer.process(exchange, doneSync -> done.incrementAndGet());

            assertEquals(1, done.get());
            assertTrue(sync);
            assertInstanceOf(IllegalStateException.class, exchange.getException());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    @Test
    void testCallbackThenThrowWithEventsCompletesOnce() throws Exception {
        List<CamelEvent> events = new ArrayList<>();
        EventNotifierSupport notifier = new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                events.add(event);
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.ExchangeSendingEvent || event instanceof CamelEvent.ExchangeSentEvent;
            }
        };
        context.getManagementStrategy().addEventNotifier(notifier);
        ServiceHelper.startService(notifier);
        EventNotifierProducer producer = new EventNotifierProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "after");
            AtomicInteger done = new AtomicInteger();

            producer.process(exchange, doneSync -> done.incrementAndGet());

            assertEquals(1, done.get());
            assertNull(exchange.getException());
            // one sending and one sent event
            assertEquals(2, events.size(), "Events: " + events);
            assertInstanceOf(CamelEvent.ExchangeSendingEvent.class, events.get(0));
            assertInstanceOf(CamelEvent.ExchangeSentEvent.class, events.get(1));
        } finally {
            ServiceHelper.stopService(producer);
            context.getManagementStrategy().removeEventNotifier(notifier);
        }
    }

    @Test
    void testInjectedProducerCallbackThenThrowSucceeds() throws Exception {
        // the producer that @EndpointInject injects
        context.addEndpoint("throwing:producer", new ThrowingEndpoint());
        Producer producer
                = PluginHelper.getDeferServiceFactory(context).createProducer(context.getEndpoint("throwing:producer"));
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "after");

            producer.process(exchange);

            // the producer completed the exchange: the exception it threw afterwards does not fail it
            assertNull(exchange.getException());
            assertEquals("Done", exchange.getMessage().getBody());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    private final class ThrowingEndpoint extends DefaultEndpoint {

        ThrowingEndpoint() {
            super("throwing:producer", null);
            setCamelContext(context);
        }

        @Override
        public Producer createProducer() {
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    if ("after".equals(exchange.getMessage().getHeader("mode"))) {
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
