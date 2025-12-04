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

package org.apache.camel.component.jms.converter;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.core.JmsTemplate;

public class JmsConverterTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    protected JmsTemplate jmsTemplate;
    protected CamelContext context;
    protected ProducerTemplate template;

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        jmsTemplate = new JmsTemplate(connectionFactory);

        return super.setupComponent(camelContext, connectionFactory, componentName);
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

    @Test
    public void toJakartaJmsMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.allMessages().inMessage().isInstanceOf(JmsMessage.class);
        mock.allMessages().body(Message.class).isInstanceOf(Message.class);

        jmsTemplate.setPubSubDomain(false);
        jmsTemplate.send("JmsConverterTest", session -> session.createTextMessage("Hello world!"));

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:JmsConverterTest").to("mock:result");
                from("direct:test").to("activemq:JmsConverterTest");
            }
        };
    }
}
