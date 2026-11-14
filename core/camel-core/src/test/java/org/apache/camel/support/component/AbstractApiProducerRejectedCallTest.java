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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.component.AbstractApiProducerTest.TestApiComponent;
import org.apache.camel.support.component.AbstractApiProducerTest.TestApiEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call of an API producer that its thread pool can no longer run, because the CamelContext of the endpoint stops or
 * has stopped, fails the exchange instead of leaving it without completion.
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

    private static AsyncProducer createProducer(CamelContext context) throws Exception {
        TestApiComponent component = new TestApiComponent(context);
        TestApiEndpoint endpoint = new TestApiEndpoint("test-api:greetMe", component, "greetMe");
        endpoint.setInBody("name");
        endpoint.configureProperties(new HashMap<>());
        AsyncProducer producer = (AsyncProducer) endpoint.createProducer();
        producer.start();
        return producer;
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
