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
package org.apache.camel.support.component;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.component.AbstractApiProducerTest.TestApiComponent;
import org.apache.camel.support.component.AbstractApiProducerTest.TestApiEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call of an API producer that its thread pool can no longer run, because the CamelContext of the endpoint stops or
 * has stopped, fails the exchange instead of leaving it without completion. A call made while the CamelContext waits
 * for its inflight exchanges, or before it is started, gets a pool.
 */
class AbstractApiProducerRejectedCallTest {

    private CamelContext context;

    @AfterEach
    void stopContext() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void testCallRacingTheStopOfItsCamelContextFails() throws Exception {
        context = new DefaultCamelContext();
        HoldingThreadPoolFactory factory
                = new HoldingThreadPoolFactory(context.getExecutorServiceManager().getThreadPoolFactory());
        context.getExecutorServiceManager().setThreadPoolFactory(factory);
        context.start();

        AsyncProducer producer = createProducer(context);
        Exchange exchange = newExchange(producer);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        // the call gets the pool, and waits before it submits its task to it
        Thread caller = new Thread(() -> producer.process(exchange, doneSync -> {
            calls.incrementAndGet();
            done.countDown();
        }), "api-caller");
        caller.start();
        try {
            assertTrue(factory.held.await(10, TimeUnit.SECONDS), "The call should wait before it submits");

            // the CamelContext stops meanwhile, and shuts down the pool
            context.stop();
        } finally {
            factory.release.countDown();
        }

        assertTrue(done.await(10, TimeUnit.SECONDS), "The call should complete, not hang");
        caller.join(10000);
        assertEquals(1, calls.get(), "The callback must be called once");
        assertInstanceOf(RejectedExecutionException.class, exchange.getException());
    }

    @Test
    void testCallAfterTheCamelContextStoppedFailsWithoutCreatingAPool() throws Exception {
        context = new DefaultCamelContext();
        HoldingThreadPoolFactory factory
                = new HoldingThreadPoolFactory(context.getExecutorServiceManager().getThreadPoolFactory());
        factory.release.countDown();
        context.getExecutorServiceManager().setThreadPoolFactory(factory);
        context.start();

        AsyncProducer producer = createProducer(context);
        Exchange first = newExchange(producer);
        CountDownLatch firstDone = new CountDownLatch(1);
        producer.process(first, doneSync -> firstDone.countDown());
        assertTrue(firstDone.await(10, TimeUnit.SECONDS), "The call should complete");
        assertNull(first.getException());
        assertEquals("Greetings World", first.getMessage().getBody());

        context.stop();

        Exchange exchange = newExchange(producer);
        AtomicInteger calls = new AtomicInteger();
        boolean sync = producer.process(exchange, doneSync -> calls.incrementAndGet());

        assertTrue(sync, "The call should fail at once");
        assertEquals(1, calls.get(), "The callback must be called once");
        assertInstanceOf(RejectedExecutionException.class, exchange.getException());
        assertEquals(1, factory.created.get(), "No pool should be created for a stopped CamelContext");
    }

