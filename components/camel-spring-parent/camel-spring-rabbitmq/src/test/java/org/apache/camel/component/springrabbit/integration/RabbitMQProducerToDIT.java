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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitMQProducerToDIT extends RabbitMQITSupport {

    @Test
    public void testToD() throws Exception {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        fluentTemplate.to("direct:start").withBody("Hello World").withHeader("whereTo", "foo").withHeader("myKey", "foo.bar")
                .send();

        AmqpTemplate template = new RabbitTemplate(cf);
        String out = (String) template.receiveAndConvert("myqueue");
        Assertions.assertEquals("Hello World", out);

        fluentTemplate.to("direct:start").withBody("Bye World").withHeader("whereTo", "foo").withHeader("myKey", "foo.bar.baz")
                .send();

        template = new RabbitTemplate(cf);
        out = (String) template.receiveAndConvert("myqueue");
        Assertions.assertEquals("Bye World", out);

        // there should only be 1 rabbit endpoint
        long count = context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("spring-rabbit")).count();
        Assertions.assertEquals(1, count);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .toD("spring-rabbitmq:${header.whereTo}?routingKey=${header.myKey}");
            }
        };
    }
}
