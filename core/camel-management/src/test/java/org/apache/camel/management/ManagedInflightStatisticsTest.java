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

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

public class ManagedInflightStatisticsTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getInflightRepository().setInflightBrowseEnabled(true);
        return context;
    }

    @Test
    public void testOldestInflight() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();

        Long inflight = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
        assertEquals(0, inflight.longValue());
        Long ts = (Long) mbeanServer.getAttribute(on, "OldestInflightDuration");
        assertNull(ts);
        String id = (String) mbeanServer.getAttribute(on, "OldestInflightExchangeId");
        assertNull(id);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        // start some exchanges.
        template.asyncSendBody("direct:start", latch1);
        Thread.sleep(250);
        template.asyncSendBody("direct:start", latch2);

        await().atMost(2, TimeUnit.SECONDS).until(() -> {
            Long num = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
            return num != null && num == 2;
        });

        inflight = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
        assertEquals(2, inflight.longValue());

        ts = (Long) mbeanServer.getAttribute(on, "OldestInflightDuration");
        assertNotNull(ts);
        id = (String) mbeanServer.getAttribute(on, "OldestInflightExchangeId");
        assertNotNull(id);

        log.info("Oldest Exchange id: {}, duration: {}", id, ts);

        // complete first exchange
        latch1.countDown();

        // Lets wait for the first exchange to complete.
        Thread.sleep(200);
        Long ts2 = (Long) mbeanServer.getAttribute(on, "OldestInflightDuration");
        assertNotNull(ts2);
        String id2 = (String) mbeanServer.getAttribute(on, "OldestInflightExchangeId");
        assertNotNull(id2);

        log.info("Oldest Exchange id: {}, duration: {}", id2, ts2);

        // Lets verify the oldest changed.
        assertTrue(!id2.equals(id));
        // The duration values could be different
        assertTrue(!Objects.equals(ts2, ts));

        latch2.countDown();

        // Lets wait for all the exchanges to complete.
        await().atMost(2, TimeUnit.SECONDS).until(() -> {
            Long num = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
            return num != null && num == 0;
        });

        assertMockEndpointsSatisfied();

        inflight = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
        assertEquals(0, inflight.longValue());
        ts = (Long) mbeanServer.getAttribute(on, "OldestInflightDuration");
        assertNull(ts);
        id = (String) mbeanServer.getAttribute(on, "OldestInflightExchangeId");
        assertNull(id);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .process(exchange -> {
                            CountDownLatch latch = (CountDownLatch) exchange.getIn().getBody();
                            latch.await(10, TimeUnit.SECONDS);
                        })
                        .to("mock:result").id("mock");
            }
        };
    }

}
