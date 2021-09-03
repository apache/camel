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

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests mbeans is registered when adding a 2nd route after CamelContext has been started.
 */
@DisabledOnOs(OS.AIX)
public class ManagedRouteAddSecondRouteTest extends ManagementTestSupport {

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
    public void testRouteAddSecondRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName route1 = getCamelObjectName(TYPE_ROUTE, "foo");

        // should be started
        String state = (String) mbeanServer.getAttribute(route1, "State");
        assertEquals(ServiceStatus.Started.name(), state, "Should be started");

        log.info(">>>>>>>>>>>>>>>>> adding 2nd route <<<<<<<<<<<<<<");
        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").to("mock:bar");
            }
        });
        log.info(">>>>>>>>>>>>>>>>> adding 2nd route DONE <<<<<<<<<<<<<<");

        // find the 2nd route
        ObjectName route2 = getCamelObjectName(TYPE_ROUTE, "bar");

        // should be started
        state = (String) mbeanServer.getAttribute(route2, "State");
        assertEquals(ServiceStatus.Started.name(), state, "Should be started");
    }

}
