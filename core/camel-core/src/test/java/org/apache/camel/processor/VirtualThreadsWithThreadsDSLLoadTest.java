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
 * Load test using the threads() DSL to directly exercise the thread pool creation which uses ContextValue/ScopedValue
 * for the "create processor" context.
 * <p>
 * This test is disabled by default as it's meant to be run manually for benchmarking.
 * <p>
 * Run with platform threads (default):
 *
 * <pre>
 * mvn test -Dtest=VirtualThreadsWithThreadsDSLLoadTest -pl core/camel-core
 * </pre>
 * <p>
 * Run with virtual threads (JDK 21+):
 *
 * <pre>
 * mvn test -Dtest=VirtualThreadsWithThreadsDSLLoadTest -pl core/camel-core -Dcamel.threads.virtual.enabled=true
 * </pre>
 */
@Disabled("Manual load test - run explicitly for benchmarking")
public class VirtualThreadsWithThreadsDSLLoadTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualThreadsWithThreadsDSLLoadTest.class);

    // Configuration - can be overridden via system properties
    private static final int TOTAL_MESSAGES = Integer.getInteger("loadtest.messages", 5_000);
    private static final int CONCURRENT_PRODUCERS = Integer.getInteger("loadtest.producers", 50);
    private static final int THREAD_POOL_SIZE = Integer.getInteger("loadtest.poolSize", 20);
    private static final int MAX_POOL_SIZE = Integer.getInteger("loadtest.maxPoolSize", 100);
    private static final int SIMULATED_IO_DELAY_MS = Integer.getInteger("loadtest.delay", 10);

    private final LongAdder processedCount = new LongAdder();
    private CountDownLatch completionLatch;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        boolean virtualThreads = "true".equalsIgnoreCase(
                System.getProperty("camel.threads.virtual.enabled", "false"));
        LOG.info("Virtual threads enabled: {}", virtualThreads);
        return context;
    }

    @Test
    public void testThreadsDSLWithSimulatedIO() throws Exception {
        completionLatch = new CountDownLatch(TOTAL_MESSAGES);
        processedCount.reset();

        LOG.info("Starting threads() DSL load test: {} messages, {} producers, pool {}-{}, {}ms I/O delay",
                TOTAL_MESSAGES, CONCURRENT_PRODUCERS, THREAD_POOL_SIZE, MAX_POOL_SIZE, SIMULATED_IO_DELAY_MS);

        StopWatch watch = new StopWatch();

        ExecutorService producerPool = Executors.newFixedThreadPool(CONCURRENT_PRODUCERS);
        for (int i = 0; i < TOTAL_MESSAGES; i++) {
            final int msgNum = i;
            producerPool.submit(() -> {
                try {
                    template.sendBody("direct:start", "Message-" + msgNum);
                } catch (Exception e) {
                    LOG.error("Error sending message", e);
                }
            });
        }

        boolean completed = completionLatch.await(5, TimeUnit.MINUTES);

        long elapsed = watch.taken();
        producerPool.shutdown();

        long processed = processedCount.sum();
        double throughput = (processed * 1000.0) / elapsed;

        // Use System.out for guaranteed visibility in test output
        System.out.println();
        System.out.println("=== threads() DSL Load Test Results ===");
        System.out.println("Completed: " + (completed ? "YES" : "NO (timeout)"));
        System.out.println("Messages processed: " + processed);
        System.out.println("Total time: " + elapsed + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " msg/sec");
        System.out.println("Virtual threads: " + System.getProperty("camel.threads.virtual.enabled", "false"));
        System.out.println();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route using threads() DSL - this exercises ContextValue for createProcessor
                from("direct:start")
                        .routeId("threadsDSLLoadTest")
                        .threads(THREAD_POOL_SIZE, MAX_POOL_SIZE)
                        .threadName("loadTest")
                        .process(new SimulatedIOProcessor())
                        .process(exchange -> {
                            processedCount.increment();
                            completionLatch.countDown();
                        });
            }
        };
    }

    private static class SimulatedIOProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Thread.sleep(SIMULATED_IO_DELAY_MS);
        }
    }
}
