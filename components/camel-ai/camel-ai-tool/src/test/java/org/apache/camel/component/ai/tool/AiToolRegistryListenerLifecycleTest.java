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
package org.apache.camel.component.ai.tool;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Verifies that {@link AiToolRegistryListener} callbacks are driven by the {@code ai-tool} consumer lifecycle: route
 * start/resume registers, route stop/suspend deregisters.
 */
class AiToolRegistryListenerLifecycleTest extends CamelTestSupport {

    private final RecordingListener listener = new RecordingListener();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        AiToolRegistry.getOrCreate(camelContext).addListener(listener);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:getWeather?tags=weather&description=Get the weather")
                        .routeId("weather-route")
                        .setBody(constant("sunny"));
            }
        };
    }

    @Test
    void testEventsOnRouteStartAndStop() throws Exception {
        assertThat(listener.events)
                .as("Route start should fire toolRegistered")
                .extracting(Event::type, Event::tag, Event::toolName)
                .containsExactly(tuple("registered", "weather", "getWeather"));

        context.getRouteController().stopRoute("weather-route");

        assertThat(listener.events)
                .extracting(Event::type, Event::tag, Event::toolName)
                .containsExactly(
                        tuple("registered", "weather", "getWeather"),
                        tuple("deregistered", "weather", "getWeather"));
    }

    @Test
    void testEventsOnSuspendAndResume() throws Exception {
        context.getRouteController().suspendRoute("weather-route");
        context.getRouteController().resumeRoute("weather-route");

        assertThat(listener.events)
                .extracting(Event::type, Event::tag, Event::toolName)
                .containsExactly(
                        tuple("registered", "weather", "getWeather"),
                        tuple("deregistered", "weather", "getWeather"),
                        tuple("registered", "weather", "getWeather"));
    }

    @Test
    void testMultiTagEndpointFiresOneEventPerTag() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:sendEmail?tags=notify,crm&description=Send an email")
                        .routeId("email-route")
                        .setBody(constant("sent"));
            }
        });

        assertThat(listener.events)
                .filteredOn(e -> "sendEmail".equals(e.toolName()))
                .extracting(Event::type, Event::tag)
                .containsExactlyInAnyOrder(
                        tuple("registered", "notify"),
                        tuple("registered", "crm"));
    }

    @Test
    void testUntaggedEndpointFiresDefaultPoolEvent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:lookupOrder?description=Look up an order")
                        .routeId("order-route")
                        .setBody(constant("order"));
            }
        });

        assertThat(listener.events)
                .filteredOn(e -> "lookupOrder".equals(e.toolName()))
                .extracting(Event::type, Event::tag)
                .containsExactly(tuple("registered", null));

        context.getRouteController().stopRoute("order-route");

        assertThat(listener.events)
                .filteredOn(e -> "lookupOrder".equals(e.toolName()))
                .extracting(Event::type, Event::tag)
                .containsExactly(
                        tuple("registered", null),
                        tuple("deregistered", null));
    }

    private record Event(String type, String tag, String toolName) {
    }

    private static final class RecordingListener implements AiToolRegistryListener {
        private final List<Event> events = new CopyOnWriteArrayList<>();

        @Override
        public void toolRegistered(String tag, AiToolSpec spec) {
            events.add(new Event("registered", tag, spec.getName()));
        }

        @Override
        public void toolDeregistered(String tag, AiToolSpec spec) {
            events.add(new Event("deregistered", tag, spec.getName()));
        }
    }
}
