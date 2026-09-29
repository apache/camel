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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With timeout and bulkhead enabled, a call that timed out but is still running must keep its bulkhead permit, so the
 * bulkhead limits the calls that run against a slow service, and not only the callers that wait for them.
 */
public class ResilienceBulkheadTimeoutTest extends CamelTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger maxRunning = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    public void releaseCalls() {
        release.countDown();
    }

    @Test
    public void testBulkheadWithTimeout() throws Exception {
        doTestBulkheadWithTimeout("direct:sync", "cbSync");
    }

    @Test
    public void testBulkheadWithTimeoutAsynchronous() throws Exception {
        doTestBulkheadWithTimeout("direct:async", "cbAsync");
    }

    private void doTestBulkheadWithTimeout(String uri, String id) throws Exception {
        // the first call times out and gets the fallback, but it is still running against the slow service
        Exchange first = template.request(uri, e -> e.getMessage().setBody("Hello 1"));
        assertEquals("Fallback message", first.getMessage().getBody());
        assertEquals(Boolean.TRUE, first.getProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK));
        assertEquals(Boolean.FALSE, first.getProperty(CircuitBreakerConstants.RESPONSE_REJECTED));

        // so the next calls must be rejected by the bulkhead (it allows 1 call), and not run as well
        for (int i = 2; i <= 3; i++) {
            String body = "Hello " + i;
            Exchange out = template.request(uri, e -> e.getMessage().setBody(body));
            assertEquals("Fallback message", out.getMessage().getBody());
            assertEquals(Boolean.TRUE, out.getProperty(CircuitBreakerConstants.RESPONSE_REJECTED),
                    "call " + i + " should be rejected by the bulkhead");
        }
        assertEquals(1, calls.get(), "only the first call should have been started");
        assertEquals(1, maxRunning.get(), "the bulkhead allows only 1 call at a time");
        ResilienceProcessor cb = context.getProcessor(id, ResilienceProcessor.class);
        assertEquals(2, cb.getNumberOfBulkheadRejectedCalls());
        assertEquals(1, cb.getNumberOfTimedOutCalls());

        // when the slow call ends it releases the permit
        release.countDown();
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> "Bye World".equals(template.requestBody(uri, "Hello 4", String.class)));
        assertEquals(1, maxRunning.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sync").circuitBreaker().id("cbSync")
                        .resilience4jConfiguration().bulkheadEnabled(true).bulkheadMaxConcurrentCalls(1)
                        .timeoutEnabled(true).timeoutDuration(200).end()
                        .to("direct:slow")
                        .onFallback().transform().constant("Fallback message").end();

                from("direct:async").circuitBreaker().id("cbAsync")
                        .resilience4jConfiguration().asynchronous(true).bulkheadEnabled(true).bulkheadMaxConcurrentCalls(1)
                        .timeoutEnabled(true).timeoutDuration(200).end()
                        .to("direct:slow")
                        .onFallback().transform().constant("Fallback message").end();

                from("direct:slow").process(e -> {
                    calls.incrementAndGet();
                    maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                    try {
                        // a slow service
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } finally {
                        running.decrementAndGet();
                    }
                }).transform().constant("Bye World");
            }
        };
    }
}
