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
package org.apache.camel.component.springrabbit.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.springrabbit.SpringRabbitMQConstants;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;

public class RabbitMQConsumerQueuesIT extends RabbitMQITSupport {

    @Test
    public void testConsumer() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Test
    public void testConsumerWithHeader() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedHeaderReceived("cheese", "gouda");

        template.sendBodyAndHeader("direct:start", "Hello World", "cheese", "gouda");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Test
    public void testConsumerWithMessage() throws Exception {
        MessageProperties props = MessagePropertiesBuilder.newInstance()
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .setMessageId("123")
                .setHeader("bar", "baz")
                .build();
        Message body = MessageBuilder.withBody("foo".getBytes())
                .andProperties(props)
                .build();

        getMockEndpoint("mock:result").expectedBodiesReceived("foo");
        getMockEndpoint("mock:result").expectedHeaderReceived("bar", "baz");
        getMockEndpoint("mock:result").expectedHeaderReceived(SpringRabbitMQConstants.CONTENT_TYPE,
                MessageProperties.CONTENT_TYPE_TEXT_PLAIN);

        template.sendBody("direct:start", body);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Test
    public void testConsumerWithMessageProperties() throws Exception {
        MessageProperties props = MessagePropertiesBuilder.newInstance()
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setType("price")
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .setMessageId("123")
                .setPriority(1)
                .setHeader("bar", "baz")
                .build();
        Message body = MessageBuilder.withBody("foo".getBytes())
                .andProperties(props)
                .build();

        getMockEndpoint("mock:result").expectedBodiesReceived("foo");
        getMockEndpoint("mock:result").expectedHeaderReceived("bar", "baz");
        getMockEndpoint("mock:result").expectedHeaderReceived(SpringRabbitMQConstants.DELIVERY_MODE,
                MessageDeliveryMode.PERSISTENT);
        getMockEndpoint("mock:result").expectedHeaderReceived(SpringRabbitMQConstants.TYPE, "price");
        getMockEndpoint("mock:result").expectedHeaderReceived(SpringRabbitMQConstants.CONTENT_TYPE,
                MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        getMockEndpoint("mock:result").expectedHeaderReceived(SpringRabbitMQConstants.MESSAGE_ID, "123");
        getMockEndpoint("mock:result").expectedHeaderReceived(SpringRabbitMQConstants.PRIORITY, 1);

        template.sendBody("direct:start", body);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("spring-rabbitmq:foo");

                from("spring-rabbitmq:foo?queues=myqueue")
                        .to("log:result")
                        .to("mock:result");
            }
        };
    }
}
