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
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.support.OrderedComparator;
import org.junit.jupiter.api.Test;

public class RoutesConfigurationBuilderIdOrPatternTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRoutesConfigurationOnException() throws Exception {
        List<RoutesBuilder> routes = new ArrayList<>();

        //        routes.add(new RouteBuilder() {
        //            @Override
        //            public void configure() throws Exception {
        //                from("direct:start")
        //                        .throwException(new IllegalArgumentException("Foo"));
        //            }
        //        });
        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start2")
                        .routeConfiguration("handle*")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        });
        routes.add(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                // named routes configuration
                routeConfiguration("handleError").onException(Exception.class).handled(true).to("mock:error");
            }
        });
        context.start();

        // sort routes according to ordered
        routes.sort(OrderedComparator.get());

        // first add the routes configurations as they are globally for all routes
        for (RoutesBuilder builder : routes) {
            if (builder instanceof RouteConfigurationsBuilder) {
                RouteConfigurationsBuilder rcb = (RouteConfigurationsBuilder) builder;
                context.addRoutesConfigurations(rcb);
            }
        }
        // then add the routes
        for (RoutesBuilder builder : routes) {
            context.addRoutes(builder);
        }

        getMockEndpoint("mock:error").expectedBodiesReceived("Bye World");

        //        try {
        //            template.sendBody("direct:start", "Hello World");
        //            fail("Should throw exception");
        //        } catch (Exception e) {
        // expected
        //        }
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

}
