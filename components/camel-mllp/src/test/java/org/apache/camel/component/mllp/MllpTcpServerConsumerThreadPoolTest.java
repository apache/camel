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
package org.apache.camel.component.mllp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that the MLLP consumer thread pool correctly handles concurrent message processing.
 *
 * This test emulates what Quarkus does: multiple concurrent requests from an HTTP executor that need to be processed by
 * the MLLP consumer. Without the SynchronousQueue fix, the consumer executor uses LinkedBlockingQueue(1000) which
 * causes tasks to queue up instead of triggering immediate thread creation, leading to acknowledgment timeouts.
 *
 * The test sends multiple concurrent messages to stress the thread pool. Each message includes a small processing delay
 * to simulate real-world scenarios. Without the fix, messages queue up and some will timeout waiting for
 * acknowledgment.
 *
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Slow test")
public class MllpTcpServerConsumerThreadPoolTest extends CamelTestSupport {

    // Number of concurrent messages - must exceed maxConcurrentConsumers (default 5)
    // to stress the thread pool and expose the queueing issue
    private static final int CONCURRENT_MESSAGES = 100;

    // Processing delay per message - simulates real processing work
    private static final int PROCESSING_DELAY_MS = 150;

    // Producer timeout - must be long enough to allow processing but short enough
    // to fail quickly if messages are stuck in queue
    private static final int PRODUCER_TIMEOUT_MS = 5000;

    String mllpHost = "localhost";
    int mllpPort;

    @Override
    protected void doPreSetup() throws Exception {
        mllpPort = AvailablePortFinder.getNextAvailable();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();
        context.setUseMDCLogging(true);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Includes processing delay to simulate real work
                fromF("mllp://%s:%d?validatePayload=true", mllpHost, mllpPort)
                        .routeId("mllp-consumer-route")
                        .delay(PROCESSING_DELAY_MS)
                        .convertBodyTo(String.class)
                        .to("mock:received");

                from("direct:sendMllp")
                        .routeId("mllp-producer-route")
                        .toF("mllp://%s:%d?receiveTimeout=%d&readTimeout=%d",
                                mllpHost, mllpPort, PRODUCER_TIMEOUT_MS, PRODUCER_TIMEOUT_MS)
                        .setBody(header(MllpConstants.MLLP_ACKNOWLEDGEMENT));
            }
        };
    }

    @Test
    public void testConcurrentMessageProcessingLikeQuarkus() throws Exception {
        // Create an executor to simulate Quarkus HTTP thread pool
        ExecutorService httpExecutor = Executors.newFixedThreadPool(CONCURRENT_MESSAGES);
        ProducerTemplate producer = context.createProducerTemplate();

        try {
            List<Future<String>> futures = new ArrayList<>();

            // Submit all messages concurrently (like Quarkus HTTP requests)
            for (int i = 1; i <= CONCURRENT_MESSAGES; i++) {
                final int messageNum = i;
                Callable<String> task = () -> {
                    String message = Hl7TestMessageGenerator.generateMessage(messageNum);
                    return producer.requestBody("direct:sendMllp", message, String.class);
                };
                futures.add(httpExecutor.submit(task));
            }

            // Collect results - all should succeed with the fix
            int successCount = 0;
            List<String> failures = new ArrayList<>();

            for (int i = 0; i < futures.size(); i++) {
                try {
                    String ack = futures.get(i).get(PRODUCER_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS);
                    if (ack != null && ack.contains("MSA|AA|")) {
                        successCount++;
                    } else {
                        failures.add("Message " + (i + 1) + ": unexpected ack: " + ack);
                    }
                } catch (Exception e) {
                    failures.add("Message " + (i + 1) + ": " + e.getClass().getSimpleName() +
                                 " - " + e.getMessage());
                }
            }

            // All messages must succeed
            if (!failures.isEmpty()) {
                fail("Expected all " + CONCURRENT_MESSAGES + " messages to succeed, but " +
                     failures.size() + " failed:\n" + String.join("\n", failures));
            }

            assertTrue(successCount == CONCURRENT_MESSAGES,
                    "All " + CONCURRENT_MESSAGES + " messages should receive acknowledgment");

        } finally {
            producer.close();
            httpExecutor.shutdown();
            httpExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
