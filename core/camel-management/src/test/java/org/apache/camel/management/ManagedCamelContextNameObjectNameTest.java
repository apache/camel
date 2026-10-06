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

import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The management name is the (unquoted) context key of every object name: a CamelContext name with characters that an
 * unquoted ObjectName value cannot have must still start, and must not share its MBeans with another CamelContext.
 */
@DisabledOnOs(OS.AIX)
class ManagedCamelContextNameObjectNameTest {

    private final List<CamelContext> contexts = new ArrayList<>();

    private CamelContext createCamelContext(String name, String managementPattern) throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().setName(name);
        if (managementPattern != null) {
            context.getManagementNameStrategy().setNamePattern(managementPattern);
        }
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("foo").to("mock:result");
            }
        });
        contexts.add(context);
        return context;
    }

    @AfterEach
    void tearDown() {
        contexts.forEach(CamelContext::stop);
    }

    @ParameterizedTest
    @ValueSource(strings = { "my,camel", "my=camel", "my:camel", "my\"camel", "my*camel", "my?camel", "my\ncamel" })
    void testContextNameWithObjectNameCharacters(String name) throws Exception {
        CamelContext context = createCamelContext(name, null);
        context.start();

        assertTrue(context.getStatus().isStarted(), "Should be started");
        assertEquals("my_camel", context.getManagementName());

        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();
        ObjectName on = ObjectName.getInstance(
                "org.apache.camel:context=my_camel,type=context,name=" + ObjectName.quote(name));
        assertTrue(mbeanServer.isRegistered(on), "The CamelContext MBean should be registered");
        assertEquals(name, mbeanServer.getAttribute(on, "CamelId"));
        assertTrue(mbeanServer.isRegistered(
                ObjectName.getInstance("org.apache.camel:context=my_camel,type=routes,name=\"foo\"")),
                "The route MBean should be registered");

        // the queries that build the context key from the management name work as for any other name
        ManagedCamelContext managed = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        ManagedRouteMBean route = managed.getManagedRoute("foo");
        assertNotNull(route);
        assertEquals(name, route.getCamelId());
    }

    @Test
    void testSanitizedNameDoesNotShareMBeansWithExistingName() throws Exception {
        CamelContext camel1 = createCamelContext("my_camel", null);
        CamelContext camel2 = createCamelContext("my,camel", null);
        camel1.start();
        camel2.start();

        assertSeparateMBeans(camel1, camel2);
    }

    @Test
    void testExistingNameDoesNotShareMBeansWithSanitizedName() throws Exception {
        CamelContext camel1 = createCamelContext("my,camel", null);
        CamelContext camel2 = createCamelContext("my_camel", null);
        camel1.start();
        camel2.start();

        assertSeparateMBeans(camel1, camel2);
    }

    @Test
    void testNameOfCamelContextIsFreeNameOfAnother() throws Exception {
        CamelContext camel1 = createCamelContext("foo", null);
        CamelContext camel2 = createCamelContext("foo", null);
        camel1.start();
        camel2.start();

        // the second foo got a free name such as foo-1, which is then the name of a third CamelContext
        String freeName = camel2.getManagementName();
        assertNotEquals("foo", freeName);
        CamelContext camel3 = createCamelContext(freeName, null);
        camel3.start();

        assertSeparateMBeans(camel2, camel3);
    }

    @Test
    void testFixedManagementNameUsedByAnotherCamelContext() throws Exception {
        CamelContext camel1 = createCamelContext("foo", "myFoo");
        CamelContext camel2 = createCamelContext("bar", "myFoo");
        camel1.start();

        // another CamelContext uses the context key, so the object names of the routes would clash
        Exception e = assertThrows(Exception.class, camel2::start);
        assertTrue(e.getCause().getMessage().contains("is already registered"), e.getCause().getMessage());
    }

    private static void assertSeparateMBeans(CamelContext camel1, CamelContext camel2) {
        assertNotEquals(camel1.getManagementName(), camel2.getManagementName());
        for (CamelContext context : List.of(camel1, camel2)) {
            ManagedCamelContext managed = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
            ManagedRouteMBean route = managed.getManagedRoute("foo");
            assertNotNull(route, "The route MBean of " + context.getName() + " should be registered");
            assertEquals(context.getName(), route.getCamelId(), "The route MBean should be the one of its CamelContext");
        }
    }
}
