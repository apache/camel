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

import java.util.concurrent.TimeUnit;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteGroupMBean;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that a route group counts each exchange once per entry into the group: calls nested inside another route of the
 * same group are not counted again (CAMEL-24887).
 */
@DisabledOnOs(OS.AIX)
public class ManagedRouteGroupExchangeCountTest extends ManagementTestSupport {

    @Test
    public void testGroupCountsExchangeOnce() throws Exception {
        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        // send 1 message through x1 -> x2 (both in group "g")
        template.sendBody("direct:x1", "Hello");

        ManagedRouteGroupMBean group = mcc.getManagedRouteGroup("g");
        assertNotNull(group);
        assertEquals(1, group.getExchangesCompleted());
        assertEquals(1, group.getExchangesTotal());
        assertEquals(0, group.getExchangesInflight());

        // send 2 more messages
        template.sendBody("direct:x1", "World");
        template.sendBody("direct:x1", "!");

        assertEquals(3, group.getExchangesCompleted());
        assertEquals(3, group.getExchangesTotal());
    }

    @Test
    public void testGroupCountsOnceWhenRoutingThroughNonGroupRoute() throws Exception {
        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        // send 1 message through x1 (group "g") -> y (no group) -> x2 (group "g")
        template.sendBody("direct:x1-via-y", "Hello");

        ManagedRouteGroupMBean group = mcc.getManagedRouteGroup("g");
        assertNotNull(group);
        assertEquals(1, group.getExchangesCompleted());
        assertEquals(1, group.getExchangesTotal());
    }

    @Test
    public void testGroupCountsWhenEntryIsOutsideGroup() throws Exception {
        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        // send 1 message to a route outside the group that calls x2 (in group "g") directly
        template.sendBody("direct:outside", "Hello");

        ManagedRouteGroupMBean group = mcc.getManagedRouteGroup("g");
        assertNotNull(group);
        assertEquals(1, group.getExchangesCompleted());
    }

    @Test
    public void testGroupCountsFailure() throws Exception {
        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        // send 1 message that will fail in x2-fail (group "g")
        try {
            template.sendBody("direct:x1-fail", "Hello");
        } catch (Exception e) {
            // expected
        }

        ManagedRouteGroupMBean group = mcc.getManagedRouteGroup("g");
        assertNotNull(group);
        assertEquals(1, group.getExchangesFailed());
        assertEquals(0, group.getExchangesCompleted());
        assertEquals(0, group.getExchangesInflight());
    }

    @Test
    public void testTwoGroupsOnOnePath() throws Exception {
        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        // send 1 message through g1-route (group "g1") -> g2-route (group "g2")
        template.sendBody("direct:g1-route", "Hello");

        ManagedRouteGroupMBean group1 = mcc.getManagedRouteGroup("g1");
        ManagedRouteGroupMBean group2 = mcc.getManagedRouteGroup("g2");
        assertNotNull(group1);
        assertNotNull(group2);
        assertEquals(1, group1.getExchangesCompleted());
        assertEquals(1, group2.getExchangesCompleted());
    }

    @Test
    public void testWireTapGroupCounts() throws Exception {
        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        getMockEndpoint("mock:wt-done").expectedMessageCount(1);

        template.sendBody("direct:wt", "Hello");

        assertMockEndpointsSatisfied();

        // wait for the wireTapped exchange to complete in route "wt-target"
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1L, mcc.getManagedRoute("wt-target").getExchangesCompleted()));

        // each route counts independently at the route level
        assertEquals(1, mcc.getManagedRoute("wt").getExchangesCompleted());
        assertEquals(1, mcc.getManagedRoute("wt-target").getExchangesCompleted());

        // the group count is consistent with how the CamelContext MBean counts exchanges
        long ctxCompleted = mcc.getManagedCamelContext().getExchangesCompleted();

        ManagedRouteGroupMBean group = mcc.getManagedRouteGroup("g");
        assertNotNull(group);
        assertEquals(ctxCompleted, group.getExchangesCompleted());
        assertEquals(ctxCompleted, group.getExchangesTotal());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // x1 -> x2, both in group "g"
                from("direct:x1").routeId("x1").routeGroup("g")
                        .to("direct:x2");
                from("direct:x2").routeId("x2").routeGroup("g")
                        .log("x2 done");

                // x1 -> y (no group) -> x2, tests routing through non-group route
                from("direct:x1-via-y").routeId("x1-via-y").routeGroup("g")
                        .to("direct:y");
                from("direct:y").routeId("y")
                        .to("direct:x2");

                // outside (no group) -> x2 (group "g")
                from("direct:outside").routeId("outside")
                        .to("direct:x2");

                // failure path: x1-fail -> x2-fail (throws), both in group "g"
                from("direct:x1-fail").routeId("x1-fail").routeGroup("g")
                        .to("direct:x2-fail");
                from("direct:x2-fail").routeId("x2-fail").routeGroup("g")
                        .throwException(new RuntimeException("Test failure"));

                // two groups: g1-route (group "g1") -> g2-route (group "g2")
                from("direct:g1-route").routeId("g1-route").routeGroup("g1")
                        .to("direct:g2-route");
                from("direct:g2-route").routeId("g2-route").routeGroup("g2")
                        .log("g2 done");

                // wireTap: wt (group "g") wireTaps to wt-target (group "g")
                from("direct:wt").routeId("wt").routeGroup("g")
                        .wireTap("direct:wt-target");
                from("direct:wt-target").routeId("wt-target").routeGroup("g")
                        .to("mock:wt-done");

            }
        };
    }

}
