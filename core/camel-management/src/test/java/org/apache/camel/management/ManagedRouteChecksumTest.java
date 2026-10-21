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

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25771: a managed route has the checksum of the route as written, and its revision in this run.
 */
@DisabledOnOs(OS.AIX)
public class ManagedRouteChecksumTest extends ManagementTestSupport {

    @Test
    public void testChecksumAndRevision() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ROUTE, "myRoute");

        String checksum = (String) mbeanServer.getAttribute(on, "Checksum");
        assertNotNull(checksum);
        assertTrue(checksum.matches("[0-9a-f]{12}"), checksum);
        assertEquals(context.getRoute("myRoute").getChecksum(), checksum);
        assertEquals(1, mbeanServer.getAttribute(on, "Revision"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute").to("mock:result");
            }
        };
    }
}
