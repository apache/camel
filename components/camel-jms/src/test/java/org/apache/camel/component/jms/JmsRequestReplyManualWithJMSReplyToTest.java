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

import jakarta.jms.JMSException;
import jakarta.jms.Queue;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Header;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsRequestReplyManualWithJMSReplyToTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Consume("activemq:queue:fooJmsRequestReplyManualWithJMSReplyToTest")
    public void doSomething(@Header("JMSReplyTo") Queue jmsReplyTo, @Body String body) throws JMSException {
        assertEquals("Hello World", body);

        String endpointName = "activemq:" + jmsReplyTo.getQueueName();
        template.sendBody(endpointName, "Bye World");
    }

    @Test
    public void testManualRequestReply() {
        // send an InOnly but force Camel to pass JMSReplyTo
        template.send("activemq:queue:fooJmsRequestReplyManualWithJMSReplyToTest?preserveMessageQos=true", exchange -> {
            exchange.getIn().setBody("Hello World");
            exchange.getIn().setHeader("JMSReplyTo", "barJmsRequestReplyManualWithJMSReplyToTest");
        });

        String reply = consumer.receiveBody("activemq:queue:barJmsRequestReplyManualWithJMSReplyToTest", 5000, String.class);
        assertEquals("Bye World", reply);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
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
}
