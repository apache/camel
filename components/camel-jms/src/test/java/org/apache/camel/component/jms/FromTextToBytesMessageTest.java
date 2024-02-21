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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
public class FromTextToBytesMessageTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testTextToBytes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:text2bytes", "3");

        MockEndpoint.assertIsSatisfied(context);

        jakarta.jms.Message msg = mock.getReceivedExchanges().get(0).getIn(JmsMessage.class).getJmsMessage();
        assertNotNull(msg);
        assertIsInstanceOf(jakarta.jms.BytesMessage.class, msg);
    }

    @Test
    public void testTextToBytesHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:text2bytesHeader", "3");

        MockEndpoint.assertIsSatisfied(context);

        jakarta.jms.Message msg = mock.getReceivedExchanges().get(0).getIn(JmsMessage.class).getJmsMessage();
        assertNotNull(msg);
        assertIsInstanceOf(jakarta.jms.BytesMessage.class, msg);
    }

    @Test
    public void testTextToText() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:text2text", "Hello");

        MockEndpoint.assertIsSatisfied(context);

        jakarta.jms.Message msg = mock.getReceivedExchanges().get(0).getIn(JmsMessage.class).getJmsMessage();
        assertNotNull(msg);
        assertIsInstanceOf(jakarta.jms.TextMessage.class, msg);
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
                from("activemq:queue:text2bytes?jmsMessageType=Text")
                        .to("activemq:queue:destQFTTB?jmsMessageType=Bytes");

                from("activemq:queue:text2bytesHeader?jmsMessageType=Text")
                        .setHeader("myHeader", constant("123"))
                        .to("activemq:queue:destQFTTB?jmsMessageType=Bytes");

                from("activemq:queue:text2text?jmsMessageType=Text")
                        .to("activemq:queue:destQFTTB?jmsMessageType=Text");

                from("activemq:queue:destQFTTB")
                        .to("mock:bar");
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
