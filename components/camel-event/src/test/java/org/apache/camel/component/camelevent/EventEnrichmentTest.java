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
package org.apache.camel.component.camelevent;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventEnrichmentTest extends CamelTestSupport {

    @Test
    void testExchangeEventHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exchangeEnrichment");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:enrichmentSource", "Hello");

        mock.assertIsSatisfied();

        // Verify enrichment headers are set
        mock.getExchanges().forEach(e -> {
            assertNotNull(e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_EXCHANGE_ID, String.class),
                    "CamelEventExchangeId should be set");
            assertEquals("enrichmentSourceRoute",
                    e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_ROUTE_ID, String.class),
                    "CamelEventRouteId should be the source route ID");
            assertNotNull(e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_ENDPOINT_URI, String.class),
                    "CamelEventEndpointUri should be set");
            assertTrue(
                    e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_ENDPOINT_URI, String.class)
                            .contains("direct://enrichmentSource"),
                    "CamelEventEndpointUri should contain the from endpoint URI");
        });
    }

    @Test
    void testExchangeSentHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sentEnrichment");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:sentSource", "Hello");

        mock.assertIsSatisfied();

        // Verify ExchangeSent events have the endpoint URI and duration headers
        mock.getExchanges().forEach(e -> {
            assertNotNull(e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_ENDPOINT_URI, String.class),
                    "ExchangeSent should have CamelEventEndpointUri");
            assertNotNull(e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_DURATION, Long.class),
                    "ExchangeSent should have CamelEventDuration");
        });
    }

    @Test
    void testExchangeFailedHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:failedEnrichment");
        mock.expectedMinimumMessageCount(1);

        try {
            template.sendBody("direct:failSource", "Hello");
        } catch (Exception e) {
            // Expected
        }

        mock.assertIsSatisfied();

        boolean foundFailedEvent = mock.getExchanges().stream()
                .anyMatch(e -> {
                    String eventType = e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_TYPE, String.class);
                    if ("ExchangeFailed".equals(eventType)) {
                        String exceptionMsg = e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_EXCEPTION, String.class);
                        assertNotNull(exceptionMsg, "CamelEventException should be set for failed exchanges");
                        assertEquals("Forced failure", exceptionMsg);
                        return true;
                    }
                    return false;
                });
        assertTrue(foundFailedEvent, "Should have received an ExchangeFailed event");
    }

    @Test
    void testRouteEventHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:routeEnrichment");
        mock.expectedMinimumMessageCount(2);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:enrichDynamic").routeId("enrichDynamic").to("mock:enrichDynamic");
            }
        });
        context.getRouteController().stopRoute("enrichDynamic");

        mock.assertIsSatisfied();

        // Route events should have CamelEventRouteId
        mock.getExchanges().forEach(e -> {
            assertNotNull(e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_ROUTE_ID, String.class),
                    "Route events should have CamelEventRouteId");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:ExchangeCompleted")
                        .routeId("exchangeEnrichmentConsumer")
                        .to("mock:exchangeEnrichment");

                from("event:ExchangeSent")
                        .routeId("sentEnrichmentConsumer")
                        .to("mock:sentEnrichment");

                from("event:ExchangeFailed")
                        .routeId("failedEnrichmentConsumer")
                        .to("mock:failedEnrichment");

                from("event:RouteStarted,RouteStopped")
                        .routeId("routeEnrichmentConsumer")
                        .to("mock:routeEnrichment");

                from("direct:enrichmentSource").routeId("enrichmentSourceRoute")
                        .log("Processing enrichment source");

                from("direct:sentSource").routeId("sentSourceRoute")
                        .to("mock:sentTarget");

                from("direct:failSource").routeId("failSourceRoute")
                        .throwException(new RuntimeException("Forced failure"));
            }
        };
    }
}
