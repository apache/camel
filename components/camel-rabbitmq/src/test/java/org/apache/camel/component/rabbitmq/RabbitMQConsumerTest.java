/**
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
package org.apache.camel.component.rabbitmq;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import com.rabbitmq.client.Consumer;
import org.apache.camel.Processor;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

public class RabbitMQConsumerTest {

    private RabbitMQEndpoint endpoint = Mockito.mock(RabbitMQEndpoint.class);
    private Connection conn = Mockito.mock(Connection.class);
    private Processor processor = Mockito.mock(Processor.class);
    private Channel channel = Mockito.mock(Channel.class);

    @Test
    public void testStoppingConsumerShutdownExecutor() throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer(endpoint, processor);

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        Mockito.when(endpoint.createExecutor()).thenReturn(e);
        Mockito.when(endpoint.getConcurrentConsumers()).thenReturn(1);
        Mockito.when(endpoint.connect(any(ExecutorService.class))).thenReturn(conn);
        Mockito.when(conn.createChannel()).thenReturn(channel);

        consumer.doStart();
        assertFalse(e.isShutdown());

        consumer.doStop();
        assertTrue(e.isShutdown());
    }

    @Test
    public void testStoppingConsumerShutdownConnection() throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer(endpoint, processor);

        Mockito.when(endpoint.createExecutor()).thenReturn(Executors.newFixedThreadPool(3));
        Mockito.when(endpoint.getConcurrentConsumers()).thenReturn(1);
        Mockito.when(endpoint.connect(any(ExecutorService.class))).thenReturn(conn);
        Mockito.when(conn.createChannel()).thenReturn(channel);

        consumer.doStart();
        consumer.doStop();

        Mockito.verify(conn).close(30 * 1000);
    }

    @Test
    public void testStoppingConsumerShutdownConnectionWhenServerHasClosedChannel() throws Exception {
        AlreadyClosedException alreadyClosedException = Mockito.mock(AlreadyClosedException.class);

        RabbitMQConsumer consumer = new RabbitMQConsumer(endpoint, processor);

        Mockito.when(endpoint.createExecutor()).thenReturn(Executors.newFixedThreadPool(3));
        Mockito.when(endpoint.getConcurrentConsumers()).thenReturn(1);
        Mockito.when(endpoint.connect(any(ExecutorService.class))).thenReturn(conn);
        Mockito.when(conn.createChannel()).thenReturn(channel);
        Mockito.when(channel.basicConsume(anyString(), anyBoolean(), any(Consumer.class))).thenReturn("TAG");
        Mockito.when(channel.isOpen()).thenReturn(false);
        Mockito.doThrow(alreadyClosedException).when(channel).basicCancel("TAG");
        Mockito.doThrow(alreadyClosedException).when(channel).close();

        consumer.doStart();
        consumer.doStop();

        Mockito.verify(conn).close(30 * 1000);
    }
}
