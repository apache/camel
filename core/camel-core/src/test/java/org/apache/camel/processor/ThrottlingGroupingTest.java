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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ThrottlingGroupingTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    private static final int MESSAGE_COUNT = 9;
    private static final int TOLERANCE = 50;

    @Test
    public void testGroupingWithSingleConstant() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:dead").expectedBodiesReceived("Kaboom");

        template.sendBodyAndHeader("seda:a", "Kaboom", "max", null);
        template.sendBodyAndHeader("seda:a", "Hello World", "max", 2);
        template.sendBodyAndHeader("seda:a", "Bye World", "max", 2);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testGroupingWithDynamicHeaderExpression() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result2").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:dead").expectedBodiesReceived("Kaboom", "Saloon");
        getMockEndpoint("mock:resultdynamic").expectedBodiesReceived("Hello Dynamic World", "Bye Dynamic World");

        Map<String, Object> headers = new HashMap<>();

        template.sendBodyAndHeaders("seda:a", "Kaboom", headers);
        template.sendBodyAndHeaders("seda:a", "Saloon", headers);

        headers.put("max", "2");
        template.sendBodyAndHeaders("seda:a", "Hello World", headers);
        template.sendBodyAndHeaders("seda:b", "Bye World", headers);
        headers.put("max", "2");
        headers.put("key", "1");
        template.sendBodyAndHeaders("seda:c", "Hello Dynamic World", headers);
        headers.put("key", "2");
        template.sendBodyAndHeaders("seda:c", "Bye Dynamic World", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendLotsOfMessagesButOnly3GetThroughWithin2Seconds() throws Exception {

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:gresult", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(3);
        resultEndpoint.setResultWaitTime(2000);

        Map<String, Object> headers = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            if (i % 2 == 0) {
                headers.put("key", "1");
            } else {
                headers.put("key", "2");
            }
            template.sendBodyAndHeaders("seda:ga", "<message>" + i + "</message>", headers);
        }

        // lets pause to give the requests time to be processed
        // to check that the throttle really does kick in
        resultEndpoint.assertIsSatisfied();
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
                        Map<String, Object> headers = new HashMap<>();
                        if (messageCount % 2 == 0) {
                            headers.put("key", "1");
                        } else {
                            headers.put("key", "2");
                        }
                        template.sendBodyAndHeaders(endpointUri, "<message>payload</message>", headers);
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

    @Test
    public void testSendLotsOfMessagesSimultaneouslyButOnlyGetThroughAsConstantThrottleValue() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:gresult", MockEndpoint.class);
        long elapsed = sendMessagesAndAwaitDelivery(MESSAGE_COUNT, "direct:ga", MESSAGE_COUNT, resultEndpoint);
        assertThrottlerTiming(elapsed, 5, INTERVAL, MESSAGE_COUNT);
    }

    @Test
    public void testConfigurationWithHeaderExpression() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:gresult", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(MESSAGE_COUNT);

        ExecutorService executor = Executors.newFixedThreadPool(MESSAGE_COUNT);
        try {
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 5, INTERVAL, MESSAGE_COUNT);
        } finally {
            executor.shutdownNow();
        }
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

    private void sendMessagesWithHeaderExpression(final ExecutorService executor, final MockEndpoint resultEndpoint, final int throttle, final int intervalMs,
                                                  final int messageCount)
        throws InterruptedException {
        resultEndpoint.expectedMessageCount(messageCount);

        long start = System.nanoTime();
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    Map<String, Object> headers = new HashMap<>();
                    headers.put("throttleValue", throttle);
                    if (messageCount % 2 == 0) {
                        headers.put("key", "1");
                    } else {
                        headers.put("key", "2");
                    }
                    template.sendBodyAndHeaders("direct:gexpressionHeader", "<message>payload</message>", headers);
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThrottlerTiming(elapsed, throttle, intervalMs, messageCount);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("seda:a").throttle(header("max"), 1).to("mock:result");
                from("seda:b").throttle(header("max"), 2).to("mock:result2");
                from("seda:c").throttle(header("max"), header("key")).to("mock:resultdynamic");

                from("seda:ga").throttle(constant(3), header("key")).timePeriodMillis(1000).to("log:gresult", "mock:gresult");

                from("direct:ga").throttle(constant(5), header("key")).timePeriodMillis(INTERVAL).to("log:gresult", "mock:gresult");

                from("direct:gexpressionHeader").throttle(header("throttleValue"), header("key")).timePeriodMillis(INTERVAL).to("log:gresult", "mock:gresult");
            }
        };
    }
}
