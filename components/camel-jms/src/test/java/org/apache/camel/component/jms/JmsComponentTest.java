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

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JmsComponentTest extends CamelTestSupport {

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createVMService();

    protected final String componentName = "activemq123";
    protected JmsEndpoint endpoint;

    @Test
    public void testComponentInOut() {
        String reply
                = template.requestBody("activemq123:queue:JmsComponentTest?requestTimeout=5000", "Hello World", String.class);
        assertEquals("Bye World", reply);
    }

    @Timeout(10)
    @Test
    public void testComponentOptions() {
        assertTrue(endpoint.isAcceptMessagesWhileStopping());
        assertTrue(endpoint.isAllowReplyManagerQuickStop());
        assertTrue(endpoint.isAlwaysCopyMessage());
        assertEquals(1, endpoint.getAcknowledgementMode());
        assertTrue(endpoint.isAutoStartup());
        assertEquals(1, endpoint.getCacheLevel());
        assertEquals("foo", endpoint.getClientId());
        assertEquals(2, endpoint.getConcurrentConsumers());
        assertTrue(endpoint.isDeliveryPersistent());
        assertTrue(endpoint.isExplicitQosEnabled());
        assertEquals(20, endpoint.getIdleTaskExecutionLimit());
        assertEquals(21, endpoint.getIdleConsumerLimit());
        assertEquals(5, endpoint.getMaxConcurrentConsumers());
        assertEquals(90, endpoint.getMaxMessagesPerTask());
        assertEquals(3, endpoint.getPriority());
        assertEquals(5000, endpoint.getReceiveTimeout());
        assertEquals(9000, endpoint.getRecoveryInterval());
        assertEquals(3000, endpoint.getTimeToLive());
        assertTrue(endpoint.isTransacted());
        assertEquals(15000, endpoint.getTransactionTimeout());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // Note: this one seems to mess with the component configuration, so we use a disposable broker
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service.serviceAddress(), 0);
        JmsComponent comp = jmsComponentAutoAcknowledge(connectionFactory);

        comp.setAcceptMessagesWhileStopping(true);
        comp.getConfiguration().setAllowReplyManagerQuickStop(true);
        comp.getConfiguration().setAlwaysCopyMessage(true);
        comp.getConfiguration().setAcknowledgementMode(1);
        comp.getConfiguration().setAutoStartup(true);
        comp.getConfiguration().setCacheLevel(1);
        comp.getConfiguration().setClientId("foo");
        comp.getConfiguration().setConcurrentConsumers(2);
        comp.getConfiguration().setDeliveryPersistent(true);
        comp.getConfiguration().setExplicitQosEnabled(true);
        comp.getConfiguration().setIdleTaskExecutionLimit(20);
        comp.getConfiguration().setIdleConsumerLimit(21);
        comp.getConfiguration().setMaxConcurrentConsumers(5);
        comp.getConfiguration().setMaxMessagesPerTask(90);
        comp.getConfiguration().setPriority(3);
        comp.getConfiguration().setReceiveTimeout(5000);
        comp.getConfiguration().setRecoveryInterval(9000);
        comp.getConfiguration().setTimeToLive(3000);
        comp.getConfiguration().setTransacted(true);
        comp.getConfiguration().setTransactionTimeout(15000);

        camelContext.addComponent(componentName, comp);

        endpoint = (JmsEndpoint) comp.createEndpoint("queue:JmsComponentTest");

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(endpoint).transform(constant("Bye World"));
            }
        };
    }
}
