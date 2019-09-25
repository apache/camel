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

import java.io.IOException;
import java.sql.SQLException;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ManagedFailoverLoadBalancerTest extends ManagementTestSupport {

    @Test
    public void testManageFailoverLoadBalancer() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:foo").whenAnyExchangeReceived(exchange -> {
            throw new IOException("Forced");
        });

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // get the object name for the delayer
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"mysend\"");

        // should be on route1
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("route1", routeId);

        String camelId = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("camel-1", camelId);

        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);

        Integer size = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(2, size.intValue());

        Boolean roundRobin = (Boolean) mbeanServer.getAttribute(on, "RoundRobin");
        assertEquals(true, roundRobin.booleanValue());

        Boolean sticky = (Boolean) mbeanServer.getAttribute(on, "Sticky");
        assertEquals(true, sticky.booleanValue());

        Integer attempts = (Integer) mbeanServer.getAttribute(on, "MaximumFailoverAttempts");
        assertEquals(3, attempts.intValue());

        String exceptions = (String) mbeanServer.getAttribute(on, "Exceptions");
        assertEquals("java.io.IOException,java.sql.SQLException", exceptions);

        String id = (String) mbeanServer.getAttribute(on, "LastGoodProcessorId");
        assertEquals("bar", id);

        TabularData data = (TabularData) mbeanServer.invoke(on, "exceptionStatistics", null, null);
        assertNotNull(data);
        assertEquals(2, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .loadBalance().failover(3, false, true, true, IOException.class, SQLException.class).id("mysend")
                        .to("mock:foo").id("foo").to("mock:bar").id("bar");
            }
        };
    }

}
