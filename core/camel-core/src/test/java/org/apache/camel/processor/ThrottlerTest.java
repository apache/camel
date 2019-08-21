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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ThrottlerTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    private static final int TOLERANCE = 50;
    private static final int MESSAGE_COUNT = 9;

    protected boolean canTest() {
        // skip test on windows as it does not run well there
        return !isPlatform("windows");
    }

    @Test
    public void testSendLotsOfMessagesButOnly3GetThroughWithin2Seconds() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(3);
        resultEndpoint.setResultWaitTime(2000);

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            template.sendBody("seda:a", "<message>" + i + "</message>");
        }

        // lets pause to give the requests time to be processed
        // to check that the throttle really does kick in
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testSendLotsOfMessagesWithRejectExecution() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);

        MockEndpoint errorEndpoint = resolveMandatoryEndpoint("mock:error", MockEndpoint.class);
        errorEndpoint.expectedMessageCount(4);

        for (int i = 0; i < 6; i++) {
            template.sendBody("direct:start", "<message>" + i + "</message>");
        }

        // lets pause to give the requests time to be processed
        // to check that the throttle really does kick in
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendLotsOfMessagesSimultaneouslyButOnly3GetThrough() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        long elapsed = sendMessagesAndAwaitDelivery(MESSAGE_COUNT, "direct:a", MESSAGE_COUNT, resultEndpoint);
        assertThrottlerTiming(elapsed, 5, INTERVAL, MESSAGE_COUNT);
    }

    @Test
    public void testConfigurationWithConstantExpression() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        long elapsed = sendMessagesAndAwaitDelivery(MESSAGE_COUNT, "direct:expressionConstant", MESSAGE_COUNT, resultEndpoint);
        assertThrottlerTiming(elapsed, 5, INTERVAL, MESSAGE_COUNT);
    }

    @Test
    public void testConfigurationWithHeaderExpression() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(MESSAGE_COUNT);

        ExecutorService executor = Executors.newFixedThreadPool(MESSAGE_COUNT);
        try {
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 5, INTERVAL, MESSAGE_COUNT);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testConfigurationWithChangingHeaderExpression() throws Exception {
        if (!canTest()) {
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 5, INTERVAL, MESSAGE_COUNT);
            Thread.sleep(INTERVAL + TOLERANCE); // sleep here to ensure the
                                                // first throttle rate does not
                                                // influence the next one.

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 10, INTERVAL, MESSAGE_COUNT);
            Thread.sleep(INTERVAL + TOLERANCE); // sleep here to ensure the
                                                // first throttle rate does not
                                                // influence the next one.

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 5, INTERVAL, MESSAGE_COUNT);
            Thread.sleep(INTERVAL + TOLERANCE); // sleep here to ensure the
                                                // first throttle rate does not
                                                // influence the next one.

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 10, INTERVAL, MESSAGE_COUNT);
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertThrottlerTiming(final long elapsedTimeMs, final int throttle, final int intervalMs, final int messageCount) {
        // now assert that they have actually been throttled (use +/- 50 as
        // slack)
        long minimum = calculateMinimum(intervalMs, throttle, messageCount) - 50;
        long maximum = calculateMaximum(intervalMs, throttle, messageCount) + 50;
        // add 500 in case running on slow CI boxes
        maximum += 500;
        log.info("Sent {} exchanges in {}ms, with throttle rate of {} per {}ms. Calculated min {}ms and max {}ms", messageCount, elapsedTimeMs, throttle, intervalMs, minimum,
                 maximum);

        assertTrue("Should take at least " + minimum + "ms, was: " + elapsedTimeMs, elapsedTimeMs >= minimum);
        assertTrue("Should take at most " + maximum + "ms, was: " + elapsedTimeMs, elapsedTimeMs <= maximum + TOLERANCE);
    }

    private long sendMessagesAndAwaitDelivery(final int messageCount, final String endpointUri, final int threadPoolSize, final MockEndpoint receivingEndpoint)
        throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            if (receivingEndpoint != null) {
                receivingEndpoint.expectedMessageCount(messageCount);
            }

            long start = System.nanoTime();
            for (int i = 0; i < messageCount; i++) {
                executor.execute(new Runnable() {
                    public void run() {
                        template.sendBody(endpointUri, "<message>payload</message>");
                    }
                });
            }

            // let's wait for the exchanges to arrive
            if (receivingEndpoint != null) {
                receivingEndpoint.assertIsSatisfied();
            }
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } finally {
            executor.shutdownNow();
        }
    }

    private void sendMessagesWithHeaderExpression(final ExecutorService executor, final MockEndpoint resultEndpoint, final int throttle, final int intervalMs,
                                                  final int messageCount)
        throws InterruptedException {
        resultEndpoint.expectedMessageCount(messageCount);

        long start = System.nanoTime();
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBodyAndHeader("direct:expressionHeader", "<message>payload</message>", "throttleValue", throttle);
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThrottlerTiming(elapsed, throttle, intervalMs, messageCount);
    }

    private long calculateMinimum(final long periodMs, final long throttleRate, final long messageCount) {
        if (messageCount % throttleRate > 0) {
            return (long)Math.floor((double)messageCount / (double)throttleRate) * periodMs;
        } else {
            return (long)(Math.floor((double)messageCount / (double)throttleRate) * periodMs) - periodMs;
        }
    }

    private long calculateMaximum(final long periodMs, final long throttleRate, final long messageCount) {
        return ((long)Math.ceil((double)messageCount / (double)throttleRate)) * periodMs;
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

                from("direct:expressionConstant").throttle(constant(5)).timePeriodMillis(INTERVAL).to("log:result", "mock:result");

                from("direct:expressionHeader").throttle(header("throttleValue")).timePeriodMillis(INTERVAL).to("log:result", "mock:result");

                from("direct:start").throttle(2).timePeriodMillis(1000).rejectExecution(true).to("log:result", "mock:result");

                from("direct:highThrottleRate").throttle(10000).timePeriodMillis(INTERVAL).to("mock:result");
            }
        };
    }
}
