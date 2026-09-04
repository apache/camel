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

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteGroupMBean;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that a route group's performance counters aggregate handled failures recorded on any of its member routes
 * (CAMEL-24590).
 */
@DisabledOnOs(OS.AIX)
public class ManagedRouteGroupFailuresHandledTest extends ManagementTestSupport {

    @Test
    public void testGroupAggregatesFailuresHandled() throws Exception {
        // the trigger route throws an exception that is handled and then hops to sibling routes in the same group
        template.sendBody("direct:trigger", "Hello World");

        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        ManagedRouteGroupMBean group = mcc.getManagedRouteGroup("flow");
        assertNotNull(group);

        // group stats must aggregate across all member routes: trigger, step1 and step2 each completed once
        assertEquals(3, group.getExchangesCompleted());

        // the handled failure recorded on the trigger route must be reflected at the group level
        assertEquals(1, group.getFailuresHandled());
        assertNotNull(group.getLastExchangeFailureHandledTimestamp(),
                "Group should report the last handled-failure timestamp of its member route");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .maximumRedeliveries(0)
                        .handled(true)
                        .to("direct:step1");

                from("direct:step1")
                        .routeGroup("flow")
                        .routeId("step1")
                        .setBody(constant("123"))
                        .to("direct:step2");

                from("direct:step2")
                        .routeGroup("flow")
                        .routeId("step2")
                        .setBody(constant("456"));

                from("direct:trigger")
                        .routeGroup("flow")
                        .routeId("trigger")
                        .throwException(new RuntimeException("Test failure"));
            }
        };
    }

}
