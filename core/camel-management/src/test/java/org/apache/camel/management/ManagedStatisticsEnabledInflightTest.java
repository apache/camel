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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enabling or disabling statistics while an exchange is inflight must not leave the inflight count wrong.
 */
@DisabledOnOs(OS.AIX)
public class ManagedStatisticsEnabledInflightTest extends ManagementTestSupport {

    private volatile CountDownLatch latch;

    @Test
    public void testDisableWhileInflight() throws Exception {
        assertInflightAfterToggle(true, false);
    }

    @Test
    public void testEnableWhileInflight() throws Exception {
        assertInflightAfterToggle(false, true);
    }

    private void assertInflightAfterToggle(boolean before, boolean after) throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName route = getCamelObjectName(TYPE_ROUTE, "foo");
        ObjectName camelContext = getContextObjectName();

        mbeanServer.setAttribute(route, new Attribute("StatisticsEnabled", before));
        mbeanServer.setAttribute(camelContext, new Attribute("StatisticsEnabled", before));

        latch = new CountDownLatch(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.asyncSendBody("direct:start", "Hello World");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("foo") == 1);

        mbeanServer.setAttribute(route, new Attribute("StatisticsEnabled", after));
        mbeanServer.setAttribute(camelContext, new Attribute("StatisticsEnabled", after));
        latch.countDown();
        assertMockEndpointsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 0);

        assertEquals(0L, mbeanServer.getAttribute(route, "ExchangesInflight"));
        assertEquals(0L, mbeanServer.getAttribute(camelContext, "ExchangesInflight"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("foo")
                        .process(e -> latch.await(20, TimeUnit.SECONDS))
                        .to("mock:result");
            }
        };
    }
}
