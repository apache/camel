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
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * In asynchronous mode, a call that fails keeps its timeout scheduled until the timeout duration has passed (the time
 * limiter cancels it only on success). The default thread pool profile is scaled down to a queue of 1 here, so one
 * failed call fills a queue that 1000 failed calls fill with the defaults: the next call must still succeed, and not
 * fail because its timeout could not be scheduled after its call was submitted.
 */
class ResilienceAsyncTimeoutSchedulerQueueTest extends CamelTestSupport {

    private final AtomicInteger healthyCalls = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ThreadPoolProfile profile = ctx.getExecutorServiceManager().getDefaultThreadPoolProfile();
        profile.setPoolSize(1);
        profile.setMaxPoolSize(1);
        profile.setMaxQueueSize(1);
        return ctx;
    }

    @Test
    void testCallAfterFailedCallIsNotRejected() {
        // the timeout of the failed call stays scheduled for a minute, longer than the test
        Exchange failed = template.send("direct:async", e -> e.getIn().setHeader("fail", true));
        assertInstanceOf(IllegalStateException.class, failed.getException());

        Exchange healthy = template.send("direct:async", e -> e.getIn().setBody("Hello"));
        assertNull(healthy.getException(), "the call to a healthy backend succeeds");
        assertEquals("Bye", healthy.getMessage().getBody());
        assertEquals(1, healthyCalls.get());

        CircuitBreaker cb = context.getProcessor("cbAsync", ResilienceProcessor.class).getCircuitBreaker();
        assertEquals(1, cb.getMetrics().getNumberOfFailedCalls());
        assertEquals(1, cb.getMetrics().getNumberOfSuccessfulCalls());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:async").circuitBreaker().id("cbAsync")
                        .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration("60000")
                        .end()
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
