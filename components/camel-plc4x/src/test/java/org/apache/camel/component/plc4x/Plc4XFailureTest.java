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
package org.apache.camel.component.plc4x;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A write or read that could not be done must fail the exchange, not complete it as if it had been done.
 */
class Plc4XFailureTest {

    private final CamelContext context = new DefaultCamelContext();
    private final Plc4XEndpoint endpoint = mock(Plc4XEndpoint.class, RETURNS_DEEP_STUBS);

    @BeforeEach
    void setUp() {
        context.start();
        when(endpoint.getEndpointUri()).thenReturn("plc4x:mock:10.10.10.1/1/1");
        when(endpoint.createExchange()).thenAnswer(invocation -> new DefaultExchange(context));
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void testProducerFailsWhenReconnectFails() throws Exception {
        PlcConnectionException cause = new PlcConnectionException("PLC unreachable");
        doThrow(cause).when(endpoint).reconnectIfNeeded();
        Map<String, Map<String, Object>> tags = Map.of("test", Map.of("testAddress", 1));
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(tags);

        new Plc4XProducer(endpoint).process(exchange, doneSync -> {
        });

        assertSame(cause, exchange.getException());
        // the message is kept, so that a redelivery writes it again
        assertEquals(tags, exchange.getIn().getBody());
    }

    @Test
    void testProducerKeepsTheMessageWhenTheWriteFails() throws Exception {
        PlcWriteRequest request = mock(PlcWriteRequest.class);
        when(request.execute()).thenAnswer(invocation -> CompletableFuture.failedFuture(new PlcRuntimeException("denied")));
        Map<String, Map<String, Object>> tags = Map.of("test", Map.of("testAddress", 1));
        when(endpoint.buildPlcWriteRequest(tags)).thenReturn(request);
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(tags);

        new Plc4XProducer(endpoint).process(exchange, doneSync -> {
        });

        assertInstanceOf(ExecutionException.class, exchange.getException());
        assertEquals(tags, exchange.getIn().getBody());
    }

    @Test
    void testPollingConsumerFailsWhenReconnectFails() throws Exception {
        PlcConnectionException cause = new PlcConnectionException("PLC unreachable");
        doThrow(cause).when(endpoint).reconnectIfNeeded();

        Exchange exchange = new Plc4XPollingConsumer(endpoint).receive(1000);

        assertNotNull(exchange);
        assertSame(cause, exchange.getException());
    }

    @Test
    void testPollingConsumerFailsWhenReadFails() throws Exception {
        PlcRuntimeException cause = new PlcRuntimeException("read failed");
        PlcReadRequest request = mock(PlcReadRequest.class);
        when(request.execute()).thenAnswer(invocation -> CompletableFuture.failedFuture(cause));
        endpoint.connection = mock(PlcConnection.class);
        when(endpoint.buildPlcReadRequest()).thenReturn(request);

        Exchange exchange = new Plc4XPollingConsumer(endpoint).receive(1000);

        assertNotNull(exchange);
        assertSame(cause, exchange.getException());
    }

    @Test
    void testPollingConsumerReturnsNullWithoutResponse() throws Exception {
        PlcReadRequest request = mock(PlcReadRequest.class);
        when(request.execute()).thenAnswer(invocation -> new CompletableFuture<>());
        endpoint.connection = mock(PlcConnection.class);
        when(endpoint.buildPlcReadRequest()).thenReturn(request);

        // receiveNoWait and receive(timeout) return null when nothing is received in time
        assertNull(new Plc4XPollingConsumer(endpoint).receiveNoWait());
        assertNull(new Plc4XPollingConsumer(endpoint).receive(10));
    }

    @Test
    void testPollingConsumerReadsTags() throws Exception {
        endpoint.connection = mock(PlcConnection.class);
        PlcReadRequest request = mock(PlcReadRequest.class, RETURNS_DEEP_STUBS);
        when(endpoint.buildPlcReadRequest()).thenReturn(request);

        Exchange exchange = new Plc4XPollingConsumer(endpoint).receive(1000);

        assertNotNull(exchange);
        assertNull(exchange.getException());
        assertInstanceOf(Map.class, exchange.getIn().getBody());
    }
}
