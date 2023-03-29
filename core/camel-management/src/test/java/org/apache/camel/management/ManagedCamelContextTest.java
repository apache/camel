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
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.RefDataFormatTest;
import org.apache.camel.impl.engine.ExplicitCamelContextNameStrategy;
import org.apache.camel.spi.DataFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCamelContextTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // to force a different management name than the camel id
        context.getManagementNameStrategy().setNamePattern("19-#name#");
        context.getCamelContextExtension().setDescription("My special Camel description");
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("my-camel-context"));
        // debugger needed for source locations
        context.setDebugging(true);
        return context;
    }

    @Test
    public void testManagedCamelContextClient() throws Exception {
        ManagedCamelContextMBean client
                = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class).getManagedCamelContext();
        assertNotNull(client);

        assertEquals("my-camel-context", client.getCamelId());
        assertEquals("My special Camel description", client.getCamelDescription());
        assertEquals("Started", client.getState());
    }

    @Test
    public void testManagedCamelContext() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        assertTrue(mbeanServer.isRegistered(on), "Should be registered");
        String name = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("my-camel-context", name);

        String managementName = (String) mbeanServer.getAttribute(on, "ManagementName");
        assertEquals("19-my-camel-context", managementName);

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
        mbeanServer.invoke(on, "sendBody", new Object[] { "direct:start", "Hello World" },
                new String[] { "java.lang.String", "java.lang.Object" });
        assertMockEndpointsSatisfied();

        resetMocks();
        mock.expectedBodiesReceived("Hello World");
        mbeanServer.invoke(on, "sendStringBody", new Object[] { "direct:start", "Hello World" },
                new String[] { "java.lang.String", "java.lang.String" });
        assertMockEndpointsSatisfied();

        Object reply = mbeanServer.invoke(on, "requestBody", new Object[] { "direct:foo", "Hello World" },
                new String[] { "java.lang.String", "java.lang.Object" });
        assertEquals("Bye World", reply);

        reply = mbeanServer.invoke(on, "requestStringBody", new Object[] { "direct:foo", "Hello World" },
                new String[] { "java.lang.String", "java.lang.String" });
        assertEquals("Bye World", reply);

        resetMocks();
        mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);
        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", 123);
        mbeanServer.invoke(on, "sendBodyAndHeaders", new Object[] { "direct:start", "Hello World", headers },
                new String[] { "java.lang.String", "java.lang.Object", "java.util.Map" });
        assertMockEndpointsSatisfied();

        resetMocks();
        mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", 123);
        reply = mbeanServer.invoke(on, "requestBodyAndHeaders", new Object[] { "direct:start", "Hello World", headers },
                new String[] { "java.lang.String", "java.lang.Object", "java.util.Map" });
        assertEquals("Hello World", reply);
        assertMockEndpointsSatisfied();

        // test can send
        Boolean can = (Boolean) mbeanServer.invoke(on, "canSendToEndpoint", new Object[] { "direct:start" },
                new String[] { "java.lang.String" });
        assertTrue(can);
        can = (Boolean) mbeanServer.invoke(on, "canSendToEndpoint", new Object[] { "timer:foo" },
                new String[] { "java.lang.String" });
        assertFalse(can);

        // stop Camel
        mbeanServer.invoke(on, "stop", null, null);
    }

    @Test
    public void testManagedCamelContextCreateEndpoint() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        assertNull(context.hasEndpoint("seda:bar"));

        // create a new endpoint
        Object reply
                = mbeanServer.invoke(on, "createEndpoint", new Object[] { "seda:bar" }, new String[] { "java.lang.String" });
        assertEquals(Boolean.TRUE, reply);

        assertNotNull(context.hasEndpoint("seda:bar"));

        ObjectName seda = getCamelObjectName(TYPE_ENDPOINT, "seda://bar");
        boolean registered = mbeanServer.isRegistered(seda);
        assertTrue(registered, "Should be registered " + seda);

        // create it again
        reply = mbeanServer.invoke(on, "createEndpoint", new Object[] { "seda:bar" }, new String[] { "java.lang.String" });
        assertEquals(Boolean.FALSE, reply);

        registered = mbeanServer.isRegistered(seda);
        assertTrue(registered, "Should be registered " + seda);
    }

    @Test
    public void testManagedCamelContextRemoveEndpoint() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        assertNull(context.hasEndpoint("seda:bar"));

        // create a new endpoint
        Object reply
                = mbeanServer.invoke(on, "createEndpoint", new Object[] { "seda:bar" }, new String[] { "java.lang.String" });
        assertEquals(Boolean.TRUE, reply);

        assertNotNull(context.hasEndpoint("seda:bar"));

        ObjectName seda = getCamelObjectName(TYPE_ENDPOINT, "seda://bar");
        boolean registered = mbeanServer.isRegistered(seda);
        assertTrue(registered, "Should be registered " + seda);

        // remove it
        Object num = mbeanServer.invoke(on, "removeEndpoints", new Object[] { "seda:*" }, new String[] { "java.lang.String" });
        assertEquals(1, num);

        assertNull(context.hasEndpoint("seda:bar"));
        registered = mbeanServer.isRegistered(seda);
        assertFalse(registered, "Should not be registered " + seda);

        // remove it again
        num = mbeanServer.invoke(on, "removeEndpoints", new Object[] { "seda:*" }, new String[] { "java.lang.String" });
        assertEquals(0, num);

        assertNull(context.hasEndpoint("seda:bar"));
        registered = mbeanServer.isRegistered(seda);
        assertFalse(registered, "Should not be registered " + seda);
    }

    @Test
    public void testLanguageNames() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getContextObjectName();

        Set<String> names = (Set<String>) mbeanServer.invoke(on, "languageNames", null, null);
        Assertions.assertEquals(2, names.size());
        Assertions.assertTrue(names.contains("constant"));
        Assertions.assertTrue(names.contains("simple"));
    }

    @Test
    public void testComponentNames() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getContextObjectName();

        Set<String> names = (Set<String>) mbeanServer.invoke(on, "componentNames", null, null);
        Assertions.assertEquals(3, names.size());
        Assertions.assertTrue(names.contains("direct"));
        Assertions.assertTrue(names.contains("mock"));
        Assertions.assertTrue(names.contains("seda"));
    }

    @Test
    public void testDataFormatNames() throws Exception {
        context.getRegistry().bind("reverse", new RefDataFormatTest.MyReverseDataFormat());
        DataFormat df = context.resolveDataFormat("reverse");
        assertNotNull(df);

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getContextObjectName();

        Set<String> names = (Set<String>) mbeanServer.invoke(on, "dataFormatNames", null, null);
        Assertions.assertEquals(1, names.size());
        Assertions.assertTrue(names.contains("reverse"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .delay(10)
                        .to("mock:result");

                from("direct:foo")
                        .delay(10)
                        .transform(constant("Bye World")).id("myTransform");
            }
        };
    }

}
