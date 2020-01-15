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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assume;
import org.junit.Test;

/**
 * Tests mbeans is registered when adding a 2nd route after CamelContext has been started.
 */
public class ManagedRouteAddRemoveTest extends ManagementTestSupport {
    
    private static final int SERVICES = 11;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("mock:result");
            }
        };
    }

    @Test
    public void testRouteAddRemoteRouteWithTo() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        Assume.assumeFalse(isPlatform("aix"));

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        result.assertIsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        // number of SERVICES
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // number of producers
        ObjectName onP = ObjectName.getInstance("org.apache.camel:context=camel-1,type=producers,*");
        Set<ObjectName> namesP = mbeanServer.queryNames(onP, null);
        assertEquals(1, namesP.size());

        log.info("Adding 2nd route");

        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").to("mock:bar");
            }
        });

        // and send a message to it
        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);
        template.sendBody("direct:bar", "Hello World");
        bar.assertIsSatisfied();

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // but we should have one more producer
        namesP = mbeanServer.queryNames(onP, null);
        assertEquals(2, namesP.size());

        log.info("Removing 2nd route");

        // now remove the 2nd route
        context.getRouteController().stopRoute("bar");
        boolean removed = context.removeRoute("bar");
        assertTrue(removed);

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // and the 2nd producer should be removed
        namesP = mbeanServer.queryNames(onP, null);
        assertEquals(1, namesP.size());

        log.info("Shutting down...");
    }

    @Test
    public void testRouteAddRemoteRouteWithRecipientList() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        result.assertIsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of SERVICES
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // number of producers
        ObjectName onP = ObjectName.getInstance("org.apache.camel:context=camel-1,type=producers,*");
        Set<ObjectName> namesP = mbeanServer.queryNames(onP, null);
        assertEquals(1, namesP.size());

        log.info("Adding 2nd route");

        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").recipientList(header("bar"));
            }
        });

        // and send a message to it
        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:bar", "Hello World", "bar", "mock:bar");
        bar.assertIsSatisfied();

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // but as its recipient list which is dynamic-to we do not add a new producer
        namesP = mbeanServer.queryNames(onP, null);
        assertEquals(1, namesP.size());

        log.info("Removing 2nd route");

        // now remove the 2nd route
        context.getRouteController().stopRoute("bar");
        boolean removed = context.removeRoute("bar");
        assertTrue(removed);

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // and we still have the original producer
        namesP = mbeanServer.queryNames(onP, null);
        assertEquals(1, namesP.size());

        log.info("Shutting down...");
    }

    @Test
    public void testRouteAddRemoteRouteWithRoutingSlip() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        result.assertIsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of SERVICES
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // number of producers
        ObjectName onP = ObjectName.getInstance("org.apache.camel:context=camel-1,type=producers,*");
        Set<ObjectName> namesP = mbeanServer.queryNames(onP, null);
        assertEquals(1, namesP.size());

        log.info("Adding 2nd route");

        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").routingSlip(header("bar"));
            }
        });

        // and send a message to it
        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:bar", "Hello World", "bar", "mock:bar");
        bar.assertIsSatisfied();

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // but as its recipient list which is dynamic-to we do not add a new producer
        namesP = mbeanServer.queryNames(onP, null);
        assertEquals(1, namesP.size());

        log.info("Removing 2nd route");

        // now remove the 2nd route
        context.getRouteController().stopRoute("bar");
        boolean removed = context.removeRoute("bar");
        assertTrue(removed);

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // and we still have the original producer
        namesP = mbeanServer.queryNames(onP, null);
        assertEquals(1, namesP.size());

        log.info("Shutting down...");
    }

    @Test
    public void testRouteAddRemoteRouteWithRecipientListAndRouteScopedOnException() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        result.assertIsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of SERVICES
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        log.info("Adding 2nd route");

        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar")
                    .onException(Exception.class)
                        .handled(true)
                        .recipientList(header("error"))
                    .end().end()
                    .recipientList(header("bar")).throwException(new IllegalArgumentException("Forced"));
            }
        });

        // and send a message to it
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("error", "mock:error");
        headers.put("bar", "mock:bar");
        template.sendBodyAndHeaders("direct:bar", "Hello World", headers);

        assertMockEndpointsSatisfied();

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // now stop and remove the 2nd route
        log.info("Stopping 2nd route");
        context.getRouteController().stopRoute("bar");

        log.info("Removing 2nd route");
        boolean removed = context.removeRoute("bar");
        assertTrue(removed);

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        log.info("Shutting down...");
    }

    @Test
    public void testRouteAddRemoteRouteWithRecipientListAndContextScopedOnException() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        result.assertIsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of SERVICES
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        log.info("Adding 2nd route");

        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .handled(true)
                    .recipientList(header("error"))
                    .end();

                from("direct:bar").routeId("bar")
                    .recipientList(header("bar")).throwException(new IllegalArgumentException("Forced"));
            }
        });

        // and send a message to it
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("error", "mock:error");
        headers.put("bar", "mock:bar");
        template.sendBodyAndHeaders("direct:bar", "Hello World", headers);

        assertMockEndpointsSatisfied();

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // now stop and remove the 2nd route
        log.info("Stopping 2nd route");
        context.getRouteController().stopRoute("bar");

        log.info("Removing 2nd route");
        boolean removed = context.removeRoute("bar");
        assertTrue(removed);

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        log.info("Shutting down...");
    }

    @Test
    public void testRouteAddRemoteRouteWithRecipientListAndRouteScopedOnCompletion() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        result.assertIsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of SERVICES
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        log.info("Adding 2nd route");

        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar")
                    .onCompletion()
                        .recipientList(header("done"))
                    .end().end()
                    .recipientList(header("bar"));
            }
        });

        // and send a message to it
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:done").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("done", "mock:done");
        headers.put("bar", "mock:bar");
        template.sendBodyAndHeaders("direct:bar", "Hello World", headers);

        assertMockEndpointsSatisfied();

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // now stop and remove the 2nd route
        log.info("Stopping 2nd route");
        context.getRouteController().stopRoute("bar");

        log.info("Removing 2nd route");
        boolean removed = context.removeRoute("bar");
        assertTrue(removed);

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        log.info("Shutting down...");
    }

    @Test
    public void testRouteAddRemoteRouteWithRecipientListAndContextScopedOnCompletion() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        result.assertIsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of SERVICES
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        log.info("Adding 2nd route");

        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion()
                    .recipientList(header("done"))
                    .end();

                from("direct:bar").routeId("bar")
                    .recipientList(header("bar"));
            }
        });

        // and send a message to it
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:done").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("done", "mock:done");
        headers.put("bar", "mock:bar");
        template.sendBodyAndHeaders("direct:bar", "Hello World", headers);

        assertMockEndpointsSatisfied();

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        // now stop and remove the 2nd route
        log.info("Stopping 2nd route");
        context.getRouteController().stopRoute("bar");

        log.info("Removing 2nd route");
        boolean removed = context.removeRoute("bar");
        assertTrue(removed);

        // there should still be the same number of SERVICES
        names = mbeanServer.queryNames(on, null);
        assertEquals(SERVICES, names.size());

        log.info("Shutting down...");
    }
}
