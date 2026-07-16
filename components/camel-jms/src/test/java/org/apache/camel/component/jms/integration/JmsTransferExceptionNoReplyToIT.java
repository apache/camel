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
package org.apache.camel.component.jms.integration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;

/**
 * Tests that transferException=true does not swallow exceptions when the incoming message has no JMSReplyTo
 * (fire-and-forget / InOnly). Before the fix, a failed exchange would silently commit the transaction and lose the
 * message because transferException suppressed the exception unconditionally — even when no reply destination existed
 * to transfer it to.
 */
public class JmsTransferExceptionNoReplyToIT extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final String QUEUE_NAME
            = "JmsTransferExceptionNoReplyToIT";

    private static final AtomicInteger counter = new AtomicInteger();

    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @BeforeEach
    public void setUp() {
        counter.set(0);
    }

    @Test
    public void testTransferExceptionNoReplyToShouldRedeliver() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:dead", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);

        // Send fire-and-forget (InOnly, no JMSReplyTo) to a transacted queue with transferException=true.
        // The route always throws, so without the fix the message is silently lost.
        template.sendBody("activemq:queue:" + QUEUE_NAME, "Kaboom");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);

        // The processor should have been invoked more than once due to redelivery
        await().atMost(5, TimeUnit.SECONDS).until(() -> counter.get() > 1);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        JmsComponent component = super.setupComponent(camelContext, connectionFactory, componentName);
        component.setObjectMessageEnabled(true);
        return component;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead")
                        .maximumRedeliveries(2)
                        .redeliveryDelay(0));

                from("activemq:queue:" + QUEUE_NAME + "?transferException=true&transacted=true")
                        .process(exchange -> {
                            counter.incrementAndGet();
                            throw new IllegalArgumentException("Boom");
                        });
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
