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
package org.apache.camel.processor.throttle.requests;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;
import org.apache.camel.processor.TotalRequestsThrottler;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The throttler cleans up its state some time after a permit was last returned. An exchange that looked up the state
 * before the clean must not take a permit from the removed state, as the next exchanges get the permits of the state
 * that replaces it, and more exchanges than allowed would pass in the time period.
 */
public class TotalRequestsThrottlerCleanTest extends ContextTestSupport {

    private final CapturingExecutor executor = new CapturingExecutor();

    @AfterEach
    public void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    public void testCleanAfterExchangeLookedUpState() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("first", "race", "second");

        // takes a permit, which schedules the clean
        template.sendBody("direct:start", "first");
        // the clean runs after race looked up the state, but before it takes a permit
        template.sendBody("direct:start", "race");

        // race took its permit from the state that replaced the cleaned one, so that state hands out only one more
        // permit, not a second full set next to the permit race took. The clean runs here while the permit of first
        // is still delayed (in practice it runs 10 periods later), so first, race and second all pass: this test is
        // about the permits of the replacement state, not about the per-period limit
        template.sendBody("direct:start", "second");
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "rejected"));
        assertInstanceOf(ThrottlerRejectedExecutionException.class, e.getCause());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCleanRemovesUnusedState() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("first");

        template.sendBody("direct:start", "first");
        assertMockEndpointsSatisfied();

        TotalRequestsThrottler throttler = context.getProcessor("throttler", TotalRequestsThrottler.class);
        assertEquals(2, throttler.getCurrentMaximumRequests());

        executor.runScheduled();
        assertEquals(0, throttler.getCurrentMaximumRequests());
    }

    @Test
    public void testCleanFollowsTimePeriod() throws Exception {
        TotalRequestsThrottler throttler = context.getProcessor("throttler", TotalRequestsThrottler.class);
        // such as from JMX
        throttler.setTimePeriodMillis(TimeUnit.HOURS.toMillis(2));

        template.sendBody("direct:start", "first");

        // the clean runs 10 periods after the last permit was returned, so no permit is still delayed then
        assertEquals(List.of(TimeUnit.HOURS.toMillis(20)), executor.delays);
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
                                return 2;
                            }
                        }).timePeriodMillis(TimeUnit.HOURS.toMillis(1)).rejectExecution(true).executorService(executor)
                        .id("throttler")
                        .to("mock:result");
            }
        };
    }

    /**
     * Keeps the scheduled clean tasks so the test decides when they run.
     */
    private static final class CapturingExecutor extends ScheduledThreadPoolExecutor {
        private final List<Runnable> scheduled = new CopyOnWriteArrayList<>();
        private final List<Long> delays = new CopyOnWriteArrayList<>();

        CapturingExecutor() {
            super(1);
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduled.add(command);
            delays.add(unit.toMillis(delay));
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
