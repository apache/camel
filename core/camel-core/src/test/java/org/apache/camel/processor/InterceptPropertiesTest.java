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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class InterceptPropertiesTest extends ContextTestSupport {

    @Test
    public void testInterceptProperties() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                intercept().to("mock:intercept");

                from("direct:start")
                        .routeId("intercept-test")
                        .process(exchange -> {})
                        .setBody(constant("Test"))
                        .to("log:body");
            }
        });

        getMockEndpoint("mock:intercept").expectedMessageCount(3);
        getMockEndpoint("mock:intercept")
                .expectedPropertyReceived(ExchangePropertyKey.INTERCEPTED_ROUTE_ID.getName(), "intercept-test");
        getMockEndpoint("mock:intercept")
                .expectedPropertyReceived(
                        ExchangePropertyKey.INTERCEPTED_ROUTE_ENDPOINT_URI.getName(), "direct://start");
        // Node IDs are not always the same
        //        getMockEndpoint("mock:intercept")
        //                .expectedPropertyValuesReceivedInAnyOrder(ExchangePropertyKey.INTERCEPTED_NODE_ID.getName(),
        // "to2", "process1",
        //                        "setBody1");

        template.sendBody("direct:start", "");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptFromProperties() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptFrom("direct:startInterceptFrom").to("mock:interceptFrom");

                from("direct:startInterceptFrom")
                        .routeId("intercept-from-test")
                        .setBody(constant("Test"))
                        .to("log:test");
            }
        });
        getMockEndpoint("mock:interceptFrom").expectedMessageCount(1);
        getMockEndpoint("mock:interceptFrom")
                .expectedPropertyReceived(ExchangePropertyKey.INTERCEPTED_ROUTE_ID.getName(), "intercept-from-test");
        getMockEndpoint("mock:interceptFrom")
                .expectedPropertyReceived(
                        ExchangePropertyKey.INTERCEPTED_ROUTE_ENDPOINT_URI.getName(), "direct://startInterceptFrom");

        template.sendBody("direct:startInterceptFrom", "");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptSendToEndpointProperties() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("log:body").to("mock:interceptSendToEndpoint");

                from("direct:start")
                        .routeId("intercept-test")
                        .process(exchange -> {})
                        .setBody(constant("Test"))
                        .to("log:body");
            }
        });
        getMockEndpoint("mock:interceptSendToEndpoint").expectedMessageCount(1);
        getMockEndpoint("mock:interceptSendToEndpoint")
                .expectedPropertyReceived(ExchangePropertyKey.INTERCEPTED_ROUTE_ID.getName(), "intercept-test");
        getMockEndpoint("mock:interceptSendToEndpoint")
                .expectedPropertyReceived(
                        ExchangePropertyKey.INTERCEPTED_ROUTE_ENDPOINT_URI.getName(), "direct://start");

        template.sendBody("direct:start", "");

        assertMockEndpointsSatisfied();
    }
}
