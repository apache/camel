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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    public void testGlobalStarConfiguration() throws Exception {
        // routeConfiguration("*") is treated as global: fires once per exchange for all routes
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("*").onCompletion().to("mock:global");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("mock:result");

                from("direct:start2")
                        .to("mock:result2");

                // multi-route exchange: start3 calls sub; must fire mock:global exactly once
                from("direct:start3")
                        .to("direct:sub");

                from("direct:sub")
                        .to("mock:sub");
            }
        });
        context.start();

        getMockEndpoint("mock:global").expectedMessageCount(3); // one per exchange, not per route
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result2").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:sub").expectedBodiesReceived("Two-Hop World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");
        template.sendBody("direct:start3", "Two-Hop World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnCompleteOnlyAndOnFailureOnlyInSameNamedConfig() throws Exception {
        // One named config with onCompleteOnly + onFailureOnly: they must not dedup-block each other.
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                RouteConfigurationDefinition rc = routeConfiguration("myconfig");
                rc.onCompletion().onCompleteOnly().to("mock:complete");
                rc.onCompletion().onFailureOnly().to("mock:failure");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeConfigurationId("myconfig")
                        .to("mock:result");

                from("direct:fail").routeConfigurationId("myconfig")
                        .throwException(new IllegalArgumentException("Boom"));
            }
        });
        context.start();

        // success case
        getMockEndpoint("mock:complete").expectedMessageCount(1);
        getMockEndpoint("mock:failure").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        // failure case
        resetMocks();
        getMockEndpoint("mock:complete").expectedMessageCount(0);
        getMockEndpoint("mock:failure").expectedMessageCount(1);

        assertThrows(Exception.class, () -> template.sendBody("direct:fail", "Boom"));
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLocalConfigurationBeforeConsumerFiringCount() throws Exception {
        // BeforeConsumer mode, both routes opt in. onCompletion should fire only once
        // and the consumer route should own the firing (so onCompletion sees state set
        // by the consumer after the direct:processor call returns).
        AtomicInteger counter = new AtomicInteger();
        // Capture what the onCompletion processor actually sees for "fromConsumer";
        // the consumer sets this header AFTER calling direct:processor, so if the
        // onCompletion fires early (in the sub-route) it will see null, not "yes".
        AtomicReference<String> seen = new AtomicReference<>();
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("myconfig").onCompletion().modeBeforeConsumer()
                        .process(e -> {
                            counter.incrementAndGet();
                            seen.set(e.getMessage().getHeader("fromConsumer", String.class));
                        })
                        .setHeader("done", constant("yes"));
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // consumer sets a header AFTER the sub-route call;
                // since BeforeConsumer defers to the consumer route, it fires last
                // and sees this header on the exchange (validated via the reply).
                from("direct:consumer").routeConfigurationId("myconfig")
                        .to("direct:processor")
                        .setHeader("fromConsumer", constant("yes"));

                from("direct:processor").routeConfigurationId("myconfig")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Exchange result = template.request("direct:consumer",
                e -> e.getMessage().setBody("Hello World"));

        assertMockEndpointsSatisfied();
        // onCompletion must fire exactly once (not once per opted-in route)
        assertEquals(1, counter.get(), "onCompletion should fire exactly once");
        // deferral: onCompletion was deferred to the consumer route, so it ran after
        // the consumer set "fromConsumer" — the processor must have seen "yes"
        assertEquals("yes", seen.get(),
                "BeforeConsumer onCompletion should run after the consumer route sets fromConsumer");
        // onCompletion itself sets the 'done' header; it should be visible on the reply
        assertEquals("yes", result.getMessage().getHeader("done"));
    }

}
