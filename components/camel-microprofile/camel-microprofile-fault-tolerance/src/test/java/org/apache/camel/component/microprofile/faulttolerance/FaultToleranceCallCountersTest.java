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
package org.apache.camel.component.microprofile.faulttolerance;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fallback, timed out and bulkhead rejected counters, which the circuit breaker listeners do not tell apart.
 */
public class FaultToleranceCallCountersTest extends CamelTestSupport {

    private final CountDownLatch permitAcquired = new CountDownLatch(1);

    @Test
    public void testFallbackAndRejectedCounters() throws Exception {
        getMockEndpoint("mock:down").expectedMessageCount(3);

        // two failures open the breaker, the third call is rejected without being attempted
        template.sendBody("direct:down", "Hello World");
        template.sendBody("direct:down", "Hello World");
        template.sendBody("direct:down", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        FaultToleranceProcessor cb = context.getProcessor("cbDown", FaultToleranceProcessor.class);
        assertEquals(3, cb.getNumberOfFallbackCalls());
        assertEquals(1, cb.getNumberOfNotPermittedCalls());
        assertEquals(0, cb.getNumberOfTimedOutCalls());
        assertEquals(0, cb.getNumberOfBulkheadRejectedCalls());

        cb.transitionToCloseState();
        assertEquals(0, cb.getNumberOfFallbackCalls());
    }

    @Test
    public void testTimedOutCounter() throws Exception {
        getMockEndpoint("mock:slow").expectedBodiesReceived("Fallback response");

        template.sendBody("direct:slow", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        FaultToleranceProcessor cb = context.getProcessor("cbSlow", FaultToleranceProcessor.class);
        assertEquals(1, cb.getNumberOfFallbackCalls());
        assertEquals(1, cb.getNumberOfTimedOutCalls());
        assertEquals(0, cb.getNumberOfNotPermittedCalls());
    }

    @Test
    public void testBulkheadRejectedWithFallbackCounter() throws Exception {
        getMockEndpoint("mock:bulkhead").expectedMessageCount(2);

        // the first call holds the only bulkhead permit while it is slow,
        // so the second call is rejected by the bulkhead and answered by the fallback
        template.asyncSendBody("direct:bulkhead", "Hello World");
        assertTrue(permitAcquired.await(5, TimeUnit.SECONDS), "first call should be inside the bulkhead");
        template.sendBody("direct:bulkhead", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // the rejected call reached the fallback with the bulkhead exception (and came back first,
        // before the slow call finished), so it was rejected, not queued
        long rejected = getMockEndpoint("mock:bulkhead").getExchanges().stream()
                .filter(e -> e.getProperty(Exchange.EXCEPTION_CAUGHT) instanceof BulkheadException).count();
        assertEquals(1, rejected, "exactly one of the two calls should have been rejected by the bulkhead");
        FaultToleranceProcessor cb = context.getProcessor("cbBulkhead", FaultToleranceProcessor.class);
        assertEquals(1, cb.getNumberOfBulkheadRejectedCalls(),
                "the rejected call is counted from inside the fallback path");
        assertEquals(1, cb.getNumberOfFallbackCalls());
        assertEquals(0, cb.getNumberOfNotPermittedCalls());
        assertEquals(0, cb.getNumberOfTimedOutCalls());
    }

    @Test
    public void testTimedOutWithoutFallbackCounter() throws Exception {
        // no onFallback: the timeout fails the exchange, and the counter is kept on that path too
        Exchange result = template.request("direct:slowNoFallback", e -> e.getMessage().setBody("Hello World"));

        assertTrue(result.isFailed(), "the timed out call should fail the exchange");
        FaultToleranceProcessor cb = context.getProcessor("cbSlowNoFallback", FaultToleranceProcessor.class);
        assertEquals(1, cb.getNumberOfTimedOutCalls());
        assertEquals(0, cb.getNumberOfFallbackCalls());
    }

    @Test
    public void testBulkheadRejectedWithoutFallbackCounter() throws Exception {
        // no onFallback: the second call is rejected by the bulkhead and fails the exchange
        template.asyncSendBody("direct:bulkheadNoFallback", "Hello World");
        assertTrue(permitAcquired.await(5, TimeUnit.SECONDS), "first call should be inside the bulkhead");
        Exchange result = template.request("direct:bulkheadNoFallback", e -> e.getMessage().setBody("Hello World"));

        assertTrue(result.isFailed(), "the rejected call should fail the exchange");
        // SmallRye needs a waiting queue of at least 1; the second call is still rejected, not queued
        assertInstanceOf(BulkheadException.class, result.getException());
        FaultToleranceProcessor cb = context.getProcessor("cbBulkheadNoFallback", FaultToleranceProcessor.class);
        assertEquals(1, cb.getNumberOfBulkheadRejectedCalls());
        assertEquals(0, cb.getNumberOfFallbackCalls());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:down")
                        .circuitBreaker().id("cbDown")
                        .faultToleranceConfiguration()
                        .requestVolumeThreshold(2).failureRatio(50).delay(60000).end()
                        .throwException(new IllegalStateException("Service is down"))
                        .onFallback()
                        .transform().constant("Fallback response")
                        .end()
                        .to("mock:down");

                from("direct:slow")
                        .circuitBreaker().id("cbSlow")
                        .faultToleranceConfiguration().timeoutEnabled(true).timeoutDuration(200).end()
                        .to("direct:slowService")
                        .onFallback()
                        .transform().constant("Fallback response")
                        .end()
                        .to("mock:slow");

                from("direct:slowService")
                        .delay(2000).transform().constant("Slow response");

                from("direct:bulkhead")
                        .circuitBreaker().id("cbBulkhead")
                        .faultToleranceConfiguration()
                        .bulkheadEnabled(true).bulkheadMaxConcurrentCalls(1).bulkheadWaitingTaskQueue(1).end()
                        .process(e -> permitAcquired.countDown())
                        .to("direct:slowService")
                        .onFallback()
                        .transform().constant("Fallback response")
                        .end()
                        .to("mock:bulkhead");

                from("direct:slowNoFallback")
                        .circuitBreaker().id("cbSlowNoFallback")
                        .faultToleranceConfiguration().timeoutEnabled(true).timeoutDuration(200).end()
                        .to("direct:slowService")
                        .end();

                from("direct:bulkheadNoFallback")
                        .circuitBreaker().id("cbBulkheadNoFallback")
                        .faultToleranceConfiguration().bulkheadEnabled(true).bulkheadMaxConcurrentCalls(1).bulkheadWaitingTaskQueue(1).end()
                        .process(e -> permitAcquired.countDown())
                        .to("direct:slowService")
                        .end();
            }
        };
    }
}
