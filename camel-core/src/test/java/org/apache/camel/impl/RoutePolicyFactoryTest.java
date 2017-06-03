/**
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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.RoutePolicySupport;

public class RoutePolicyFactoryTest extends ContextTestSupport {

    public void testRoutePolicyFactory() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedHeaderReceived("RoutePolicy", "foo-route");
        getMockEndpoint("mock:bar").expectedHeaderReceived("RoutePolicy", "bar-route");

        template.sendBody("direct:foo", "Hello Foo");
        template.sendBody("direct:bar", "Hello Bar");

        assertMockEndpointsSatisfied();
    }

    public static final class MyRoutePolicyFactory implements RoutePolicyFactory {

        public MyRoutePolicyFactory() {
        }

        @Override
        public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, RouteDefinition route) {
            return new MyRoutePolicy(routeId);
        }
    }

    private static final class MyRoutePolicy extends RoutePolicySupport {

        private final String routeId;

        private MyRoutePolicy(String routeId) {
            this.routeId = routeId;
        }

        public String getRouteId() {
            return routeId;
        }

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            exchange.getIn().setHeader("RoutePolicy", routeId);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addRoutePolicyFactory(new MyRoutePolicyFactory());

                from("direct:foo").routeId("foo-route")
                    .to("mock:foo");

                from("direct:bar").routeId("bar-route")
                    .to("mock:bar");
            }
        };
    }
}

