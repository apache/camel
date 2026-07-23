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

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResilienceNewOptionsTest extends CamelTestSupport {

    @Test
    public void testSlidingWindowSynchronizationStrategy() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, true);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        ResilienceProcessor processor = findResilienceProcessor();
        assertNotNull(processor);

        CircuitBreakerConfig config = processor.getCircuitBreaker().getCircuitBreakerConfig();
        assertEquals(CircuitBreakerConfig.SlidingWindowSynchronizationStrategy.LOCK_FREE,
                config.getSlidingWindowSynchronizationStrategy());
    }

    @Test
    public void testMaxWaitDurationInHalfOpenState() throws Exception {
        getMockEndpoint("mock:result2").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start2", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        ResilienceProcessor processor = findResilienceProcessor("start2");
        assertNotNull(processor);

        CircuitBreakerConfig config = processor.getCircuitBreaker().getCircuitBreakerConfig();
        assertEquals(30_000, config.getMaxWaitDurationInHalfOpenState().toMillis());
    }

    @Test
    public void testBulkheadFairCallHandlingDisabled() throws Exception {
        getMockEndpoint("mock:result3").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start3", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        ResilienceProcessor processor = findResilienceProcessor("start3");
        assertNotNull(processor);
        assertTrue(processor.isBulkheadEnabled());
    }

    private ResilienceProcessor findResilienceProcessor() {
        return findResilienceProcessor("start");
    }

    private ResilienceProcessor findResilienceProcessor(String routeSuffix) {
        Route route = context.getRoute(routeSuffix);
        assertNotNull(route, "Route '" + routeSuffix + "' not found");
        return findResilienceProcessor(route.navigate());
    }

    private ResilienceProcessor findResilienceProcessor(Navigate<Processor> navigate) {
        for (Processor processor : navigate.next()) {
            if (processor instanceof ResilienceProcessor) {
                return (ResilienceProcessor) processor;
            }
            if (processor instanceof Navigate) {
                @SuppressWarnings("unchecked")
                Navigate<Processor> nav = (Navigate<Processor>) processor;
                if (nav.hasNext()) {
                    ResilienceProcessor found = findResilienceProcessor(nav);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start")
                        .circuitBreaker()
                            .resilience4jConfiguration()
                                .slidingWindowSynchronizationStrategy("LOCK_FREE")
                            .end()
                            .to("direct:foo")
                        .end()
                        .to("mock:result");

                from("direct:start2").routeId("start2")
                        .circuitBreaker()
                            .resilience4jConfiguration()
                                .maxWaitDurationInHalfOpenState("30s")
                            .end()
                            .to("direct:foo")
                        .end()
                        .to("mock:result2");

                from("direct:start3").routeId("start3")
                        .circuitBreaker()
                            .resilience4jConfiguration()
                                .bulkheadEnabled(true)
                                .bulkheadFairCallHandlingEnabled(false)
                            .end()
                            .to("direct:foo")
                        .end()
                        .to("mock:result3");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }
}
