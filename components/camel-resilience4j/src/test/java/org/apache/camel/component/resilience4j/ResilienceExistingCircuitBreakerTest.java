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

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ResilienceExistingCircuitBreakerTest extends CamelTestSupport {

    @BindToRegistry
    public CircuitBreaker myCircuitBreaker() {
        return CircuitBreaker.ofDefaults("myCircuitBreaker");
    }

    @Test
    public void testResilience() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Fallback message");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, false);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, true);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        CircuitBreaker cb = context().getRegistry().lookupByNameAndType("myCircuitBreaker", CircuitBreaker.class);
        assertNotNull(cb);
        assertEquals("myCircuitBreaker", cb.getName());
        assertEquals(0, cb.getMetrics().getNumberOfSuccessfulCalls());
        assertEquals(1, cb.getMetrics().getNumberOfFailedCalls());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:start").circuitBreaker().resilience4jConfiguration().circuitBreakerRef("myCircuitBreaker").end()
                    .throwException(new IllegalArgumentException("Forced")).onFallback().transform().constant("Fallback message").end().to("log:result").to("mock:result");
            }
        };
    }

}
