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
package org.apache.camel.spring.management;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ServiceStatus;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ManagedEndpointInjectRefEndpointTest extends SpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/management/ManagedEndpointInjectRefEndpointTest.xml");
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testRef() throws Exception {
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

        for (ObjectName on : set) {
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

        for (ObjectName on : set) {
            boolean registered = mbeanServer.isRegistered(on);
            assertEquals("Should be registered", true, registered);

            String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
            assertTrue(uri, uri.equals("direct://start") || uri.equals("mock://foo") || uri.equals("mock://result") || uri.equals("ref://foo"));
        }
    }

}
