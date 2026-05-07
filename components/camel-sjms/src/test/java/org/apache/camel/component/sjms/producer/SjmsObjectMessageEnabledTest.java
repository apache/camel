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
package org.apache.camel.component.sjms.producer;

import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that JMS ObjectMessage support is disabled by default in camel-sjms and can be enabled via the
 * {@code objectMessageEnabled} option.
 */
public class SjmsObjectMessageEnabledTest extends JmsTestSupport {

    private static final String DISABLED_QUEUE = "test.SjmsObjectMessageEnabledTest.disabled";
    private static final String ENABLED_QUEUE = "test.SjmsObjectMessageEnabledTest.enabled";
    private static final String CONSUMER_QUEUE = "test.SjmsObjectMessageEnabledTest.consumer";

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testProducerRefusesSerializableBodyByDefault() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("sjms:queue:" + DISABLED_QUEUE + "?jmsMessageType=Object",
                        new MyOrder("beer", 10)));
        Throwable cause = rootCause(ex);
        assertTrue(cause.getMessage().contains("objectMessageEnabled=true"),
                "Expected guidance to enable objectMessageEnabled=true, got: " + cause.getMessage());
    }

    @Test
    public void testProducerAcceptsSerializableBodyWhenEnabled() throws Exception {
        // when objectMessageEnabled=true, sending a Serializable body via Object message type should succeed
        template.sendBody("sjms:queue:" + ENABLED_QUEUE + "?jmsMessageType=Object&objectMessageEnabled=true",
                new MyOrder("beer", 10));
        // verify it was actually sent as an ObjectMessage
        MessageConsumer mc = createQueueConsumer(ENABLED_QUEUE);
        Message message = mc.receive(5000);
        assertNotNull(message, "Should have received the message");
        assertTrue(message instanceof ObjectMessage, "Expected ObjectMessage but got: " + message.getClass().getName());
        mc.close();
    }

    @Test
    public void testConsumerRefusesObjectMessageByDefault() throws Exception {
        // Send an ObjectMessage directly to the queue (bypassing Camel) using the underlying JMS session
        MessageProducer producer = getSession().createProducer(getSession().createQueue(CONSUMER_QUEUE));
        ObjectMessage objectMessage = getSession().createObjectMessage();
        objectMessage.setObject(new MyOrder("beer", 5));
        producer.send(objectMessage);
        producer.close();

        // Reading the body via Camel should fail because objectMessageEnabled=false (default)
        Exchange exchange = consumer.receive("sjms:queue:" + CONSUMER_QUEUE, 5000);
        assertNotNull(exchange, "Should have received the exchange wrapper");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> exchange.getIn().getBody());
        assertTrue(ex.getMessage().contains("objectMessageEnabled=true"),
                "Expected guidance to enable objectMessageEnabled=true, got: " + ex.getMessage());
    }

    @Test
    public void testComponentLevelObjectMessageEnabled() throws Exception {
        SjmsComponent custom = new SjmsComponent();
        custom.setConnectionFactory(connectionFactory);
        custom.setObjectMessageEnabled(true);
        context.addComponent("sjmsObjectEnabled", custom);

        String queue = "test.SjmsObjectMessageEnabledTest.componentLevel";
        template.sendBody("sjmsObjectEnabled:queue:" + queue + "?jmsMessageType=Object",
                new MyOrder("beer", 7));
        MessageConsumer mc = createQueueConsumer(queue);
        Message message = mc.receive(5000);
        assertNotNull(message, "Should have received the message");
        assertTrue(message instanceof ObjectMessage, "Expected ObjectMessage but got: " + message.getClass().getName());
        mc.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // No routes: this test uses the producer template and the consumer template directly
        return new RouteBuilder() {
            @Override
            public void configure() {
                // intentionally empty
            }
        };
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    public static class MyOrder implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final String item;
        private final int quantity;

        public MyOrder(String item, int quantity) {
            this.item = item;
            this.quantity = quantity;
        }

        public String getItem() {
            return item;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
