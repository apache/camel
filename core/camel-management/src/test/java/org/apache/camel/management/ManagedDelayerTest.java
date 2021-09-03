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

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedDelayerTest extends ManagementTestSupport {

    @Test
    public void testManageDelay() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // get the object name for the delayer
        ObjectName delayerName = getCamelObjectName(TYPE_PROCESSOR, "mydelayer");

        // use route to get the total time
        ObjectName routeName = getCamelObjectName(TYPE_ROUTE, "route1");
        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(1, completed.longValue());

        Long last = (Long) mbeanServer.getAttribute(routeName, "LastProcessingTime");
        Long total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        assertTrue(last > 90, "Should take around 0.1 sec: was " + last);
        assertTrue(total > 90, "Should take around 0.1 sec: was " + total);

        // change the delay time using JMX
        mbeanServer.invoke(delayerName, "constantDelay", new Object[] { 200 }, new String[] { "java.lang.Integer" });

        // send in another message
        template.sendBody("direct:start", "Bye World");

        Long delay = (Long) mbeanServer.getAttribute(delayerName, "Delay");
        assertNotNull(delay);

        completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(2, completed.longValue());
        last = (Long) mbeanServer.getAttribute(routeName, "LastProcessingTime");
        total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        assertTrue(last > 190, "Should take around 0.2 sec: was " + last);
        assertTrue(total > 290, "Should be around 0.3 sec now: was " + total);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("log:foo")
                        .delay(100).id("mydelayer")
                        .to("mock:result");
            }
        };
    }

}
