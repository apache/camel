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
package org.apache.camel.component.amqp;

import java.util.function.Consumer;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.amqp.AMQPComponent.amqpComponent;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.discoverAMQP;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AMQPRouteTest extends AMQPTestSupport {

    private ProducerTemplate template;

    private MockEndpoint resultEndpoint;
    private final String expectedBody = "Hello there!";

    @BeforeEach
    void setupTemplate() {
        template = contextExtension.getProducerTemplate();
        resultEndpoint = contextExtension.getMockEndpoint("mock:result");
    }

    @Test
    public void testJmsQueue() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        template.sendBodyAndHeader("amqp-customized:queue:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testRequestReply() {
        String response = template.requestBody("amqp-customized:queue:inOut", expectedBody, String.class);
        assertEquals("response", response);
    }

    @Test
    public void testJmsTopic() throws Exception {
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("amqp-customized:topic:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testPrefixWildcard() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("amqp-customized:wildcard.foo.bar", expectedBody);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testIncludeDestination() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("JMSDestination").isEqualTo("ping");
        template.sendBody("amqp-customized:queue:ping", expectedBody);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testNoAmqpAnnotations() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        // default doesn't map annotations to headers
        resultEndpoint.message(0).header("JMS_AMQP_MA_cheese").isNull();
        sendAmqpMessage(contextExtension.getContext().getComponent("amqp-customized", AMQPComponent.class),
                "ping", expectedBody, facade -> {
                    try {
                        facade.setApplicationProperty("cheese", 123);
                        facade.setTracingAnnotation("cheese", 456);
                    } catch (JMSException e) {
                        throw new RuntimeCamelException(e);
                    }
                });
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAmqpAnnotations() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        resultEndpoint.message(0).header("JMS_AMQP_MA_cheese").isEqualTo(456);
        sendAmqpMessage(contextExtension.getContext().getComponent("amqp-customized2", AMQPComponent.class),
                "ping2", expectedBody, facade -> {
                    try {
                        facade.setApplicationProperty("cheese", 123);
                        facade.setTracingAnnotation("cheese", 456);
                    } catch (JMSException e) {
                        throw new RuntimeCamelException(e);
                    }
                });
        resultEndpoint.assertIsSatisfied();
    }

    private void sendAmqpMessage(
            AMQPComponent component, String queue, String body,
            Consumer<AmqpJmsMessageFacade> messageCustomizer)
            throws JMSException {
        ConnectionFactory factory = component.getConfiguration().getConnectionFactory();
        try (Connection connection = factory.createConnection();
             Session session = connection.createSession();
             MessageProducer producer = session.createProducer(session.createQueue(queue))) {
            TextMessage message = session.createTextMessage(body);
            messageCustomizer.accept((AmqpJmsMessageFacade) ((JmsMessage) message).getFacade());
            producer.send(message);
        }
    }

    @ContextFixture
    public void configureContext(CamelContext context) {
        System.setProperty(AMQPConnectionDetails.AMQP_PORT, String.valueOf(service.brokerPort()));

        context.getRegistry().bind("amqpConnection", discoverAMQP(context));
        context.addComponent("amqp-customized", amqpComponent(service.serviceAddress()));
        context.addComponent("amqp-customized2", amqpComponent(service.serviceAddress()));
        context.getComponent("amqp-customized2", AMQPComponent.class).setIncludeAmqpAnnotations(true);
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    private static RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("amqp-customized:queue:ping")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp-customized2:queue:ping2")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp-customized:queue:inOut")
                        .setBody().constant("response");

                from("amqp-customized:topic:ping")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp-customized:topic:ping")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp-customized:queue:wildcard.#")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp:queue:uriEndpoint")
                        .to("log:routing")
                        .to("mock:result");
            }
        };
    }
}
