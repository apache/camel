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
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventFilterTest extends CamelTestSupport {

    @Test
    void testFilterMultipleRouteIds() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:multiFilter");
        mock.expectedMinimumMessageCount(2);

        // Send to both filtered routes
        template.sendBody("direct:filterA", "Hello A");
        template.sendBody("direct:filterB", "Hello B");

        // Send to a route that should NOT be captured
        template.sendBody("direct:filterC", "Hello C");

        mock.assertIsSatisfied();

        // All received events should be from filterRouteA or filterRouteB
        mock.getExchanges().forEach(e -> {
            CamelEvent event = e.getIn().getBody(CamelEvent.class);
            if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
                String routeId = exchangeEvent.getExchange().getFromRouteId();
                assertEquals(true,
                        "filterRouteA".equals(routeId) || "filterRouteB".equals(routeId),
                        "Expected filterRouteA or filterRouteB but got: " + routeId);
            }
        });
    }

    @Test
    void testFilterExcludesUnmatched() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:singleFilter");
        mock.expectedMessageCount(1);

        // Send to the filtered route
        template.sendBody("direct:filterA", "Hello A");

        // Send to an unmatched route
        template.sendBody("direct:filterC", "Hello C");

        mock.assertIsSatisfied();

        // Only filterRouteA events should be received
        mock.getExchanges().forEach(e -> {
            CamelEvent event = e.getIn().getBody(CamelEvent.class);
            if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
                assertEquals("filterRouteA", exchangeEvent.getExchange().getFromRouteId());
            }
        });
    }

    @Test
    void testFilterRouteEvents() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:routeFilter");
        mock.expectedMinimumMessageCount(1);

        // Add and stop a route matching the filter
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:filteredDynamic").routeId("filteredDynamic").to("mock:filteredDynamic");
            }
        });
        context.getRouteController().stopRoute("filteredDynamic");

        mock.assertIsSatisfied();

        // All route events should be for filteredDynamic
        mock.getExchanges().forEach(e -> {
            assertEquals("filteredDynamic", e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_ROUTE_ID, String.class));
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:ExchangeCompleted?include=filterRouteA,filterRouteB")
                        .routeId("multiFilterConsumer")
                        .to("mock:multiFilter");

                from("event:ExchangeCompleted?include=filterRouteA")
                        .routeId("singleFilterConsumer")
                        .to("mock:singleFilter");

                from("event:RouteStarted,RouteStopped?include=filteredDynamic")
                        .routeId("routeFilterConsumer")
                        .to("mock:routeFilter");

                from("direct:filterA").routeId("filterRouteA")
                        .log("Processing filter A");

                from("direct:filterB").routeId("filterRouteB")
                        .log("Processing filter B");

                from("direct:filterC").routeId("filterRouteC")
                        .log("Processing filter C");
            }
        };
    }
}
