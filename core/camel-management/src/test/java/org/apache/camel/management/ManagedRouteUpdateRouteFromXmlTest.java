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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ManagedRouteUpdateRouteFromXmlTest extends ManagementTestSupport {

    @Test
    public void testUpdateRouteFromXml() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // should be started
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("myRoute", routeId);

        String xml =
                  "<route id=\"myRoute\" xmlns=\"http://camel.apache.org/schema/spring\">"
                + "  <from uri=\"direct:start\"/>"
                + "  <log message=\"This is a changed route saying ${body}\"/>"
                + "  <to uri=\"mock:changed\"/>"
                + "</route>";

        mbeanServer.invoke(on, "updateRouteFromXml", new Object[]{xml}, new String[]{"java.lang.String"});

        assertEquals(1, context.getRoutes().size());

        getMockEndpoint("mock:changed").expectedMessageCount(1);

        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUpdateRouteFromXmlWithoutRouteId() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // should be started
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("myRoute", routeId);

        String xml =
                  "<route xmlns=\"http://camel.apache.org/schema/spring\">"
                + "  <from uri=\"direct:start\"/>"
                + "  <log message=\"This is a changed route saying ${body}\"/>"
                + "  <to uri=\"mock:changed\"/>"
                + "</route>";

        mbeanServer.invoke(on, "updateRouteFromXml", new Object[]{xml}, new String[]{"java.lang.String"});

        assertEquals(1, context.getRoutes().size());

        getMockEndpoint("mock:changed").expectedMessageCount(1);

        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUpdateRouteFromXmlMismatchRouteId() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // should be started
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("myRoute", routeId);

        String xml =
                  "<route id=\"foo\" xmlns=\"http://camel.apache.org/schema/spring\">"
                + "  <from uri=\"direct:start\"/>"
                + "  <log message=\"This is a changed route saying ${body}\"/>"
                + "  <to uri=\"mock:changed\"/>"
                + "</route>";

        try {
            mbeanServer.invoke(on, "updateRouteFromXml", new Object[]{xml}, new String[]{"java.lang.String"});
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Cannot update route from XML as routeIds does not match. routeId: myRoute, routeId from XML: foo", e.getCause().getMessage());
        }
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
                from("direct:start").routeId("myRoute")
                    .log("Got ${body}")
                    .to("mock:result");
            }
        };
    }

}
