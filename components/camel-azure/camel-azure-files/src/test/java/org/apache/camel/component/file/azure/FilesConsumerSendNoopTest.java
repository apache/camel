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
package org.apache.camel.component.file.azure;

import java.io.UncheckedIOException;
import java.net.SocketException;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The configuration of this component turns sendNoop off for the producer only, so the consumer still checks the share
 * before each poll, also after an I/O failure while processing a file.
 */
class FilesConsumerSendNoopTest extends CamelTestSupport {

    private final FilesOperations operations = mock(FilesOperations.class);

    @Test
    void testConsumerChecksShareBeforeEachPoll() {
        TestConsumer consumer = createLoggedInConsumer();
        when(operations.sendNoop()).thenReturn(true);

        consumer.checkConnection();

        verify(operations, times(1)).sendNoop();
        verify(operations, never()).isConnected();
        verify(operations, never()).connect(any(), any());
    }

    @Test
    void testConsumerChecksShareAfterIOFailure() {
        TestConsumer consumer = createLoggedInConsumer();
        when(operations.sendNoop()).thenReturn(true);

        consumer.handleFailure(new GenericFileOperationFailedException(
                "Cannot retrieve file", new UncheckedIOException(new SocketException("Connection reset"))));
        consumer.checkConnection();

        verify(operations, times(1)).sendNoop();
        verify(operations, never()).isConnected();
        verify(operations, never()).forceDisconnect();
        verify(operations, never()).connect(any(), any());
    }

    private TestConsumer createLoggedInConsumer() {
        FilesEndpoint endpoint = context.getEndpoint("azure-files://account/share/inbox", FilesEndpoint.class);
        assertFalse(endpoint.getConfiguration().isSendNoop(), "sendNoop should be off for the producer");
        TestConsumer consumer = new TestConsumer(endpoint, operations);
        when(operations.connect(any(), any())).thenReturn(true);
        consumer.checkConnection();
        verify(operations, times(1)).connect(any(), any());
        Mockito.clearInvocations(operations);
        return consumer;
    }

    /**
     * Gives the test access to the connection check that the consumer does before each poll, and to its handling of a
     * failure while processing a file.
     */
    private static final class TestConsumer extends FilesConsumer {

        TestConsumer(FilesEndpoint endpoint, FilesOperations operations) {
            super(endpoint, exchange -> {
            }, operations, null);
        }

        void checkConnection() {
            connectIfNecessary();
        }

        void handleFailure(Throwable t) {
            handleException("Error processing file", null, t);
        }
    }
}
