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

import java.util.Collection;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_HEALTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedHealthCheckTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // install health check manually
        HealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setCamelContext(context);
        Object hc = registry.resolveById("context");
        registry.register(hc);
        context.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);

        return context;
    }

    @Test
    public void testHealthCheck() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_HEALTH, "DefaultHealthCheck");
        assertTrue(mbeanServer.isRegistered(on), "Object should be registered: " + on);

        Boolean up = (Boolean) mbeanServer.getAttribute(on, "Healthy");
        assertTrue(up);
        up = (Boolean) mbeanServer.getAttribute(on, "HealthyReadiness");
        assertTrue(up);
        up = (Boolean) mbeanServer.getAttribute(on, "HealthyLiveness");
        assertTrue(up);

        TabularData data = (TabularData) mbeanServer.invoke(on, "details", null, null);
        assertEquals(1, data.size());

        Collection<String> ids = (Collection) mbeanServer.invoke(on, "getHealthChecksIDs", null, null);
        assertEquals(1, ids.size());

        assertEquals("context", ids.iterator().next());
    }

    @Test
    public void testHealthCheckDisableById() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class)
                .register(new AbstractHealthCheck("custom", "myCheck") {
                    @Override
                    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                        // make it always down
                        builder.down();
                    }
                });

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_HEALTH, "DefaultHealthCheck");
        assertTrue(mbeanServer.isRegistered(on), "Object should be registered: " + on);

        Boolean up = (Boolean) mbeanServer.getAttribute(on, "Healthy");
        assertFalse(up);

        Collection<String> ids = (Collection) mbeanServer.invoke(on, "getHealthChecksIDs", null, null);
        assertEquals(2, ids.size());

        mbeanServer.invoke(on, "disableById", new Object[] { "myCheck" }, new String[] { "java.lang.String" });

        up = (Boolean) mbeanServer.getAttribute(on, "Healthy");
        assertTrue(up);

        mbeanServer.invoke(on, "enableById", new Object[] { "myCheck" }, new String[] { "java.lang.String" });

        up = (Boolean) mbeanServer.getAttribute(on, "Healthy");
        assertFalse(up);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }

}
