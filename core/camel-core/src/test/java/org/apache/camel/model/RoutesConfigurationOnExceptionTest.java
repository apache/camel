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

import static org.junit.jupiter.api.Assertions.assertThrows;

public class RoutesConfigurationOnExceptionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testGlobal() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                // global routes configuration
                routeConfiguration()
                        .onException(IllegalArgumentException.class).handled(true).to("mock:error")
                        .onException(Exception.class).handled(true).to("mock:error2");

            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .throwException(new IllegalArgumentException("Foo"));

                from("direct:start2")
                        .throwException(new Exception("Foo2"));
            }
        });
        context.start();

        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:error2").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLocalOverride() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                // global routes configuration
                routeConfiguration()
                        .onException(IllegalArgumentException.class).handled(true).to("mock:error")
                        .onException(Exception.class).handled(true).to("mock:error2");

            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .throwException(new IllegalArgumentException("Foo"));

                from("direct:start2")
                        .onException(Exception.class).handled(true).to("mock:error3").end()
                        .throwException(new Exception("Foo2"));
            }
        });
        context.start();

        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:error2").expectedMessageCount(0);
        getMockEndpoint("mock:error3").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLocalConfiguration() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("mylocal")
                        .onException(IllegalArgumentException.class).handled(true).to("mock:error")
                        .onException(Exception.class).handled(true).to("mock:error2");

            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .throwException(new IllegalArgumentException("Foo"));

                from("direct:start2").routeConfigurationId("mylocal")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        });
        context.start();

        getMockEndpoint("mock:error").expectedBodiesReceived("Bye World");

        assertThrows(Exception.class, () -> template.sendBody("direct:start", "Hello World"),
                "Should throw exception");

        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testGlobalAndLocal() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                // global routes configuration
                routeConfiguration()
                        .onException(IllegalArgumentException.class).handled(true).to("mock:error")
                        .onException(Exception.class).handled(true).to("mock:error2");

                routeConfiguration("mylocal")
                        .onException(IllegalArgumentException.class).handled(true).to("mock:error3")
                        .onException(Exception.class).handled(true).to("mock:error4");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .throwException(new IllegalArgumentException("Foo"));

                from("direct:start2").routeConfigurationId("mylocal")
                        .throwException(new IllegalArgumentException("Foo2"));
            }
        });
        context.start();

        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:error2").expectedMessageCount(0);
        getMockEndpoint("mock:error3").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:error4").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

}
