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

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endpoint registry holds an intercepting endpoint that wraps the endpoint whose MBean was registered, and removing
 * another intercepted endpoint with the same masked name should not unregister that MBean.
 */
@DisabledOnOs(OS.AIX)
class ManagedRemoveInterceptedEndpointTest extends ManagementTestSupport {

    @Test
    void testRemoveInterceptedEndpointWithMaskedSecret() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ENDPOINT, "stub://bar\\?password=xxxxxx");
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        context.getRouteController().stopRoute("c");
        context.removeRoute("c");
        assertTrue(mbeanServer.isRegistered(on), "The MBean of the endpoint of route b should still be registered");

        context.getRouteController().stopRoute("b");
        context.removeRoute("b");
        assertFalse(mbeanServer.isRegistered(on), "Should no longer be registered");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("stub:*").to("mock:intercepted");

                from("direct:b").routeId("b").to("stub:bar?password=b");
                from("direct:c").routeId("c").to("stub:bar?password=c");
            }
        };
    }
}
