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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.springrabbit.SpringRabbitMQComponent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * With {@code ignoreDeclarationExceptions=true}, a consumer starts on a queue that exists with other arguments than the
 * ones it declares, and consumes from it.
 */
public class RabbitMQConsumerIgnoreDeclarationExceptionsIT extends RabbitMQITSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getComponent("spring-rabbitmq", SpringRabbitMQComponent.class).setIgnoreDeclarationExceptions(true);
        return context;
    }

    @Test
    public void testConsumerOnQueueDeclaredWithOtherArguments() throws Exception {
        String exchange = uniqueName("orders");
        String queue = uniqueName("orders-queue");
        // the queue is a quorum queue, the consumer declares a classic queue: the broker refuses that declaration
        ConnectionFactory cf = context.getRegistry().lookupByNameAndType("myCF", ConnectionFactory.class);
        new RabbitAdmin(cf).declareQueue(QueueBuilder.durable(queue).quorum().build());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order 1");

        assertDoesNotThrow(() -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("spring-rabbitmq:" + exchange + "?queues=" + queue + "&routingKey=new")
                        .to("mock:result");
            }
        }));

        template.sendBody("spring-rabbitmq:" + exchange + "?routingKey=new", "order 1");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }
}
