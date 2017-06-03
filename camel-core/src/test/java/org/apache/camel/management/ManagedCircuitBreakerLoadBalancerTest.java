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

import java.io.IOException;
import java.sql.SQLException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ManagedCircuitBreakerLoadBalancerTest extends ManagementTestSupport {

    public void testManageCircuitBreakerLoadBalancer() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:foo").whenExchangeReceived(1, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new SQLException("Forced");
            }
        });

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Hello World", "Bye World");

        try {
            template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");
            fail("Should fail");
        } catch (Exception e) {
            assertIsInstanceOf(SQLException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", "123");

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
        assertEquals(1, size.intValue());

        Long half = (Long) mbeanServer.getAttribute(on, "HalfOpenAfter");
        assertEquals(5000, half.longValue());

        Integer attempts = (Integer) mbeanServer.getAttribute(on, "Threshold");
        assertEquals(2, attempts.intValue());

        String exceptions = (String) mbeanServer.getAttribute(on, "Exceptions");
        assertEquals("java.io.IOException,java.sql.SQLException", exceptions);

        String cbState = (String) mbeanServer.getAttribute(on, "CircuitBreakerState");
        assertEquals("closed", cbState);

        String dump = (String) mbeanServer.invoke(on, "dumpState", null, null);
        assertTrue(dump.startsWith("State closed, failures 0, closed since"));

        TabularData data = (TabularData) mbeanServer.invoke(on, "exceptionStatistics", null, null);
        assertNotNull(data);
        assertEquals(2, data.size());

        data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{false}, new String[]{"boolean"});
        assertNotNull(data);
        assertEquals(2, data.size());

        data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{true}, new String[]{"boolean"});
        assertNotNull(data);
        assertEquals(5, data.size());

        String json = (String) mbeanServer.invoke(on, "informationJson", null, null);
        assertNotNull(json);
        assertTrue(json.contains("\"description\": \"Balances message processing among a number of nodes"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .loadBalance().circuitBreaker(2, 5000, IOException.class, SQLException.class).id("mysend")
                        .to("mock:foo");
            }
        };
    }

}
