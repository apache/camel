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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExecutionException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An AsyncProducer that breaks the async contract by throwing from process before it called the callback, called
 * through the {@link UnitOfWorkProducer}, as the producers injected with {@code @EndpointInject} and the
 * {@code @Produce} proxies are (DefaultDeferServiceFactory): the unit of work that the producer created is done once,
 * so the exchange does not stay in the inflight repository.
 */
class UnitOfWorkProducerProducerThrowsTest extends ContextTestSupport {

    private final AtomicInteger unitOfWorkDone = new AtomicInteger();
    private final AtomicReference<AsyncCallback> savedCallback = new AtomicReference<>();
    private EventNotifierSupport notifier;

    @BeforeEach
    void addNotifier() throws Exception {
        // each unit of work that is done fires one completed or failed event
        notifier = new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                unitOfWorkDone.incrementAndGet();
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.ExchangeCompletedEvent
                        || event instanceof CamelEvent.ExchangeFailedEvent;
            }
        };
        context.getManagementStrategy().addEventNotifier(notifier);
        ServiceHelper.startService(notifier);
    }

    @AfterEach
    void removeNotifier() {
        context.getManagementStrategy().removeEventNotifier(notifier);
    }

    @Test
    void testInjectedProducerThrowBeforeCallbackLeavesNothingInflight() throws Exception {
        // the producer that @EndpointInject injects
        context.addEndpoint("throwing:producer", new ThrowingEndpoint());
        Producer producer
                = PluginHelper.getDeferServiceFactory(context).createProducer(context.getEndpoint("throwing:producer"));
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "before");

            producer.process(exchange);

            assertInstanceOf(IllegalStateException.class, exchange.getException());
            assertEquals(0, context.getInflightRepository().size(), "The exchange stays inflight");
            assertEquals(1, unitOfWorkDone.get());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    @Test
    void testThrowBeforeCallbackCompletesOnce() throws Exception {
        UnitOfWorkProducer producer = new UnitOfWorkProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "before");
            AtomicInteger done = new AtomicInteger();

            boolean sync = producer.process(exchange, doneSync -> done.incrementAndGet());

            assertEquals(1, done.get());
            assertTrue(sync);
            assertInstanceOf(IllegalStateException.class, exchange.getException());
            assertNull(exchange.getUnitOfWork());
            assertEquals(0, context.getInflightRepository().size());
            assertEquals(1, unitOfWorkDone.get());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    @Test
    void testThrowBeforeCallbackThenCallbackCompletesOnce() throws Exception {
        UnitOfWorkProducer producer = new UnitOfWorkProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "save");
            AtomicInteger done = new AtomicInteger();

            producer.process(exchange, doneSync -> done.incrementAndGet());
            // the producer calls the callback after it threw: the send is already completed
            savedCallback.get().done(false);

            assertEquals(1, done.get());
            assertInstanceOf(IllegalStateException.class, exchange.getException());
            assertEquals(0, context.getInflightRepository().size());
            assertEquals(1, unitOfWorkDone.get());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    @Test
    void testErrorBeforeCallbackFailsTheExchange() throws Exception {
        UnitOfWorkProducer producer = new UnitOfWorkProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "error");
            AtomicInteger done = new AtomicInteger();

            boolean sync = producer.process(exchange, doneSync -> done.incrementAndGet());

            // an Error that is not a VirtualMachineError fails the exchange, as for a processor (CAMEL-26741)
            assertEquals(1, done.get());
            assertTrue(sync);
            // the exchange keeps an Error wrapped in a CamelExecutionException
            assertInstanceOf(CamelExecutionException.class, exchange.getException());
            assertInstanceOf(AssertionError.class, exchange.getException().getCause());
            assertEquals(0, context.getInflightRepository().size());
            assertEquals(1, unitOfWorkDone.get());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    @Test
    void testVirtualMachineErrorIsThrownOn() throws Exception {
        UnitOfWorkProducer producer = new UnitOfWorkProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "vm");
            AtomicInteger done = new AtomicInteger();

            // left as before (CAMEL-26741): a VirtualMachineError is not handled as a failure of the exchange
            assertThrows(StackOverflowError.class, () -> producer.process(exchange, doneSync -> done.incrementAndGet()));
            assertEquals(0, done.get());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    @Test
    void testCallbackThenThrowIsThrownOn() throws Exception {
        UnitOfWorkProducer producer = new UnitOfWorkProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "after");
            AtomicInteger done = new AtomicInteger();

            // the producer completed the exchange and then threw: the unit of work is done once, and the exception is
            // thrown on to the caller, which knows that its callback was called (EventNotifierProducer)
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> producer.process(exchange, doneSync -> done.incrementAndGet()));
            assertEquals("Simulated producer that throws", e.getMessage());
            assertEquals(1, done.get());
            assertNull(exchange.getException());
            assertEquals("Done", exchange.getMessage().getBody());
            assertEquals(0, context.getInflightRepository().size());
            assertEquals(1, unitOfWorkDone.get());
        } finally {
            ServiceHelper.stopService(producer);
        }
    }

    @Test
    void testCallbackOnlyCompletesOnce() throws Exception {
        UnitOfWorkProducer producer = new UnitOfWorkProducer(new ThrowingEndpoint().createProducer());
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getMessage().setHeader("mode", "ok");
            AtomicInteger done = new AtomicInteger();

            boolean sync = producer.process(exchange, doneSync -> done.incrementAndGet());

            assertEquals(1, done.get());
            assertTrue(sync);
            assertNull(exchange.getException());
            assertEquals("Done", exchange.getMessage().getBody());
            assertEquals(0, context.getInflightRepository().size());
            assertEquals(1, unitOfWorkDone.get());
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
                    String mode = exchange.getMessage().getHeader("mode", String.class);
                    switch (mode) {
                        case "ok" -> {
                            exchange.getMessage().setBody("Done");
                            callback.done(true);
                            return true;
                        }
                        case "after" -> {
                            exchange.getMessage().setBody("Done");
                            callback.done(true);
                        }
                        case "save" -> savedCallback.set(callback);
                        case "error" -> throw new AssertionError("Simulated producer that throws an Error");
                        case "vm" -> throw new StackOverflowError("Simulated");
                        default -> {
                        }
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
