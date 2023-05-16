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

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedRouteRestartTest extends ManagementTestSupport {

    private MyRoutePolicy myRoutePolicy = new MyRoutePolicy();

    @Test
    public void testRestartRoute() throws Exception {
        assertEquals(1, myRoutePolicy.getStart());
        assertEquals(0, myRoutePolicy.getStop());

        // fire a message to get it running
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        boolean registered = mbeanServer.isRegistered(on);
        assertEquals(true, registered, "Should be registered");

        String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
        // the route has this starting endpoint uri
        assertEquals("direct://start", uri);

        // should be started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state, "Should be started");

        String uptime = (String) mbeanServer.getAttribute(on, "Uptime");
        assertNotNull(uptime);
        log.info("Uptime: {}", uptime);

        long uptimeMillis = (Long) mbeanServer.getAttribute(on, "UptimeMillis");
        assertTrue(uptimeMillis > 0);

        mbeanServer.invoke(on, "restart", null, null);

        registered = mbeanServer.isRegistered(on);
        assertEquals(true, registered, "Should be registered");

        // should be started
        state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state, "Should be started");

        assertEquals(2, myRoutePolicy.getStart());
        assertEquals(1, myRoutePolicy.getStop());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routePolicy(myRoutePolicy)
                        .delayer(10).to("log:foo").to("mock:result");
            }
        };
    }

    private static final class MyRoutePolicy extends RoutePolicySupport {

        private int start;
        private int stop;

        @Override
        public void onStart(Route route) {
            start++;
        }

        @Override
        public void onStop(Route route) {
            stop++;
        }

        public int getStart() {
            return start;
        }

        public int getStop() {
            return stop;
        }
    }

}
