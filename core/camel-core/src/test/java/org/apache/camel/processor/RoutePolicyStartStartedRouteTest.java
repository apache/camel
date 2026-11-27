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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starting (or resuming) a route that is already started is a no-op: the route policy is not called, and the consumer
 * that the route policy keeps stopped while the route is started (such as the master route policy on a slave) is not
 * started again.
 */
public class RoutePolicyStartStartedRouteTest extends ContextTestSupport {

    private final SlaveRoutePolicy policy = new SlaveRoutePolicy();

    /**
     * Keeps the consumer stopped while the route is started, as a route policy does on a node that is not the master.
     */
    private static final class SlaveRoutePolicy extends RoutePolicySupport {

        private final AtomicInteger starts = new AtomicInteger();

        @Override
        public void onStart(Route route) {
            starts.incrementAndGet();
            try {
                stopConsumer(route.getConsumer());
            } catch (Exception e) {
                handleException(e);
            }
        }
    }

    @BeforeEach
    void assertSlave() {
        assertSlaveRoute();
    }

    @Test
    public void testStartRoute() throws Exception {
        assertNotConsumedAfter(() -> context.getRouteController().startRoute("foo"));
    }

    @Test
    public void testResumeRoute() throws Exception {
        assertNotConsumedAfter(() -> context.getRouteController().resumeRoute("foo"));
    }

    @Test
    public void testStartRouteGroup() throws Exception {
        assertNotConsumedAfter(() -> context.getRouteController().startRouteGroup("g"));
    }

    @Test
    public void testStartAllRoutes() throws Exception {
        assertNotConsumedAfter(() -> context.getRouteController().startAllRoutes());
    }

    @Test
    public void testStopAndStartRoute() throws Exception {
        // a route that is stopped is started again, and so its route policy is called again
        context.getRouteController().stopRoute("foo");
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));

        context.getRouteController().startRoute("foo");

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));
        assertEquals(2, policy.starts.get());
        assertTrue(ServiceHelper.isStopped(context.getRoute("foo").getConsumer()), "The consumer should be stopped");
    }

    private void assertSlaveRoute() {
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));
        assertEquals(1, policy.starts.get());
        assertTrue(ServiceHelper.isStopped(context.getRoute("foo").getConsumer()), "The consumer should be stopped");
    }

    private void assertNotConsumedAfter(StartAction action) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(200);

        template.sendBody("seda:foo", "Hello World");

        action.run();

        // the route is still started, and its consumer still stopped by the route policy
        assertSlaveRoute();
        assertMockEndpointsSatisfied();
    }

    @FunctionalInterface
    private interface StartAction {
        void run() throws Exception;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // purge the queue when stopping, as the stopped consumer does not take the pending message
                from("seda:foo?purgeWhenStopping=true").routeId("foo").group("g").routePolicy(policy)
                        .to("mock:result");
            }
        };
    }
}
