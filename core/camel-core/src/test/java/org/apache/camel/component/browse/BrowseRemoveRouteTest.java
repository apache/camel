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
package org.apache.camel.component.browse;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BrowsableEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The route that sends to a browse endpoint keeps working when the route that consumes from it is stopped or removed,
 * and a stopped consumer gets no message. The browsed exchanges are kept until CamelContext is stopping.
 */
class BrowseRemoveRouteTest extends ContextTestSupport {

    @Test
    void removeConsumerRoute() throws Exception {
        MockEndpoint consumed = getMockEndpoint("mock:consumed");
        consumed.expectedBodiesReceived("a");
        template.sendBody("direct:start", "a");
        assertMockEndpointsSatisfied();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedBodiesReceived("b");
        template.sendBody("direct:start", "b");
        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = context.getEndpoint("browse:orders", BrowsableEndpoint.class).getExchanges();
        assertEquals("b", exchanges.get(exchanges.size() - 1).getMessage().getBody());
    }

    @Test
    void removeAndAddConsumerRouteKeepsTheBrowsedExchanges() throws Exception {
        template.sendBody("direct:start", "a");
        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");
        template.sendBody("direct:start", "b");
        assertEquals(List.of("a", "b"), browsedBodies());

        // such as a reload in dev mode
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("browse:orders").routeId("consumer").to("mock:consumed");
            }
        });
        MockEndpoint consumed = getMockEndpoint("mock:consumed");
        consumed.reset();
        consumed.expectedBodiesReceived("c");
        template.sendBody("direct:start", "c");
        consumed.assertIsSatisfied();
        assertEquals(List.of("a", "b", "c"), browsedBodies());
    }

    @Test
    void stoppingCamelContextClearsTheBrowsedExchanges() {
        template.sendBody("direct:start", "a");
        BrowsableEndpoint endpoint = context.getEndpoint("browse:orders", BrowsableEndpoint.class);
        assertEquals(1, endpoint.getExchanges().size());

        context.stop();

        assertEquals(0, endpoint.getExchanges().size());
    }

    @Test
    void stoppedConsumerGetsNoMessage() throws Exception {
        context.getRouteController().stopRoute("consumer");

        MockEndpoint consumed = getMockEndpoint("mock:consumed");
        consumed.expectedMessageCount(0);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("a");
        template.sendBody("direct:start", "a");
        assertMockEndpointsSatisfied();

        context.getRouteController().startRoute("consumer");
        consumed.reset();
        consumed.expectedBodiesReceived("b");
        template.sendBody("direct:start", "b");
        consumed.assertIsSatisfied();
    }

    private List<Object> browsedBodies() {
        return context.getEndpoint("browse:orders", BrowsableEndpoint.class).getExchanges().stream()
                .map(e -> e.getMessage().getBody())
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("browse:orders").to("mock:result");

                from("browse:orders").routeId("consumer").to("mock:consumed");
            }
        };
    }
}
