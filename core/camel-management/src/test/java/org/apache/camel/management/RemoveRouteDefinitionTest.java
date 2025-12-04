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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public class RemoveRouteDefinitionTest extends ManagementTestSupport {

    @Test
    public void testShutdownRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        boolean registered = mbeanServer.isRegistered(on);
        assertTrue(registered, "Should be registered");

        context.getRouteController().stopRoute("route1");
        context.removeRoute("route1");

        // route is shutdown (= also removed), so its not longer in JMX
        set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(0, set.size());
    }

    @Test
    public void testStopAndRemoveRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        boolean registered = mbeanServer.isRegistered(on);
        assertTrue(registered, "Should be registered");

        // must stop before we can remove
        context.getRouteController().stopRoute("route1");
        context.removeRoute("route1");

        // route is removed, so its not longer in JMX
        set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(0, set.size());
    }

    @Test
    public void testStopRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        boolean registered = mbeanServer.isRegistered(on);
        assertTrue(registered, "Should be registered");

        context.getRouteController().stopRoute("route1");

        // route is only stopped so its still in JMX
        set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("route1").to("log:foo").to("mock:result");
            }
        };
    }
}
