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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCBRTest extends ManagementTestSupport {

    // CAMEL-4044: mbeans not registered for children of choice
    @Test
    public void testManagedCBR() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getCamelObjectName(TYPE_ROUTE, "route");
        assertTrue(mbeanServer.isRegistered(on), "MBean '" + on + "' not registered");

        on = getCamelObjectName(TYPE_PROCESSOR, "task-a");
        assertTrue(mbeanServer.isRegistered(on), "MBean '" + on + "' not registered");

        on = getCamelObjectName(TYPE_PROCESSOR, "choice");
        assertTrue(mbeanServer.isRegistered(on), "MBean '" + on + "' not registered");

        on = getCamelObjectName(TYPE_PROCESSOR, "task-b");
        assertTrue(mbeanServer.isRegistered(on), "MBean '" + on + "' not registered");

        on = getCamelObjectName(TYPE_PROCESSOR, "task-c");
        assertTrue(mbeanServer.isRegistered(on), "MBean '" + on + "' not registered");

        on = getCamelObjectName(TYPE_PROCESSOR, "task-d");
        assertTrue(mbeanServer.isRegistered(on), "MBean '" + on + "' not registered");

        on = getCamelObjectName(TYPE_PROCESSOR, "task-e");
        assertTrue(mbeanServer.isRegistered(on), "MBean '" + on + "' not registered");

        on = getCamelObjectName(TYPE_PROCESSOR, "task-done");
        assertTrue(mbeanServer.isRegistered(on), "MBean '" + on + "' not registered");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("route")
                        .to("mock:a").id("task-a")
                        .choice().id("choice")
                        .when(simple("${body} contains 'Camel'")).id("when")
                        .to("mock:b").id("task-b")
                        .to("mock:c").id("task-c")
                        .when(simple("${body} contains 'Donkey'")).id("when2")
                        .to("mock:d").id("task-d")
                        .otherwise().id("otherwise")
                        .to("mock:e").id("task-e")
                        .end()
                        .to("mock:done").id("task-done");
            }
        };
    }

}
