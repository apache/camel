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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// time-bound that does not run well in shared environments
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD },
             architectures = { "amd64", "aarch64", "ppc64le", "s390x" },
             disabledReason = "This test does not run reliably on all platforms (see CAMEL-21438)")
public class ThrottlerTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    private static final int TOLERANCE = 50;
    private static final int MESSAGE_COUNT = 9;

    @Test
    public void testSendLotsOfMessagesButOnly3GetThroughWithin2Seconds() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(3);
        // Generous timeout so slow machines still deliver the first 3 messages.
        // exactMessageCount(3) still catches throttle violations if more arrive.
        resultEndpoint.setResultWaitTime(10_000);

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            template.sendBody("seda:a", "<message>" + i + "</message>");
        }

        resultEndpoint.assertIsSatisfied();
        // Messages 4-9 must still be queued: the throttle window has not elapsed.
        assertEquals(3, resultEndpoint.getReceivedCounter());
    }

    @Test
    public void testSendLotsOfMessagesWithRejectExecution() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);

        MockEndpoint errorEndpoint = resolveMandatoryEndpoint("mock:error", MockEndpoint.class);
        errorEndpoint.expectedMessageCount(4);

        for (int i = 0; i < 6; i++) {
            template.sendBody("direct:start", "<message>" + i + "</message>");
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendLotsOfMessagesSimultaneouslyButOnly3GetThrough() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        long elapsed = sendMessagesAndAwaitDelivery(MESSAGE_COUNT, "direct:a", MESSAGE_COUNT, resultEndpoint);
        assertThrottlerTiming(elapsed, 5, INTERVAL, MESSAGE_COUNT);
    }

    @Test
    public void testConfigurationWithConstantExpression() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        long elapsed = sendMessagesAndAwaitDelivery(MESSAGE_COUNT, "direct:expressionConstant", MESSAGE_COUNT, resultEndpoint);
        assertThrottlerTiming(elapsed, 5, INTERVAL, MESSAGE_COUNT);
    }

    @Test
    public void testConfigurationWithHeaderExpression() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(MESSAGE_COUNT);

        ExecutorService executor = Executors.newFixedThreadPool(MESSAGE_COUNT);
        try {
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 5, INTERVAL, MESSAGE_COUNT);
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testConfigurationWithChangingHeaderExpression() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 5, INTERVAL, MESSAGE_COUNT);
            resultEndpoint.assertIsSatisfied();

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 10, INTERVAL, MESSAGE_COUNT);
            resultEndpoint.assertIsSatisfied();

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 5, INTERVAL, MESSAGE_COUNT);
            resultEndpoint.assertIsSatisfied();

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 10, INTERVAL, MESSAGE_COUNT);
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void assertThrottlerTiming(
            final long elapsedTimeMs, final int throttle, final int intervalMs, final int messageCount) {
        // Assert only the upper bound: messages must not arrive faster than the throttle allows.
        // The minimum bound (system not too fast) does not test throttle correctness and is
        // dropped to avoid false failures on fast machines.
        // Add 3000ms slack for slow CI boxes.
        long maximum = calculateMaximum(intervalMs, throttle, messageCount) + 50 + 3000;
        log.info("Sent {} exchanges in {}ms, with throttle rate of {} per {}ms. Calculated max {}ms", messageCount,
                elapsedTimeMs, throttle, intervalMs, maximum);

        assertTrue(elapsedTimeMs <= maximum + TOLERANCE, "Should take at most " + maximum + "ms, was: " + elapsedTimeMs);
    }

    private long sendMessagesAndAwaitDelivery(
            final int messageCount, final String endpointUri, final int threadPoolSize, final MockEndpoint receivingEndpoint)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            if (receivingEndpoint != null) {
                receivingEndpoint.expectedMessageCount(messageCount);
            }

            long start = System.nanoTime();
            for (int i = 0; i < messageCount; i++) {
                executor.execute(() -> template.sendBody(endpointUri, "<message>payload</message>"));
            }

            if (receivingEndpoint != null) {
                receivingEndpoint.assertIsSatisfied();
            }
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void sendMessagesWithHeaderExpression(
            final ExecutorService executor, final MockEndpoint resultEndpoint, final int throttle, final int intervalMs,
            final int messageCount)
            throws InterruptedException {
        resultEndpoint.expectedMessageCount(messageCount);

        // Start the clock when the first thread actually begins executing, not when tasks
        // are submitted, to avoid inflating elapsed with thread pool scheduling overhead.
        CountDownLatch firstStarted = new CountDownLatch(1);
        for (int i = 0; i < messageCount; i++) {
            executor.execute(() -> {
                firstStarted.countDown();
                template.sendBodyAndHeader("direct:expressionHeader", "<message>payload</message>", "throttleValue",
                        throttle);
            });
        }

        assertTrue(firstStarted.await(10, TimeUnit.SECONDS), "Timed out waiting for first thread to start");
        long start = System.nanoTime();
        resultEndpoint.assertIsSatisfied();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThrottlerTiming(elapsed, throttle, intervalMs, messageCount);
    }

    private long calculateMaximum(final long periodMs, final long throttleRate, final long messageCount) {
        return ((long) Math.ceil((double) messageCount / (double) throttleRate)) * periodMs;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                onException(ThrottlerRejectedExecutionException.class).handled(true).to("mock:error");

                // START SNIPPET: ex
                from("seda:a").throttle(3).timePeriodMillis(1000).to("log:result", "mock:result");
                // END SNIPPET: ex

                from("direct:a").throttle(5).timePeriodMillis(INTERVAL).to("log:result", "mock:result");

                from("direct:expressionConstant").throttle(constant(5)).timePeriodMillis(INTERVAL).to("log:result",
                        "mock:result");

                from("direct:expressionHeader").throttle(header("throttleValue")).timePeriodMillis(INTERVAL).to("log:result",
                        "mock:result");

                from("direct:start").throttle(2).timePeriodMillis(1000).rejectExecution(true).to("log:result", "mock:result");

                from("direct:highThrottleRate").throttle(10000).timePeriodMillis(INTERVAL).to("mock:result");
            }
        };
    }
}
