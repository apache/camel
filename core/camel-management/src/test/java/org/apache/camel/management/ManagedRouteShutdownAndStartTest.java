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

import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class ManagedRouteShutdownAndStartTest extends ManagementTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/managed");
        super.setUp();
    }

    @Test
    public void testShutdownAndStartRoute() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/data/managed", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        // should be started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);

        // calling the stop and remove
        mbeanServer.invoke(on, "stop", null, null);
        mbeanServer.invoke(on, "remove", null, null);

        // the managed route object should be removed
        assertFalse("The managed route should be removed", mbeanServer.isRegistered(on));
        
        mock.reset();
        mock.expectedBodiesReceived("Bye World");
        // wait a bit while route is stopped to verify that file was not consumed
        mock.setResultWaitTime(250);

        template.sendBodyAndHeader("file://target/data/managed", "Bye World", Exchange.FILE_NAME, "bye.txt");

        // route is stopped so we do not get the file
        mock.assertIsNotSatisfied();
    }

    static ObjectName getRouteObjectName(MBeanServer mbeanServer) throws Exception {
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        return set.iterator().next();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/managed?initialDelay=0&delay=10").to("mock:result");
            }
        };
    }

}
