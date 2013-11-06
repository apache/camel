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

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ManagedStatisticsTest extends ManagementTestSupport {

    public void testManageStatistics() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        // use route to get the total time
        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(0, completed.longValue());
        
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(5);

        // send in 5 messages
        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");
        template.sendBody("direct:start", "D");
        template.sendBody("direct:start", "E");

        assertMockEndpointsSatisfied();
        
        // should be 5 on the route
        completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(5, completed.longValue());
        
        String first = (String) mbeanServer.getAttribute(on, "FirstExchangeCompletedExchangeId");
        assertEquals(result.getReceivedExchanges().get(0).getExchangeId(), first);

        String firstFail = (String) mbeanServer.getAttribute(on, "FirstExchangeFailureExchangeId");
        assertNull(firstFail);

        String last = (String) mbeanServer.getAttribute(on, "LastExchangeCompletedExchangeId");
        assertEquals(result.getReceivedExchanges().get(4).getExchangeId(), last);

        String lastFail = (String) mbeanServer.getAttribute(on, "LastExchangeFailureExchangeId");
        assertNull(lastFail);

        // should be 5 on the processors
        ObjectName foo = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"foo\"");
        completed = (Long) mbeanServer.getAttribute(foo, "ExchangesCompleted");
        assertEquals(5, completed.longValue());

        ObjectName mock = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"mock\"");
        completed = (Long) mbeanServer.getAttribute(mock, "ExchangesCompleted");
        assertEquals(5, completed.longValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("log:foo").id("foo")
                    .to("log:bar").id("bar")
                    .to("mock:result").id("mock");
            }
        };
    }

}