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

import java.lang.reflect.Field;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outputs of a context scoped onException are the same definition in every route, and share one processor mbean.
 */
@DisabledOnOs(OS.AIX)
class ManagedRouteRemoveContextScopedOnExceptionTest extends ManagementTestSupport {

    @Test
    void testRemoveRouteKeepsMBeanOfOtherRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "myError");

        template.sendBody("direct:a", "A");
        template.sendBody("direct:b", "B");
        assertEquals(2L, mbeanServer.getAttribute(on, "ExchangesTotal"));
        assertEquals("a", mbeanServer.getAttribute(on, "RouteId"));

        context.getRouteController().stopRoute("a");
        context.removeRoute("a");

        assertTrue(mbeanServer.isRegistered(on), "The onException processor mbean should still be registered for route b");
        assertEquals("b", mbeanServer.getAttribute(on, "RouteId"));
        assertEquals("Started", mbeanServer.getAttribute(on, "State"));
        DefaultManagementAgent agent = (DefaultManagementAgent) context.getManagementStrategy().getManagementAgent();
        assertTrue(agent.getRouteProcessorMBeanNames("b", false).contains(on), "Should be a processor of route b");
        assertFalse(agent.getRouteProcessorMBeanNames("a", false).contains(on), "Should not be a processor of route a");

        template.sendBody("direct:b", "B");
        assertEquals(3L, mbeanServer.getAttribute(on, "ExchangesTotal"));
    }

    @Test
    void testRemoveOtherRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "myError");

        template.sendBody("direct:a", "A");
        template.sendBody("direct:b", "B");

        context.getRouteController().stopRoute("b");
        context.removeRoute("b");

        assertTrue(mbeanServer.isRegistered(on), "The onException processor mbean should still be registered for route a");
        assertEquals("a", mbeanServer.getAttribute(on, "RouteId"));
        assertEquals("Started", mbeanServer.getAttribute(on, "State"));

        template.sendBody("direct:a", "A");
        assertEquals(3L, mbeanServer.getAttribute(on, "ExchangesTotal"));
    }

    @Test
    void testRemoveAllRoutes() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "myError");

        template.sendBody("direct:a", "A");
        template.sendBody("direct:b", "B");

        context.getRouteController().stopRoute("a");
        context.removeRoute("a");
        context.getRouteController().stopRoute("b");
        context.removeRoute("b");

        assertFalse(mbeanServer.isRegistered(on), "The onException processor mbean should be unregistered");
        assertEquals(0, wrappedProcessors().size(), "No wrapped processors should be left after removing all routes");
    }

    private Map<?, ?> wrappedProcessors() throws Exception {
        JmxManagementLifecycleStrategy strategy = context.getLifecycleStrategies().stream()
                .filter(JmxManagementLifecycleStrategy.class::isInstance)
                .map(JmxManagementLifecycleStrategy.class::cast)
                .findFirst().orElseThrow();
        Field field = JmxManagementLifecycleStrategy.class.getDeclaredField("wrappedProcessors");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(strategy);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // context scoped
                onException(Exception.class).handled(true).to("mock:error").id("myError");

                from("direct:a").routeId("a").throwException(new IllegalArgumentException("Forced a"));
                from("direct:b").routeId("b").throwException(new IllegalArgumentException("Forced b"));
            }
        };
    }
}
