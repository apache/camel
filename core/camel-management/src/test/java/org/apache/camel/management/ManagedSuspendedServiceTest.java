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

import java.io.File;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.Before;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

public class ManagedSuspendedServiceTest extends ManagementTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/suspended");
        super.setUp();
    }

    @Test
    public void testConsumeSuspendAndResumeFile() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        boolean registered = mbeanServer.isRegistered(on);
        assertEquals("Should be registered", true, registered);
        Boolean ss = (Boolean) mbeanServer.getAttribute(on, "SupportSuspension");
        assertEquals(true, ss.booleanValue());
        Boolean suspended = (Boolean) mbeanServer.getAttribute(on, "Suspended");
        assertEquals(false, suspended.booleanValue());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("file://target/data/suspended", "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader("file://target/data/suspended", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // now its suspended by the policy
            Boolean bool = (Boolean) mbeanServer.getAttribute(on, "Suspended");
            assertEquals(true, bool.booleanValue());
        });

        // the route is suspended by the policy so we should only receive one
        String[] files = new File("target/data/suspended/").list();
        assertNotNull(files);
        assertEquals("The file should exists", 1, files.length);

        // reset mock
        mock.reset();
        mock.expectedMessageCount(1);

        // now resume it
        mbeanServer.invoke(on, "resume", null, null);

        assertMockEndpointsSatisfied();

        suspended = (Boolean) mbeanServer.getAttribute(on, "Suspended");
        assertEquals(false, suspended.booleanValue());

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // and the file is now deleted
            String[] names = new File("target/data/suspended/").list();
            assertNotNull(names);
            assertEquals("The file should exists", 0, names.length);
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                MyPolicy myPolicy = new MyPolicy();

                from("file://target/data/suspended?initialDelay=0&delay=10&maxMessagesPerPoll=1&delete=true")
                    .routePolicy(myPolicy).id("myRoute")
                    .to("mock:result");
            }
        };
    }

    private static class MyPolicy extends RoutePolicySupport {

        private int counter;

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            // only stop it at first run
            if (counter++ == 0) {
                try {
                    super.suspendOrStopConsumer(route.getConsumer());
                } catch (Exception e) {
                    handleException(e);
                }
            }
        }

    }

}
