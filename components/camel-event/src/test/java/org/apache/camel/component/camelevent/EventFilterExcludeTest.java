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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class EventFilterExcludeTest extends CamelTestSupport {

    @Test
    void testFilterExcludeByRouteId() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:excludeResult");
        mock.expectedMinimumMessageCount(1);

        // Send to the included route
        template.sendBody("direct:includeMe", "Hello Include");

        // Send to the excluded route
        template.sendBody("direct:excludeMe", "Hello Exclude");

        mock.assertIsSatisfied();

        // All received events should NOT be from excludedRoute
        mock.getExchanges().forEach(e -> {
            CamelEvent event = e.getIn().getBody(CamelEvent.class);
            if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
                assertNotEquals("excludedRoute", exchangeEvent.getExchange().getFromRouteId(),
                        "Should not receive events from excludedRoute");
            }
        });
    }

    @Test
    void testFilterExcludeWithInclusion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:combinedFilterResult");
        mock.expectedMessageCount(1);

        // Send to each route
        template.sendBody("direct:includeA", "Hello A");
        template.sendBody("direct:includeB", "Hello B");
        template.sendBody("direct:excludeC", "Hello C");

        mock.assertIsSatisfied();

        // Only includeRouteA should come through (includeRouteB is excluded, excludeRouteC not included)
        mock.getExchanges().forEach(e -> {
            CamelEvent event = e.getIn().getBody(CamelEvent.class);
            if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
                assertEquals("includeRouteA", exchangeEvent.getExchange().getFromRouteId());
            }
        });
    }

    @Test
    void testFilterExcludeRouteEvents() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:routeExclude");
        mock.expectedMinimumMessageCount(1);

        // Add and stop routes
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:keepDynamic").routeId("keepDynamic").to("mock:keepDynamic");
                from("direct:dropDynamic").routeId("dropDynamic").to("mock:dropDynamic");
            }
        });
        context.getRouteController().stopRoute("keepDynamic");
        context.getRouteController().stopRoute("dropDynamic");

        mock.assertIsSatisfied();

        // No events should be from dropDynamic
        mock.getExchanges().forEach(e -> {
            assertNotEquals("dropDynamic",
                    e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_ROUTE_ID, String.class),
                    "Should not receive events from dropDynamic");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:ExchangeCompleted?exclude=excludedRoute")
                        .routeId("excludeConsumer")
                        .to("mock:excludeResult");

                from("event:ExchangeCompleted?include=includeRouteA,includeRouteB&exclude=includeRouteB")
                        .routeId("combinedFilterConsumer")
                        .to("mock:combinedFilterResult");

                from("event:RouteStarted,RouteStopped?exclude=dropDynamic")
                        .routeId("routeExcludeConsumer")
                        .to("mock:routeExclude");

                from("direct:includeMe").routeId("includedRoute")
                        .log("Processing include");

                from("direct:excludeMe").routeId("excludedRoute")
                        .log("Processing exclude");

                from("direct:includeA").routeId("includeRouteA")
                        .log("Processing A");

                from("direct:includeB").routeId("includeRouteB")
                        .log("Processing B");

                from("direct:excludeC").routeId("excludeRouteC")
                        .log("Processing C");
            }
        };
    }
}
