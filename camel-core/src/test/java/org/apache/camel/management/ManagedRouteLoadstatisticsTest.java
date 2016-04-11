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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedRouteLoadstatisticsTest extends ManagementTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testLoadStatisticsAreDisabledByDefault() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").delay(2000).to("mock:result");
            }
        });
        context.start();

        boolean load = context.getManagementStrategy().getManagementAgent().getLoadStatisticsEnabled() != null
                && context.getManagementStrategy().getManagementAgent().getLoadStatisticsEnabled();
        assertFalse(load);
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route1\"");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.asyncSendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        String load01 = (String)mbeanServer.getAttribute(on, "Load01");
        String load05 = (String)mbeanServer.getAttribute(on, "Load05");
        String load15 = (String)mbeanServer.getAttribute(on, "Load15");
        assertEquals("", load01);
        assertEquals("", load05);
        assertEquals("", load15);
    }

    public void testEnableLoadStatistics() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        context.getManagementStrategy().getManagementAgent().setLoadStatisticsEnabled(true);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").delay(2000).to("mock:result");
            }
        });
        context.start();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route1\"");

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.asyncSendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        Thread.sleep(2000);
        String load01 = (String)mbeanServer.getAttribute(on, "Load01");
        String load05 = (String)mbeanServer.getAttribute(on, "Load05");
        String load15 = (String)mbeanServer.getAttribute(on, "Load15");
        assertNotNull(load01);
        assertNotNull(load05);
        assertNotNull(load15);
        assertTrue(Double.parseDouble(load01.replace(',', '.')) >= 0);
        assertTrue(Double.parseDouble(load05.replace(',', '.')) >= 0);
        assertTrue(Double.parseDouble(load15.replace(',', '.')) >= 0);
    }

}
