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
package org.apache.camel.component.resilience4j;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * In asynchronous mode, each call that fails keeps its timeout scheduled until the timeout duration has passed (the
 * time limiter cancels it only on success). With the default thread pool profile (a queue of 1000), 1000 calls that
 * fail fast must not make the next call fail because its timeout could not be scheduled after its call was submitted.
 */
class ResilienceAsyncTimeoutSchedulerDefaultSizeTest extends CamelTestSupport {

    private final AtomicInteger healthyCalls = new AtomicInteger();

    @Test
    void testCallAfterThousandFailedCallsIsNotRejected() {
        for (int i = 0; i < 1000; i++) {
            Exchange failed = template.send("direct:async", e -> e.getIn().setHeader("fail", true));
            assertInstanceOf(IllegalStateException.class, failed.getException());
        }

        Exchange healthy = template.send("direct:async", e -> e.getIn().setBody("Hello"));
        assertNull(healthy.getException(), "the call to a healthy backend succeeds");
        assertEquals("Bye", healthy.getMessage().getBody());
        assertEquals(1, healthyCalls.get(), "the backend gets exactly one call for the healthy exchange");

        CircuitBreaker cb = context.getProcessor("cbAsync", ResilienceProcessor.class).getCircuitBreaker();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(1000, cb.getMetrics().getNumberOfFailedCalls());
        assertEquals(1, cb.getMetrics().getNumberOfSuccessfulCalls());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:async").circuitBreaker().id("cbAsync")
                        // the timeouts of the failed calls stay scheduled for a minute, longer than the test, and the
                        // window is larger than the test, so the breaker stays closed
                        .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration("60000")
                        .slidingWindowSize(5000).minimumNumberOfCalls(5000).end()
                        .process(e -> {
                            if (e.getIn().getHeader("fail", false, Boolean.class)) {
                                throw new IllegalStateException("backend error");
                            }
                            healthyCalls.incrementAndGet();
                            e.getMessage().setBody("Bye");
                        })
                        .end();
            }
        };
    }
}
