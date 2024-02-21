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

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Extended test to see if mbeans is removed and stats are correct
 */
@DisabledOnOs(OS.AIX)
public class ManagedRouteNoAutoStartupTest extends ManagementTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").noAutoStartup()
                        .to("mock:result");
            }
        };
    }

    static ObjectName getRouteObjectName(MBeanServer mbeanServer) throws Exception {
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        return set.iterator().next();
    }

    @Test
    public void testRouteNoAutoStartup() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        // should be stopped
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Stopped.name(), state, "Should be stopped");

        // start
        mbeanServer.invoke(on, "start", null, null);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // need a bit time to let JMX update
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            // should have 1 completed exchange
            Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
            assertEquals(1, completed.longValue());
        });

        // should be 1 consumer and 1 processor
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals(1, set.size(), "Should be 1 consumer");

        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals(1, set.size(), "Should be 1 processor");

        // stop
        mbeanServer.invoke(on, "stop", null, null);

        state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Stopped.name(), state, "Should be stopped");

        // should be 0 consumer and 0 processor
        set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals(0, set.size(), "Should be 0 consumer");

        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals(0, set.size(), "Should be 0 processor");
    }

}
