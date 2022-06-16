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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedSanitizeTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setMask(true);
        return context;
    }

    @Test
    public void testSanitize() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "stub://foo\\?password=xxxxxx&username=xxxxxx");
        assertTrue(mbeanServer.isRegistered(name), "Should be registered");
        String uri = (String) mbeanServer.getAttribute(name, "EndpointUri");
        assertEquals("stub://foo?password=xxxxxx&username=xxxxxx", uri);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                        .to("stub:foo?username=foo&password=secret")
                        .to("mock:result");

                from("stub:foo?username=foo&password=secret").routeId("stub")
                        .to("mock:stub");
            }
        };
    }

}
