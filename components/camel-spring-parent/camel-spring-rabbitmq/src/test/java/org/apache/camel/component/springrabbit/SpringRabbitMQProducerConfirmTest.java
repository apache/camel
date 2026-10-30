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

import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With the default {@code confirm=auto}, an InOnly producer waits for the publisher confirm when the connection factory
 * has publisher confirms, of either type.
 * <p>
 * The connection factory is a real Spring {@link CachingConnectionFactory}: it decides what
 * {@code isPublisherConfirms()} (true for {@link ConfirmType#CORRELATED} only) and {@code isSimplePublisherConfirms()}
 * (true for {@link ConfirmType#SIMPLE} only) answer, and it puts the channel in confirm mode. Below it, the RabbitMQ
 * client is mocked; its {@link Channel#waitForConfirms(long)} answers as the RabbitMQ Java client documents it:
 * {@code true} when every message published since the last call was acknowledged, {@code false} when one was negatively
 * acknowledged (for example by a queue with {@code x-overflow=reject-publish} that is full), and a
 * {@link TimeoutException} when the timeout elapses. Spring's {@code RabbitTemplate.waitForConfirms(long)} turns that
 * {@link TimeoutException} into an {@link AmqpTimeoutException}.
 */
class SpringRabbitMQProducerConfirmTest {

    private final Connection connection = mock(Connection.class);
    private final Channel channel = mock(Channel.class);
    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);
        when(channel.isOpen()).thenReturn(true);
        when(channel.getConnection()).thenReturn(connection);
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void negativeAcknowledgementFailsTheExchangeWithSimplePublisherConfirms() throws Exception {
        when(channel.waitForConfirms(anyLong())).thenReturn(false);

        Exchange exchange = send(ConfirmType.SIMPLE);

        assertInstanceOf(TimeoutException.class, exchange.getException());
        assertEquals("Message negatively acknowledged by RabbitMQ", exchange.getException().getMessage());
        verify(channel).confirmSelect();
        verify(channel).basicPublish(eq("orders"), eq("new"), eq(false), any(), any());
    }

    @Test
    void missingConfirmFailsTheExchangeWithSimplePublisherConfirms() throws Exception {
        when(channel.waitForConfirms(anyLong())).thenThrow(new TimeoutException());

        Exchange exchange = send(ConfirmType.SIMPLE);

        assertInstanceOf(AmqpTimeoutException.class, exchange.getException());
        assertInstanceOf(TimeoutException.class, exchange.getException().getCause());
        verify(channel).waitForConfirms(5000L);
    }

    @Test
    void acknowledgedMessageIsSentWithSimplePublisherConfirms() throws Exception {
        when(channel.waitForConfirms(anyLong())).thenReturn(true);

        Exchange exchange = send(ConfirmType.SIMPLE);

        assertNull(exchange.getException());
        verify(channel).waitForConfirms(5000L);
    }

    @Test
    void acknowledgedMessageIsSentWithCorrelatedPublisherConfirms() throws Exception {
        // control, which passes without the fix too
        when(channel.waitForConfirms(anyLong())).thenReturn(true);

        Exchange exchange = send(ConfirmType.CORRELATED);

        assertNull(exchange.getException());
        verify(channel).waitForConfirms(5000L);
    }

    @Test
    void noConfirmIsAwaitedWithoutPublisherConfirms() throws Exception {
        // control, which passes without the fix too
        Exchange exchange = send(ConfirmType.NONE);

        assertNull(exchange.getException());
        verify(channel).basicPublish(eq("orders"), eq("new"), eq(false), any(), any());
        verify(channel, never()).waitForConfirms(anyLong());
    }

    private Exchange send(ConfirmType confirmType) throws Exception {
        ConnectionFactory rabbit = mock(ConnectionFactory.class, invocation -> invocation.getMethod().getReturnType()
                .equals(Connection.class) ? connection : RETURNS_DEFAULTS.answer(invocation));
        CachingConnectionFactory cf = new CachingConnectionFactory(rabbit);
        cf.setPublisherConfirmType(confirmType);
        context.getRegistry().bind("cf", cf);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("spring-rabbitmq:orders?routingKey=new&connectionFactory=#cf");
            }
        });
        context.start();
        ProducerTemplate template = context.createProducerTemplate();
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody("order 1"));
        return exchange;
    }
}
