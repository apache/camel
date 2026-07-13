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
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedPercentileStatisticsTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return context;
    }

    @Test
    public void testPercentileStatsOnRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ROUTE, context.getRoutes().get(0).getRouteId());

        getMockEndpoint("mock:result").expectedMessageCount(5);

        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        assertMockEndpointsSatisfied();

        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(5, completed.longValue());

        Long p50 = (Long) mbeanServer.getAttribute(on, "ProcessingTimeP50");
        Long p95 = (Long) mbeanServer.getAttribute(on, "ProcessingTimeP95");
        Long p99 = (Long) mbeanServer.getAttribute(on, "ProcessingTimeP99");
        Long rate = (Long) mbeanServer.getAttribute(on, "ExchangeRate1m");

        assertTrue(p50 >= 0, "p50 should be >= 0, was: " + p50);
        assertTrue(p95 >= 0, "p95 should be >= 0, was: " + p95);
        assertTrue(p99 >= 0, "p99 should be >= 0, was: " + p99);
        assertTrue(p50 <= p95, "p50 should be <= p95, was p50=" + p50 + " p95=" + p95);
        assertTrue(p95 <= p99, "p95 should be <= p99, was p95=" + p95 + " p99=" + p99);
        assertEquals(5, rate.longValue());
    }

    @Test
    public void testPercentileStatsOnContext() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getContextObjectName();

        getMockEndpoint("mock:result").expectedMessageCount(3);

        for (int i = 0; i < 3; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        assertMockEndpointsSatisfied();

        Long p50 = (Long) mbeanServer.getAttribute(on, "ProcessingTimeP50");
        Long p95 = (Long) mbeanServer.getAttribute(on, "ProcessingTimeP95");
        Long p99 = (Long) mbeanServer.getAttribute(on, "ProcessingTimeP99");
        Long rate = (Long) mbeanServer.getAttribute(on, "ExchangeRate1m");

        assertTrue(p50 >= 0, "p50 should be >= 0, was: " + p50);
        assertTrue(p95 >= 0, "p95 should be >= 0, was: " + p95);
        assertTrue(p99 >= 0, "p99 should be >= 0, was: " + p99);
        assertEquals(3, rate.longValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }
}
