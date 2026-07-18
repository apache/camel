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

import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FaultToleranceSuspendResumeTest extends CamelTestSupport {

    @Test
    public void testSuspendResumePreservesCircuitBreakerState() throws Exception {
        // send a message to verify the route works
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        template.sendBody("direct:start", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        // find the FaultToleranceProcessor
        FaultToleranceProcessor processor = findFaultToleranceProcessor();
        assertNotNull(processor);

        // verify circuit breaker starts in CLOSED state
        assertEquals("CLOSED", processor.getCircuitBreakerState());

        // suspend the processor
        processor.suspend();

        // circuit breaker state must still be CLOSED after suspend
        assertEquals("CLOSED", processor.getCircuitBreakerState());

        // resume the processor
        processor.resume();

        // circuit breaker state must still be CLOSED after resume
        assertEquals("CLOSED", processor.getCircuitBreakerState());

        // verify route still works after resume
        MockEndpoint.resetMocks(context);
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, true);
        template.sendBody("direct:start", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    private FaultToleranceProcessor findFaultToleranceProcessor() {
        Route route = context.getRoute("start");
        return findProcessor(route.getProcessor());
    }

    private FaultToleranceProcessor findProcessor(Processor processor) {
        if (processor instanceof FaultToleranceProcessor) {
            return (FaultToleranceProcessor) processor;
        }
        if (processor instanceof Navigate) {
            Navigate<?> nav = (Navigate<?>) processor;
            if (nav.hasNext()) {
                for (Object next : nav.next()) {
                    if (next instanceof Processor) {
                        FaultToleranceProcessor found = findProcessor((Processor) next);
                        if (found != null) {
                            return found;
                        }
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
                            .to("direct:foo")
                        .onFallback()
                            .transform().constant("Fallback message")
                        .end()
                        .to("log:result").to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }
}
