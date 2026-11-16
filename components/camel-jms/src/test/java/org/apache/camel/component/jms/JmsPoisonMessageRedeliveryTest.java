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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;

import jakarta.jms.Message;
import jakarta.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A poison message (eagerLoadingOfProperties) goes to the error handler without redeliveries, as its payload cannot be
 * read again: the route is not run with the poison body.
 */
public class JmsPoisonMessageRedeliveryTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;

    @Test
    public void testPoisonMessageGoesToDeadLetterChannel() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("activemq:queue:JmsPoisonMessageRedeliveryTest.dlc?objectMessageEnabled=true", new Poison());

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(0, getMockEndpoint("mock:dlc-step").getReceivedCounter());
        Exchange exchange = dead.getReceivedExchanges().get(0);
        assertTrue(exchange.getIn().getBody(String.class).startsWith("Poison JMS message payload: "));
        Exception caught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(caught);
        assertTrue(caught.getMessage().contains("poison!"), caught.getMessage());
    }

    @Test
    public void testPoisonMessageGoesToOnException() throws Exception {
        MockEndpoint poison = getMockEndpoint("mock:poison");
        poison.expectedMessageCount(1);
        poison.expectedBodiesReceived("Poison JMS message payload: cannot convert this message");

        template.sendBody("activemq:queue:JmsPoisonMessageRedeliveryTest.converter", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(0, getMockEndpoint("mock:converter-step").getReceivedCounter());
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getRegistry().bind("poisonConverter", new PoisonConverter());

                from("activemq:queue:JmsPoisonMessageRedeliveryTest.dlc?eagerLoadingOfProperties=true&objectMessageEnabled=true")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:dlc-step");

                from("activemq:queue:JmsPoisonMessageRedeliveryTest.converter?eagerLoadingOfProperties=true&messageConverter=#poisonConverter")
                        .onException(Exception.class).maximumRedeliveries(2).redeliveryDelay(0).handled(true)
                        .to("mock:poison")
                        .end()
                        .to("mock:converter-step");
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
    }

    public static class Poison implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Serial
        private void readObject(ObjectInputStream in) throws IOException {
            throw new IOException("poison!");
        }
    }

    private static class PoisonConverter implements MessageConverter {
        @Override
        public Message toMessage(Object object, Session session) {
            throw new MessageConversionException("not used");
        }

        @Override
        public Object fromMessage(Message message) {
            throw new MessageConversionException("cannot convert this message");
        }
    }
}
