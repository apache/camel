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

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class BacklogTracerMessageHistoryBreadcrumbTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testMessageHistoryCorrelatesBySedaBreadcrumb() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        // disable remove-on-dump so polling in await doesn't drain the queue
        mbeanServer.setAttribute(on, new javax.management.Attribute("RemoveOnDump", Boolean.FALSE));

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // wait for the history to be completed (the seda consumer runs async)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<BacklogTracerEventMessage> events
                    = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpLatestMessageHistory", null, null);
            assertNotNull(events);

            // verify events span multiple routes (connected via seda which creates independent exchanges)
            Set<String> routeIds = events.stream()
                    .map(BacklogTracerEventMessage::getRouteId)
                    .collect(Collectors.toSet());
            assertTrue(routeIds.contains("starter"), "Should contain starter route events");
            assertTrue(routeIds.contains("processor"), "Should contain processor route events");

            // all events should share the same breadcrumb ID
            String breadcrumb = events.get(0).getBreadcrumbId();
            assertNotNull(breadcrumb, "Breadcrumb ID should be set");
            for (BacklogTracerEventMessage event : events) {
                assertEquals(breadcrumb, event.getBreadcrumbId(),
                        "All events should share the same breadcrumb ID");
            }

            // verify events span multiple exchange IDs (seda creates new exchanges)
            Set<String> exchangeIds = events.stream()
                    .map(BacklogTracerEventMessage::getExchangeId)
                    .collect(Collectors.toSet());
            assertTrue(exchangeIds.size() > 1,
                    "Should have events from multiple exchange IDs (seda creates new exchanges)");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(true);
                context.setBacklogTracing(true);
                context.setMessageHistory(true);

                from("direct:start").routeId("starter")
                        .to("mock:a").id("a")
                        .to("seda:next");

                from("seda:next").routeId("processor")
                        .to("mock:b").id("b")
                        .to("mock:result").id("result");
            }
        };
    }

}
