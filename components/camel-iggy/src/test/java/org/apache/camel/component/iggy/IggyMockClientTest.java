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
package org.apache.camel.component.iggy;

import java.io.Closeable;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.iggy.client.IggyClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.message.Partitioning;
import org.apache.iggy.message.PollingStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * The producer and the consumer must give back or discard the pooled client of a failed request, and close their
 * clients when they stop; without autoCommit the consumer starts at the default starting offset 0. The Iggy clients are
 * mocks, no Iggy server is needed.
 */
public class IggyMockClientTest {

    private static final String URI
            = "iggy:topic?streamName=stream&autoCreateStream=false&autoCreateTopic=false&consumerGroupName=group";

    private final AtomicInteger closed = new AtomicInteger();

    @Test
    void testFailedSendsDoNotExhaustThePool() throws Exception {
        IggyBaseClient client = newClient();
        when(client.messages().sendMessages(any(StreamId.class), any(TopicId.class), any(Partitioning.class), anyList()))
                .thenThrow(new IllegalStateException("Connection reset"));

        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint = context.getEndpoint(URI);
            // created and started in this thread, where the client factory is mocked
            Producer producer = endpoint.createProducer();
            producer.start();

            // the pool holds at most 8 clients: if each failed send keeps its client, the 9th send waits forever
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                for (int i = 0; i < 10; i++) {
                    Exchange exchange = endpoint.createExchange();
                    exchange.getIn().setBody("hello");
                    producer.process(exchange);
                    assertInstanceOf(IllegalStateException.class, exchange.getException());
                }
            });
            // the clients of the failed sends are not reused but closed
            assertEquals(10, closed.get());

            producer.stop();
        }
    }

    @Test
    void testProducerStopClosesTheClients() throws Exception {
        IggyBaseClient client = newClient();

        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Producer producer = context.getEndpoint(URI).createProducer();
            producer.start();
            assertEquals(0, closed.get());

            producer.stop();
            assertEquals(1, closed.get());
        }
    }

    @Test
    void testFailedPollDiscardsTheClient() throws Exception {
        IggyBaseClient client = newClient();
        AtomicInteger polls = new AtomicInteger();
        AtomicInteger closedBeforeSecondPoll = new AtomicInteger(-1);
        CountDownLatch secondPoll = new CountDownLatch(1);
        when(client.messages().pollMessages(any(StreamId.class), any(TopicId.class), any(), any(), any(), any(),
                anyBoolean())).thenAnswer(invocation -> {
                    if (polls.incrementAndGet() == 2) {
                        closedBeforeSecondPoll.set(closed.get());
                        secondPoll.countDown();
                    }
                    throw new IllegalStateException("Connection reset");
                });

        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Consumer consumer = context.getEndpoint(URI).createConsumer(exchange -> {
            });
            consumer.start();

            assertTrue(secondPoll.await(30, TimeUnit.SECONDS));
            // the client of the failed poll was closed (not kept out of the pool) before the next poll
            assertEquals(1, closedBeforeSecondPoll.get());

            consumer.stop();
        }
    }

    @Test
    void testManualCommitStartsAtOffsetZeroByDefault() throws Exception {
        IggyBaseClient client = newClient();
        AtomicReference<PollingStrategy> strategy = new AtomicReference<>();
        CountDownLatch polled = new CountDownLatch(1);
        when(client.messages().pollMessages(any(StreamId.class), any(TopicId.class), any(), any(), any(), any(),
                anyBoolean())).thenAnswer(invocation -> {
                    strategy.compareAndSet(null, invocation.getArgument(4));
                    polled.countDown();
                    throw new IllegalStateException("Stop here");
                });

        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Consumer consumer = context.getEndpoint(URI + "&autoCommit=false").createConsumer(exchange -> {
            });
            consumer.start();

            assertTrue(polled.await(30, TimeUnit.SECONDS));
            assertEquals(PollingStrategy.offset(BigInteger.ZERO), strategy.get());

            consumer.stop();
        }
    }

    private IggyBaseClient newClient() throws Exception {
        IggyBaseClient client = mock(IggyBaseClient.class,
                withSettings().extraInterfaces(Closeable.class).defaultAnswer(RETURNS_DEEP_STUBS));
        doAnswer(invocation -> closed.incrementAndGet()).when((Closeable) client).close();
        return client;
    }

    private static MockedConstruction<IggyClientFactory> mockFactories(IggyBaseClient client) {
        return mockConstruction(IggyClientFactory.class, withSettings().defaultAnswer(CALLS_REAL_METHODS),
                (factory, context) -> doReturn(client).when(factory).create());
    }
}
