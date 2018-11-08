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
import java.util.EventObject;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class ManagedCamelContextRestartTest extends ManagementTestSupport {

    private int starts;
    private int stops;

    @Override
    @Before
    public void setUp() throws Exception {

        super.setUp();

        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(EventObject event) throws Exception {
                // Empty.
            }
            @Override
            public boolean isEnabled(EventObject event) {
                return true;
            }
            @Override
            protected void doStart() throws Exception {
                starts++;
            }
            @Override
            protected void doStop() throws Exception {
                stops++;
            }
        });
    }

    @Test
    public void testManagedCamelContext() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");

        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        String name = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("camel-1", name);

        String uptime = (String) mbeanServer.getAttribute(on, "Uptime");
        assertNotNull(uptime);

        long uptimeMillis = (Long) mbeanServer.getAttribute(on, "UptimeMillis");
        assertTrue(uptimeMillis > 0);

        String status = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Started", status);

        // invoke operations
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Object reply = mbeanServer.invoke(on, "requestBody", new Object[]{"direct:foo", "Hello World"}, new String[]{"java.lang.String", "java.lang.Object"});
        assertEquals("Bye World", reply);

        // restart Camel
        assertEquals(0, starts);
        assertEquals(0, stops);
        mbeanServer.invoke(on, "restart", null, null);
        assertEquals(1, starts);
        assertEquals(1, stops);

        status = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Started", status);

        reply = mbeanServer.invoke(on, "requestBody", new Object[]{"direct:foo", "Hello Camel"}, new String[]{"java.lang.String", "java.lang.Object"});
        assertEquals("Bye World", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").delay(10).transform(constant("Bye World"));
            }
        };
    }

}
