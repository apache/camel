/**
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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

/**
 * @version $Revision: $
 */
public class JmsEndpointConfigurationTest extends ContextTestSupport {
    private Processor dummyProcessor = new Processor() {
        public void process(Exchange exchange) throws Exception {
            log.info("Received: " + exchange);
        }
    };

    public void testDurableSubscriberConfiguredWithDoubleSlash() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms://topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC");
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    public void testDurableSubscriberConfiguredWithNoSlashes() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:topic:Foo.Bar?durableSubscriptionName=James&clientId=ABC");
        assertDurableSubscriberEndpointIsValid(endpoint);
    }

    public void testSelector() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:Foo.Bar?selector=foo%3D'ABC'");
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        assertEquals("selector", "foo='ABC'", container.getMessageSelector());

        Object object = container.getMessageListener();
        EndpointMessageListener messageListener = assertIsInstanceOf(EndpointMessageListener.class, object);
        assertFalse("Should not have replyToDisabled", messageListener.isDisableReplyTo());
        assertFalse("Should not have isEagerLoadingOfProperties()", messageListener.isEagerLoadingOfProperties());
    }

    public void testConfigureMessageListener() throws Exception {
        JmsEndpoint endpoint = (JmsEndpoint) resolveMandatoryEndpoint("jms:Foo.Bar?disableReplyTo=true&eagerLoadingOfProperties=true");
        JmsConsumer consumer = endpoint.createConsumer(dummyProcessor);

        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        Object object = container.getMessageListener();
        EndpointMessageListener messageListener = assertIsInstanceOf(EndpointMessageListener.class, object);
        assertTrue("Should have replyToDisabled", messageListener.isDisableReplyTo());
        assertTrue("Should have isEagerLoadingOfProperties()", messageListener.isEagerLoadingOfProperties());
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
