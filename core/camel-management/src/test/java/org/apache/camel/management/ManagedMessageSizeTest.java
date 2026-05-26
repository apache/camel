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

import java.nio.charset.StandardCharsets;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedMessageSizeTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setMessageSize(true);
        return context;
    }

    @Test
    public void testMessageSize() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        String body1 = "Hello World";
        template.sendBodyAndHeader("direct:start", body1, "myHeader", "myValue");

        String body2 = "Bye World";
        template.sendBodyAndHeader("direct:start", body2, "myHeader", "myValue");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ROUTE, "route1");

        long minBody = (Long) mbeanServer.getAttribute(on, "MinBodySize");
        long maxBody = (Long) mbeanServer.getAttribute(on, "MaxBodySize");
        long meanBody = (Long) mbeanServer.getAttribute(on, "MeanBodySize");

        long body1Size = body1.getBytes(StandardCharsets.UTF_8).length;
        long body2Size = body2.getBytes(StandardCharsets.UTF_8).length;
        long expectedMin = Math.min(body1Size, body2Size);
        long expectedMax = Math.max(body1Size, body2Size);
        long expectedMean = (body1Size + body2Size) / 2;

        assertEquals(expectedMin, minBody);
        assertEquals(expectedMax, maxBody);
        assertEquals(expectedMean, meanBody);

        long minHeaders = (Long) mbeanServer.getAttribute(on, "MinHeadersSize");
        long maxHeaders = (Long) mbeanServer.getAttribute(on, "MaxHeadersSize");
        long meanHeaders = (Long) mbeanServer.getAttribute(on, "MeanHeadersSize");

        assertTrue(minHeaders > 0, "MinHeadersSize should be positive");
        assertTrue(maxHeaders > 0, "MaxHeadersSize should be positive");
        assertTrue(meanHeaders > 0, "MeanHeadersSize should be positive");
        // both messages have the same header, so min == max
        assertEquals(minHeaders, maxHeaders);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("route1")
                        .to("mock:result");
            }
        };
    }
}
