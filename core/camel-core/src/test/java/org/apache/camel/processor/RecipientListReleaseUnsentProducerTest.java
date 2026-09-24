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

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the recipient list releases the producers it acquired for recipients which it did not send to, because it
 * was done before (stopOnException, timeout), and that it emits no exchange sending or sent events for them.
 * <p/>
 * The pooled component has no singleton producer, so a producer which is released is reused from the pool by the next
 * exchange, and a producer which is not released makes the next exchange create and start a new producer.
 */
class RecipientListReleaseUnsentProducerTest extends ContextTestSupport {

    private final AtomicInteger producersStarted = new AtomicInteger();
    private final AtomicInteger producersSent = new AtomicInteger();
    private final AtomicInteger endpointsStarted = new AtomicInteger();
    private final AtomicInteger endpointsStopped = new AtomicInteger();
    private final CountDownLatch slowLatch = new CountDownLatch(1);
    private final Map<String, AtomicInteger> sendingEvents = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sentEvents = new ConcurrentHashMap<>();
    private final ManualExecutorService manualExecutor = new ManualExecutorService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("pooled", new PooledComponent());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof ExchangeSendingEvent sending) {
                    count(sendingEvents, sending.getEndpoint());
                } else if (event instanceof ExchangeSentEvent sent) {
                    count(sentEvents, sent.getEndpoint());
                }
            }
        });
        return context;
    }

    @Test
    void testStopOnException() {
        for (int i = 0; i < 5; i++) {
            Exchange out = template.send("direct:stop", e -> e.getIn().setHeader("to", "direct:boom,pooled:b"));
            assertNotNull(out.getException());
        }

        assertEquals(0, producersSent.get());
        // the producer of pooled:b is released after each exchange, so it is reused from the pool
        assertEquals(1, producersStarted.get());
    }

    @Test
    void testStopOnExceptionParallel() {
        for (int i = 0; i < 5; i++) {
            Exchange out = template.send("direct:parallel", e -> e.getIn().setHeader("to", "direct:boom,pooled:b"));
            assertNotNull(out.getException());
        }

        // the single thread of the pool fails direct:boom before the recipient list task gets to send to pooled:b
        assertEquals(0, producersSent.get());
        assertEquals(1, producersStarted.get());
    }

    @Test
    void testStopOnExceptionParallelSkippedRecipientHasNoEvents() throws Exception {
        Future<Exchange> future
                = template.asyncSend("direct:manual", e -> e.getIn().setHeader("to", "direct:boom,pooled:b"));

        // the recipient list task submits the task of direct:boom, and then schedules itself again
        runTask(manualExecutor.tasks.pollFirst(10, TimeUnit.SECONDS));
        // the recipient list task runs again, before direct:boom, and submits the task of pooled:b
        runTask(manualExecutor.tasks.pollLast());
        // direct:boom fails, so the recipient list is done, and releases pooled:b before its task has begun
        runTask(manualExecutor.tasks.pollFirst());
        // the task of pooled:b, which must skip it
        runTask(manualExecutor.tasks.pollFirst());
        assertTrue(manualExecutor.tasks.isEmpty());

        Exchange out = future.get(10, TimeUnit.SECONDS);
        assertNotNull(out.getException());
        assertEquals(0, producersSent.get());
        assertEquals(1, producersStarted.get());

        assertEquals(1, eventCount(sendingEvents, "direct://boom"));
        assertEquals(1, eventCount(sentEvents, "direct://boom"));
        // pooled:b is not sent to, so there must be no events for it
        assertEquals(0, eventCount(sendingEvents, "pooled://b"));
        assertEquals(0, eventCount(sentEvents, "pooled://b"));
    }

    @Test
    void testPrototypeEndpointStopped() {
        for (int i = 0; i < 5; i++) {
            final int n = i;
            Exchange out = template.send("direct:prototype", e -> e.getIn().setHeader("to", "direct:boom,pooled:c" + n));
            assertNotNull(out.getException());
        }

        assertEquals(5, endpointsStarted.get());
        // every prototype endpoint must be stopped, also when it was not sent to
        assertEquals(5, endpointsStopped.get());
    }

    @Test
    void testTimeout() {
        try {
            for (int i = 0; i < 2; i++) {
                Exchange out = template.send("direct:timeout",
                        e -> e.getIn().setHeader("to", "direct:slow,pooled:b"));
                assertNull(out.getException());
            }
        } finally {
            slowLatch.countDown();
        }

        assertEquals(0, producersSent.get());
        assertEquals(1, producersStarted.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());

                from("direct:stop").recipientList(header("to")).stopOnException();

                from("direct:parallel").recipientList(header("to")).stopOnException()
                        .executorService(context.getExecutorServiceManager().newSingleThreadExecutor(this, "single"));

                // the test runs the tasks of the pool itself, in the order it needs
                from("direct:manual").recipientList(header("to")).parallelProcessing().stopOnException()
                        .executorService(manualExecutor);

                from("direct:prototype").recipientList(header("to")).stopOnException().cacheSize(-1);

                // the single thread of the pool is blocked by direct:slow, so pooled:b is not sent before the timeout
                from("direct:timeout").recipientList(header("to")).parallelProcessing().timeout(100)
                        .executorService(context.getExecutorServiceManager().newSingleThreadExecutor(this, "slow"));

                from("direct:boom").throwException(new IllegalArgumentException("Forced"));

                from("direct:slow").process(e -> assertTrue(slowLatch.await(20, TimeUnit.SECONDS)));
            }
        };
    }

    private static void runTask(Runnable task) {
        assertNotNull(task);
        task.run();
    }

    private static void count(Map<String, AtomicInteger> events, Endpoint endpoint) {
        events.computeIfAbsent(endpoint.getEndpointUri(), k -> new AtomicInteger()).incrementAndGet();
    }

    private static int eventCount(Map<String, AtomicInteger> events, String uri) {
        AtomicInteger count = events.get(uri);
        return count != null ? count.get() : 0;
    }

    /**
     * Only queues the submitted tasks, which the test then runs itself.
     */
    private static final class ManualExecutorService extends AbstractExecutorService {

        private final BlockingDeque<Runnable> tasks = new LinkedBlockingDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        @Override
        public void shutdown() {
            // noop
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    private final class PooledComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new PooledEndpoint(uri, this);
        }
    }

    private final class PooledEndpoint extends DefaultEndpoint {

        private PooledEndpoint(String uri, PooledComponent component) {
            super(uri, component);
        }

        @Override
        public boolean isSingletonProducer() {
            return false;
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    producersSent.incrementAndGet();
                }

                @Override
                protected void doStart() {
                    producersStarted.incrementAndGet();
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException("Consumer not supported");
        }

        @Override
        protected void doStart() throws Exception {
            endpointsStarted.incrementAndGet();
            super.doStart();
        }

        @Override
        protected void doStop() throws Exception {
            endpointsStopped.incrementAndGet();
            super.doStop();
        }
    }
}
