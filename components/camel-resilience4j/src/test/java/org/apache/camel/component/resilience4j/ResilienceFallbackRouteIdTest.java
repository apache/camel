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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.MessageHistory;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.OnFallbackDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The steps of a fallback set as a whole, as the YAML and XML loaders do, belong to the route of the circuit breaker:
 * the message history and the error registry have its route, not the id of the first step of the fallback.
 */
public class ResilienceFallbackRouteIdTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setMessageHistory(true);
        return context;
    }

    @Test
    public void testTheStepsOfTheFallbackBelongToTheRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Fallback response");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
        @SuppressWarnings("unchecked")
        List<MessageHistory> history = mock.getReceivedExchanges().get(0).getProperty(Exchange.MESSAGE_HISTORY, List.class);
        List<String> nodes = history.stream().map(h -> h.getNode().getId()).toList();
        assertTrue(nodes.containsAll(List.of("cb", "choice", "fallbackBody")), "the steps of the fallback: " + nodes);
        for (MessageHistory h : history) {
            assertEquals("start", h.getRouteId(), "the route of " + h.getNode().getId());
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                CircuitBreakerDefinition cb = from("direct:start").routeId("start").circuitBreaker();
                cb.id("cb").throwException(new IllegalStateException("Forced"));
                // the fallback as the loaders set it: built on its own, its steps added before it has a parent
                OnFallbackDefinition fallback = new OnFallbackDefinition();
                ChoiceDefinition choice = new ChoiceDefinition();
                choice.id("choice");
                choice.when(constant(true)).setBody(constant("Fallback response")).id("fallbackBody");
                fallback.addOutput(choice);
                cb.setOnFallback(fallback);
                cb.end().to("mock:result");
            }
        };
    }
}
