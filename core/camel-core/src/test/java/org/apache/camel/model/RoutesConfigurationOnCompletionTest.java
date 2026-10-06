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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoutesConfigurationOnCompletionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testGlobal() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration().onCompletion().to("mock:global");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
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
            public void configuration() {
                routeConfiguration("mylocal").onCompletion().to("mock:local");

            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
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
            public void configuration() {
                routeConfiguration().onCompletion().to("mock:global");
                routeConfiguration("mylocal").onCompletion().to("mock:local");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
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

    @Test
    public void testLocalConfigurationWithIndirectConsumer() throws Exception {
        // CAMEL-25135: onCompletion from a named routeConfiguration must fire even when
        // the opted-in route is not itself the consumer route (e.g. REST DSL → direct:).
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("myconfig").onCompletion().to("mock:completion");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // consumer route — does NOT carry the routeConfigurationId
                from("direct:consumer")
                        .to("direct:processor");

                // processing route — opts in to the named configuration
                from("direct:processor").routeConfigurationId("myconfig")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:completion").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:consumer", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLocalConfigurationBothRoutesOptIn() throws Exception {
        // CAMEL-25135: when both the consumer route and the called direct: route
        // opt in to the same named configuration, onCompletion must fire only once
        // per exchange, not once per opted-in route.
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("myconfig").onCompletion().to("mock:completion");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // both routes opt in to the same named configuration
                from("direct:consumer").routeConfigurationId("myconfig")
                        .to("direct:processor");

                from("direct:processor").routeConfigurationId("myconfig")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:completion").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:consumer", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLocalConfigurationSubRouteCalledTwice() throws Exception {
        // CAMEL-25135: when the opted-in processor route is called twice in the same
        // exchange, onCompletion must fire only once per exchange, not once per visit.
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("myconfig").onCompletion().to("mock:completion");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:consumer")
                        .to("direct:processor")
                        .to("direct:processor"); // called twice

                from("direct:processor").routeConfigurationId("myconfig")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:completion").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(2); // called twice

        template.sendBody("direct:consumer", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLocalConfigurationBeforeConsumerWithIndirectConsumer() throws Exception {
        // CAMEL-25135: BeforeConsumer mode with a named routeConfiguration must fire
        // when the opted-in route is not itself the consumer route.
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("myconfig").onCompletion().modeBeforeConsumer()
                        .setHeader("done", constant("yes"));
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // consumer route — does NOT carry the routeConfigurationId
                from("direct:consumer")
                        .to("direct:processor");

                // processing route — opts in to the named configuration
                from("direct:processor").routeConfigurationId("myconfig")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Exchange result = template.request("direct:consumer",
                e -> e.getMessage().setBody("Hello World"));

        assertMockEndpointsSatisfied();
        // BeforeConsumer onCompletion runs before the consumer sends the reply,
        // so the header should be visible on the reply exchange
        assertEquals("yes", result.getMessage().getHeader("done"));
    }

    @Test
    public void testLocalConfigurationBeforeConsumerBothRoutesOptIn() throws Exception {
        // CAMEL-25135: BeforeConsumer + both routes opt in → fire only once.
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("myconfig").onCompletion().modeBeforeConsumer()
                        .setHeader("done", constant("yes"));
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:consumer").routeConfigurationId("myconfig")
                        .to("direct:processor");

                from("direct:processor").routeConfigurationId("myconfig")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Exchange result = template.request("direct:consumer",
                e -> e.getMessage().setBody("Hello World"));

        assertMockEndpointsSatisfied();
        assertEquals("yes", result.getMessage().getHeader("done"));
    }

}
