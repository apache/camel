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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedProducerStepIdTest extends ManagementTestSupport {

    @Test
    public void testProducerStepId() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=producers,*"), null);
        assertEquals(3, set.size());

        for (ObjectName on : set) {
            boolean registered = mbeanServer.isRegistered(on);
            assertTrue(registered, "Should be registered");

            String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
            String stepId = (String) mbeanServer.getAttribute(on, "StepId");

            if ("log://foo".equals(uri) || "mock://foo".equals(uri)) {
                assertEquals("myStep", stepId, "Producer inside step should have stepId");
            } else if ("mock://result".equals(uri)) {
                assertNull(stepId, "Producer outside step should not have stepId");
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("route1")
                        .step("myStep")
                        .to("log:foo")
                        .to("mock:foo")
                        .end()
                        .to("mock:result");
            }
        };
    }

}
