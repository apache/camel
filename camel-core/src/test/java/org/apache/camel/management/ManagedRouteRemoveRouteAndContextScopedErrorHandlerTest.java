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

import java.util.Iterator;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ManagedRouteRemoveRouteAndContextScopedErrorHandlerTest extends ManagementTestSupport {

    public void testRemoveFoo() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer, "foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();

        // should be started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);

        // and 1 context scoped and 1 route scoped error handler
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=errorhandlers,*"), null);
        assertEquals(2, set.size());

        // stop
        mbeanServer.invoke(on, "stop", null, null);

        state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be stopped", ServiceStatus.Stopped.name(), state);

        // remove
        mbeanServer.invoke(on, "remove", null, null);

        // should not be registered anymore
        boolean registered = mbeanServer.isRegistered(on);
        assertFalse("Route mbean should have been unregistered", registered);

        // and only the other route
        set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        // and the route scoped error handler should be removed
        set = mbeanServer.queryNames(new ObjectName("*:type=errorhandlers,*"), null);
        assertEquals(1, set.size());
        // should be the context scoped logging error handler
        assertTrue("Should be context scoped error handler: " + set, set.iterator().next().toString().contains("Logging"));
    }

    public void testRemoveBar() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer, "bar");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:bar", "Hello World");

        assertMockEndpointsSatisfied();

        // should be started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);

        // and 1 context scoped and 1 route scoped error handler
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=errorhandlers,*"), null);
        assertEquals(2, set.size());

        // stop
        mbeanServer.invoke(on, "stop", null, null);

        state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be stopped", ServiceStatus.Stopped.name(), state);

        // remove
        mbeanServer.invoke(on, "remove", null, null);

        // should not be registered anymore
        boolean registered = mbeanServer.isRegistered(on);
        assertFalse("Route mbean should have been unregistered", registered);

        // and only the other route
        set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        // should still be the context scoped error handler as its not removed when removing a route
        set = mbeanServer.queryNames(new ObjectName("*:type=errorhandlers,*"), null);
        assertEquals(2, set.size());
    }

    static ObjectName getRouteObjectName(MBeanServer mbeanServer, String name) throws Exception {
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(2, set.size());

        // return the foo route
        Iterator<ObjectName> it = set.iterator();
        ObjectName on = it.next();
        if (on.toString().contains(name)) {
            return on;
        } else {
            return it.next();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // context scoped error handler
                errorHandler(loggingErrorHandler("global"));

                from("seda:bar").routeId("bar")
                        .to("mock:result");

                from("seda:foo").routeId("foo")
                    // route scoped error handler
                    .errorHandler(deadLetterChannel("mock:dead"))
                    .to("mock:result");
            }
        };
    }

}
