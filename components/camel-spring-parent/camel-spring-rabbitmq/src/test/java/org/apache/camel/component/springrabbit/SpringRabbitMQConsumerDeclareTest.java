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
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With {@code autoDeclare=true} (the default), the consumer declares the exchange, queue and binding when it starts,
 * and again on every new connection of the connection factory. When RabbitMQ is not available, the consumer starts
 * anyway, unless {@code testConnectionOnStartup} is enabled.
 */
class SpringRabbitMQConsumerDeclareTest {

    private CamelContext context;
    private CachingConnectionFactory brokerDown;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        if (brokerDown != null) {
            brokerDown.destroy();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void consumerStartsWhileTheBrokerIsDown(String containerType) throws Exception {
        brokerDown();
        addRoute("spring-rabbitmq:orders?connectionFactory=#cf&queues=orders&routingKey=new"
                 + "&messageListenerContainerType=" + containerType);

        assertDoesNotThrow(() -> context.start());
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("orders"));
    }

    @Test
    void consumerFailsToStartWhileTheBrokerIsDownWithTestConnectionOnStartup() throws Exception {
        brokerDown();
        addRoute("spring-rabbitmq:orders?connectionFactory=#cf&queues=orders&routingKey=new&testConnectionOnStartup=true");

        assertThrows(FailedToStartRouteException.class, () -> context.start());
    }

    @Test
    void consumerFailsToStartWhileTheBrokerIsDownWhenConnectionsAreCached() throws Exception {
        // as RabbitAdmin, the consumer does not declare on new connections with this cache mode: it declares on start only
        brokerDown().setCacheMode(CachingConnectionFactory.CacheMode.CONNECTION);
        addRoute("spring-rabbitmq:orders?connectionFactory=#cf&queues=orders&routingKey=new");

        assertThrows(FailedToStartRouteException.class, () -> context.start());
    }

    @Test
    void consumerDeclaresOnEveryNewConnection() throws Exception {
        ConnectionFactory cf = connectedConnectionFactory();
        AmqpAdmin admin = admin();
        addRoute("spring-rabbitmq:orders?connectionFactory=#cf&queues=orders&routingKey=new&autoStartup=false");

        context.start();

        // once, although the connection factory called the listener when it was added, as a connected one does
        ConnectionListener listener = connectionListener(cf);
        verify(admin).declareExchange(any(Exchange.class));
        verify(admin).declareQueue(any(Queue.class));
        verify(admin).declareBinding(any(Binding.class));

        listener.onCreate(mock(Connection.class));
        listener.onCreate(mock(Connection.class));

        verify(admin, times(3)).declareExchange(any(Exchange.class));
        ArgumentCaptor<Queue> queues = ArgumentCaptor.forClass(Queue.class);
        verify(admin, times(3)).declareQueue(queues.capture());
        assertEquals(List.of("orders", "orders", "orders"), queues.getAllValues().stream().map(Queue::getName).toList());
        verify(admin, times(3)).declareBinding(any(Binding.class));

        context.getRouteController().stopRoute("orders");
        verify(cf).removeConnectionListener(listener);
    }

    @Test
    void consumerDeclaresTheQueueItGeneratedOnEveryNewConnection() throws Exception {
        ConnectionFactory cf = connectedConnectionFactory();
        AmqpAdmin admin = admin();
        // no queues: the consumer declares a queue with a generated name
        addRoute("spring-rabbitmq:orders?connectionFactory=#cf&routingKey=new&autoStartup=false");

        context.start();
        connectionListener(cf).onCreate(mock(Connection.class));

        ArgumentCaptor<Queue> queues = ArgumentCaptor.forClass(Queue.class);
        verify(admin, times(2)).declareQueue(queues.capture());
        String generated = queues.getAllValues().get(0).getName();
        assertEquals(generated, queues.getAllValues().get(1).getName());
        ArgumentCaptor<Binding> bindings = ArgumentCaptor.forClass(Binding.class);
        verify(admin, times(2)).declareBinding(bindings.capture());
        assertEquals(List.of(generated, generated),
                bindings.getAllValues().stream().map(Binding::getDestination).toList());
    }

    @Test
    void consumerWithoutAutoDeclareDoesNotListenToNewConnections() throws Exception {
        ConnectionFactory cf = connectedConnectionFactory();
        AmqpAdmin admin = admin();
        addRoute("spring-rabbitmq:orders?connectionFactory=#cf&queues=orders&routingKey=new&autoStartup=false"
                 + "&autoDeclare=false");

        context.start();

        verify(cf, never()).addConnectionListener(any(ConnectionListener.class));
        verify(admin, never()).declareQueue(any(Queue.class));
    }

    private CachingConnectionFactory brokerDown() throws IOException {
        // nothing listens on this port
        brokerDown = new CachingConnectionFactory("localhost", closedPort());
        context.getRegistry().bind("cf", brokerDown);
        return brokerDown;
    }

    /**
     * A connection factory that is connected: it calls a connection listener when the listener is added, as
     * {@link CachingConnectionFactory} does.
     */
    private ConnectionFactory connectedConnectionFactory() {
        ConnectionFactory cf = mock(ConnectionFactory.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, ConnectionListener.class).onCreate(mock(Connection.class));
            return null;
        }).when(cf).addConnectionListener(any(ConnectionListener.class));
        context.getRegistry().bind("cf", cf);
        return cf;
    }

    private AmqpAdmin admin() {
        AmqpAdmin admin = mock(AmqpAdmin.class);
        when(admin.declareQueue(any(Queue.class))).thenAnswer(invocation -> invocation.getArgument(0, Queue.class).getName());
        context.getComponent("spring-rabbitmq", SpringRabbitMQComponent.class).setAmqpAdmin(admin);
        return admin;
    }

    private static ConnectionListener connectionListener(ConnectionFactory cf) {
        ArgumentCaptor<ConnectionListener> listener = ArgumentCaptor.forClass(ConnectionListener.class);
        verify(cf).addConnectionListener(listener.capture());
        return listener.getValue();
    }

    private void addRoute(String from) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("orders").to("mock:orders");
            }
        });
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
