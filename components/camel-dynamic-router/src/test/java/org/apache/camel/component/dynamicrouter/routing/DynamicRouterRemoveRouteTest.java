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
package org.apache.camel.component.dynamicrouter.routing;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The channel of a Dynamic Router endpoint can be used again after the endpoint was stopped, such as when the route
 * that used it is removed.
 */
public class DynamicRouterRemoveRouteTest {

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    static volatile CountDownLatch reached = new CountDownLatch(1);

    static volatile CountDownLatch release = new CountDownLatch(1);

    CamelContext context;

    ProducerTemplate template;

    MockEndpoint mock;

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:subscribe")
                        .routeId("subscribe")
                        .to("dynamic-router-control:subscribe?subscribeChannel=orders&subscriptionId=all"
                            + "&destinationUri=mock:result&priority=1&predicate=true")
                        .to("dynamic-router-control:subscribe?subscribeChannel=sync&subscriptionId=all"
                            + "&destinationUri=mock:result&priority=1&predicate=true")
                        .to("dynamic-router-control:subscribe?subscribeChannel=par&subscriptionId=all"
                            + "&destinationUri=direct:slow&priority=1&predicate=true");
                from("direct:slow")
                        .routeId("slow")
                        .process(e -> {
                            reached.countDown();
                            release.await(10, TimeUnit.SECONDS);
                        })
                        .to("mock:slow");
                from("direct:dynamic")
                        .routeId("dynamic")
                        .toD("dynamic-router:${header.channel}");
            }
        });
    }

    @BeforeEach
    void setUp() throws Exception {
        context = contextExtension.getContext();
        template = contextExtension.getProducerTemplate();
        mock = contextExtension.getMockEndpoint("mock:result");
        mock.reset();
        for (String id : new String[] { "router", "syncRouter", "parallelRouter" }) {
            if (context.getRoute(id) != null) {
                context.getRouteController().stopRoute(id);
                context.removeRoute(id);
            }
        }
        template.sendBody("direct:subscribe", "");
        context.addRoutes(routerRoutes());
    }

    private static RoutesBuilder routerRoutes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("router").to("dynamic-router:orders");
                from("direct:sync").routeId("syncRouter").to("dynamic-router:sync?synchronous=true");
                from("direct:parallel").routeId("parallelRouter").to("dynamic-router:par?parallelProcessing=true");
            }
        };
    }

    private DynamicRouterComponent component() {
        return context.getComponent("dynamic-router", DynamicRouterComponent.class);
    }

    @Test
    void addRouteAgainAfterRemove() throws Exception {
        mock.expectedBodiesReceived("before", "after");

        template.sendBody("direct:start", "before");
        DynamicRouterProcessor processor = component().getRoutingProcessor("orders");

        context.getRouteController().stopRoute("router");
        context.removeRoute("router");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("router").to("dynamic-router:orders");
            }
        });
        template.sendBody("direct:start", "after");

        mock.assertIsSatisfied();
        // the endpoint of the route that is added again uses the processor of the channel again
        assertSame(processor, component().getRoutingProcessor("orders"));
    }

    @Test
    void addRouteAgainWithOtherOptions() throws Exception {
        mock.expectedBodiesReceived("before", "after");

        template.sendBody("direct:start", "before");
        DynamicRouterProcessor processor = component().getRoutingProcessor("orders");

        context.getRouteController().stopRoute("router");
        context.removeRoute("router");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("router").to("dynamic-router:orders?recipientMode=allMatch");
            }
        });
        template.sendBody("direct:start", "after");

        mock.assertIsSatisfied();
        // the endpoint with other options gets a new processor, and the processor of the removed endpoint is stopped
        assertNotSame(processor, component().getRoutingProcessor("orders"));
        assertTrue(processor.getRecipientList().isStopped());
    }

    @Test
    void addRouteAgainWithOtherOptionsKeepsThePoolOfTheReplacedProcessor() throws Exception {
        DynamicRouterProcessor processor = component().getRoutingProcessor("par");

        context.getRouteController().stopRoute("parallelRouter");
        context.removeRoute("parallelRouter");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:parallel").routeId("parallelRouter")
                        .to("dynamic-router:par?parallelProcessing=true&recipientMode=allMatch");
            }
        });

        // the recipient list of the replaced processor is stopped, but its thread pool is not shut down, as another
        // route could still have exchanges in flight in it
        assertNotSame(processor, component().getRoutingProcessor("par"));
        assertTrue(processor.getRecipientList().isStopped());
        assertFalse(processor.getRecipientList().getExecutorService().isShutdown());
    }

    @Test
    void toDAfterRemovedRoute() throws Exception {
        mock.expectedBodiesReceived("before", "after");

        // the toD uses the endpoint of the route
        template.sendBodyAndHeader("direct:dynamic", "before", "channel", "orders");
        DynamicRouterProcessor processor = component().getRoutingProcessor("orders");

        context.getRouteController().stopRoute("router");
        context.removeRoute("router");

        // the toD creates the endpoint of the channel again
        template.sendBodyAndHeader("direct:dynamic", "after", "channel", "orders");

        mock.assertIsSatisfied();
        assertSame(processor, component().getRoutingProcessor("orders"));
    }

    @Test
    void stopAndStartEndpoint() throws Exception {
        mock.expectedBodiesReceived("after");
        Endpoint endpoint = context.getEndpoint("dynamic-router:orders");
        DynamicRouterProcessor processor = component().getRoutingProcessor("orders");
        assertNotNull(processor);

        // a stopped endpoint is initialized again when it starts, and uses the same processor
        endpoint.stop();
        endpoint.start();
        assertSame(processor, component().getRoutingProcessor("orders"));

        template.sendBody("direct:start", "after");
        mock.assertIsSatisfied();
    }

    @Test
    void secondEndpointForChannelKeepsTheProcessor() throws Exception {
        mock.expectedBodiesReceived("after");
        DynamicRouterProcessor processor = component().getRoutingProcessor("orders");

        // another endpoint for the channel fails as before while the endpoint of the channel is in use
        assertThrows(Exception.class, () -> context.getEndpoint("dynamic-router:orders?recipientMode=allMatch"));

        assertSame(processor, component().getRoutingProcessor("orders"));
        assertFalse(processor.getRecipientList().isStopped());
        template.sendBody("direct:start", "after");
        mock.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "orders", "sync" })
    void sendThroughRemovedEndpoint(String channel) throws Exception {
        mock.expectedBodiesReceived("routed");
        // a route that still sends to an endpoint that was removed (such as an endpoint that a toD producer cache
        // evicted while it was used) still routes with the processor of the channel
        context.removeEndpoint(context.getEndpoint(channel.equals("sync")
                ? "dynamic-router:sync?synchronous=true" : "dynamic-router:orders"));

        Exchange out = template.send(channel.equals("sync") ? "direct:sync" : "direct:start",
                e -> e.getMessage().setBody("routed"));

        assertNull(out.getException());
        mock.assertIsSatisfied();
    }

    @Test
    void exchangeInFlightCompletesWhenEndpointIsRemoved() throws Exception {
        reached = new CountDownLatch(1);
        release = new CountDownLatch(1);
        MockEndpoint slow = contextExtension.getMockEndpoint("mock:slow");
        slow.reset();
        slow.expectedBodiesReceived("in flight");

        CompletableFuture<Exchange> future
                = template.asyncSend("direct:parallel", e -> e.getMessage().setBody("in flight"));
        try {
            assertTrue(reached.await(10, TimeUnit.SECONDS));
            context.removeEndpoint(context.getEndpoint("dynamic-router:par?parallelProcessing=true"));
        } finally {
            release.countDown();
        }

        Exchange out = future.get(10, TimeUnit.SECONDS);
        assertNull(out.getException());
        slow.assertIsSatisfied();
    }
}
