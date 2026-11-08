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
package org.apache.camel.management;

import javax.management.MBeanServer;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A node starts its statistics at the end time of the node before it, and an advice that does work between two nodes
 * clears that time. That only holds because of how the channel wires the advices: the route delayer runs before the
 * statistics of each node, and the statistics run inside the error handler when it can redeliver. This test checks that
 * wiring with a real route: the time a node waits in the route delayer must not be counted in that node.
 */
@DisabledOnOs(OS.AIX)
public class ManagedProcessorTimeWithRouteDelayerTest extends ManagementTestSupport {

    private static final long DELAY = 200;

    @Test
    public void testNodeDoesNotCountTheRouteDelayer() throws Exception {
        assertNodeDoesNotCountTheRouteDelayer("direct:start", "route1", "b");
    }

    @Test
    public void testNodeDoesNotCountTheRouteDelayerWithRedelivery() throws Exception {
        assertNodeDoesNotCountTheRouteDelayer("direct:redelivery", "redelivery", "redeliveryB");
    }

    private void assertNodeDoesNotCountTheRouteDelayer(String uri, String routeId, String nodeId) throws Exception {
        getMockEndpoint("mock:b").expectedMessageCount(1);

        template.sendBody(uri, "Hello World");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        long route = (Long) mbeanServer.getAttribute(getCamelObjectName(TYPE_ROUTE, routeId), "MaxProcessingTime");
        long node = (Long) mbeanServer.getAttribute(getCamelObjectName(TYPE_PROCESSOR, nodeId), "MaxProcessingTime");

        // the delayer waits before each of the two nodes
        assertTrue(route >= 2 * DELAY - 20, "The route should take around " + 2 * DELAY + " ms: was " + route);
        assertTrue(node < DELAY / 2, "The node should not count the time it waited in the route delayer: was " + node);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("route1").delayer(DELAY)
                        .to("mock:a")
                        .to("mock:b").id("b");

                // the error handler can redeliver, so the statistics of each node run inside it
                from("direct:redelivery").routeId("redelivery").delayer(DELAY)
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0))
                        .to("mock:a")
                        .to("mock:b").id("redeliveryB");
            }
        };
    }
}
