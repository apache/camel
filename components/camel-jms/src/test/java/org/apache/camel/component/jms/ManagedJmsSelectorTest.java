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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tags({ @Tag("not-parallel") })
public class ManagedJmsSelectorTest implements CamelTestSupportHelper {

    @Order(1)
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    @ContextFixture
    public void setupContext(CamelContext context) {
        final ConnectionFactory connectionFactory
                = ConnectionFactoryHelper.createConnectionFactory(service.serviceAddress(), null);

        context.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        DefaultCamelContext.setDisableJmx(false);
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();

        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    protected MBeanServer getMBeanServer() {
        return camelContextExtension.getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testJmsSelectorChangeViaJmx() throws Exception {
        ProducerTemplate template = camelContextExtension.getProducerTemplate();
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        String selector = (String) mbeanServer.getAttribute(on, "MessageSelector");
        assertEquals("brand='beer'", selector);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Carlsberg");

        template.sendBodyAndHeader("activemq:queue:startManagedJmsSelectorTest", "Pepsi", "brand", "softdrink");
        template.sendBodyAndHeader("activemq:queue:startManagedJmsSelectorTest", "Carlsberg", "brand", "beer");

        MockEndpoint.assertIsSatisfied(camelContextExtension.getContext());

        // change the selector at runtime

        MockEndpoint.resetMocks(camelContextExtension.getContext());
        mock.reset();

        mbeanServer.setAttribute(on, new Attribute("MessageSelector", "brand='softdrink'"));

        mock.expectedBodiesReceived("Pepsi");
        mock.reset();

        template.sendBodyAndHeader("activemq:queue:startManagedJmsSelectorTest", "Pepsi", "brand", "softdrink");
        template.sendBodyAndHeader("activemq:queue:startManagedJmsSelectorTest", "Carlsberg", "brand", "beer");

        mock.assertIsSatisfied();

        selector = (String) mbeanServer.getAttribute(on, "MessageSelector");
        assertEquals("brand='softdrink'", selector);
    }

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
