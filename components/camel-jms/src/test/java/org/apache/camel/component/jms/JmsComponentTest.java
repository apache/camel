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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsComponentTest extends CamelTestSupport {

    protected String componentName = "activemq123";
    protected JmsEndpoint endpoint;

    @Test
    public void testComponentOptions() throws Exception {
        String reply = template.requestBody("activemq123:queue:hello?requestTimeout=5000", "Hello World", String.class);
        assertEquals("Bye World", reply);

        assertEquals(true, endpoint.isAcceptMessagesWhileStopping());
        assertEquals(true, endpoint.isAllowReplyManagerQuickStop());
        assertEquals(true, endpoint.isAlwaysCopyMessage());
        assertEquals(1, endpoint.getAcknowledgementMode());
        assertEquals(true, endpoint.isAutoStartup());
        assertEquals(1, endpoint.getCacheLevel());
        assertEquals("foo", endpoint.getClientId());
        assertEquals(2, endpoint.getConcurrentConsumers());
        assertEquals(true, endpoint.isDeliveryPersistent());
        assertEquals(true, endpoint.isExplicitQosEnabled());
        assertEquals(20, endpoint.getIdleTaskExecutionLimit());
        assertEquals(21, endpoint.getIdleConsumerLimit());
        assertEquals(5, endpoint.getMaxConcurrentConsumers());
        assertEquals(90, endpoint.getMaxMessagesPerTask());
        assertEquals(3, endpoint.getPriority());
        assertEquals(5000, endpoint.getReceiveTimeout());
        assertEquals(9000, endpoint.getRecoveryInterval());
        assertEquals(3000, endpoint.getTimeToLive());
        assertEquals(true, endpoint.isTransacted());
        assertEquals(15000, endpoint.getTransactionTimeout());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
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

        endpoint = (JmsEndpoint) comp.createEndpoint("queue:hello");

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(endpoint).transform(constant("Bye World"));
            }
        };
    }
}
