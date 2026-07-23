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

import java.util.concurrent.TimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ResilienceTimeoutWithFallbackExchangePropertyTest extends CamelTestSupport {

    @Test
    public void testTimeoutWithFallbackExchangeProperties() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Fallback response");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, true);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_TIMED_OUT, true);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker()
                        .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(2000).end()
                        .to("direct:slow")
                        .onFallback()
                        .process(e -> {
                            Throwable caught = e.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                            assertIsInstanceOf(TimeoutException.class, caught);
                            assertNull(e.getException());
                        })
                        .transform().constant("Fallback response")
                        .end()
                        .to("mock:result");

                from("direct:slow")
                        .delay(5000).transform().constant("Slow response");
            }
        };
    }
}
