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

import javax.jms.ConnectionFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test that all thread pools is removed when adding and removing a route dynamically
 */
public class JmsAddAndRemoveRouteManagementTest extends AbstractJMSTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testAddAndRemoveRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> before = mbeanServer.queryNames(new ObjectName("*:type=threadpools,*"), null);

        getMockEndpoint("mock:result").expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsAddAndRemoveRouteManagementTest.in").routeId("myNewRoute")
                        .to("activemq:queue:JmsAddAndRemoveRouteManagementTest.foo");
            }
        });

        Set<ObjectName> during = mbeanServer.queryNames(new ObjectName("*:type=threadpools,*"), null);
        assertEquals(before.size() + 1, during.size(), "There should be one more thread pool in JMX");

        template.sendBody("activemq:queue:JmsAddAndRemoveRouteManagementTest.in", "Hello World");

        assertMockEndpointsSatisfied();

        // now stop and remove that route
        context.getRouteController().stopRoute("myNewRoute");
        context.removeRoute("myNewRoute");

        Set<ObjectName> after = mbeanServer.queryNames(new ObjectName("*:type=threadpools,*"), null);
        assertEquals(before.size(), after.size(), "Should have removed all thread pools from removed route");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsAddAndRemoveRouteManagementTest.foo").to("mock:result");
            }
        };
    }
}
