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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

public class RoutesConfigurationOnCompletionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testGlobal() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration().onCompletion().to("mock:global");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("mock:result");

                from("direct:start2")
                        .to("mock:result2");
            }
        });
        context.start();

        getMockEndpoint("mock:global").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result2").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLocalConfiguration() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration("mylocal").onCompletion().to("mock:local");

            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("mock:result");

                from("direct:start2").routeConfigurationId("mylocal")
                        .to("mock:result2");
            }
        });
        context.start();

        getMockEndpoint("mock:global").expectedMessageCount(0);
        getMockEndpoint("mock:local").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result2").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testGlobalAndLocal() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration().onCompletion().to("mock:global");
                routeConfiguration("mylocal").onCompletion().to("mock:local");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("mock:result");

                from("direct:start2").routeConfigurationId("mylocal")
                        .to("mock:result2");
            }
        });
        context.start();

        getMockEndpoint("mock:global").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:local").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result2").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

}
