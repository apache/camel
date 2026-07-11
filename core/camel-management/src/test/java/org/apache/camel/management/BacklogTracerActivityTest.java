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

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class BacklogTracerActivityTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testActivityCapture() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName(
                        "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        Integer activitySize = (Integer) mbeanServer.getAttribute(on, "ActivitySize");
        assertEquals(100, activitySize.intValue());

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        String json = (String) mbeanServer.invoke(on, "dumpActivityAsJSon", null, null);
        assertNotNull(json);
        assertTrue(json.contains("activity"));
        assertTrue(json.contains("exchangeId"));
        assertTrue(json.contains("routeId"));
        assertTrue(json.contains("elapsed"));

        // each exchange produces one activity entry (the isLast event)
        // so 2 messages = 2 activity entries
        assertTrue(json.contains("\"failed\":false"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testActivityOverflow() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName(
                        "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        // set activity size to 5
        mbeanServer.setAttribute(on, new Attribute("ActivitySize", 5));
        Integer activitySize = (Integer) mbeanServer.getAttribute(on, "ActivitySize");
        assertEquals(5, activitySize.intValue());

        getMockEndpoint("mock:foo").expectedMessageCount(10);
        getMockEndpoint("mock:bar").expectedMessageCount(10);

        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        assertMockEndpointsSatisfied();

        // with removeOnDump=true (default), dumpActivity returns and clears
        // the activity queue should have been drained to at most 5 entries
        String json = (String) mbeanServer.invoke(on, "dumpActivityAsJSon", null, null);
        assertNotNull(json);

        // count exchangeId occurrences to verify bounded size
        int count = 0;
        int idx = 0;
        while ((idx = json.indexOf("\"exchangeId\"", idx)) != -1) {
            count++;
            idx++;
        }
        assertEquals(5, count, "Activity queue should be bounded to activitySize=5");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testActivityClearOnDump() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName(
                        "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        // first dump should have entries
        String json1 = (String) mbeanServer.invoke(on, "dumpActivityAsJSon", null, null);
        assertNotNull(json1);
        assertTrue(json1.contains("exchangeId"));

        // second dump should be empty (removeOnDump=true by default)
        String json2 = (String) mbeanServer.invoke(on, "dumpActivityAsJSon", null, null);
        assertNotNull(json2);
        assertFalse(json2.contains("exchangeId"), "Activity should be cleared after dump");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testActivityCapturesFailed() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName(
                        "org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        String json = (String) mbeanServer.invoke(on, "dumpActivityAsJSon", null, null);
        assertNotNull(json);
        assertTrue(json.contains("\"failed\":false"));
        assertTrue(json.contains("\"routeId\":\"route1\""));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(false);
                context.setBacklogTracing(true);

                from("direct:start")
                        .to("mock:foo").id("foo")
                        .to("mock:bar").id("bar");
            }
        };
    }

}
