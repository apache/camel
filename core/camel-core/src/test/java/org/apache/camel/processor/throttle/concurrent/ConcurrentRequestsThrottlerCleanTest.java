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
package org.apache.camel.processor.throttle.concurrent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.ConcurrentRequestsThrottler;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The throttler cleans up its state some time after the last permit was returned. That must not happen while an
 * exchange that took a permit later is still being processed, or the next exchange gets a fresh set of permits.
 */
public class ConcurrentRequestsThrottlerCleanTest extends ContextTestSupport {

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CapturingExecutor executor = new CapturingExecutor();

    @AfterEach
    public void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    public void testCleanDoesNotRemoveStateInUse() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("fast", "slow", "after");

        // takes and returns the only permit, which schedules the clean
        template.sendBody("direct:start", "fast");

        Future<Object> slow = template.asyncSendBody("direct:start", "slow");
        assertTrue(entered.await(10, TimeUnit.SECONDS));

        // the clean runs while slow holds the only permit
        executor.runScheduled();

        // so there is still no permit for another exchange
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "rejected"));
        assertInstanceOf(ThrottlerRejectedExecutionException.class, e.getCause());

        release.countDown();
        slow.get(10, TimeUnit.SECONDS);

        template.sendBody("direct:start", "after");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCleanAfterExchangeLookedUpState() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("fast", "race", "after");

        // takes and returns the only permit, which schedules the clean
        template.sendBody("direct:start", "fast");

        // the clean runs after race looked up the state, but before it takes the permit
        Future<Object> race = template.asyncSendBody("direct:start", "race");
        assertTrue(entered.await(10, TimeUnit.SECONDS));

        // race holds the only permit
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "rejected"));
        assertInstanceOf(ThrottlerRejectedExecutionException.class, e.getCause());

        release.countDown();
        race.get(10, TimeUnit.SECONDS);

        template.sendBody("direct:start", "after");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCleanRemovesUnusedState() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("fast");

        template.sendBody("direct:start", "fast");
        assertMockEndpointsSatisfied();

        ConcurrentRequestsThrottler throttler = context.getProcessor("throttler", ConcurrentRequestsThrottler.class);
        assertEquals(1, throttler.getCurrentMaximumRequests());

        executor.runScheduled();
        assertEquals(0, throttler.getCurrentMaximumRequests());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .throttle(new ExpressionAdapter() {
                            @Override
                            public Object evaluate(Exchange exchange) {
                                if ("race".equals(exchange.getMessage().getBody())) {
                                    // the throttler evaluates this after looking up its state for the exchange
                                    executor.runScheduled();
                                }
                                return 1;
                            }
                        }).concurrentRequestsMode().rejectExecution(true).executorService(executor).id("throttler")
                        .process(e -> {
                            Object body = e.getMessage().getBody();
                            if ("slow".equals(body) || "race".equals(body)) {
                                entered.countDown();
                                release.await(10, TimeUnit.SECONDS);
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    /**
     * Keeps the scheduled clean tasks so the test decides when they run.
     */
    private static final class CapturingExecutor extends ScheduledThreadPoolExecutor {
        private final List<Runnable> scheduled = new CopyOnWriteArrayList<>();

        CapturingExecutor() {
            super(1);
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduled.add(command);
            return super.schedule(command, 1, TimeUnit.DAYS);
        }

        void runScheduled() {
            for (Runnable task : scheduled) {
                task.run();
            }
            scheduled.clear();
        }
    }
}
