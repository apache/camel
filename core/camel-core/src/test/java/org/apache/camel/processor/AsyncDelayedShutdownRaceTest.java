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

import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RouteStoppingException;
import org.apache.camel.Service;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The thread pool of a Delay or Throttle (asyncDelayed) EIP is shut down by the EIP while an exchange is being handed
 * to it. The exchange must be completed exactly once: either by doShutdown, or by the thread that hands it over, never
 * by both, and never by none.
 */
class AsyncDelayedShutdownRaceTest extends ContextTestSupport {

    private final AtomicInteger done = new AtomicInteger();
    private Exchange exchange;
    private HookPool pool;

    @BeforeEach
    void createExchange() {
        exchange = new DefaultExchange(context);
    }

    @AfterEach
    void shutdownPool() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testDelayRejectedAfterShutdownFailedTheExchange() throws Exception {
        // the EIP is shut down after the task was registered and before the pool takes it, so doShutdown fails the
        // exchange, then the pool that is shut down rejects the task
        pool = new HookPool(new ThreadPoolExecutor.AbortPolicy());
        Delayer delayer = new Delayer(context, e -> {
        }, ExpressionBuilder.constantExpression(60000L), pool, true);
        ServiceHelper.startService(delayer);
        pool.beforeSchedule = () -> ServiceHelper.stopAndShutdownService(delayer);

        boolean sync = delayer.process(exchange, doneSync -> done.incrementAndGet());

        assertFalse(sync);
        assertCompletedOnce();
        assertEquals(0, delayer.getDelayedCount());
    }

    @Test
    void testDelayDroppedByShutDownPool() throws Exception {
        // doShutdown has failed the scheduled tasks before this one was registered, and the pool that is shut down
        // drops it without an exception (CallerRuns)
        pool = new HookPool(new ThreadPoolExecutor.CallerRunsPolicy());
        Delayer delayer = new Delayer(context, e -> {
        }, ExpressionBuilder.constantExpression(60000L), pool, true);
        ServiceHelper.startService(delayer);
        ServiceHelper.stopAndShutdownService(delayer);

        boolean sync = delayer.processDelay(exchange, doneSync -> done.incrementAndGet(), 60000L);

        assertTrue(sync);
        assertCompletedOnce();
        assertEquals(0, delayer.getDelayedCount());
    }

    @Test
    void testConcurrentThrottlerRejectedAfterShutdownFailedTheExchange() throws Exception {
        pool = new HookPool(new ThreadPoolExecutor.AbortPolicy());
        ConcurrentRequestsThrottler throttler = new ConcurrentRequestsThrottler(
                context, ExpressionBuilder.constantExpression(1), pool, true, false, null);
        throttler.setAsyncDelayed(true);
        assertRejectedAfterShutdownCompletesOnce(throttler,
                () -> throttler.processAsynchronously(exchange, doneSync -> done.incrementAndGet(), null));
    }

    @Test
    void testTotalThrottlerRejectedAfterShutdownFailedTheExchange() throws Exception {
        pool = new HookPool(new ThreadPoolExecutor.AbortPolicy());
        TotalRequestsThrottler throttler = new TotalRequestsThrottler(
                context, ExpressionBuilder.constantExpression(1), 60000L, pool, true, false, null);
        throttler.setAsyncDelayed(true);
        TotalRequestsThrottler.ThrottlingState state = throttler.new ThrottlingState("test");
        assertRejectedAfterShutdownCompletesOnce(throttler,
                () -> throttler.processAsynchronously(exchange, doneSync -> done.incrementAndGet(), state));
    }

    @Test
    void testConcurrentThrottlerDroppedByShutDownPool() throws Exception {
        pool = new HookPool(new ThreadPoolExecutor.CallerRunsPolicy());
        ConcurrentRequestsThrottler throttler = new ConcurrentRequestsThrottler(
                context, ExpressionBuilder.constantExpression(1), pool, true, false, null);
        throttler.setAsyncDelayed(true);
        assertDroppedByShutDownPoolCompletesOnce(throttler,
                () -> throttler.processAsynchronously(exchange, doneSync -> done.incrementAndGet(), null));
    }

    @Test
    void testTotalThrottlerDroppedByShutDownPool() throws Exception {
        pool = new HookPool(new ThreadPoolExecutor.CallerRunsPolicy());
        TotalRequestsThrottler throttler = new TotalRequestsThrottler(
                context, ExpressionBuilder.constantExpression(1), 60000L, pool, true, false, null);
        throttler.setAsyncDelayed(true);
        TotalRequestsThrottler.ThrottlingState state = throttler.new ThrottlingState("test");
        assertDroppedByShutDownPoolCompletesOnce(throttler,
                () -> throttler.processAsynchronously(exchange, doneSync -> done.incrementAndGet(), state));
    }

    private void assertRejectedAfterShutdownCompletesOnce(Service throttler, Callable<Boolean> handOver)
            throws Exception {
        ServiceHelper.startService(throttler);
        pool.beforeSchedule = () -> ServiceHelper.stopAndShutdownService(throttler);

        boolean sync = handOver.call();

        assertFalse(sync);
        assertCompletedOnce();
    }

    private void assertDroppedByShutDownPoolCompletesOnce(Service throttler, Callable<Boolean> handOver)
            throws Exception {
        ServiceHelper.startService(throttler);
        ServiceHelper.stopAndShutdownService(throttler);

        boolean sync = handOver.call();

        assertTrue(sync);
        assertCompletedOnce();
    }

    private void assertCompletedOnce() {
        assertEquals(1, done.get(), "the callback must be called exactly once");
        assertInstanceOf(RouteStoppingException.class, exchange.getException());
    }

    /**
     * A scheduled thread pool that runs a hook just before it takes a task.
     */
    private static final class HookPool extends ScheduledThreadPoolExecutor {
        private volatile Runnable beforeSchedule;

        HookPool(RejectedExecutionHandler handler) {
            super(1, handler);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            runHook();
            return super.schedule(command, delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            runHook();
            return super.schedule(callable, delay, unit);
        }

        private void runHook() {
            Runnable hook = beforeSchedule;
            beforeSchedule = null;
            if (hook != null) {
                hook.run();
            }
        }
    }
}
