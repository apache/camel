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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The fallback, timed out and bulkhead rejected counters, which the circuit breaker metrics do not tell apart.
 */
public class ResilienceCallCountersTest extends CamelTestSupport {

    @Test
    public void testFallbackAndRejectedCounters() throws Exception {
        getMockEndpoint("mock:down").expectedMessageCount(3);

        // two failures open the breaker, the third call is rejected without being attempted
        template.sendBody("direct:down", "Hello World");
        template.sendBody("direct:down", "Hello World");
        template.sendBody("direct:down", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        ResilienceProcessor cb = context.getProcessor("cbDown", ResilienceProcessor.class);
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

        ResilienceProcessor cb = context.getProcessor("cbSlow", ResilienceProcessor.class);
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
        Thread.sleep(500);
        template.sendBody("direct:bulkhead", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        ResilienceProcessor cb = context.getProcessor("cbBulkhead", ResilienceProcessor.class);
        assertEquals(1, cb.getNumberOfBulkheadRejectedCalls(),
                "the rejected call is counted from inside the fallback path");
        assertEquals(1, cb.getNumberOfFallbackCalls());
        assertEquals(0, cb.getNumberOfNotPermittedCalls());
        assertEquals(0, cb.getNumberOfTimedOutCalls());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:down")
                        .circuitBreaker().id("cbDown")
                        .resilience4jConfiguration()
                        .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50)
                        .waitDurationInOpenState(60).end()
                        .throwException(new IllegalStateException("Service is down"))
                        .onFallback()
                        .transform().constant("Fallback response")
                        .end()
                        .to("mock:down");

                from("direct:slow")
                        .circuitBreaker().id("cbSlow")
                        .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(200).end()
                        .to("direct:slowService")
                        .onFallback()
                        .transform().constant("Fallback response")
                        .end()
                        .to("mock:slow");

                from("direct:slowService")
                        .delay(2000).transform().constant("Slow response");

                from("direct:bulkhead")
                        .circuitBreaker().id("cbBulkhead")
                        .resilience4jConfiguration()
                        .bulkheadEnabled(true).bulkheadMaxConcurrentCalls(1).bulkheadMaxWaitDuration(0).end()
                        .to("direct:slowService")
                        .onFallback()
                        .transform().constant("Fallback response")
                        .end()
                        .to("mock:bulkhead");
            }
        };
    }
}
