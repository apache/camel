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

import java.util.Collections;

import jakarta.jms.DeliveryMode;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTemplate;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With preserveMessageQos=true, a message that carries no JMSExpiration must be sent with the time to live of the
 * endpoint (timeToLive, or requestTimeout for request/reply), as documented for the option.
 */
class JmsPreserveMessageQosTimeToLiveTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final String QUEUE = "JmsPreserveMessageQosTimeToLiveTest";

    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    void inOnlyMessageWithoutExpirationUsesEndpointTimeToLive() throws Exception {
        MessageProducer producer = mock(MessageProducer.class);
        Message message = messageWithoutQosHeaders(0L);

        sendInOnly("activemq:queue:qos?preserveMessageQos=true&timeToLive=5000", producer, message);

        verify(producer).send(same(message), eq(DeliveryMode.PERSISTENT), eq(4), eq(5000L));
    }

    @Test
    void inOutRequestWithoutExpirationUsesRequestTimeout() throws Exception {
        MessageProducer producer = mock(MessageProducer.class);
        Message message = messageWithoutQosHeaders(0L);

        JmsEndpoint endpoint = context.getEndpoint("activemq:queue:qos?preserveMessageQos=true", JmsEndpoint.class);
        CamelJmsTemplate inOut = (CamelJmsTemplate) endpoint.createInOutTemplate();
        inOut.doSend(producer, message);

        verify(producer).send(same(message), eq(DeliveryMode.PERSISTENT), eq(4), eq(20000L));
    }

    @Test
    void inOutRequestWithoutExpirationAndTimeToLiveDisabledNeverExpires() throws Exception {
        MessageProducer producer = mock(MessageProducer.class);
        Message message = messageWithoutQosHeaders(0L);

        JmsEndpoint endpoint = context.getEndpoint(
                "activemq:queue:qos?preserveMessageQos=true&disableTimeToLive=true", JmsEndpoint.class);
        CamelJmsTemplate inOut = (CamelJmsTemplate) endpoint.createInOutTemplate();
        inOut.doSend(producer, message);

        verify(producer).send(same(message), eq(DeliveryMode.PERSISTENT), eq(4), eq(0L));
    }

    @Test
    void messageExpirationStillOverridesEndpointTimeToLive() throws Exception {
        MessageProducer producer = mock(MessageProducer.class);
        // an expiration one hour ahead: the remaining time is used, not the endpoint value of 5 seconds
        Message message = messageWithoutQosHeaders(System.currentTimeMillis() + 3600000L);

        sendInOnly("activemq:queue:qos?preserveMessageQos=true&timeToLive=5000", producer, message);

        verify(producer).send(same(message), eq(DeliveryMode.PERSISTENT), eq(4),
                longThat(ttl -> ttl > 3500000L && ttl <= 3600000L));
    }

    @Test
    void endpointWithoutTimeToLiveStillSendsMessagesThatNeverExpire() throws Exception {
        MessageProducer producer = mock(MessageProducer.class);
        Message message = messageWithoutQosHeaders(0L);

        sendInOnly("activemq:queue:qos?preserveMessageQos=true", producer, message);

        verify(producer).send(same(message), eq(DeliveryMode.PERSISTENT), eq(4), eq(0L));
    }

    @Test
    void brokerReceivesTheEndpointTimeToLive() {
        long before = System.currentTimeMillis();
        template.sendBody("direct:start", "hello");

        Exchange received = consumer.receive("activemq:queue:" + QUEUE, 10000);
        assertNotNull(received, "the message must reach the queue");
        Long expiration = received.getMessage().getHeader("JMSExpiration", Long.class);
        assertNotNull(expiration);
        assertTrue(expiration >= before + 600000L && expiration <= System.currentTimeMillis() + 600000L,
                "the message must expire about 10 minutes after it was sent, but JMSExpiration was " + expiration);
    }

    private void sendInOnly(String uri, MessageProducer producer, Message message) throws Exception {
        JmsEndpoint endpoint = context.getEndpoint(uri, JmsEndpoint.class);
        CamelJmsTemplate inOnly = (CamelJmsTemplate) endpoint.createInOnlyTemplate();
        inOnly.doSend(producer, message);
    }

    private static Message messageWithoutQosHeaders(long expiration) throws Exception {
        Message message = mock(Message.class);
        when(message.getJMSExpiration()).thenReturn(expiration);
        when(message.getJMSPriority()).thenReturn(4);
        when(message.getJMSDeliveryMode()).thenReturn(DeliveryMode.PERSISTENT);
        when(message.getPropertyNames()).thenReturn(Collections.emptyEnumeration());
        return message;
    }

    @Override
    public String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("activemq:queue:" + QUEUE + "?preserveMessageQos=true&timeToLive=600000");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
