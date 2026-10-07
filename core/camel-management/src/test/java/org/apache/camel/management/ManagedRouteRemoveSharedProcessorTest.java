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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outputs of a context scoped onCompletion and of the onException of a route configuration are also the same
 * definition in every route that uses them, and share one processor mbean.
 */
@DisabledOnOs(OS.AIX)
class ManagedRouteRemoveSharedProcessorTest extends ManagementTestSupport {

    @Test
    void testRemoveRouteKeepsMBeansOfOtherRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName done = getCamelObjectName(TYPE_PROCESSOR, "myDone");
        ObjectName error = getCamelObjectName(TYPE_PROCESSOR, "myError");

        template.sendBody("direct:a", "A");
        template.sendBody("direct:b", "B");
        assertEquals(2L, mbeanServer.getAttribute(done, "ExchangesTotal"));
        assertEquals(2L, mbeanServer.getAttribute(error, "ExchangesTotal"));

        context.getRouteController().stopRoute("a");
        context.removeRoute("a");

        for (ObjectName on : new ObjectName[] { done, error }) {
            assertTrue(mbeanServer.isRegistered(on), on + " should still be registered for route b");
            assertEquals("b", mbeanServer.getAttribute(on, "RouteId"));
        }
        template.sendBody("direct:b", "B");
        assertEquals(3L, mbeanServer.getAttribute(done, "ExchangesTotal"));
        assertEquals(3L, mbeanServer.getAttribute(error, "ExchangesTotal"));
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        RoutesBuilder configuration = new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration("myConfig").onException(Exception.class).handled(true).to("mock:error").id("myError");
            }
        };
        RoutesBuilder routes = new RouteBuilder() {
            @Override
            public void configure() {
                // context scoped
                onCompletion().to("mock:done").id("myDone");

                from("direct:a").routeId("a").routeConfigurationId("myConfig")
                        .throwException(new IllegalArgumentException("Forced a"));
                from("direct:b").routeId("b").routeConfigurationId("myConfig")
                        .throwException(new IllegalArgumentException("Forced b"));
            }
        };
        return new RoutesBuilder[] { configuration, routes };
    }
}
