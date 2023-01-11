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

import jakarta.jms.ConnectionFactory;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.activemq.services.LegacyEmbeddedBroker;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
public class ManagedJmsSelectorTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected CamelContext createCamelContext() {
        CamelContext context = new DefaultCamelContext();

        final String brokerUrl = LegacyEmbeddedBroker.createBrokerUrl();
        final ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(brokerUrl, null);

        context.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return context;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testJmsSelectorChangeViaJmx() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        String selector = (String) mbeanServer.getAttribute(on, "MessageSelector");
        assertEquals("brand='beer'", selector);

        getMockEndpoint("mock:result").expectedBodiesReceived("Carlsberg");

        template.sendBodyAndHeader("activemq:queue:startManagedJmsSelectorTest", "Pepsi", "brand", "softdrink");
        template.sendBodyAndHeader("activemq:queue:startManagedJmsSelectorTest", "Carlsberg", "brand", "beer");

        MockEndpoint.assertIsSatisfied(context);

        // change the selector at runtime

        MockEndpoint.resetMocks(context);

        mbeanServer.setAttribute(on, new Attribute("MessageSelector", "brand='softdrink'"));

        // give it a little time to adjust
        Thread.sleep(100);

        getMockEndpoint("mock:result").expectedBodiesReceived("Pepsi");

        template.sendBodyAndHeader("activemq:queue:startManagedJmsSelectorTest", "Pepsi", "brand", "softdrink");
        template.sendBodyAndHeader("activemq:queue:startManagedJmsSelectorTest", "Carlsberg", "brand", "beer");

        MockEndpoint.assertIsSatisfied(context);

        selector = (String) mbeanServer.getAttribute(on, "MessageSelector");
        assertEquals("brand='softdrink'", selector);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:startManagedJmsSelectorTest?cacheLevelName=CACHE_NONE&selector=brand='beer'")
                        .routeId("foo").to("log:foo")
                        .to("mock:result");
            }
        };
    }

}
