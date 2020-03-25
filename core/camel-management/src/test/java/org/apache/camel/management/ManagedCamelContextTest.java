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

import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ManagedCamelContextTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // to force a different management name than the camel id
        context.getManagementNameStrategy().setNamePattern("19-#name#");
        return context;
    }

    @Test
    public void testManagedCamelContextClient() throws Exception {
        // JMX tests don't work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        ManagedCamelContextMBean client = context.getExtension(ManagedCamelContext.class).getManagedCamelContext();
        assertNotNull(client);

        assertEquals("camel-1", client.getCamelId());
        assertEquals("Started", client.getState());
    }

    @Test
    public void testManagedCamelContext() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        String name = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("camel-1", name);

        String managementName = (String) mbeanServer.getAttribute(on, "ManagementName");
        assertEquals("19-camel-1", managementName);

        String level = (String) mbeanServer.getAttribute(on, "ManagementStatisticsLevel");
        assertEquals("Default", level);

        String uptime = (String) mbeanServer.getAttribute(on, "Uptime");
        assertNotNull(uptime);

        long uptimeMillis = (Long) mbeanServer.getAttribute(on, "UptimeMillis");
        assertTrue(uptimeMillis > 0);

        String status = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Started", status);

        Boolean messageHistory = (Boolean) mbeanServer.getAttribute(on, "MessageHistory");
        assertEquals(Boolean.FALSE, messageHistory);

        Boolean logMask = (Boolean) mbeanServer.getAttribute(on, "LogMask");
        assertEquals(Boolean.FALSE, logMask);

        Integer total = (Integer) mbeanServer.getAttribute(on, "TotalRoutes");
        assertEquals(2, total.intValue());

        Integer started = (Integer) mbeanServer.getAttribute(on, "StartedRoutes");
        assertEquals(2, started.intValue());

        // invoke operations
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mbeanServer.invoke(on, "sendBody", new Object[]{"direct:start", "Hello World"}, new String[]{"java.lang.String", "java.lang.Object"});
        assertMockEndpointsSatisfied();

        resetMocks();
        mock.expectedBodiesReceived("Hello World");
        mbeanServer.invoke(on, "sendStringBody", new Object[]{"direct:start", "Hello World"}, new String[]{"java.lang.String", "java.lang.String"});
        assertMockEndpointsSatisfied();

        Object reply = mbeanServer.invoke(on, "requestBody", new Object[]{"direct:foo", "Hello World"}, new String[]{"java.lang.String", "java.lang.Object"});
        assertEquals("Bye World", reply);

        reply = mbeanServer.invoke(on, "requestStringBody", new Object[]{"direct:foo", "Hello World"}, new String[]{"java.lang.String", "java.lang.String"});
        assertEquals("Bye World", reply);

        resetMocks();
        mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);
        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", 123);
        mbeanServer.invoke(on, "sendBodyAndHeaders", new Object[]{"direct:start", "Hello World", headers}, new String[]{"java.lang.String", "java.lang.Object", "java.util.Map"});
        assertMockEndpointsSatisfied();

        resetMocks();
        mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);
        reply = mbeanServer.invoke(on, "requestBodyAndHeaders", new Object[]{"direct:start", "Hello World", headers}, new String[]{"java.lang.String", "java.lang.Object", "java.util.Map"});
        assertEquals("Hello World", reply);
        assertMockEndpointsSatisfied();

        // test can send
        Boolean can = (Boolean) mbeanServer.invoke(on, "canSendToEndpoint", new Object[]{"direct:start"}, new String[]{"java.lang.String"});
        assertEquals(true, can.booleanValue());
        can = (Boolean) mbeanServer.invoke(on, "canSendToEndpoint", new Object[]{"timer:foo"}, new String[]{"java.lang.String"});
        assertEquals(false, can.booleanValue());

        // stop Camel
        mbeanServer.invoke(on, "stop", null, null);
    }

    @Test
    public void testManagedCamelContextCreateEndpoint() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        assertNull(context.hasEndpoint("seda:bar"));

        // create a new endpoint
        Object reply = mbeanServer.invoke(on, "createEndpoint", new Object[]{"seda:bar"}, new String[]{"java.lang.String"});
        assertEquals(Boolean.TRUE, reply);

        assertNotNull(context.hasEndpoint("seda:bar"));

        ObjectName seda = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=endpoints,name=\"seda://bar\"");
        boolean registered = mbeanServer.isRegistered(seda);
        assertTrue("Should be registered " + seda, registered);

        // create it again
        reply = mbeanServer.invoke(on, "createEndpoint", new Object[]{"seda:bar"}, new String[]{"java.lang.String"});
        assertEquals(Boolean.FALSE, reply);

        registered = mbeanServer.isRegistered(seda);
        assertTrue("Should be registered " + seda, registered);
    }

    @Test
    public void testManagedCamelContextRemoveEndpoint() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        assertNull(context.hasEndpoint("seda:bar"));

        // create a new endpoint
        Object reply = mbeanServer.invoke(on, "createEndpoint", new Object[]{"seda:bar"}, new String[]{"java.lang.String"});
        assertEquals(Boolean.TRUE, reply);

        assertNotNull(context.hasEndpoint("seda:bar"));

        ObjectName seda = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=endpoints,name=\"seda://bar\"");
        boolean registered = mbeanServer.isRegistered(seda);
        assertTrue("Should be registered " + seda, registered);

        // remove it
        Object num = mbeanServer.invoke(on, "removeEndpoints", new Object[]{"seda:*"}, new String[]{"java.lang.String"});
        assertEquals(1, num);

        assertNull(context.hasEndpoint("seda:bar"));
        registered = mbeanServer.isRegistered(seda);
        assertFalse("Should not be registered " + seda, registered);

        // remove it again
        num = mbeanServer.invoke(on, "removeEndpoints", new Object[]{"seda:*"}, new String[]{"java.lang.String"});
        assertEquals(0, num);

        assertNull(context.hasEndpoint("seda:bar"));
        registered = mbeanServer.isRegistered(seda);
        assertFalse("Should not be registered " + seda, registered);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").delay(10).to("mock:result");

                from("direct:foo").delay(10).transform(constant("Bye World")).id("myTransform");
            }
        };
    }

}
