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

import java.util.Arrays;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The polling consumer (pollEnrich and ConsumerTemplate) reads the message before the JMS consumer and session are
 * closed. The Artemis client streams the body of a large message through the consumer, so the body cannot be read after
 * the consumer is closed.
 */
public class JmsPollingConsumerLargeMessageTest extends AbstractJMSTest {

    private static final String QUEUE = "JmsPollingConsumerLargeMessageTest";
    private static final byte[] LARGE = payload(200_000);
    private static final byte[] SMALL = payload(512);

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    protected ConnectionFactory connectionFactory;
    protected JmsTemplate jmsTemplate;

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ArtemisService service, String componentName) {
        // every message above 1 KB is an Artemis large message
        connectionFactory = new ActiveMQConnectionFactory(service.serviceAddress() + "?minLargeMessageSize=1024");
        jmsTemplate = new JmsTemplate(connectionFactory);
        jmsTemplate.setReceiveTimeout(5000);
        return setupComponent(camelContext, connectionFactory, componentName);
    }

    @Test
    void consumerTemplateReceivesLargeBytesMessage() {
        template.sendBody("jms:queue:" + QUEUE + ".receive", LARGE);

        Exchange exchange = consumer.receive("jms:queue:" + QUEUE + ".receive", 5000);

        assertNotNull(exchange);
        // the message is an Artemis large message
        assertEquals(200_000L, exchange.getIn().getHeader("_AMQ_LARGE_SIZE", Long.class));
        assertArrayEquals(LARGE, exchange.getIn().getBody(byte[].class));
    }

    @Test
    void consumerTemplateReceivesLargeBytesMessageWithArtemisStreaming() {
        String uri = "jms:queue:" + QUEUE + ".streaming?artemisStreamingEnabled=true";
        template.sendBody(uri, LARGE);

        Exchange exchange = consumer.receive(uri, 5000);

        assertNotNull(exchange);
        assertArrayEquals(LARGE, exchange.getIn().getBody(byte[].class));
    }

    @Test
    void consumerTemplateReceivesLargeBytesMessageWithSelector() {
        String uri = "jms:queue:" + QUEUE + ".selector";
        template.sendBodyAndHeader(uri, "other", "kind", "other");
        template.sendBodyAndHeader(uri, LARGE, "kind", "large");

        assertArrayEquals(LARGE, consumer.receiveBody(uri + "?selector=kind='large'", 5000, byte[].class));
        assertEquals("other", consumer.receiveBody(uri + "?selector=kind='other'", 5000, String.class));
    }

    @Test
    void pollEnrichReceivesLargeBytesMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + QUEUE + ".result");
        mock.expectedMessageCount(1);
        template.sendBody("jms:queue:" + QUEUE + ".pollEnrich", LARGE);

        template.sendBody("direct:" + QUEUE + ".pollEnrich", "trigger");

        MockEndpoint.assertIsSatisfied(context);
        assertArrayEquals(LARGE, mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    void transactedConsumerTemplateCommitsTheReceive() {
        String uri = "jms:queue:" + QUEUE + ".transacted?transacted=true";
        template.sendBody(uri, LARGE);

        assertArrayEquals(LARGE, consumer.receiveBody(uri, 5000, byte[].class));
        // the message is not delivered again
        assertNull(consumer.receive(uri, 1000));
    }

    @Test
    void clientAcknowledgeConsumerTemplateAcknowledgesTheMessage() {
        String uri = "jms:queue:" + QUEUE + ".clientAck?acknowledgementModeName=CLIENT_ACKNOWLEDGE";
        template.sendBody(uri, LARGE);

        assertArrayEquals(LARGE, consumer.receiveBody(uri, 5000, byte[].class));
        // the message is not delivered again
        assertNull(consumer.receive(uri, 1000));
    }

    @Test
    void transactedConsumerTemplateRollsBackMessageWhoseBodyCannotBeRead() throws Exception {
        String queue = QUEUE + ".unreadable";
        jmsTemplate.send(queue, session -> session.createObjectMessage("not allowed"));

        // objectMessageEnabled is false, so the body cannot be read: the receive fails and is rolled back
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> consumer.receive("jms:queue:" + queue + "?transacted=true", 5000));
        assertTrue(e.getMessage().contains("objectMessageEnabled=true"), e.getMessage());

        Message message = jmsTemplate.receive(queue);
        assertEquals("not allowed", assertInstanceOf(ObjectMessage.class, message).getObject());
    }

    @Test
    void clientAcknowledgeConsumerTemplateRecoversMessageWhoseBodyCannotBeRead() throws Exception {
        String queue = QUEUE + ".unreadableClientAck";
        String options = "?acknowledgementModeName=CLIENT_ACKNOWLEDGE";
        // a pooled session (as with camel-activemq) is not closed after the receive, but goes back to the pool
        JmsPoolConnectionFactory pooledConnectionFactory = new JmsPoolConnectionFactory();
        pooledConnectionFactory.setConnectionFactory(connectionFactory);
        try {
            context.addComponent("pooledjms", JmsComponent.jmsComponentAutoAcknowledge(pooledConnectionFactory));
            jmsTemplate.send(queue, session -> session.createObjectMessage("not allowed"));

            // objectMessageEnabled is false, so the body cannot be read: the receive fails and the session is recovered
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> consumer.receive("pooledjms:queue:" + queue + options, 5000));
            assertTrue(e.getMessage().contains("objectMessageEnabled=true"), e.getMessage());

            // the next receive on the pooled session acknowledges its own message only
            template.sendBody("jms:queue:" + queue + ".next", "next");
            assertEquals("next", consumer.receiveBody("pooledjms:queue:" + queue + ".next" + options, 5000, String.class));

            Message message = jmsTemplate.receive(queue);
            assertEquals("not allowed", assertInstanceOf(ObjectMessage.class, message).getObject());
            assertTrue(message.getJMSRedelivered());
        } finally {
            pooledConnectionFactory.stop();
        }
    }

    @Test
    void consumerTemplateReceivesSmallBytesMessage() {
        template.sendBody("jms:queue:" + QUEUE + ".small", SMALL);

        assertArrayEquals(SMALL, consumer.receiveBody("jms:queue:" + QUEUE + ".small", 5000, byte[].class));
    }

    @Test
    void routeReceivesLargeBytesMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + QUEUE + ".route");
        mock.expectedMessageCount(1);

        template.sendBody("jms:queue:" + QUEUE + ".route", LARGE);

        MockEndpoint.assertIsSatisfied(context);
        assertArrayEquals(LARGE, mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    private static byte[] payload(int size) {
        byte[] answer = new byte[size];
        Arrays.fill(answer, (byte) 'a');
        for (int i = 0; i < size; i += 1000) {
            answer[i] = (byte) ('0' + (i / 1000) % 10);
        }
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:" + QUEUE + ".route").to("mock:" + QUEUE + ".route");

                from("direct:" + QUEUE + ".pollEnrich")
                        .pollEnrich("jms:queue:" + QUEUE + ".pollEnrich", 5000)
                        .to("mock:" + QUEUE + ".result");
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
