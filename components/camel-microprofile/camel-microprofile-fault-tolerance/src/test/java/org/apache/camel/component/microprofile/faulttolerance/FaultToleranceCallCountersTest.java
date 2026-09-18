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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The fallback, timed out and bulkhead rejected counters, which the circuit breaker listeners do not tell apart.
 */
public class FaultToleranceCallCountersTest extends CamelTestSupport {

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
            }
        };
    }
}
