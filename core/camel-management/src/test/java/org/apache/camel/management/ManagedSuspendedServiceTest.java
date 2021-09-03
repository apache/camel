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
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedSuspendedServiceTest extends ManagementTestSupport {

    @Test
    public void testConsumeSuspendAndResumeFile() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        boolean registered = mbeanServer.isRegistered(on);
        assertTrue(registered, "Should be registered");
        Boolean ss = (Boolean) mbeanServer.getAttribute(on, "SupportSuspension");
        assertTrue(ss);
        Boolean suspended = (Boolean) mbeanServer.getAttribute(on, "Suspended");
        assertFalse(suspended);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // now its suspended by the policy
            Boolean bool = (Boolean) mbeanServer.getAttribute(on, "Suspended");
            assertTrue(bool);
        });

        // the route is suspended by the policy so we should only receive one
        String[] files = testDirectory().toFile().list();
        assertNotNull(files);
        assertEquals(1, files.length, "The file should exists");

        // reset mock
        mock.reset();
        mock.expectedMessageCount(1);

        // now resume it
        mbeanServer.invoke(on, "resume", null, null);

        assertMockEndpointsSatisfied();

        suspended = (Boolean) mbeanServer.getAttribute(on, "Suspended");
        assertFalse(suspended);

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // and the file is now deleted
            String[] names = testDirectory().toFile().list();
            assertNotNull(names);
            assertEquals(0, names.length, "The file should exists");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                MyPolicy myPolicy = new MyPolicy();

                from(fileUri("?initialDelay=0&delay=10&maxMessagesPerPoll=1&delete=true"))
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
