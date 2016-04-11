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
import javax.management.openmbean.TabularData;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.loadbalancer.LoadBalancerSupport;

/**
 * @version 
 */
public class ManagedCustomLoadBalancerTest extends ManagementTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBalancer", new MyLoadBalancer());
        return jndi;
    }

    public void testManageCustomLoadBalancer() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

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

        String ref = (String) mbeanServer.getAttribute(on, "Ref");
        assertEquals("myBalancer", ref);

        String name = (String) mbeanServer.getAttribute(on, "LoadBalancerClassName");
        assertEquals(MyLoadBalancer.class.getName(), name);

        TabularData data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{false}, new String[]{"boolean"});
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
                    .loadBalance().custom("myBalancer").id("mysend")
                        .to("mock:foo", "mock:bar");
            }
        };
    }

    public static class MyLoadBalancer extends LoadBalancerSupport {

        public boolean process(Exchange exchange, AsyncCallback callback) {
            String body = exchange.getIn().getBody(String.class);
            try {
                if ("x".equals(body)) {
                    getProcessors().get(0).process(exchange);
                } else if ("y".equals(body)) {
                    getProcessors().get(1).process(exchange);
                } else {
                    getProcessors().get(2).process(exchange);
                }
            } catch (Throwable e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }
    }

}
