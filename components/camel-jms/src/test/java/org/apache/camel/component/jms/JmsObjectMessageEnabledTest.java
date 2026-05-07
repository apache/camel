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
import jakarta.jms.ObjectMessage;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that JMS ObjectMessage support is disabled by default and can be enabled via the
 * {@code objectMessageEnabled} option.
 */
public class JmsObjectMessageEnabledTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    protected JmsTemplate jmsTemplate;

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        jmsTemplate = new JmsTemplate(connectionFactory);
        // do not enable objectMessageEnabled here: this test verifies the default disabled behavior
        return super.setupComponent(camelContext, connectionFactory, componentName);
    }

    @Test
    public void testProducerRefusesSerializableBodyByDefault() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("activemq:queue:JmsObjectMessageEnabledTest.disabled?jmsMessageType=Object",
                        new MyOrder("beer", 10)));
        Throwable cause = rootCause(ex);
        assertTrue(cause.getMessage().contains("objectMessageEnabled=true"),
                "Expected guidance to enable objectMessageEnabled=true, got: " + cause.getMessage());
    }

    @Test
    public void testConsumerRefusesObjectMessageByDefault() {
        // Send an ObjectMessage directly to the queue with a JmsTemplate (bypassing Camel)
        jmsTemplate.setPubSubDomain(false);
        jmsTemplate.send("JmsObjectMessageEnabledTest.consumer", session -> {
            ObjectMessage msg = session.createObjectMessage();
            msg.setObject(new MyOrder("beer", 5));
            return msg;
        });

        // Reading the body via Camel should fail because objectMessageEnabled=false
        Exchange exchange = consumer.receive("activemq:JmsObjectMessageEnabledTest.consumer", 5000);
        assertNotNull(exchange, "Should have received the exchange wrapper");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> exchange.getIn().getBody());
        assertTrue(ex.getMessage().contains("objectMessageEnabled=true"),
                "Expected guidance to enable objectMessageEnabled=true, got: " + ex.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // No routes: this test uses ConsumerTemplate to receive directly
        return null;
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
