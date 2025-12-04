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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public class ManagedRouteGroupTest extends ManagementTestSupport {

    @Test
    public void testRouteGroup() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routegroups,*"), null);
        assertEquals(2, set.size());

        ObjectName on = set.iterator().next();

        boolean registered = mbeanServer.isRegistered(on);
        assertTrue(registered, "Should be registered");

        String group = (String) mbeanServer.getAttribute(on, "RouteGroup");
        assertTrue(group.equals("first") || group.equals("second"));
        Long val = (Long) mbeanServer.getAttribute(on, "ExchangesTotal");
        assertEquals(1, val);
        Integer size = (Integer) mbeanServer.getAttribute(on, "GroupSize");
        if ("first".equals(group)) {
            assertEquals(3, size);
        } else {
            assertEquals(2, size);
        }

        // stop all the route
        context.getRouteController().stopAllRoutes();

        // remove all routes in a group should remove the group
        registered = mbeanServer.isRegistered(on);
        assertTrue(registered, "Should be registered");
        for (Route r : context.getRoutesByGroup(group)) {
            context.removeRoute(r.getRouteId());
        }
        registered = mbeanServer.isRegistered(on);
        assertFalse(registered, "Should not be registered");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .routeGroup("first")
                        .to("direct:a", "direct:b", "direct:c", "direct:d", "direct:e")
                        .to("mock:result");
                from("direct:a").routeId("a").routeGroup("first").to("log:a");
                from("direct:b").routeId("b").to("log:b"); // no group
                from("direct:c").routeId("c").routeGroup("second").to("log:c");
                from("direct:d").routeId("d").routeGroup("second").to("log:d");
                from("direct:e").routeId("e").routeGroup("first").to("log:e");
            }
        };
    }
}