    @Test
    void testFirstCallOfAnInflightExchangeWhileTheCamelContextStopsCompletes() throws Exception {
        context = new DefaultCamelContext();
        context.getShutdownStrategy().setTimeout(20);
        TestApiEndpoint endpoint = createEndpoint(context);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .process(e -> {
                            entered.countDown();
                            release.await(10, TimeUnit.SECONDS);
                        })
                        .to(endpoint);
            }
        });
        context.start();

        // an exchange is in flight, before its first call of the API (so the pool of the endpoint is not there yet)
        ProducerTemplate template = context.createProducerTemplate();
        CompletableFuture<Exchange> reply = template.asyncSend("direct:start", e -> e.getMessage().setBody("World"));
        assertTrue(entered.await(10, TimeUnit.SECONDS), "The exchange should be in flight");

        // the CamelContext stops, and waits for the exchange
        Thread stopper = new Thread(context::stop, "stopper");
        stopper.start();
        try {
            Awaitility.await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> context.getStatus() == ServiceStatus.Stopping);
        } finally {
            release.countDown();
        }

        // the first call of the API is made while the CamelContext stops: it gets a pool and completes
        Exchange out = reply.get(10, TimeUnit.SECONDS);
        stopper.join(20000);
        assertNull(out.getException());
        assertEquals("Greetings World", out.getMessage().getBody());
        assertTrue(context.isStopped());
    }

    @Test
    void testCallBeforeTheCamelContextStartedCompletes() throws Exception {
        context = new DefaultCamelContext();

        // a producer used before its CamelContext is started
        AsyncProducer producer = createProducer(context);
        Exchange exchange = newExchange(producer);
        CountDownLatch done = new CountDownLatch(1);
        producer.process(exchange, doneSync -> done.countDown());

        assertTrue(done.await(10, TimeUnit.SECONDS), "The call should complete");
        assertNull(exchange.getException());
        assertEquals("Greetings World", exchange.getMessage().getBody());

        // the pool is shut down with the CamelContext
        ExecutorService pool = ((TestApiEndpoint) producer.getEndpoint()).getExecutorService();
        context.start();
        context.stop();
        assertTrue(pool.isShutdown());
    }

    @Test
    void testCallAfterTheCamelContextStartedAgainCompletes() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        AsyncProducer producer = createProducer(context);
        Exchange first = newExchange(producer);
        CountDownLatch firstDone = new CountDownLatch(1);
        producer.process(first, doneSync -> firstDone.countDown());
        assertTrue(firstDone.await(10, TimeUnit.SECONDS), "The call should complete");
        assertNull(first.getException());

        context.stop();
        context.start();

        // the endpoint gets a new pool once the CamelContext is started again
        Exchange exchange = newExchange(producer);
        CountDownLatch done = new CountDownLatch(1);
        producer.process(exchange, doneSync -> done.countDown());
        assertTrue(done.await(10, TimeUnit.SECONDS), "The call should complete");
        assertNull(exchange.getException());
        assertEquals("Greetings World", exchange.getMessage().getBody());
    }

    private static AsyncProducer createProducer(CamelContext context) throws Exception {
        AsyncProducer producer = (AsyncProducer) createEndpoint(context).createProducer();
        producer.start();
        return producer;
    }

    private static TestApiEndpoint createEndpoint(CamelContext context) throws Exception {
        TestApiComponent component = new TestApiComponent(context);
        TestApiEndpoint endpoint = new TestApiEndpoint("test-api:greetMe", component, "greetMe");
        endpoint.setInBody("name");
        endpoint.configureProperties(new HashMap<>());
        return endpoint;
    }

    private static Exchange newExchange(AsyncProducer producer) {
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getMessage().setBody("World");
        return exchange;
    }

    /**
     * Creates the scheduled pools of the CamelContext as its default factory does, with the rejection policy of the
     * profile, but the first submit to a pool waits until it is released.
     */
    static final class HoldingThreadPoolFactory implements ThreadPoolFactory {

        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger created = new AtomicInteger();
        private final ThreadPoolFactory delegate;

        HoldingThreadPoolFactory(ThreadPoolFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
            created.incrementAndGet();
            RejectedExecutionHandler handler = profile.getRejectedExecutionHandler();
            if (handler == null) {
                handler = new ThreadPoolExecutor.CallerRunsPolicy();
            }
            int size = profile.getPoolSize() != null ? profile.getPoolSize() : 1;
            return new ScheduledThreadPoolExecutor(size, threadFactory, handler) {
                @Override
                public Future<?> submit(Runnable task) {
                    held.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return super.submit(task);
                }
            };
        }

        @Override
        public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
            return delegate.newCachedThreadPool(threadFactory);
        }

        @Override
        public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
            return delegate.newThreadPool(profile, threadFactory);
        }
    }
}
