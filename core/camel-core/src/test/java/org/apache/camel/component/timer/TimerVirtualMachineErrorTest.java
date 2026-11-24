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
package org.apache.camel.component.timer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a VirtualMachineError (StackOverflowError) thrown in one timer route does not kill the shared
 * java.util.Timer thread and stop other routes sharing the same timer name.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-27470">CAMEL-27470</a>
 */
public class TimerVirtualMachineErrorTest extends ContextTestSupport {

    private static final String TIMER_NAME = "shared";

    /** Counts how many times route A has fired (used to trigger the error on the third). */
    private final AtomicInteger routeAFireCount = new AtomicInteger();
    /** Set to true once route A has thrown its Error. */
    private volatile boolean routeAHasThrown;
    /** Open once route B fires at least once after route A has already thrown its Error. */
    private final CountDownLatch routeBFiredAfterError = new CountDownLatch(1);
    /** Counts how many times route B has fired. */
    private final AtomicInteger routeBFireCount = new AtomicInteger();

    @Test
    public void sharedTimerSurvivesStackOverflowErrorInSiblingRoute() throws Exception {
        // Wait up to 5 seconds for route B to fire at least once after route A has thrown.
        // Before the fix this never happens: the StackOverflowError escapes run() and kills
        // the shared java.util.Timer thread, which silently cancels both timer tasks.
        boolean firedAfterError = routeBFiredAfterError.await(5, TimeUnit.SECONDS);
        assertTrue(firedAfterError,
                "route B must keep firing after a StackOverflowError in route A (shared timer must survive)");

        // the timer task of route A is cancelled: while route B fires several more times, route A does not fire again
        int routeBCount = routeBFireCount.get();
        await().atMost(5, TimeUnit.SECONDS).until(() -> routeBFireCount.get() >= routeBCount + 5);
        assertEquals(3, routeAFireCount.get());

        // restarting route A makes it fire again
        context.getRouteController().stopRoute("route-a");
        context.getRouteController().startRoute("route-a");
        await().atMost(5, TimeUnit.SECONDS).until(() -> routeAFireCount.get() > 3);
    }

    @Override
    protected int getShutdownTimeout() {
        // the exchange that failed with a VirtualMachineError can stay inflight (CAMEL-27515)
        return 1;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route A: throws a StackOverflowError on its third exchange only, so that it fires again once restarted.
                // Uses synchronous=true so the error propagates synchronously through the timer task.
                from("timer:" + TIMER_NAME + "?period=50&synchronous=true")
                        .routeId("route-a")
                        .process(exchange -> {
                            int count = routeAFireCount.incrementAndGet();
                            if (count == 3) {
                                routeAHasThrown = true;
                                throw new StackOverflowError("simulated StackOverflowError in route A");
                            }
                        });

                // Route B: a bystander on the same timer. It must keep firing even after route A
                // throws its StackOverflowError, because the fix prevents the error from killing
                // the shared java.util.Timer thread.
                from("timer:" + TIMER_NAME + "?period=50&synchronous=true")
                        .routeId("route-b")
                        .process(exchange -> {
                            routeBFireCount.incrementAndGet();
                            if (routeAHasThrown) {
                                routeBFiredAfterError.countDown();
                            }
                        });
            }
        };
    }
}
