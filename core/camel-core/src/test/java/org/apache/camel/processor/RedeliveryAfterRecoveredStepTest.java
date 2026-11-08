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
package org.apache.camel.processor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-26734: a step that fails after another step was redelivered and then succeeded gets its own redeliveries. The
 * redelivery headers of the step that recovered stay on the message, but they must not count for the next failure, in
 * the same route or in a route that gets a copy of the exchange over seda.
 */
public class RedeliveryAfterRecoveredStepTest extends ContextTestSupport {

    private final AtomicInteger oneCalls = new AtomicInteger();
    private final AtomicInteger twoCalls = new AtomicInteger();
    private final List<Object> twoSaw = new CopyOnWriteArrayList<>();

    @Test
    public void testNextStepIsRedelivered() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        // step one: the first call and 2 redeliveries; step two: the first call and 1 redelivery
        assertEquals(3, oneCalls.get());
        assertEquals(2, twoCalls.get());
        // the first call of step two still sees the headers of step one, and its redelivery counts from 1
        assertEquals(List.of(2, 1), twoSaw);
    }

    @Test
    public void testRouteAfterSedaHandOffIsRedelivered() throws Exception {
        getMockEndpoint("mock:shipped").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        template.sendBody("direct:handOff", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(3, oneCalls.get());
        assertEquals(2, twoCalls.get());
        assertEquals(List.of(2, 1), twoSaw);
    }

    @Test
    public void testRedeliveryHeadersStayAfterTheStepRecovered() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:afterOne");
        result.expectedMessageCount(1);

        template.sendBody("direct:recoverOnly", "Hello World");

        assertMockEndpointsSatisfied();
        Exchange exchange = result.getReceivedExchanges().get(0);
        // the next steps can still see that the message was redelivered
        assertEquals(Boolean.TRUE, exchange.getMessage().getHeader(Exchange.REDELIVERED));
        assertEquals(2, exchange.getMessage().getHeader(Exchange.REDELIVERY_COUNTER));
        assertEquals(2, exchange.getMessage().getHeader(Exchange.REDELIVERY_MAX_COUNTER));
        assertEquals(3, oneCalls.get());
    }

    @Test
    public void testRoutingSlipRedeliveriesAfterARecoveredStep() throws Exception {
        // as RoutingSlipNestedSlipRedeliveryTest: the routing slip step is redelivered twice, also when a step was
        // redelivered and recovered before it
        getMockEndpoint("mock:inner").expectedMessageCount(3);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:nested", "Hello World");

        assertMockEndpointsSatisfied();
        assertEquals(3, oneCalls.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));

                from("direct:start")
                        .process(this::stepOne)
                        .process(this::stepTwo)
                        .to("mock:result");

                from("direct:handOff")
                        .process(this::stepOne)
                        .to("seda:shipping");
                from("seda:shipping")
                        .process(this::stepTwo)
                        .to("mock:shipped");

                from("direct:nested")
                        .process(this::stepOne)
                        .routingSlip(constant("direct:slipThenFail"));
                from("direct:slipThenFail").errorHandler(noErrorHandler())
                        .routingSlip(constant("mock:inner"))
                        .throwException(new IllegalStateException("boom"));

                from("direct:recoverOnly")
                        .process(this::stepOne)
                        .to("mock:afterOne");
            }

            // fails twice, and succeeds at the second redelivery
            private void stepOne(Exchange exchange) {
                if (oneCalls.incrementAndGet() < 3) {
                    throw new IllegalStateException("step one fails");
                }
            }

            // fails once, at its first call
            private void stepTwo(Exchange exchange) {
                Integer counter = exchange.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
                twoSaw.add(counter != null ? counter : "none");
                if (twoCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("step two fails once");
                }
            }
        };
    }
}
