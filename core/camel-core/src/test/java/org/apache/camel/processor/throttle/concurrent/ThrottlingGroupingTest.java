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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Isolated
public class ThrottlingGroupingTest extends ContextTestSupport {
    private static final int MESSAGE_COUNT = 20;
    protected static final int CONCURRENT_REQUESTS = 2;
    protected static Map<String, Semaphore> semaphores;

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
    public void testSendLotsOfMessagesSimultaneouslyButOnlyGetThroughAsConstantThrottleValue() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:gresult", MockEndpoint.class);
        sendMessagesAndAwaitDelivery(MESSAGE_COUNT, "direct:ga", resultEndpoint);
    }

    private void sendMessagesAndAwaitDelivery(
            final int messageCount, final String endpointUri, final MockEndpoint receivingEndpoint)
            throws InterruptedException {

        semaphores = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(messageCount);
        try {
            if (receivingEndpoint != null) {
                receivingEndpoint.expectedMessageCount(messageCount);
            }

            for (int i = 0; i < messageCount; i++) {
                int finalI = i;
                executor.execute(() -> {
                    Map<String, Object> headers = new HashMap<>();
                    if (finalI % 2 == 0) {
                        headers.put("key", "1");
                    } else {
                        headers.put("key", "2");
                    }
                    template.sendBodyAndHeaders(endpointUri, "<message>payload</message>", headers);
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

    @Test
    public void testConfigurationWithHeaderExpression() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:gresult", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(MESSAGE_COUNT);

        ExecutorService executor = Executors.newFixedThreadPool(MESSAGE_COUNT);
        try {
            sendMessagesWithHeaderExpression(executor, resultEndpoint, CONCURRENT_REQUESTS, MESSAGE_COUNT);
        } finally {
            shutdownAndAwait(executor);
        }
    }

    private void sendMessagesWithHeaderExpression(
            final ExecutorService executor, final MockEndpoint resultEndpoint, final int throttle, final int messageCount)
            throws InterruptedException {
        resultEndpoint.expectedMessageCount(messageCount);

        semaphores = new ConcurrentHashMap<>();
        for (int i = 0; i < messageCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                Map<String, Object> headers = new HashMap<>();
                headers.put("throttleValue", throttle);
                if (finalI % 2 == 0) {
                    headers.put("key", "1");
                } else {
                    headers.put("key", "2");
                }
                template.sendBodyAndHeaders("direct:gexpressionHeader", "<message>payload</message>", headers);
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();
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
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("seda:a").throttle(header("max"), 1).concurrentRequestsMode().to("mock:result");
                from("seda:b").throttle(header("max"), 2).concurrentRequestsMode().to("mock:result2");
                from("seda:c").throttle(header("max")).concurrentRequestsMode().correlationExpression(header("key"))
                        .to("mock:resultdynamic");

                from("direct:ga").throttle(constant(CONCURRENT_REQUESTS), header("key")).concurrentRequestsMode()
                        .process(exchange -> {
                            String key = (String) exchange.getMessage().getHeader("key");
                            // should be no more in-flight exchanges than set on the throttle
                            assertTrue(semaphores.computeIfAbsent(key, k -> new Semaphore(CONCURRENT_REQUESTS)).tryAcquire(),
                                    "'direct:ga' too many requests for key " + key);
                        })
                        .delay(100)
                        .process(exchange -> {
                            semaphores.get(exchange.getMessage().getHeader("key")).release();
                        })
                        .to("log:gresult", "mock:gresult");

                from("direct:gexpressionHeader").throttle(header("throttleValue"), header("key")).concurrentRequestsMode()
                        .process(exchange -> {
                            String key = (String) exchange.getMessage().getHeader("key");
                            // should be no more in-flight exchanges than set on the throttle via the 'throttleValue' header
                            assertTrue(
                                    semaphores.computeIfAbsent(key,
                                            k -> new Semaphore(
                                                    (Integer) exchange.getMessage().getHeader("throttleValue")))
                                            .tryAcquire(),
                                    "'direct:gexpressionHeader' too many requests for key " + key);
                        })
                        .delay(100)
                        .process(exchange -> {
                            semaphores.get(exchange.getMessage().getHeader("key")).release();
                        })
                        .to("log:gresult", "mock:gresult");
            }
        };
    }
}
