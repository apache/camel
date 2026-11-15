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
package org.apache.camel.component.controlbus;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RouteController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stop of the route that routes the exchange must not wait for that exchange until the shutdown timeout.
 */
public class ControlBusStopCurrentRouteTest extends ContextTestSupport {

    @BeforeEach
    void shortShutdownTimeout() {
        context.getShutdownStrategy().setTimeout(5);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
    }

    @Test
    public void testStopCurrentRoute() throws Exception {
        getMockEndpoint("mock:current").expectedBodiesReceived("Hello World");

        Exchange out = template.send("direct:current", e -> e.getMessage().setBody("Hello World"));

        assertNull(out.getException());
        MockEndpoint.assertIsSatisfied(context);
        RouteController controller = context.getRouteController();
        await().atMost(5, TimeUnit.SECONDS).until(() -> controller.getRouteStatus("current").isStopped());
    }

    @Test
    public void testSuspendCurrentRoute() throws Exception {
        getMockEndpoint("mock:suspend").expectedBodiesReceived("Hello World");

        Exchange out = template.send("direct:suspend", e -> e.getMessage().setBody("Hello World"));

        assertNull(out.getException());
        MockEndpoint.assertIsSatisfied(context);
        RouteController controller = context.getRouteController();
        await().atMost(5, TimeUnit.SECONDS).until(() -> controller.getRouteStatus("suspend").isSuspended());
    }

    @Test
    public void testStopRouteOfExchangeFromSubRoute() throws Exception {
        getMockEndpoint("mock:sub").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:main").expectedBodiesReceived("Hello World");

        Exchange out = template.send("direct:main", e -> e.getMessage().setBody("Hello World"));

        assertNull(out.getException());
        MockEndpoint.assertIsSatisfied(context);
        RouteController controller = context.getRouteController();
        await().atMost(5, TimeUnit.SECONDS).until(() -> controller.getRouteStatus("main").isStopped());
        assertTrue(controller.getRouteStatus("sub").isStarted());
    }

    @Test
    public void testStopOtherRouteIsDoneWhenTheStepReturns() throws Exception {
        // the route to stop does not route the exchange, so it is stopped on the calling thread
        getMockEndpoint("mock:other").expectedBodiesReceived("Stopped");

        template.sendBody("direct:other", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:current").routeId("current")
                        .to("controlbus:route?routeId=current&action=stop")
                        .to("mock:current");

                from("direct:suspend").routeId("suspend")
                        .to("controlbus:route?routeId=current&action=suspend")
                        .to("mock:suspend");

                from("direct:main").routeId("main")
                        .to("direct:sub")
                        .to("mock:main");

                from("direct:sub").routeId("sub")
                        .to("controlbus:route?routeId=main&action=stop")
                        .to("mock:sub");

                from("direct:other").routeId("other")
                        .to("controlbus:route?routeId=foo&action=stop")
                        .to("controlbus:route?routeId=foo&action=status")
                        .to("mock:other");

                from("direct:foo").routeId("foo")
                        .to("mock:foo");
            }
        };
    }
}
