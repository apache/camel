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

import com.rabbitmq.client.ShutdownSignalException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

public class RabbitMQProducerInvalidExchangeIT extends RabbitMQITSupport {

    @Override
    protected boolean confirmEnabled() {
        return true;
    }

    @Test
    public void testProducer() {
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);

        Queue q = new Queue("myqueue");
        TopicExchange t = new TopicExchange("foo");

        AmqpAdmin admin = new RabbitAdmin(cf);
        admin.declareQueue(q);
        admin.declareExchange(t);
        admin.declareBinding(BindingBuilder.bind(q).to(t).with("foo.bar.#"));

        final CamelExecutionException exception = Assertions.assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "Hello World"));
        Assertions.assertInstanceOf(ShutdownSignalException.class, exception.getCause());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("spring-rabbitmq:unknown?routingKey=foo.bar");
            }
        };
    }
}
