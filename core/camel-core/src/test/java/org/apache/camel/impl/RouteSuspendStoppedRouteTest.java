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
package org.apache.camel.impl;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteError;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Suspending a route that is not started (stopped, or not auto started) must leave it stopped, so that starting or
 * resuming it later starts its consumer, and must keep the last error of the route.
 */
public class RouteSuspendStoppedRouteTest extends ContextTestSupport {

    @Test
    public void testSuspendStoppedRouteThenStart() throws Exception {
        RouteController controller = context.getRouteController();
        controller.stopRoute("foo");
        controller.suspendRoute("foo");
        assertEquals(ServiceStatus.Stopped, controller.getRouteStatus("foo"));

        controller.startRoute("foo");
        assertEquals(ServiceStatus.Started, controller.getRouteStatus("foo"));
        assertConsumes("A");
    }

    @Test
    public void testSuspendStoppedRouteThenResume() throws Exception {
        RouteController controller = context.getRouteController();
        controller.stopRoute("foo");
        controller.suspendRoute("foo");

        // resuming a route that is not suspended starts it
        controller.resumeRoute("foo");
        assertEquals(ServiceStatus.Started, controller.getRouteStatus("foo"));
        assertConsumes("B");
    }

    @Test
    public void testSuspendNotAutoStartedRouteThenResume() throws Exception {
        RouteController controller = context.getRouteController();
        assertEquals(ServiceStatus.Stopped, controller.getRouteStatus("bar"));
        controller.suspendRoute("bar");
        assertEquals(ServiceStatus.Stopped, controller.getRouteStatus("bar"));

        controller.resumeRoute("bar");
        assertEquals(ServiceStatus.Started, controller.getRouteStatus("bar"));

        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedBodiesReceived("C");
        template.sendBody("seda:bar", "C");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSuspendStartedRouteThenResume() throws Exception {
        RouteController controller = context.getRouteController();
        controller.suspendRoute("foo");
        assertEquals(ServiceStatus.Suspended, controller.getRouteStatus("foo"));

        controller.resumeRoute("foo");
        assertEquals(ServiceStatus.Started, controller.getRouteStatus("foo"));
        assertConsumes("D");
    }

    @Test
    public void testSuspendRouteThatFailedToStartKeepsItsError() throws Exception {
        RouteController controller = context.getRouteController();
        assertThrows(Exception.class, () -> controller.startRoute("baz"));
        RouteError error = context.getRoute("baz").getLastError();
        assertNotNull(error);
        assertEquals(RouteError.Phase.START, error.getPhase());
        ServiceStatus status = controller.getRouteStatus("baz");
        assertFalse(status.isStarted());

        // the suspend leaves the route as it is, so the error of the failed start is still shown
        controller.suspendRoute("baz");
        assertEquals(status, controller.getRouteStatus("baz"));
        assertSame(error, context.getRoute("baz").getLastError());
    }

    @Test
    public void testSuspendFailedRouteKeepsItsError() throws Exception {
        RouteController controller = context.getRouteController();
        controller.stopRoute("foo", new IllegalArgumentException("Forced"));
        RouteError error = context.getRoute("foo").getLastError();
        assertNotNull(error);
        assertEquals(RouteError.Phase.STOP, error.getPhase());

        controller.suspendRoute("foo");
        assertEquals(ServiceStatus.Stopped, controller.getRouteStatus("foo"));
        assertSame(error, context.getRoute("foo").getLastError());
    }

    private void assertConsumes(String body) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(body);
        template.sendBody("seda:foo", body);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").to("mock:result");
                from("seda:bar").routeId("bar").autoStartup(false).to("mock:bar");
                from(new FailToStartEndpoint("direct:baz", getContext().getComponent("direct"))).routeId("baz")
                        .autoStartup(false).to("mock:baz");
            }
        };
    }

    private static final class FailToStartEndpoint extends DefaultEndpoint {

        FailToStartEndpoint(String uri, Component component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor) {
                @Override
                protected void doStart() throws Exception {
                    throw new IllegalStateException("Forced start failure");
                }
            };
        }
    }
}
