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

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedStatisticsLevelRoutesOnlyTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // only routes
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.RoutesOnly);
        return context;
    }

    @Test
    public void testManageStatisticsRoutesOnly() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals(1, set.size());

        ObjectName on2 = set.iterator().next();

        // route should have stats enabled
        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(2, completed.longValue());

        // but not processor
        Long completed2 = (Long) mbeanServer.getAttribute(on2, "ExchangesCompleted");
        assertEquals(0, completed2.longValue());

        // send in another message
        template.sendBody("direct:start", "Goodday World");

        // should be 3
        completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(3, completed.longValue());

        completed2 = (Long) mbeanServer.getAttribute(on2, "ExchangesCompleted");
        assertEquals(0, completed2.longValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }

}
