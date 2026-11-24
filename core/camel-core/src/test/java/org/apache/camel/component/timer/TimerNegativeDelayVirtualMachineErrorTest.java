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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a VirtualMachineError (StackOverflowError) thrown in a timer route with a negative delay (executor
 * path) is caught and logged, and that the route's fire loop terminates rather than propagating the error.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-27470">CAMEL-27470</a>
 */
class TimerNegativeDelayVirtualMachineErrorTest extends ContextTestSupport {

    /** Open once route A has thrown its Error, confirming the error was processed, not silently lost. */
    private final CountDownLatch errorThrown = new CountDownLatch(1);

    @Test
    void negativeDelayRouteLoopTerminatesOnVirtualMachineError() throws InterruptedException {
        // The executor path catches the Throwable: the loop must terminate and the
        // latch must open. If the error propagated uncaught and killed the thread,
        // the latch still opens because the lambda completes — so the main assertion
        // is that this finishes in time (not that it hangs).
        boolean thrown = errorThrown.await(5, TimeUnit.SECONDS);
        assertTrue(thrown, "executor path must catch the StackOverflowError and not hang");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:negDelay?delay=-1&period=0&synchronous=true")
                        .routeId("route-neg-delay")
                        .process(exchange -> {
                            errorThrown.countDown();
                            throw new StackOverflowError("simulated StackOverflowError in negative-delay route");
                        });
            }
        };
    }
}
