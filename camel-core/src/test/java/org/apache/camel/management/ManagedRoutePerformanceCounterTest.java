/**
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

import static org.awaitility.Awaitility.await;

/**
 * @version 
 */
public class ManagedRoutePerformanceCounterTest extends ManagementTestSupport {

    public void testPerformanceCounterStats() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route1\"");

        Long delta = (Long) mbeanServer.getAttribute(on, "DeltaProcessingTime");
        assertEquals(0, delta.intValue());

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.asyncSendBody("direct:start", "Hello World");

        // cater for slow boxes
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Integer num = (Integer) mbeanServer.getAttribute(on, "InflightExchanges");
            return num == 1;
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
        assertTrue("Should take around 1 sec: was " + last, last > 900);
        assertTrue("Should take around 1 sec: was " + total, total > 900);

        // send in another message
        template.sendBody("direct:start", "Bye World");

        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(2, completed.longValue());
        delta = (Long) mbeanServer.getAttribute(on, "DeltaProcessingTime");
        last = (Long) mbeanServer.getAttribute(on, "LastProcessingTime");
        total = (Long) mbeanServer.getAttribute(on, "TotalProcessingTime");

        assertNotNull(delta);
        assertTrue("Should take around 1 sec: was " + last, last > 900);
        assertTrue("Should be around 2 sec now: was " + total, total > 1900);

        Date reset = (Date) mbeanServer.getAttribute(on, "ResetTimestamp");
        assertNotNull(reset);

        Date lastFailed = (Date) mbeanServer.getAttribute(on, "LastExchangeFailureTimestamp");
        Date firstFailed = (Date) mbeanServer.getAttribute(on, "FirstExchangeFailureTimestamp");
        assertNull(lastFailed);
        assertNull(firstFailed);

        Integer inFlight = (Integer) mbeanServer.getAttribute(on, "InflightExchanges");
        assertEquals(0, inFlight.longValue());
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
