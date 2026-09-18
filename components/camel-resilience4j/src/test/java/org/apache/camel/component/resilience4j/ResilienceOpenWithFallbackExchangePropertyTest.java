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

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Inside the onFallback the rejected property tells a call that was never attempted (breaker open) apart from a call
 * that was made and failed.
 */
public class ResilienceOpenWithFallbackExchangePropertyTest extends CamelTestSupport {

    @Test
    public void testRejectedOnlyWhenBreakerOpen() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.allMessages().body().isEqualTo("Fallback response");
        mock.allMessages().exchangeProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK).isEqualTo(true);
        mock.allMessages().exchangeProperty(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED).isEqualTo(true);

        // two failures fill the window and open the breaker, the third call is not attempted
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        Exchange first = mock.getExchanges().get(0);
        assertEquals(false, first.getProperty(CircuitBreakerConstants.RESPONSE_REJECTED));
        assertInstanceOf(IllegalStateException.class, first.getProperty(Exchange.EXCEPTION_CAUGHT));

        Exchange third = mock.getExchanges().get(2);
        assertEquals(true, third.getProperty(CircuitBreakerConstants.RESPONSE_REJECTED));
        assertEquals("OPEN", third.getProperty(CircuitBreakerConstants.RESPONSE_STATE));
        assertInstanceOf(CallNotPermittedException.class, third.getProperty(Exchange.EXCEPTION_CAUGHT));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker()
                        .resilience4jConfiguration()
                        .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50)
                        .waitDurationInOpenState(60).end()
                        .to("direct:down")
                        .onFallback()
                        .transform().constant("Fallback response")
                        .end()
                        .to("mock:result");

                from("direct:down")
                        .throwException(new IllegalStateException("Service is down"));
            }
        };
    }
}
