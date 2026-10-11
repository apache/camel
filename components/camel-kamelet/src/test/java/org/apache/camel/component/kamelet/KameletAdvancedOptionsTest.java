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

package org.apache.camel.component.kamelet;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KameletAdvancedOptionsTest extends CamelTestSupport {

    @Test
    void advancedSourceOptionsAreApplied() {
        RouteDefinition route = getRoute("sourceTrigger");
        assertNotNull(route, "Expected the source caller route to be created");

        String inputUri = findExpandedInputUri("direct://source?");
        assertTrue(inputUri.contains("block=true"),
                () -> "Expected block=true on the expanded source endpoint, but was: " + inputUri);
    }

    @Test
    void advancedSourceOptionsOverrideExistingUriParameters() {
        RouteDefinition route = getRoute("sourceOverrideTrigger");
        assertNotNull(route, "Expected the source override caller route to be created");

        String inputUri = findExpandedInputUri("direct://source-with-options?");
        assertTrue(inputUri.contains("block=true"),
                () -> "Expected block=true to override block=false: " + inputUri);
        assertFalse(inputUri.contains("block=false"),
                () -> "The old block=false value must not remain: " + inputUri);
    }

    @Test
    void advancedSinkOptionsAreAppliedAndMessageIsDelivered() throws Exception {
        MockEndpoint sink = getMockEndpoint("mock:advanced-sink-result");
        sink.expectedBodiesReceived("hello");

        template.sendBody("direct:sink-trigger", "hello");

        sink.assertIsSatisfied();
        assertEquals(1, sink.getRetainFirst(),
                "Expected retain-first=1 on the configured sink endpoint");
        assertEquals(5, sink.getRetainLast(),
                "Expected retain-last=5 to be preserved on the configured sink endpoint");
    }

    @Test
    void noAdvancedOptionsRetainsExistingBehavior() throws Exception {
        MockEndpoint sink = getMockEndpoint("mock:basic-sink-result");
        sink.expectedBodiesReceived("hello");

        template.sendBody("direct:basic-trigger", "hello");

        sink.assertIsSatisfied();
        assertEquals(-1, sink.getRetainFirst(),
                "The basic sink should retain the default retainFirst value");
        assertEquals(-1, sink.getRetainLast(),
                "The basic sink should retain the default retainLast value");
    }

    @Test
    void sourceKameletRejectsSinkOptions() throws Exception {
        try (CamelContext testContext = new DefaultCamelContext()) {
            testContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("source-with-invalid-options")
                            .from("direct:source")
                            .to("kamelet:sink");

                    from("direct:trigger")
                            .to("kamelet:source-with-invalid-options/test?sink.block=true");
                }
            });

            Exception exception = assertThrows(Exception.class, testContext::start);

            assertTrue(hasIllegalArgumentException(exception),
                    () -> "Expected an IllegalArgumentException for sink.* on a source Kamelet, but got: "
                          + exception);
        }
    }

    @Test
    void advancedSinkOptionsAreAppliedToAllRealEndpoints() throws Exception {
        MockEndpoint firstSink = getMockEndpoint("mock:first-advanced-sink");
        MockEndpoint secondSink = getMockEndpoint("mock:second-advanced-sink");

        firstSink.expectedBodiesReceived("hello");
        secondSink.expectedBodiesReceived("hello");

        template.sendBody("direct:multi-sink-trigger", "hello");

        firstSink.assertIsSatisfied();
        secondSink.assertIsSatisfied();

        assertEquals(1, firstSink.getRetainFirst(),
                "Expected retain-first=1 on the first sink endpoint");
        assertEquals(1, secondSink.getRetainFirst(),
                "Expected retain-first=1 on the second sink endpoint");
    }

    private boolean hasIllegalArgumentException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof IllegalArgumentException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private String findExpandedInputUri(String prefix) {
        String uri = context.getRouteDefinitions().stream()
                .filter(route -> Boolean.TRUE.equals(route.isKamelet()))
                .filter(route -> route.getInput() != null)
                .map(route -> route.getInput().getEndpointUri())
                .filter(endpointUri -> endpointUri != null && endpointUri.startsWith(prefix))
                .findFirst()
                .orElse(null);

        assertNotNull(uri, "No expanded Kamelet route input found with prefix " + prefix);
        return uri;
    }

    private RouteDefinition getRoute(String routeId) {
        return context.getRouteDefinitions().stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("direct-source")
                        .from("direct:source?block=false")
                        .to("kamelet:sink");

                routeTemplate("direct-source-with-options")
                        .from("direct:source-with-options?block=false")
                        .to("kamelet:sink");

                from("direct:source-trigger")
                        .routeId("sourceTrigger")
                        .to("kamelet:direct-source/sourceTest?source.block=true");

                from("direct:source-override-trigger")
                        .routeId("sourceOverrideTrigger")
                        .to("kamelet:direct-source-with-options/sourceOverride?source.block=true");

                routeTemplate("mock-sink-with-options")
                        .from("kamelet:source")
                        .to("mock:advanced-sink-result?retain-first=0");

                from("direct:sink-trigger")
                        .routeId("sinkTrigger")
                        .kamelet("mock-sink-with-options?sink.retain-first=1&sink.retain-last=5");

                routeTemplate("mock-sink")
                        .from("kamelet:source")
                        .to("mock:basic-sink-result");

                from("direct:basic-trigger")
                        .routeId("basicTrigger")
                        .kamelet("mock-sink");

                routeTemplate("mock-multi-sink")
                        .from("kamelet:source")
                        .to("mock:first-advanced-sink?retain-first=0")
                        .to("mock:second-advanced-sink?retain-first=0");

                from("direct:multi-sink-trigger")
                        .routeId("multiSinkTrigger")
                        .kamelet("mock-multi-sink?sink.retain-first=1");
            }
        };
    }
}
