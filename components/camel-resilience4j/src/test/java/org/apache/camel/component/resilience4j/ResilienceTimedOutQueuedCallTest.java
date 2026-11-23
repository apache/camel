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
package org.apache.camel.component.resilience4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.resilience4j.bulkhead.Bulkhead;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A call that times out while its task still waits for a thread of the pool must not start later: its exchange has
 * already gone on with the timeout (or the fallback). Each route runs its calls on a pool of one thread, so a blocked
 * call holds the only thread and the next call waits in the queue until it times out.
 */
class ResilienceTimedOutQueuedCallTest extends CamelTestSupport {

    private static final int BULKHEAD_LIMIT = 5;

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger backendStarts = new AtomicInteger();
    private final ExecutorService syncPool = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService asyncPool = Executors.newScheduledThreadPool(1);
    private final Processor backend = exchange -> {
        backendStarts.incrementAndGet();
        if (exchange.getIn().getHeader("block", false, Boolean.class)) {
            // a backend that does not answer, until the test releases it
            release.await(10, TimeUnit.SECONDS);
        }
        exchange.getMessage().setBody("Bye");
    };

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.getRegistry().bind("syncPool", syncPool);
        ctx.getRegistry().bind("asyncPool", asyncPool);
        return ctx;
    }

    @AfterEach
    void releaseBlockedCalls() {
        release.countDown();
        syncPool.shutdownNow();
        asyncPool.shutdownNow();
    }

    @Test
    void testSyncQueuedTimedOutCallDoesNotStart() throws Exception {
        assertQueuedTimedOutCall("direct:sync", "cbSync", syncPool, 1);
    }

    @Test
    void testSyncBulkheadQueuedTimedOutCallDoesNotStart() throws Exception {
        assertQueuedTimedOutCall("direct:syncBulkhead", "cbSyncBulkhead", syncPool, 1);
    }

    @Test
    void testAsyncQueuedTimedOutCallDoesNotStart() throws Exception {
        assertQueuedTimedOutCall("direct:async", "cbAsync", asyncPool, 1);
    }

    @Test
    void testAsyncBulkheadQueuedTimedOutCallDoesNotStart() throws Exception {
        assertQueuedTimedOutCall("direct:asyncBulkhead", "cbAsyncBulkhead", asyncPool, 1);
    }

    @Test
    void testAsyncQueuedTimedOutCallStartsWithoutCancelRunningFuture() throws Exception {
        // timeoutCancelRunningFuture=false: the call is not cancelled, and starts once a thread is free
        assertQueuedTimedOutCall("direct:asyncNoCancel", "cbAsyncNoCancel", asyncPool, 2);
    }

    private void assertQueuedTimedOutCall(String uri, String id, ExecutorService pool, int expectedStarts)
            throws Exception {
        Bulkhead bulkhead = bulkhead(id);
        // the thread of the pool is started and idle before X1, so X1 starts well within its timeout (a call that
        // has not started when it times out is not started at all)
        pool.submit(() -> {
        }).get(5, TimeUnit.SECONDS);

        // X1 blocks the only thread of the pool, times out and keeps running
        Future<Exchange> x1 = template.asyncSend(uri, e -> e.getIn().setHeader("block", true));
        await().atMost(5, TimeUnit.SECONDS).until(() -> backendStarts.get() == 1);
        assertInstanceOf(TimeoutException.class, x1.get(5, TimeUnit.SECONDS).getException(), "X1 times out");

        // X2 waits in the queue of the pool and times out before it starts
        Exchange x2 = template.send(uri, e -> e.getIn().setBody("X2"));
        assertInstanceOf(TimeoutException.class, x2.getException(), "X2 times out");
        assertEquals(1, backendStarts.get(), "X2 has not started when its exchange completes");
        // the permit of X1 is held while it runs; the one of X2 is released when X2 is cancelled
        int availableWhileX1Runs = bulkhead != null ? bulkhead.getMetrics().getAvailableConcurrentCalls() : -1;

        // X1 ends, and the thread takes what is queued behind it: the task of X2, then this one
        release.countDown();
        pool.submit(() -> {
        }).get(5, TimeUnit.SECONDS);

        assertEquals(expectedStarts, backendStarts.get(), "calls that reached the backend");
        if (bulkhead != null) {
            assertEquals(BULKHEAD_LIMIT - 1, availableWhileX1Runs, "bulkhead permits while X1 runs");
            assertEquals(BULKHEAD_LIMIT, bulkhead.getMetrics().getAvailableConcurrentCalls(), "bulkhead permits at the end");
        }
    }

    private Bulkhead bulkhead(String id) {
        ResilienceProcessor processor = context.getProcessor(id, ResilienceProcessor.class);
        return processor.isBulkheadEnabled() ? processor.getBulkheadRegistry().bulkhead(id) : null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sync").circuitBreaker().id("cbSync")
                        .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration("200")
                        .timeoutExecutorService("syncPool").end()
                        .process(backend)
                        .end();

                from("direct:syncBulkhead").circuitBreaker().id("cbSyncBulkhead")
                        .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration("200")
                        .timeoutExecutorService("syncPool")
                        .bulkheadEnabled(true).bulkheadMaxConcurrentCalls(BULKHEAD_LIMIT).end()
                        .process(backend)
                        .end();

                from("direct:async").circuitBreaker().id("cbAsync")
                        .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration("200")
                        .timeoutExecutorService("asyncPool").end()
                        .process(backend)
                        .end();

                from("direct:asyncBulkhead").circuitBreaker().id("cbAsyncBulkhead")
                        .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration("200")
                        .timeoutExecutorService("asyncPool")
                        .bulkheadEnabled(true).bulkheadMaxConcurrentCalls(BULKHEAD_LIMIT).end()
                        .process(backend)
                        .end();

                Resilience4jConfigurationDefinition noCancel = from("direct:asyncNoCancel").circuitBreaker()
                        .id("cbAsyncNoCancel")
                        .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration("200")
                        .timeoutExecutorService("asyncPool");
                noCancel.setTimeoutCancelRunningFuture("false");
                noCancel.end()
                        .process(backend)
                        .end();

            }
        };
    }
}
