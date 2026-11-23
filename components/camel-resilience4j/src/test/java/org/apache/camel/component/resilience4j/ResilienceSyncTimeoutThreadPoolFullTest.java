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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.resilience4j.bulkhead.Bulkhead;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synchronous mode with a timeout: when the thread pool that runs the calls is full, a call must not run on the caller
 * thread, where the time limiter cannot time it out. The default thread pool profile (10/20 threads, a queue of 1000)
 * is scaled down to one thread and a queue of one.
 */
class ResilienceSyncTimeoutThreadPoolFullTest extends CamelTestSupport {

    private final CountDownLatch firstCallStarted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> backendThreads = new CopyOnWriteArrayList<>();
    private final ExecutorService callers = Executors.newCachedThreadPool();
    private final ThreadPoolExecutor userThreadPool = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy());

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        ThreadPoolProfile profile = context.getExecutorServiceManager().getDefaultThreadPoolProfile();
        profile.setPoolSize(1);
        profile.setMaxPoolSize(1);
        profile.setMaxQueueSize(1);
        context.getRegistry().bind("userThreadPool", userThreadPool);
        return context;
    }

    @AfterEach
    void releaseBlockedCalls() {
        release.countDown();
        userThreadPool.shutdown();
        callers.shutdownNow();
    }

    /**
     * Sends from a thread of the test, as the thread pool of the producer template comes from the scaled down profile
     * too.
     */
    private Future<Exchange> send(String uri, Processor processor) {
        return callers.submit(() -> template.send(uri, processor));
    }

    @Test
    void testCallIsRejectedWhenTheThreadPoolIsFull() throws Exception {
        ResilienceProcessor cb = context.getProcessor("cbLong", ResilienceProcessor.class);
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) cb.getExecutorService();

        // X1 holds the only thread, X2 waits in the queue: the thread pool is full
        Future<Exchange> x1 = send("direct:long", e -> e.getIn().setHeader("block", true));
        assertTrue(firstCallStarted.await(10, TimeUnit.SECONDS));
        Future<Exchange> x2 = send("direct:long", e -> e.getIn().setBody("X2"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> threadPool.getQueue().size() == 1);

        // X3 fails at once instead of running on this thread
        Exchange x3 = template.send("direct:long", e -> e.getIn().setBody("X3"));

        assertFalse(backendThreads.contains(Thread.currentThread().getName()), "X3 ran on the caller thread");
        assertEquals(1, backendThreads.size(), "only X1 has reached the backend");
        assertInstanceOf(RejectedExecutionException.class, x3.getException());
        assertEquals(Boolean.FALSE, x3.getProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION));
        assertEquals(1, cb.getNumberOfFailedCalls(), "the breaker records the rejected call as a failure");
        assertEquals(0, cb.getNumberOfSuccessfulCalls());
        assertEquals(0, cb.getNumberOfTimedOutCalls());
        assertEquals(0, cb.getNumberOfBulkheadRejectedCalls());

        // the calls in the thread pool complete as usual
        release.countDown();
        assertNull(x1.get(10, TimeUnit.SECONDS).getException());
        assertNull(x2.get(10, TimeUnit.SECONDS).getException());
        assertEquals(2, cb.getNumberOfSuccessfulCalls());
    }

    @Test
    void testRejectedCallGoesToTheFallbackAndReleasesTheBulkheadPermit() throws Exception {
        ResilienceProcessor cb = context.getProcessor("cbBulkhead", ResilienceProcessor.class);
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) cb.getExecutorService();
        Bulkhead bulkhead = cb.getBulkheadRegistry().bulkhead("cbBulkhead");

        Future<Exchange> x1 = send("direct:bulkhead", e -> e.getIn().setHeader("block", true));
        assertTrue(firstCallStarted.await(10, TimeUnit.SECONDS));
        Future<Exchange> x2 = send("direct:bulkhead", e -> e.getIn().setBody("X2"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> threadPool.getQueue().size() == 1);

        // the bulkhead lets X3 in, the full thread pool rejects it, and the fallback answers
        Exchange x3 = template.send("direct:bulkhead", e -> e.getIn().setBody("X3"));

        assertFalse(backendThreads.contains(Thread.currentThread().getName()), "X3 ran on the caller thread");
        assertNull(x3.getException());
        assertEquals("Fallback", x3.getMessage().getBody());
        assertEquals(Boolean.TRUE, x3.getProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK));
        assertEquals(8, bulkhead.getMetrics().getAvailableConcurrentCalls(), "X3 released its bulkhead permit");
        assertEquals(1, cb.getNumberOfFailedCalls());
        assertEquals(1, cb.getNumberOfFallbackCalls());
        assertEquals(0, cb.getNumberOfTimedOutCalls());
        assertEquals(0, cb.getNumberOfBulkheadRejectedCalls());

        release.countDown();
        assertEquals("Bye", x1.get(10, TimeUnit.SECONDS).getMessage().getBody());
        assertEquals("Bye", x2.get(10, TimeUnit.SECONDS).getMessage().getBody());
    }

    @Test
    void testTimedOutCallLeavesTheQueue() throws Exception {
        ResilienceProcessor cb = context.getProcessor("cbShort", ResilienceProcessor.class);
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) cb.getExecutorService();

        // X1 times out and keeps the only thread (the backend does not answer)
        Future<Exchange> x1 = send("direct:short", e -> e.getIn().setHeader("block", true));
        assertTrue(firstCallStarted.await(10, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, x1.get(10, TimeUnit.SECONDS).getException());

        // X2 times out while it waits in the queue, and leaves the queue
        Exchange x2 = template.send("direct:short", e -> e.getIn().setBody("X2"));
        assertInstanceOf(TimeoutException.class, x2.getException());
        assertEquals(0, threadPool.getQueue().size(), "the timed-out call X2 still takes the queue slot");

        // X3 waits in the queue too, and times out: it does not run on this thread
        Exchange x3 = template.send("direct:short", e -> e.getIn().setBody("X3"));
        assertInstanceOf(TimeoutException.class, x3.getException());
        assertFalse(backendThreads.contains(Thread.currentThread().getName()), "X3 ran on the caller thread");
        assertEquals(1, backendThreads.size(), "only X1 has reached the backend");
        assertEquals(0, threadPool.getQueue().size());
        assertEquals(3, cb.getNumberOfFailedCalls());
        assertEquals(0, cb.getNumberOfSuccessfulCalls());
        assertEquals(3, cb.getNumberOfTimedOutCalls());
    }

    @Test
    void testUserThreadPoolKeepsItsRejectedPolicy() throws Exception {
        // a timeoutExecutorService of the user is used as it is: its CallerRunsPolicy runs X3 on the caller thread
        Future<Exchange> x1 = send("direct:user", e -> e.getIn().setHeader("block", true));
        assertTrue(firstCallStarted.await(10, TimeUnit.SECONDS));
        Future<Exchange> x2 = send("direct:user", e -> e.getIn().setBody("X2"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> userThreadPool.getQueue().size() == 1);

        Exchange x3 = template.send("direct:user", e -> e.getIn().setBody("X3"));

        assertNull(x3.getException());
        assertEquals("Bye", x3.getMessage().getBody());
        assertEquals(Thread.currentThread().getName(), backendThreads.get(1));

        release.countDown();
        assertNull(x1.get(10, TimeUnit.SECONDS).getException());
        assertNull(x2.get(10, TimeUnit.SECONDS).getException());
    }

    private Processor backend() {
        return e -> {
            backendThreads.add(Thread.currentThread().getName());
            if (e.getIn().getHeader("block", false, Boolean.class)) {
                firstCallStarted.countDown();
                // a backend that does not answer, until the test releases it
                assertTrue(release.await(30, TimeUnit.SECONDS));
            }
            e.getMessage().setBody("Bye");
        };
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:short")
                        .circuitBreaker().id("cbShort")
                            .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration("200ms").end()
                            .process(backend())
                        .end();

                // a timeout that the calls of the test do not reach, so X2 stays in the queue
                from("direct:long")
                        .circuitBreaker().id("cbLong")
                            .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration("30s").end()
                            .process(backend())
                        .end();

                from("direct:bulkhead")
                        .circuitBreaker().id("cbBulkhead")
                            .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration("30s")
                                .bulkheadEnabled(true).bulkheadMaxConcurrentCalls(10).end()
                            .process(backend())
                        .onFallback()
                            .transform().constant("Fallback")
                        .end();

                from("direct:user")
                        .circuitBreaker().id("cbUser")
                            .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration("30s")
                                .timeoutExecutorService("userThreadPool").end()
                            .process(backend())
                        .end();
            }
        };
    }
}
