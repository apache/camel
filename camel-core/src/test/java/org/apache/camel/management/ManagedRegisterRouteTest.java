/**
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

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedRegisterRouteTest extends ManagementTestSupport {

    public void testRoutes() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        boolean registered = mbeanServer.isRegistered(on);
        assertEquals("Should be registered", true, registered);

        String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
        // the route has this starting endpoint uri
        assertEquals("direct://start", uri);

        Integer val = (Integer) mbeanServer.getAttribute(on, "InflightExchanges");
        // the route has no inflight exchanges
        assertEquals(0, val.intValue());

        // should be started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);

        // stop the route
        context.stopRoute(context.getRouteDefinitions().get(0));

        registered = mbeanServer.isRegistered(on);
        assertEquals("Should be registered", true, registered);

        // should be stopped, eg its removed
        state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be stopped", ServiceStatus.Stopped.name(), state);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("mock:result");
            }
        };
    }

}
