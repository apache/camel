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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spi.Registry;
import org.junit.Test;

/**
 * @version
 */
public class ManagedRefProducerTest extends ManagementTestSupport {

    private Map registry = new SimpleRegistry();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext((Registry) registry);
        registry.put("foo", new MockEndpoint("mock://foo"));
        return context;
    }

    @Test
    public void testProducer() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // fire a message to get it running
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("foo").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=producers,*"), null);
        assertEquals(2, set.size());
        Iterator<ObjectName> it = set.iterator();

        for (int i = 0; i < 2; i++) {
            ObjectName on = it.next();

            boolean registered = mbeanServer.isRegistered(on);
            assertEquals("Should be registered", true, registered);

            String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
            assertTrue(uri, uri.equals("mock://foo") || uri.equals("mock://result"));

            // should be started
            String state = (String) mbeanServer.getAttribute(on, "State");
            assertEquals("Should be started", ServiceStatus.Started.name(), state);
        }

        set = mbeanServer.queryNames(new ObjectName("*:type=endpoints,*"), null);
        assertEquals(4, set.size());
        it = set.iterator();

        for (int i = 0; i < 4; i++) {
            ObjectName on = it.next();

            boolean registered = mbeanServer.isRegistered(on);
            assertEquals("Should be registered", true, registered);

            String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
            assertTrue(uri, uri.equals("direct://start") || uri.equals("ref://foo") || uri.equals("mock://foo") || uri.equals("mock://result"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                        .to("ref:foo").to("mock:result");
            }
        };
    }

}