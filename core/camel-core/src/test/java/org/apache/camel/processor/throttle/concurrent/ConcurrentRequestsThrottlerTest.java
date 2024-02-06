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

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// time-bound that does not run well in shared environments
@DisabledOnOs(OS.WINDOWS)
@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on Github CI")
public class ConcurrentRequestsThrottlerTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    private static final int MESSAGE_COUNT = 9;
    private static final int CONCURRENT_REQUESTS = 2;
    protected static Semaphore semaphore;

    @Test
    public void testSendLotsOfMessagesWithRejectExecution() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);

        MockEndpoint errorEndpoint = resolveMandatoryEndpoint("mock:error", MockEndpoint.class);
        errorEndpoint.expectedMessageCount(4);

        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            for (int i = 0; i < 6; i++) {
                executor.execute(() -> template.sendBody("direct:start", "<message>payload</message>"));
            }
            assertMockEndpointsSatisfied();
        } finally {
            shutdownAndAwait(executor);
        }
    }

    @Test
    public void testSendLotsOfMessagesSimultaneouslyButOnly3GetThrough() throws Exception {
        semaphore = new Semaphore(CONCURRENT_REQUESTS);
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        sendMessagesAndAwaitDelivery(MESSAGE_COUNT, "direct:a", MESSAGE_COUNT, resultEndpoint);
    }

    @Test
    public void testConfigurationWithConstantExpression() throws Exception {
        semaphore = new Semaphore(CONCURRENT_REQUESTS);
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        sendMessagesAndAwaitDelivery(MESSAGE_COUNT, "direct:expressionConstant", MESSAGE_COUNT, resultEndpoint);
    }

    @Test
    public void testConfigurationWithHeaderExpression() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(MESSAGE_COUNT);

        ExecutorService executor = Executors.newFixedThreadPool(MESSAGE_COUNT);
        try {
            sendMessagesWithHeaderExpression(executor, resultEndpoint, CONCURRENT_REQUESTS, MESSAGE_COUNT);
        } finally {
            shutdownAndAwait(executor);
        }
    }

    @Test
    public void testConfigurationWithChangingHeaderExpression() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 2, MESSAGE_COUNT);
            Thread.sleep(INTERVAL); // sleep here to ensure the
                                   // first throttle rate does not
                                   // influence the next one.

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 4, MESSAGE_COUNT);
            Thread.sleep(INTERVAL); // sleep here to ensure the
                                   // first throttle rate does not
                                   // influence the next one.

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 2, MESSAGE_COUNT);
            Thread.sleep(INTERVAL); // sleep here to ensure the
                                   // first throttle rate does not
                                   // influence the next one.

            resultEndpoint.reset();
            sendMessagesWithHeaderExpression(executor, resultEndpoint, 4, MESSAGE_COUNT);
        } finally {
            shutdownAndAwait(executor);
        }
    }

    @Test
    public void testFifo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A", "B", "C", "D", "E", "F", "G", "H");
        sendBody("direct:fifo");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPermitReleaseOnException() throws Exception {
        // verify that failed processing releases throttle permit
        getMockEndpoint("mock:error").expectedBodiesReceived("A", "B", "C", "D", "E", "F", "G", "H");
        sendBody("direct:release");
        assertMockEndpointsSatisfied();
    }

    private void sendMessagesAndAwaitDelivery(
            final int messageCount, final String endpointUri, final int threadPoolSize, final MockEndpoint receivingEndpoint)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            if (receivingEndpoint != null) {
                receivingEndpoint.expectedMessageCount(messageCount);
            }

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
        } finally {
            shutdownAndAwait(executor);
        }
    }

    private void sendMessagesWithHeaderExpression(
            final ExecutorService executor, final MockEndpoint resultEndpoint, final int throttle, final int messageCount)
            throws InterruptedException {
        resultEndpoint.expectedMessageCount(messageCount);
        semaphore = new Semaphore(throttle);

        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBodyAndHeader("direct:expressionHeader", "<message>payload</message>", "throttleValue",
                            throttle);
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();
    }

    private void sendBody(String endpoint) {
        Arrays.stream(new String[] { "A", "B", "C", "D", "E", "F", "G", "H" })
                .forEach(b -> template.sendBody(endpoint, b));
    }

    private void shutdownAndAwait(final ExecutorService executorService) {
        executorService.shutdown();
        try {
            assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS),
                    "Test ExecutorService shutdown is not expected to take longer than 10 seconds.");
        } catch (InterruptedException e) {
            fail("Test ExecutorService shutdown is not expected to be interrupted.");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                onException(ThrottlerRejectedExecutionException.class).handled(true).to("mock:error");

                from("direct:a").throttle(CONCURRENT_REQUESTS).concurrentRequestsMode()
                        .process(exchange -> {
                            assertTrue(semaphore.tryAcquire(), "'direct:a' too many requests");
                        })
                        .delay(100)
                        .process(exchange -> {
                            semaphore.release();
                        })
                        .to("log:result", "mock:result");

                from("direct:expressionConstant").throttle(constant(CONCURRENT_REQUESTS)).concurrentRequestsMode()
                        .process(exchange -> {
                            assertTrue(semaphore.tryAcquire(), "'direct:expressionConstant' too many requests");
                        })
                        .delay(100)
                        .process(exchange -> {
                            semaphore.release();
                        })
                        .to("log:result", "mock:result");

                from("direct:expressionHeader").throttle(header("throttleValue")).concurrentRequestsMode()
                        .process(exchange -> {
                            assertTrue(semaphore.tryAcquire(), "'direct:expressionHeader' too many requests");
                        })
                        .delay(100)
                        .process(exchange -> {
                            semaphore.release();
                        })
                        .to("log:result", "mock:result");

                from("direct:start").throttle(2).concurrentRequestsMode().rejectExecution(true).delay(1000).to("log:result",
                        "mock:result");

                from("direct:fifo").throttle(1).concurrentRequestsMode().delay(100).to("mock:result");

                from("direct:release").errorHandler(deadLetterChannel("mock:error")).throttle(1).delay(100)
                        .process(exchange -> {
                            throw new RuntimeException();
                        }).to("mock:result");
            }
        };
    }
}
