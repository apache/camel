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

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.TreeSet;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A route mbean must not read the processor mbeans of the other routes to find its own processors, as calling it for
 * every route is then quadratic in the size of the integration (CAMEL-25267).
 */
@DisabledOnOs(OS.AIX)
public class ManagedRouteProcessorsOfOtherRoutesTest extends ManagementTestSupport {

    // the processor and step mbeans that have been read
    private final Set<String> touched = new TreeSet<>();
    private volatile boolean recording;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        MBeanServer platform = ManagementFactory.getPlatformMBeanServer();
        MBeanServer server = (MBeanServer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { MBeanServer.class }, (proxy, method, args) -> {
                    if (recording && args != null && args.length > 1 && args[0] instanceof ObjectName on) {
                        String name = method.getName();
                        if (name.equals("getAttribute") || name.equals("getAttributes") || name.equals("invoke")) {
                            record(on);
                        } else if (name.equals("queryNames") && args[1] != null) {
                            // the query expression is evaluated against every mbean matching the pattern
                            platform.queryNames(on, null).forEach(this::record);
                        }
                    }
                    try {
                        return method.invoke(platform, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        context.getManagementStrategy().getManagementAgent().setMBeanServer(server);
        return context;
    }

    private void record(ObjectName on) {
        String type = on.getKeyProperty("type");
        if ("processors".equals(type) || "steps".equals(type)) {
            touched.add(ObjectName.unquote(on.getKeyProperty("name")));
        }
    }

    private ManagedCamelContext mcc() {
        return context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
    }

    @Test
    public void testProcessorIds() throws Exception {
        recording = true;
        Set<String> ids = new TreeSet<>(mcc().getManagedRoute("foo").processorIds());
        recording = false;

        assertEquals(Set.of("foo-log", "foo-to"), ids);
        assertEquals(Set.of("foo-log", "foo-to"), touched);
    }

    @Test
    public void testReset() throws Exception {
        template.sendBody("direct:foo", "Hello");
        template.sendBody("direct:bar", "Hello");

        recording = true;
        mcc().getManagedRoute("foo").reset(true);
        recording = false;

        assertEquals(Set.of("foo-log", "foo-step", "foo-to"), touched);
        assertEquals(0, mcc().getManagedProcessor("foo-to").getExchangesTotal());
        assertEquals(0, mcc().getManagedStep("foo-step").getExchangesTotal());
        assertEquals(1, mcc().getManagedProcessor("bar-to").getExchangesTotal());
    }

    @Test
    public void testRemoveRoute() throws Exception {
        context.getRouteController().stopRoute("bar");
        context.removeRoute("bar");

        assertNull(mcc().getManagedProcessor("bar-to"));
        assertNull(mcc().getManagedStep("bar-step"));
        assertEquals(Set.of("foo-log", "foo-to"), new TreeSet<>(mcc().getManagedRoute("foo").processorIds()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo")
                        .step("foo-step").log("${body}").id("foo-log").end()
                        .to("mock:foo").id("foo-to");

                from("direct:bar").routeId("bar")
                        .step("bar-step").log("${body}").id("bar-log").end()
                        .to("mock:bar").id("bar-to");
            }
        };
    }
}
