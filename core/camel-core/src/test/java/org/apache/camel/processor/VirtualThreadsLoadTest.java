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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load test to compare performance of platform threads vs virtual threads.
 * <p>
 * This test is disabled by default as it's meant to be run manually for benchmarking.
 * <p>
 * Run with platform threads (default):
 *
 * <pre>
 * mvn test -Dtest=VirtualThreadsLoadTest -pl core/camel-core
 * </pre>
 * <p>
 * Run with virtual threads (JDK 21+):
 *
 * <pre>
 * mvn test -Dtest=VirtualThreadsLoadTest -pl core/camel-core -Dcamel.threads.virtual.enabled=true
 * </pre>
 * <p>
 * Run with virtual threads and thread-per-task mode (optimal for virtual threads):
 *
 * <pre>
 * mvn test -Dtest=VirtualThreadsLoadTest -pl core/camel-core -Dcamel.threads.virtual.enabled=true -Dloadtest.virtualThreadPerTask=true
 * </pre>
 */
@Disabled("Manual load test - run explicitly for benchmarking")
public class VirtualThreadsLoadTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualThreadsLoadTest.class);

    // Configuration - adjust these for your environment
    // With 200 consumers and 5ms delay, theoretical max throughput = 200 * 1000/5 = 40,000 msg/sec
    private static final int TOTAL_MESSAGES = Integer.getInteger("loadtest.messages", 5_000);
    private static final int CONCURRENT_PRODUCERS = Integer.getInteger("loadtest.producers", 50);
    private static final int CONCURRENT_CONSUMERS = Integer.getInteger("loadtest.consumers", 100);
    private static final int SIMULATED_IO_DELAY_MS = Integer.getInteger("loadtest.delay", 5);
    // When true, uses virtualThreadPerTask mode which spawns a new thread per message
    // This is optimal for virtual threads where thread creation is cheap
    private static final boolean VIRTUAL_THREAD_PER_TASK = Boolean.getBoolean("loadtest.virtualThreadPerTask");

    private final LongAdder processedCount = new LongAdder();
    private CountDownLatch completionLatch;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // Log whether virtual threads are enabled
        boolean virtualThreads = "true".equalsIgnoreCase(
                System.getProperty("camel.threads.virtual.enabled", "false"));
        LOG.info("Virtual threads enabled: {}", virtualThreads);
        return context;
    }

    @Test
    public void testHighConcurrencyWithSimulatedIO() throws Exception {
        completionLatch = new CountDownLatch(TOTAL_MESSAGES);
        processedCount.reset();

        System.out.println("Starting load test: " + TOTAL_MESSAGES + " messages, "
                           + CONCURRENT_PRODUCERS + " producers, " + CONCURRENT_CONSUMERS + " consumers, "
                           + SIMULATED_IO_DELAY_MS + "ms I/O delay"
                           + (VIRTUAL_THREAD_PER_TASK ? ", virtualThreadPerTask=true" : ""));

        StopWatch watch = new StopWatch();

        // Create producer threads - use virtual threads when available for producers too
        ExecutorService producerPool;
        try {
            producerPool = (ExecutorService) Executors.class
                    .getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
            System.out.println("Using virtual threads for producers");
        } catch (Exception e) {
            producerPool = Executors.newFixedThreadPool(CONCURRENT_PRODUCERS);
            System.out.println("Using platform threads for producers");
        }

        for (int i = 0; i < TOTAL_MESSAGES; i++) {
            final int msgNum = i;
            producerPool.submit(() -> {
                try {
                    template.sendBody("seda:start", "Message-" + msgNum);
                } catch (Exception e) {
                    LOG.error("Error sending message", e);
                }
            });
        }

        // Wait for all messages to be processed
        boolean completed = completionLatch.await(5, TimeUnit.MINUTES);

        long elapsed = watch.taken();
        producerPool.shutdown();

        // Calculate metrics
        long processed = processedCount.sum();
        double throughput = (processed * 1000.0) / elapsed;
        double avgLatency = elapsed / (double) processed;

        // Use System.out for guaranteed visibility in test output
        System.out.println();
        System.out.println("=== Load Test Results ===");
        System.out.println("Completed: " + (completed ? "YES" : "NO (timeout)"));
        System.out.println("Messages processed: " + processed);
        System.out.println("Total time: " + elapsed + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " msg/sec");
        System.out.println("Average latency: " + String.format("%.2f", avgLatency) + " ms/msg");
        System.out.println("Virtual threads: " + System.getProperty("camel.threads.virtual.enabled", "false"));
        System.out.println("Thread-per-task mode: " + VIRTUAL_THREAD_PER_TASK);
        System.out.println();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route with concurrent consumers and simulated I/O delay
                // Use larger queue size to avoid blocking
                String sedaOptions = "concurrentConsumers=" + CONCURRENT_CONSUMERS
                                     + "&size=" + (TOTAL_MESSAGES + 1000);
                if (VIRTUAL_THREAD_PER_TASK) {
                    // Use thread-per-task mode - optimal for virtual threads
                    // concurrentConsumers becomes a concurrency limit
                    sedaOptions += "&virtualThreadPerTask=true";
                }
                from("seda:start?" + sedaOptions)
                        .routeId("loadTestRoute")
                        .process(new SimulatedIOProcessor())
                        .process(exchange -> {
                            processedCount.increment();
                            completionLatch.countDown();
                        });
            }
        };
    }

    /**
     * Processor that simulates I/O delay (e.g., database call, HTTP request). This is where virtual threads should show
     * significant improvement - platform threads block during sleep, while virtual threads yield.
     */
    private static class SimulatedIOProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            // Simulate blocking I/O operation
            Thread.sleep(SIMULATED_IO_DELAY_MS);
        }
    }
}
