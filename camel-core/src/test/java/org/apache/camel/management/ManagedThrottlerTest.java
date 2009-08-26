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

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class ManagedThrottlerTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DefaultManagementNamingStrategy naming = (DefaultManagementNamingStrategy) context.getManagementStrategy().getManagementNamingStrategy();
        naming.setHostName("localhost");
        naming.setDomainName("org.apache.camel");
        return context;
    }

    @SuppressWarnings("unchecked")
    public void testManageThrottler() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(10);

        // send in 10 messages
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        // get the object name for the delayer
        ObjectName throttlerName = ObjectName.getInstance("org.apache.camel:context=localhost/camel-1,type=processors,name=\"mythrottler\"");

        // use route to get the total time
        ObjectName routeName = ObjectName.getInstance("org.apache.camel:context=localhost/camel-1,type=routes,name=\"route1\"");
        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());

        Long last = (Long) mbeanServer.getAttribute(routeName, "LastProcessingTime");
        Long total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        assertTrue("Should take at most 1.5 sec: was " + total, total < 1500);

        // change the throttler using JMX
        mbeanServer.setAttribute(throttlerName, new Attribute("MaximumRequestsPerPeriod", (long) 2));

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        // send in another 10 messages
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        Long period = (Long) mbeanServer.getAttribute(throttlerName, "MaximumRequestsPerPeriod");
        assertNotNull(period);
        assertEquals(2, period.longValue());

        completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());
        total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        assertTrue("Should be around 5 sec now: was " + total, total > 4500);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("log:foo")
                    .throttle(10).id("mythrottler")
                    .to("mock:result");
            }
        };
    }

}
