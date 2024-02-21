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

import org.apache.camel.ManagementMBeansLevel;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that mbeans for routes and processors are NOT added after addition of a 2nd route after CamelContext has been
 * started, because the mbeansLevel is set to contextOnly.
 */
@DisabledOnOs(OS.AIX)
public class ManagedMBeansLevelContextOnlyWithAddedRouteTest extends ManagedMBeansLevelTestSupport {

    public ManagedMBeansLevelContextOnlyWithAddedRouteTest() {
        super(ManagementMBeansLevel.ContextOnly);
    }

    @Override
    void assertResults(Set<ObjectName> contexts, Set<ObjectName> routes, Set<ObjectName> processors) {
        assertEquals(1, contexts.size());
        assertEquals(0, routes.size());
        assertEquals(0, processors.size());
    }

    @Test
    @Override
    public void test() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        //test state after before adding a new route
        assertMBeans(mbeanServer);

        log.info(">>>>>>>>>>>>>>>>> adding 2nd route <<<<<<<<<<<<<<");
        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").to("mock:bar");
            }
        });
        log.info(">>>>>>>>>>>>>>>>> adding 2nd route DONE <<<<<<<<<<<<<<");

        //no route or processor MBean should be created
        assertMBeans(mbeanServer);
    }

}
