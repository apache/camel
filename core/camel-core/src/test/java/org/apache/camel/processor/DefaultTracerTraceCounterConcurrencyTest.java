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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link org.apache.camel.impl.engine.DefaultTracer#traceCounter} is thread-safe under concurrent
 * routing. Before the fix (CAMEL-24265) the counter was a plain {@code long} incremented with {@code ++}, which is a
 * compound read-modify-write — a classic lost-update race under concurrency. The fix switches to
 * {@link java.util.concurrent.atomic.AtomicLong}.
 */
class DefaultTracerTraceCounterConcurrencyTest extends ContextTestSupport {

    private static final int THREADS = 8;
    private static final int MESSAGES_PER_THREAD = 250;
    private static final int TOTAL_MESSAGES = THREADS * MESSAGES_PER_THREAD;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.setTracing(true);
        return ctx;
    }

    @Test
    void traceCounterShouldBeAccurateUnderConcurrentRouting() throws Exception {
        // Each message traverses one traced node ("mock:result"), so we expect
        // the trace counter to equal the total number of messages sent.
        getMockEndpoint("mock:result").expectedMessageCount(TOTAL_MESSAGES);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int t = 0; t < THREADS; t++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        for (int i = 0; i < MESSAGES_PER_THREAD; i++) {
                            template.sendBody("direct:start", "msg");
                        }
                    } catch (Exception e) {
                        // let the assertion on the mock catch failures
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            // release all threads at once to maximise contention
            startGate.countDown();
            doneLatch.await();
        } finally {
            pool.shutdown();
        }

        assertMockEndpointsSatisfied();

        Tracer tracer = context.getTracer();
        assertThat(tracer.getTraceCounter())
                .as("Trace counter must equal total messages when using AtomicLong")
                .isEqualTo(TOTAL_MESSAGES);
    }

    @Test
    void resetTraceCounterShouldClearCount() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();

        Tracer tracer = context.getTracer();
        assertThat(tracer.getTraceCounter()).isGreaterThan(0);

        tracer.resetTraceCounter();
        assertThat(tracer.getTraceCounter()).isZero();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }
}
