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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.StringHelper;

/**
 * @version 
 */
public class ManagedCamelContextTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // to force a different management name than the camel id
        context.getManagementNameStrategy().setNamePattern("19-#name#");
        return context;
    }

    public void testManagedCamelContextClient() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        ManagedCamelContextMBean client = context.getManagedCamelContext();
        assertNotNull(client);

        assertEquals("camel-1", client.getCamelId());
        assertEquals("Started", client.getState());

        List<String> names = client.findComponentNames();
        assertNotNull(names);
        assertTrue(names.contains("mock"));
    }

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
        assertEquals(Boolean.TRUE, messageHistory);

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
        Map<String, Object> headers = new HashMap<String, Object>();
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

    public void testFindComponentsInClasspath() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        assertTrue("Should be registered", mbeanServer.isRegistered(on));

        @SuppressWarnings("unchecked")
        Map<String, Properties> info = (Map<String, Properties>) mbeanServer.invoke(on, "findComponents", null, null);
        assertNotNull(info);

        assertTrue(info.size() > 20);
        Properties prop = info.get("seda");
        assertNotNull(prop);
        assertEquals("seda", prop.get("name"));
        assertEquals("org.apache.camel", prop.get("groupId"));
        assertEquals("camel-core", prop.get("artifactId"));
    }

    public void testManagedCamelContextCreateRouteStaticEndpointJson() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        // get the json
        String json = (String) mbeanServer.invoke(on, "createRouteStaticEndpointJson", null, null);
        assertNotNull(json);
        assertEquals(7, StringHelper.countChar(json, '{'));
        assertEquals(7, StringHelper.countChar(json, '}'));
        assertTrue(json.contains("{ \"uri\": \"direct://start\" }"));
        assertTrue(json.contains("{ \"uri\": \"direct://foo\" }"));
    }

    public void testManagedCamelContextExplainEndpointUriFalse() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        // get the json
        String json = (String) mbeanServer.invoke(on, "explainEndpointJson", new Object[]{"log:foo?groupDelay=2000&groupSize=5", false},
                new String[]{"java.lang.String", "boolean"});
        assertNotNull(json);

        // the loggerName option should come before the groupDelay option
        int pos = json.indexOf("loggerName");
        int pos2 = json.indexOf("groupDelay");
        assertTrue("LoggerName should come before groupDelay", pos < pos2);

        assertEquals(6, StringHelper.countChar(json, '{'));
        assertEquals(6, StringHelper.countChar(json, '}'));

        assertTrue(json.contains("\"scheme\": \"log\""));
        assertTrue(json.contains("\"label\": \"core,monitoring\""));

        assertTrue(json.contains("\"loggerName\": { \"kind\": \"path\", \"group\": \"producer\", \"required\": \"true\""));
        assertTrue(json.contains("\"groupSize\": { \"kind\": \"parameter\", \"group\": \"producer\", \"type\": \"integer\","
                + " \"javaType\": \"java.lang.Integer\", \"deprecated\": \"false\", \"secret\": \"false\", \"value\": \"5\""));

        // and we should also have the javadoc documentation
        assertTrue(json.contains("Set the initial delay for stats (in millis)"));
    }

    public void testManagedCamelContextExplainEndpointUriTrue() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        // get the json
        String json = (String) mbeanServer.invoke(on, "explainEndpointJson", new Object[]{"log:foo?groupDelay=2000&groupSize=5", true},
                new String[]{"java.lang.String", "boolean"});
        assertNotNull(json);

        // the loggerName option should come before the groupDelay option
        int pos = json.indexOf("loggerName");
        int pos2 = json.indexOf("groupDelay");
        assertTrue("LoggerName should come before groupDelay", pos < pos2);

        assertEquals(30, StringHelper.countChar(json, '{'));
        assertEquals(30, StringHelper.countChar(json, '}'));

        assertTrue(json.contains("\"scheme\": \"log\""));
        assertTrue(json.contains("\"label\": \"core,monitoring\""));

        assertTrue(json.contains("\"loggerName\": { \"kind\": \"path\", \"group\": \"producer\", \"required\": \"true\""));
        assertTrue(json.contains("\"groupSize\": { \"kind\": \"parameter\", \"group\": \"producer\", \"type\": \"integer\","
                + " \"javaType\": \"java.lang.Integer\", \"deprecated\": \"false\", \"secret\": \"false\", \"value\": \"5\""));
        // and we should also have the javadoc documentation
        assertTrue(json.contains("Set the initial delay for stats (in millis)"));
    }

    public void testManagedCamelContextExplainEipFalse() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        // get the json
        String json = (String) mbeanServer.invoke(on, "explainEipJson", new Object[]{"myTransform", false}, new String[]{"java.lang.String", "boolean"});
        assertNotNull(json);

        assertTrue(json.contains("\"label\": \"eip,transformation\""));
        assertTrue(json.contains("\"expression\": { \"kind\": \"expression\", \"required\": \"true\", \"type\": \"object\""));
        // we should see the constant value
        assertTrue(json.contains("Bye World"));
    }

    public void testManagedCamelContextExplainEipTrue() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        // get the json
        String json = (String) mbeanServer.invoke(on, "explainEipJson", new Object[]{"myTransform", true}, new String[]{"java.lang.String", "boolean"});
        assertNotNull(json);

        assertTrue(json.contains("\"label\": \"eip,transformation\""));
        assertTrue(json.contains("\"expression\": { \"kind\": \"expression\", \"required\": \"true\", \"type\": \"object\""));
        // and now we have the description option also
        assertTrue(json.contains("\"description\": { \"kind\": \"element\", \"required\": \"false\", \"type\": \"object\", \"javaType\""));
        // we should see the constant value
        assertTrue(json.contains("Bye World"));
    }

    public void testManagedCamelContextExplainEipModel() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        // get the json
        String json = (String) mbeanServer.invoke(on, "explainEipJson", new Object[]{"aggregate", false}, new String[]{"java.lang.String", "boolean"});
        assertNotNull(json);

        assertTrue(json.contains("\"description\": \"Aggregates many messages into a single message\""));
        assertTrue(json.contains("\"label\": \"eip,routing\""));
        assertTrue(json.contains("\"correlationExpression\": { \"kind\": \"expression\", \"displayName\": \"Correlation Expression\", \"required\": true, \"type\": \"object\""));
        assertTrue(json.contains("\"discardOnCompletionTimeout\": { \"kind\": \"attribute\", \"displayName\": \"Discard On Completion Timeout\", \"required\": false, \"type\": \"boolean\""));
    }

    public void testManagedCamelContextExplainComponentModel() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=19-camel-1,type=context,name=\"camel-1\"");

        // get the json
        String json = (String) mbeanServer.invoke(on, "explainComponentJson", new Object[]{"seda", false}, new String[]{"java.lang.String", "boolean"});
        assertNotNull(json);

        assertTrue(json.contains("\"label\": \"core,endpoint\""));
        assertTrue(json.contains("\"queueSize\": { \"kind\": \"property\", \"group\": \"advanced\", \"label\": \"advanced\""));
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
