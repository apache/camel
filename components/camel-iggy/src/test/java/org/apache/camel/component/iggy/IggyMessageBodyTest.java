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

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.iggy.client.IggyClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.message.Message;
import org.apache.iggy.message.MessageHeader;
import org.apache.iggy.message.MessageId;
import org.apache.iggy.message.Partitioning;
import org.apache.iggy.message.PolledMessages;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * The producer sends bytes, streams and files as they are and text as UTF-8, and the consumer gives the payload as
 * bytes, so a binary payload survives the round trip. No body, an empty List and a List with a null element are handled
 * before the Iggy client is called. The Iggy client is a mock, no Iggy server is needed.
 */
public class IggyMessageBodyTest {

    private static final String URI
            = "iggy:topic?streamName=stream&autoCreateStream=false&autoCreateTopic=false&consumerGroupName=group";

    // not valid UTF-8: decoding it as text replaces ff and fe with the replacement character
    private static final byte[] BINARY = { (byte) 0xff, (byte) 0xfe, 0x00, 0x41 };

    @Test
    void testByteArrayBodyIsSentAsItsBytes() throws Exception {
        Message message = sendOne(BINARY.clone());

        assertArrayEquals(BINARY, message.payload());
        assertEquals(BINARY.length, message.header().payloadLength());
        assertEquals(0L, message.header().userHeadersLength());
    }

    @Test
    void testStreamBodyIsSentAsItsBytes() throws Exception {
        Message message = sendOne(new ByteArrayInputStream(BINARY.clone()));

        assertArrayEquals(BINARY, message.payload());
        assertEquals(BINARY.length, message.header().payloadLength());
    }

    @Test
    void testTextBodyIsSentAsUtf8() throws Exception {
        Message message = sendOne("Grüße");

        assertArrayEquals("Grüße".getBytes(StandardCharsets.UTF_8), message.payload());
    }

    @Test
    void testNoBodyFailsBeforeSending() throws Exception {
        IggyBaseClient client = newClient();

        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint = context.getEndpoint(URI);
            Producer producer = endpoint.createProducer();
            producer.start();

            Exchange exchange = endpoint.createExchange();
            producer.process(exchange);

            assertInstanceOf(InvalidPayloadException.class, exchange.getException());
            verify(client.messages(), never()).sendMessages(any(StreamId.class), any(TopicId.class), any(Partitioning.class),
                    anyList());

            producer.stop();
        }
    }

    @Test
    void testEmptyListSendsNothing() throws Exception {
        IggyBaseClient client = newClient();

        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint = context.getEndpoint(URI);
            Producer producer = endpoint.createProducer();
            producer.start();

            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(Collections.emptyList());
            producer.process(exchange);

            assertNull(exchange.getException());
            verify(client.messages(), never()).sendMessages(any(StreamId.class), any(TopicId.class), any(Partitioning.class),
                    anyList());

            producer.stop();
        }
    }

    @Test
    void testListWithNullElementFailsBeforeSending() throws Exception {
        IggyBaseClient client = newClient();

        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint = context.getEndpoint(URI);
            Producer producer = endpoint.createProducer();
            producer.start();

            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(Arrays.asList("Hello", null));
            producer.process(exchange);

            RuntimeCamelException e = assertInstanceOf(RuntimeCamelException.class, exchange.getException());
            assertEquals("The List body must not contain null elements", e.getMessage());
            verify(client.messages(), never()).sendMessages(any(StreamId.class), any(TopicId.class), any(Partitioning.class),
                    anyList());

            producer.stop();
        }
    }

    @Test
    void testConsumerGivesThePayloadAsBytes() throws Exception {
        IggyBaseClient client = newClient();
        MessageHeader header = new MessageHeader(
                BigInteger.ZERO, MessageId.from(BigInteger.ONE), BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                0L, (long) BINARY.length, BigInteger.ZERO);
        Message message = Message.of(header, BINARY.clone(), Collections.emptyList());
        AtomicInteger polls = new AtomicInteger();
        when(client.messages().pollMessages(any(StreamId.class), any(TopicId.class), any(), any(), any(), any(),
                anyBoolean())).thenAnswer(
                        invocation -> polls.incrementAndGet() == 1
                                ? new PolledMessages(0L, BigInteger.ZERO, 1L, List.of(message))
                                : new PolledMessages(0L, BigInteger.ZERO, 0L, List.of()));

        AtomicReference<Object> body = new AtomicReference<>();
        CountDownLatch received = new CountDownLatch(1);
        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Consumer consumer = context.getEndpoint(URI).createConsumer(exchange -> {
                body.set(exchange.getIn().getBody());
                received.countDown();
            });
            consumer.start();

            assertTrue(received.await(30, TimeUnit.SECONDS));
            byte[] payload = assertInstanceOf(byte[].class, body.get());
            assertArrayEquals(BINARY, payload);

            consumer.stop();
        }
    }

    private Message sendOne(Object body) throws Exception {
        IggyBaseClient client = newClient();
        List<Message> sent = new ArrayList<>();
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.captor();

        try (MockedConstruction<IggyClientFactory> factories = mockFactories(client);
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint = context.getEndpoint(URI);
            Producer producer = endpoint.createProducer();
            producer.start();

            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(body);
            producer.process(exchange);

            assertNull(exchange.getException());
            verify(client.messages()).sendMessages(any(StreamId.class), any(TopicId.class), any(Partitioning.class),
                    captor.capture());
            sent.addAll(captor.getValue());

            producer.stop();
        }
        assertEquals(1, sent.size());
        return sent.get(0);
    }

    private static IggyBaseClient newClient() {
        return mock(IggyBaseClient.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
    }

    private static MockedConstruction<IggyClientFactory> mockFactories(IggyBaseClient client) {
        return mockConstruction(IggyClientFactory.class, withSettings().defaultAnswer(CALLS_REAL_METHODS),
                (factory, context) -> doReturn(client).when(factory).create());
    }
}
