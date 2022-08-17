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
package org.apache.camel.component.jms;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class ManagedJmsEndpointTest extends AbstractPersistentJMSTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testJmsEndpoint() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> objectNames = mbeanServer.queryNames(
                new ObjectName(
                        "org.apache.camel:context=camel-*,type=endpoints,name=\"activemq://queue:ManagedJmsEndpointTest\""),
                null);
        assertEquals(1, objectNames.size());
        ObjectName name = objectNames.iterator().next();

        String uri = (String) mbeanServer.getAttribute(name, "EndpointUri");
        assertEquals("activemq://queue:ManagedJmsEndpointTest", uri);

        Boolean singleton = (Boolean) mbeanServer.getAttribute(name, "Singleton");
        assertTrue(singleton);

        Integer running = (Integer) mbeanServer.getAttribute(name, "RunningMessageListeners");
        assertEquals(1, running.intValue());

        Long size = (Long) mbeanServer.invoke(name, "queueSize", null, null);
        assertEquals(0, size.intValue());

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("activemq:queue:ManagedJmsEndpointTest", "Hello World");
        template.sendBody("activemq:queue:ManagedJmsEndpointTest", "Bye World");

        assertMockEndpointsSatisfied();

        // stop route
        context.getRouteController().stopRoute("foo");

        // send a message to queue
        template.sendBody("activemq:queue:ManagedJmsEndpointTest", "Hi World");

        size = (Long) mbeanServer.invoke(name, "queueSize", null, null);
        assertEquals(1, size.intValue());

        String body = (String) mbeanServer.invoke(name, "browseMessageBody", new Object[] { 0 },
                new String[] { "java.lang.Integer" });
        assertEquals("Hi World", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:ManagedJmsEndpointTest").routeId("foo").to("log:foo").to("mock:result");
            }
        };
    }

}
