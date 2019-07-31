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
import org.junit.Test;

public class ManagedLogEndpointTest extends ManagementTestSupport {

    @Test
    public void testLogEndpoint() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:a").expectedMessageCount(10);

        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "" + i);
            Thread.sleep(100);
        }

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"log-foo\"");
        mbeanServer.isRegistered(name);
        
        Long total = (Long) mbeanServer.getAttribute(name, "ExchangesTotal");
        assertEquals(10, total.intValue());

        Integer received = (Integer) mbeanServer.getAttribute(name, "ReceivedCounter");
        assertEquals(10, received.intValue());

        String last = (String) mbeanServer.getAttribute(name, "LastLogMessage");
        assertNotNull(last);
        assertTrue(last.startsWith("Received: 10 messages so far."));

        Double rate = (Double) mbeanServer.getAttribute(name, "Rate");
        assertNotNull(rate);
        assertTrue(rate > 0);

        Double average = (Double) mbeanServer.getAttribute(name, "Average");
        assertNotNull(average);
        assertTrue(average > 0);

        // reset
        mbeanServer.invoke(name, "resetThroughputLogger", null, null);

        // total not reset
        total = (Long) mbeanServer.getAttribute(name, "ExchangesTotal");
        assertEquals(10, total.intValue());

        // but the last log message is
        last = (String) mbeanServer.getAttribute(name, "LastLogMessage");
        assertNull(last);

        received = (Integer) mbeanServer.getAttribute(name, "ReceivedCounter");
        assertEquals(0, received.intValue());

        rate = (Double) mbeanServer.getAttribute(name, "Rate");
        assertEquals((Double) 0.0d, rate);

        average = (Double) mbeanServer.getAttribute(name, "Average");
        assertEquals((Double) 0.0d, average);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                    .to("log:foo?groupSize=10").id("log-foo")
                    .to("mock:a");
            }
        };
    }

}
