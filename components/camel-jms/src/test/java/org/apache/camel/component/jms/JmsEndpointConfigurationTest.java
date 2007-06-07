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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

import javax.jms.ConnectionFactory;

/**
 * @version $Revision: $
 */
public class JmsEndpointConfigurationTest extends ContextTestSupport {

    public void testDurableSubscriberConfiguredWithDoubleSlash() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms://topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC");
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    public void testDurableSubscriberConfiguredWithNoSlashes() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC");
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    protected void assertDurableSubscriberEndpointIsValid(JmsEndpoint endpoint) throws Exception {
        JmsConfiguration configuration = endpoint.getConfiguration();
        assertEquals("getDurableSubscriptionName()", "James", configuration.getDurableSubscriptionName());
        assertEquals("getClientId()", "ABC", configuration.getClientId());
        assertEquals("isDeliveryPersistent()", true, configuration.isDeliveryPersistent());

        JmsConsumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                log.info("Received: " + exchange);
            }
        });
        AbstractMessageListenerContainer listenerContainer = consumer.getListenerContainer();
        assertEquals("getDurableSubscriptionName()", "James", listenerContainer.getDurableSubscriptionName());
        assertEquals("getClientId()", "ABC", listenerContainer.getClientId());
        assertEquals("isSubscriptionDurable()", true, listenerContainer.isSubscriptionDurable());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("jms", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }
}
