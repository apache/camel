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
package org.apache.camel.component.file.remote.mina;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test that verifies the connection check the consumer does before each poll: by default it sends a noop (a stat
 * of the root directory), and with sendNoop=false it only checks whether the session and the SFTP client are open,
 * unless an operation failed with an I/O error.
 */
class MinaSftpConsumerSendNoopTest extends CamelTestSupport {

    private final MinaSftpOperations operations = mock(MinaSftpOperations.class);

    /**
     * Verifies that with sendNoop=false the consumer checks the connection state of the client and sends no noop.
     */
    @Test
    void testSendNoopFalseChecksConnectionStateInsteadOfNoop() {
        TestConsumer consumer = createLoggedInConsumer("mina-sftp://localhost/inbox?sendNoop=false");
        assertFalse(consumer.getEndpoint().getConfiguration().isSendNoop(), "sendNoop should be false");
        when(operations.isConnected()).thenReturn(true);

        consumer.checkConnection();

        verify(operations, times(1)).isConnected();
        verify(operations, never()).sendNoop();
        verify(operations, never()).connect(any(), any());
    }

    /**
     * Verifies that with sendNoop=false the consumer connects again when the client is no longer connected.
     */
    @Test
    void testSendNoopFalseReconnectsWhenNotConnected() {
        TestConsumer consumer = createLoggedInConsumer("mina-sftp://localhost/inbox?sendNoop=false");
        when(operations.isConnected()).thenReturn(false);

        consumer.checkConnection();

        verify(operations, never()).sendNoop();
        verify(operations, times(1)).connect(any(), any());
    }

    /**
     * Verifies that with sendNoop=false a status reply from the server, which is an I/O error in this client, makes the
     * consumer send a noop before the next poll, and that it keeps the connection when the noop succeeds.
     */
    @Test
    void testSendNoopFalseSendsNoopAfterStatusReply() {
        TestConsumer consumer = createLoggedInConsumer("mina-sftp://localhost/inbox?sendNoop=false");
        when(operations.isConnected()).thenReturn(true);
        when(operations.sendNoop()).thenReturn(true);

        consumer.fail(new GenericFileOperationFailedException(
                "Cannot retrieve file", new SftpException(SftpConstants.SSH_FX_PERMISSION_DENIED, "Permission denied")));
        consumer.checkConnection();

        verify(operations, times(1)).sendNoop();
        verify(operations, never()).forceDisconnect();
        verify(operations, never()).connect(any(), any());

        // and the poll after that only checks the connection state again
        consumer.checkConnection();

        verify(operations, times(1)).sendNoop();
        verify(operations, times(1)).isConnected();
    }

    /**
     * Verifies that by default the consumer sends the noop, and does not only check the connection state.
     */
    @Test
    void testSendNoopDefaultSendsNoop() {
        TestConsumer consumer = createLoggedInConsumer("mina-sftp://localhost/inbox");
        assertTrue(consumer.getEndpoint().getConfiguration().isSendNoop(), "sendNoop should be true by default");
        when(operations.sendNoop()).thenReturn(true);

        consumer.checkConnection();

        verify(operations, times(1)).sendNoop();
        verify(operations, never()).isConnected();
        verify(operations, never()).connect(any(), any());
    }

    /**
     * Creates a consumer that has connected and logged in once, as after its first poll.
     */
    private TestConsumer createLoggedInConsumer(String uri) {
        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
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
    private static final class TestConsumer extends MinaSftpConsumer {

        TestConsumer(MinaSftpEndpoint endpoint, MinaSftpOperations operations) {
            super(endpoint, exchange -> {
            }, operations, null);
        }

        void checkConnection() {
            connectIfNecessary();
        }

        void fail(Throwable t) {
            handleException("Error processing file", null, t);
        }
    }
}
