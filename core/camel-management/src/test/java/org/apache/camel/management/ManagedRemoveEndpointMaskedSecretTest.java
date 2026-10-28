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

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Endpoints that only differ in a secret get the same (masked) MBean name, and removing one of them should not
 * unregister the MBean of the other.
 */
@DisabledOnOs(OS.AIX)
class ManagedRemoveEndpointMaskedSecretTest extends ManagementTestSupport {

    @Test
    void testRemoveEndpointCreatedAtRuntime() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ENDPOINT, "stub://foo\\?password=xxxxxx");
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        // not registered as it is created after CamelContext has been started
        Endpoint other = context.getEndpoint("stub:foo?password=other");
        context.removeEndpoint(other);
        assertTrue(mbeanServer.isRegistered(on), "The MBean of the endpoint of route a should still be registered");

        context.getRouteController().stopRoute("a");
        context.removeRoute("a");
        context.removeEndpoint(context.hasEndpoint("stub:foo?password=a"));
        assertFalse(mbeanServer.isRegistered(on), "Should no longer be registered");
    }

    @Test
    void testRemoveEndpointOfOtherRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ENDPOINT, "stub://foo\\?password=xxxxxx");
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        context.getRouteController().stopRoute("b");
        context.removeRoute("b");
        context.removeEndpoint(context.hasEndpoint("stub:foo?password=b"));
        assertTrue(mbeanServer.isRegistered(on), "The MBean of the endpoint of route a should still be registered");

        context.getRouteController().stopRoute("a");
        context.removeRoute("a");
        context.removeEndpoint(context.hasEndpoint("stub:foo?password=a"));
        assertFalse(mbeanServer.isRegistered(on), "Should no longer be registered");
    }

    @Test
    void testRemoveEndpointThatReplacedTheOwner() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ENDPOINT, "stub://foo\\?password=xxxxxx");
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        // replaces the endpoint of route a in the registry (the replaced endpoint is not removed)
        Endpoint replacement = context.getComponent("stub").createEndpoint("stub://foo?password=a");
        context.addEndpoint("stub:foo?password=a", replacement);
        context.removeEndpoint(replacement);
        assertFalse(mbeanServer.isRegistered(on), "Should no longer be registered");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("stub:foo?password=a").routeId("a").to("mock:a");
                from("stub:foo?password=b").routeId("b").to("mock:b");
            }
        };
    }
}
