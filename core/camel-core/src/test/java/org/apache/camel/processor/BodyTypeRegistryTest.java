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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BodyTypeRegistry;
import org.apache.camel.spi.BodyTypeRegistry.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body types each node has been reached with, kept across the exchanges (CAMEL-25750): not only what the last
 * exchange had, as the message history of one exchange says.
 */
public class BodyTypeRegistryTest extends ContextTestSupport {

    private BodyTypeRegistry registry;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        registry = context.getCamelContextExtension().getContextPlugin(BodyTypeRegistry.class);
        assertNotNull(registry);
    }

    @Test
    public void testTypesAcrossExchangesAndBranches() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "hello");
        template.sendBody("direct:start", "map");
        assertMockEndpointsSatisfied();

        // both messages reached the processor as text, and it turned the second one into a Map
        assertEquals(List.of("java.lang.String"), registry.getBodyTypes("orders", "maybeMap"));
        // so the choice was reached with both types: it varied, and with a payload and data, so it needs attention
        assertEquals(List.of("java.lang.String", "java.util.LinkedHashMap"), registry.getBodyTypes("orders", "choice"));
        assertEquals(List.of(Kind.PAYLOAD, Kind.DATA), registry.getBodyKinds("orders", "choice"));
        assertEquals(List.of(Kind.PAYLOAD), registry.getBodyKinds("orders", "maybeMap"));
        // each branch has the type of the message that was routed there, also the branch the last message did not take
        assertEquals(List.of("java.lang.String"), registry.getBodyTypes("orders", "onlyText"));
        assertEquals(List.of("java.util.LinkedHashMap"), registry.getBodyTypes("orders", "onlyMaps"));
        assertEquals(List.of("java.lang.String", "java.util.LinkedHashMap"), registry.getBodyTypes("orders", "result"));
    }

    @Test
    public void testANullBodyIsAType() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", null);
        assertMockEndpointsSatisfied();

        // a null body is shown as such, as in the message history
        assertEquals(List.of("null"), registry.getBodyTypes("orders", "maybeMap"));
        assertEquals(List.of(Kind.NULL), registry.getBodyKinds("orders", "maybeMap"));
    }

    @Test
    public void testNotReachedIsEmpty() {
        assertTrue(registry.getBodyTypes("orders", "maybeMap").isEmpty());
        assertTrue(registry.getBodyTypes("unknown", "maybeMap").isEmpty());
    }

    @Test
    public void testNotRecordedWithoutMessageHistory() throws Exception {
        getMockEndpoint("mock:quiet").expectedMessageCount(1);

        template.sendBody("direct:quiet", "hello");
        assertMockEndpointsSatisfied();

        assertTrue(registry.getBodyTypes("quiet", "quietResult").isEmpty());
    }

    @Test
    public void testRemovedRouteIsForgotten() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "hello");
        assertMockEndpointsSatisfied();
        assertEquals(List.of("java.lang.String"), registry.getBodyTypes("orders", "maybeMap"));

        // a route that is loaded again (such as by a reload in dev mode) starts afresh
        context.getRouteController().stopRoute("orders");
        context.removeRoute("orders");
        assertTrue(registry.getBodyTypes("orders", "maybeMap").isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setMessageHistory(true);

                from("direct:start").routeId("orders")
                        .process(e -> {
                            if ("map".equals(e.getMessage().getBody())) {
                                e.getMessage().setBody(new LinkedHashMap<>(Map.of("id", 1)));
                            }
                        }).id("maybeMap")
                        .choice().id("choice")
                        .when(body().isInstanceOf(Map.class))
                        .log("a map").id("onlyMaps")
                        .otherwise()
                        .log("a text").id("onlyText")
                        .end()
                        .to("mock:result").id("result");

                from("direct:quiet").routeId("quiet").messageHistory(false)
                        .to("mock:quiet").id("quietResult");
            }
        };
    }
}
