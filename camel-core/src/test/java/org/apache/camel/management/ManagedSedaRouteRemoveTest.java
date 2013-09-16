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
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ManagedSedaRouteRemoveTest extends ManagementTestSupport {

    public void testRemove() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();

        // should be started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);

        // and there should be 2 thread pools (1 default + 1 seda)
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=threadpools,*"), null);
        // there should be a seda thread pool in there
        boolean seda = false;
        for (ObjectName names : set) {
            if (names.toString().contains("Seda")) {
                seda = true;
                break;
            }
        }
        assertTrue("There should be a seda thread pool", seda);

        // stop
        mbeanServer.invoke(on, "stop", null, null);

        state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be stopped", ServiceStatus.Stopped.name(), state);

        // remove
        mbeanServer.invoke(on, "remove", null, null);

        // should not be registered anymore
        boolean registered = mbeanServer.isRegistered(on);
        assertFalse("Route mbean should have been unregistered", registered);

        // and no more routes
        set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(0, set.size());

        // and thread pool should be removed (shutdown creates a new thread pool as well)
        set = mbeanServer.queryNames(new ObjectName("*:type=threadpools,*"), null);
        // there should NOT be a seda thread pool in there
        seda = false;
        for (ObjectName names : set) {
            if (names.toString().contains("Seda")) {
                seda = true;
                break;
            }
        }
        assertFalse("There should not be a seda thread pool", seda);
    }

    static ObjectName getRouteObjectName(MBeanServer mbeanServer) throws Exception {
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        return set.iterator().next();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").to("mock:result");
            }
        };
    }

}
