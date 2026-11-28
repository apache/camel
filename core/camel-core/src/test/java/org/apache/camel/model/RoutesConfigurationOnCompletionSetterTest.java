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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25420: the YAML and XML DSLs do not build a route configuration with the fluent
 * {@link RouteConfigurationDefinition#onCompletion()}, they set the list with
 * {@link RouteConfigurationDefinition#setOnCompletions(List)}. The onCompletion then has no back-reference to its route
 * configuration, and must still fire once per exchange, the same as with the Java DSL (see
 * {@link RoutesConfigurationOnCompletionTest}).
 */
public class RoutesConfigurationOnCompletionSetterTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Adds a route configuration the way the YAML and XML DSLs do: the onCompletion is created on its own and set as a
     * list.
     */
    private void addConfiguration(String id, OnCompletionDefinition... onCompletions) throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                RouteConfigurationDefinition rc = id != null ? routeConfiguration(id) : routeConfiguration();
                rc.setOnCompletions(new ArrayList<>(List.of(onCompletions)));
            }
        });
    }

    private void addConsumerAndProcessorRoutes(String consumerConfigId) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition consumer = from("direct:consumer");
                if (consumerConfigId != null) {
                    consumer.routeConfigurationId(consumerConfigId);
                }
                consumer.to("direct:processor");

                from("direct:processor").routeConfigurationId("myconfig")
                        .to("mock:result");
            }
        });
    }

    @Test
    public void testGlobalFiresOncePerExchange() throws Exception {
        // a global configuration applies to both routes of the exchange: it must fire for the consumer route only
        addConfiguration(null, new OnCompletionDefinition().to("mock:global"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("direct:sub");
                from("direct:sub").to("mock:sub");
            }
        });
        context.start();

        getMockEndpoint("mock:global").expectedMessageCount(1);
        getMockEndpoint("mock:sub").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testGlobalStarFiresOncePerExchange() throws Exception {
        addConfiguration("*", new OnCompletionDefinition().to("mock:global"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("direct:sub");
                from("direct:sub").to("mock:sub");
            }
        });
        context.start();

        getMockEndpoint("mock:global").expectedMessageCount(1);
        getMockEndpoint("mock:sub").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNamedWithIndirectConsumer() throws Exception {
        // only the called route opts in: the onCompletion must still fire (REST DSL -> direct: case)
        addConfiguration("myconfig", new OnCompletionDefinition().to("mock:completion"));
        addConsumerAndProcessorRoutes(null);
        context.start();

        getMockEndpoint("mock:completion").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:consumer", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNamedBothRoutesOptIn() throws Exception {
        // the bug of CAMEL-25135: without the dedup it fires once per opted-in route
        addConfiguration("myconfig", new OnCompletionDefinition().to("mock:completion"));
        addConsumerAndProcessorRoutes("myconfig");
        context.start();

        getMockEndpoint("mock:completion").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:consumer", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNamedBeforeConsumerBothRoutesOptIn() throws Exception {
        // BeforeConsumer must fire once, from the consumer route, so it sees the header the consumer sets after the call
        List<String> seen = new ArrayList<>();
        OnCompletionDefinition onCompletion = new OnCompletionDefinition();
        onCompletion.setMode(OnCompletionMode.BeforeConsumer.name());
        onCompletion.process(e -> seen.add(e.getMessage().getHeader("fromConsumer", String.class)));
        addConfiguration("myconfig", onCompletion);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:consumer").routeConfigurationId("myconfig")
                        .to("direct:processor")
                        .setHeader("fromConsumer", constant("yes"));

                from("direct:processor").routeConfigurationId("myconfig")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Exchange result = template.request("direct:consumer", e -> e.getMessage().setBody("Hello World"));

        assertMockEndpointsSatisfied();
        assertEquals("yes", result.getMessage().getHeader("fromConsumer"));
        assertEquals(List.of("yes"), seen);
    }
}
