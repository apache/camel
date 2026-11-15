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
package org.apache.camel.component.springrabbit;

import java.io.IOException;
import java.net.ServerSocket;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With {@code ignoreDeclarationExceptions=true}, {@code RabbitAdmin.declareQueue} logs a failed queue declaration and
 * returns {@code null}: the consumer binds and consumes the queue by its name.
 */
class SpringRabbitMQIgnoreDeclarationExceptionsTest {

    private CachingConnectionFactory connectionFactory;
    private CamelContext context;

    @BeforeEach
    void setUp() throws IOException {
        // nothing listens on this port: every declaration fails
        connectionFactory = new CachingConnectionFactory("localhost", closedPort());
        context = new DefaultCamelContext();
        context.getRegistry().bind("cf", connectionFactory);
        context.getComponent("spring-rabbitmq", SpringRabbitMQComponent.class).setIgnoreDeclarationExceptions(true);
    }

    @AfterEach
    void tearDown() {
        context.stop();
        connectionFactory.destroy();
    }

    @Test
    void consumerStartsWhenTheDeclarationsFailAndAreIgnored() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("spring-rabbitmq:orders?connectionFactory=#cf&queues=orders&routingKey=new").routeId("orders")
                        .to("mock:orders");
            }
        });

        assertDoesNotThrow(() -> context.start());
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("orders"));
    }

    @Test
    void queueIsBoundByItsNameWhenItsDeclarationFailsAndIsIgnored() {
        context.start();
        SpringRabbitMQEndpoint endpoint = context.getEndpoint(
                "spring-rabbitmq:orders?connectionFactory=#cf&queues=orders&routingKey=new", SpringRabbitMQEndpoint.class);
        AmqpAdmin admin = mock(AmqpAdmin.class);
        // what RabbitAdmin answers when it ignores the failed declaration
        when(admin.declareQueue(any(Queue.class))).thenReturn(null);

        endpoint.declareElements(new DirectMessageListenerContainer(connectionFactory), admin);

        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(admin).declareBinding(binding.capture());
        assertEquals("orders", binding.getValue().getDestination());
        assertEquals("orders", binding.getValue().getExchange());
        assertEquals("new", binding.getValue().getRoutingKey());
    }

    @Test
    void generatedQueueIsConsumedByItsNameWhenItsDeclarationFailsAndIsIgnored() {
        context.start();
        // no queues: the consumer declares a queue with a name generated on the client
        SpringRabbitMQEndpoint endpoint = context.getEndpoint(
                "spring-rabbitmq:orders?connectionFactory=#cf&routingKey=new", SpringRabbitMQEndpoint.class);
        AbstractMessageListenerContainer container = new DirectMessageListenerContainer(connectionFactory);
        AmqpAdmin admin = mock(AmqpAdmin.class);
        when(admin.declareQueue(any(Queue.class))).thenReturn(null);

        endpoint.declareElements(container, admin);

        ArgumentCaptor<Queue> queue = ArgumentCaptor.forClass(Queue.class);
        verify(admin).declareQueue(queue.capture());
        String generated = queue.getValue().getName();
        assertArrayEquals(new String[] { generated }, container.getQueueNames());
        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(admin).declareBinding(binding.capture());
        assertEquals(generated, binding.getValue().getDestination());
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
