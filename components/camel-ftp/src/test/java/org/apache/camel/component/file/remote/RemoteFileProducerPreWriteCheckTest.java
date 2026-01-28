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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RemoteFileProducer#preWriteCheck(Exchange)} to verify that reconnection is properly triggered
 * when NOOP fails.
 */
@ExtendWith(MockitoExtension.class)
class RemoteFileProducerPreWriteCheckTest {

    @Mock
    private RemoteFileEndpoint<Object> endpoint;

    @Mock
    private RemoteFileOperations<Object> operations;

    @Mock
    private RemoteFileConfiguration configuration;

    @Mock
    private Exchange exchange;

    private RemoteFileProducer<Object> producer;

    @BeforeEach
    void setUp() {
        when(endpoint.getConfiguration()).thenReturn(configuration);
        producer = new RemoteFileProducer<>(endpoint, operations);
    }

    /**
     * Helper method to put the producer in a logged-in state by simulating a successful connection.
     */
    private void simulateLoggedIn() throws Exception {
        // First call to preWriteCheck when not logged in will trigger connect
        when(operations.connect(any(RemoteFileConfiguration.class), any(Exchange.class))).thenReturn(true);
        producer.preWriteCheck(exchange);
        // Reset the mock to clear the interaction count
        verify(operations, times(1)).connect(configuration, exchange);
        org.mockito.Mockito.clearInvocations(operations);
    }

    /**
     * Test that when sendNoop() returns false (without throwing an exception), the producer forces reconnection by
     * setting loggedIn to false.
     * <p>
     * This is the main bug fix test - previously, when sendNoop() returned false without throwing an exception,
     * loggedIn remained true and reconnection might be skipped if isConnected() returned true.
     */
    @Test
    void testNoopReturnsFalseShouldForceReconnection() throws Exception {
        // Setup: producer is logged in, sendNoop is enabled
        when(configuration.isSendNoop()).thenReturn(true);
        simulateLoggedIn();

        // sendNoop() returns false (connection is dead, but no exception)
        when(operations.sendNoop()).thenReturn(false);

        // Note: with the fix, loggedIn is set to false when noop returns false,
        // so isConnected() is never called due to short-circuit evaluation in connectIfNecessary:
        // if (!loggedIn || !getOperations().isConnected())
        // This is the correct behavior - we don't need to check isConnected() when we know the connection is bad.

        // connect() succeeds
        when(operations.connect(any(RemoteFileConfiguration.class), any(Exchange.class))).thenReturn(true);

        // Act
        producer.preWriteCheck(exchange);

        // Assert: connect should be called because loggedIn was set to false when noop returned false
        verify(operations, times(1)).connect(configuration, exchange);
    }

    /**
     * Test that when sendNoop() throws an exception, the producer forces reconnection. This is the existing behavior
     * that should still work.
     */
    @Test
    void testNoopThrowsExceptionShouldForceReconnection() throws Exception {
        // Setup: producer is logged in, sendNoop is enabled
        when(configuration.isSendNoop()).thenReturn(true);
        simulateLoggedIn();

        // sendNoop() throws an exception
        when(operations.sendNoop()).thenThrow(new GenericFileOperationFailedException("Connection reset"));

        // Note: loggedIn is set to false when exception is caught, so isConnected() is never called
        // due to short-circuit evaluation in connectIfNecessary.

        // connect() succeeds
        when(operations.connect(any(RemoteFileConfiguration.class), any(Exchange.class))).thenReturn(true);

        // Act
        producer.preWriteCheck(exchange);

        // Assert: connect should be called
        verify(operations, times(1)).connect(configuration, exchange);
    }

    /**
     * Test that when sendNoop() returns true (connection is alive), no reconnection is attempted.
     */
    @Test
    void testNoopReturnsTrueShouldNotReconnect() throws Exception {
        // Setup: producer is logged in, sendNoop is enabled
        when(configuration.isSendNoop()).thenReturn(true);
        simulateLoggedIn();

        // sendNoop() returns true (connection is alive)
        when(operations.sendNoop()).thenReturn(true);

        // Act
        producer.preWriteCheck(exchange);

        // Assert: connect should NOT be called
        verify(operations, never()).connect(any(RemoteFileConfiguration.class), any(Exchange.class));
    }

    /**
     * Test that when sendNoop is disabled, the connection check is skipped and no reconnection is attempted (when
     * already logged in).
     */
    @Test
    void testSendNoopDisabledShouldNotReconnect() throws Exception {
        // Setup: producer is logged in, but sendNoop is disabled
        when(configuration.isSendNoop()).thenReturn(false);
        simulateLoggedIn();

        // Act
        producer.preWriteCheck(exchange);

        // Assert: sendNoop should NOT be called
        verify(operations, never()).sendNoop();
        // Assert: connect should NOT be called
        verify(operations, never()).connect(any(RemoteFileConfiguration.class), any(Exchange.class));
    }

    /**
     * Test that when not logged in, reconnection is attempted regardless of noop.
     */
    @Test
    void testNotLoggedInShouldConnect() throws Exception {
        // Setup: producer is NOT logged in (default state after construction)

        // Note: isConnected() is not called because !loggedIn is already true (short-circuit evaluation)

        // connect() succeeds
        when(operations.connect(any(RemoteFileConfiguration.class), any(Exchange.class))).thenReturn(true);

        // Act
        producer.preWriteCheck(exchange);

        // Assert: sendNoop should NOT be called (we're not logged in)
        verify(operations, never()).sendNoop();
        // Assert: connect should be called
        verify(operations, times(1)).connect(configuration, exchange);
    }
}
