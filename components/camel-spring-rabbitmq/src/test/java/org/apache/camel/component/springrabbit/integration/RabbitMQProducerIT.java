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

import java.nio.charset.Charset;
import java.util.Map;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.springrabbit.SpringRabbitMQConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitMQProducerIT extends RabbitMQITSupport {

    @Test
    public void testProducer() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        template.sendBody("direct:start", "Hello World");

        AmqpTemplate template = new RabbitTemplate(cf);
        String out = (String) template.receiveAndConvert("myqueue");
        Assertions.assertEquals("Hello World", out);
    }

    @Test
    public void testProducerWithHeader() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        template.sendBodyAndHeader("direct:start", "Hello World", "cheese", "gouda");

        AmqpTemplate template = new RabbitTemplate(cf);
        Message out = template.receive("myqueue");
        Assertions.assertEquals("Hello World", new String(out.getBody()));
        Assertions.assertEquals("gouda", out.getMessageProperties().getHeader("cheese"));
    }

    @Test
    public void testProducerWithMessage() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        MessageProperties props = MessagePropertiesBuilder.newInstance()
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .setMessageId("123")
                .setHeader("bar", "baz")
                .build();
        Message body = MessageBuilder.withBody("foo".getBytes())
                .andProperties(props)
                .build();

        template.sendBody("direct:start", body);

        AmqpTemplate template = new RabbitTemplate(cf);
        Message out = template.receive("myqueue");
        Assertions.assertEquals("foo", new String(out.getBody()));
        Assertions.assertEquals("baz", out.getMessageProperties().getHeader("bar"));
    }

    @Test
    public void testProducerWithMessageProperties() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        template.sendBodyAndHeaders("direct:start", "<price>123</price>",
                Map.of(SpringRabbitMQConstants.DELIVERY_MODE, MessageDeliveryMode.PERSISTENT,
                        SpringRabbitMQConstants.TYPE, "price",
                        SpringRabbitMQConstants.CONTENT_TYPE, "application/xml",
                        SpringRabbitMQConstants.MESSAGE_ID, "0fe9c142-f9c1-426f-9237-f5a4c988a8ae",
                        SpringRabbitMQConstants.PRIORITY, 1));

        AmqpTemplate template = new RabbitTemplate(cf);
        Message out = template.receive("myqueue");

        String encoding = out.getMessageProperties().getContentEncoding();
        Assertions.assertEquals(Charset.defaultCharset().name(), encoding);
        Assertions.assertEquals("<price>123</price>", new String(out.getBody(), encoding));
        Assertions.assertEquals(MessageDeliveryMode.PERSISTENT, out.getMessageProperties().getReceivedDeliveryMode());
        Assertions.assertEquals("price", out.getMessageProperties().getType());
        Assertions.assertEquals("application/xml", out.getMessageProperties().getContentType());
        Assertions.assertEquals("0fe9c142-f9c1-426f-9237-f5a4c988a8ae", out.getMessageProperties().getMessageId());
        Assertions.assertEquals(1, out.getMessageProperties().getPriority());
        Assertions.assertEquals(0, out.getMessageProperties().getHeaders().size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("spring-rabbitmq:foo?routingKey=foo.bar");
            }
        };
    }
}
