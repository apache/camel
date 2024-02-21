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

import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedRoutePerformanceCounterTest extends ManagementTestSupport {

    @Test
    public void testPerformanceCounterStats() throws Exception {
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ROUTE, context.getRoutes().get(0).getRouteId());

        Long delta = (Long) mbeanServer.getAttribute(on, "DeltaProcessingTime");
        assertEquals(0, delta.intValue());

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.asyncSendBody("direct:start", "Hello World");

        // cater for slow boxes
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Long num = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
            return num == 1L;
        });

        assertMockEndpointsSatisfied();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
            assertEquals(1, completed.longValue());
        });

        delta = (Long) mbeanServer.getAttribute(on, "DeltaProcessingTime");
        Long last = (Long) mbeanServer.getAttribute(on, "LastProcessingTime");
        Long total = (Long) mbeanServer.getAttribute(on, "TotalProcessingTime");

        assertNotNull(delta);
        assertTrue(last > 900, "Should take around 1 sec: was " + last);
        assertTrue(total > 900, "Should take around 1 sec: was " + total);

        // send in another message
        template.sendBody("direct:start", "Bye World");

        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(2, completed.longValue());
        delta = (Long) mbeanServer.getAttribute(on, "DeltaProcessingTime");
        last = (Long) mbeanServer.getAttribute(on, "LastProcessingTime");
        total = (Long) mbeanServer.getAttribute(on, "TotalProcessingTime");

        assertNotNull(delta);
        assertTrue(last > 900, "Should take around 1 sec: was " + last);
        assertTrue(total > 1900, "Should be around 2 sec now: was " + total);

        Date reset = (Date) mbeanServer.getAttribute(on, "ResetTimestamp");
        assertNotNull(reset);

        Date lastFailed = (Date) mbeanServer.getAttribute(on, "LastExchangeFailureTimestamp");
        Date firstFailed = (Date) mbeanServer.getAttribute(on, "FirstExchangeFailureTimestamp");
        assertNull(lastFailed);
        assertNull(firstFailed);

        Long inFlight = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
        assertEquals(0L, inFlight.longValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").delay(1000).to("mock:result");
            }
        };
    }

}
