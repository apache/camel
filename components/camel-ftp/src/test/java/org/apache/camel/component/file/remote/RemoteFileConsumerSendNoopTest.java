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

import java.io.IOException;
import java.net.SocketException;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the connection check that {@link RemoteFileConsumer#connectIfNecessary()} does before each poll: by
 * default it sends a noop, and with sendNoop=false it only checks the connection state of the client. With
 * sendNoop=false the consumer sends a noop before the next poll after an I/O failure, whether the failure is thrown out
 * of the poll or happens while processing a file, also when the ftp consumer ignores the failure as a file that does
 * not exist, unless it disconnects before that poll.
 */
class RemoteFileConsumerSendNoopTest extends CamelTestSupport {

    private RemoteFileOperations<?> operations;

    @SuppressWarnings("unchecked")
    private RemoteFileConsumer<?> createConsumer(String uri) {
        RemoteFileEndpoint<?> endpoint = context.getEndpoint(uri, RemoteFileEndpoint.class);
        if (endpoint instanceof SftpEndpoint sftpEndpoint) {
            RemoteFileOperations<SftpRemoteFile> sftpOperations = mock(RemoteFileOperations.class);
            operations = sftpOperations;
            return new SftpConsumer(sftpEndpoint, exchange -> {
            }, sftpOperations, mock(GenericFileProcessStrategy.class));
        }
        // the ftp consumer (also used by ftps) needs the ftp specific operations
        FtpOperations ftpOperations = mock(FtpOperations.class);
        operations = ftpOperations;
        return new FtpConsumer((RemoteFileEndpoint<FTPFile>) endpoint, exchange -> {
        }, ftpOperations, mock(GenericFileProcessStrategy.class));
    }

    /**
     * Creates a consumer that has connected and logged in once, as after its first poll.
     */
    private RemoteFileConsumer<?> createLoggedInConsumer(String uri) {
        RemoteFileConsumer<?> consumer = createConsumer(uri);
        when(operations.connect(any(), any())).thenReturn(true);
        consumer.connectIfNecessary();
        verify(operations, times(1)).connect(any(), any());
        Mockito.clearInvocations(operations);
        return consumer;
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseChecksConnectionStateInsteadOfNoop(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");
        when(operations.isConnected()).thenReturn(true);

        consumer.connectIfNecessary();

        verify(operations, times(1)).isConnected();
        verify(operations, never()).sendNoop();
        verify(operations, never()).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseReconnectsWhenNotConnected(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");
        when(operations.isConnected()).thenReturn(false);

        consumer.connectIfNecessary();

        verify(operations, never()).sendNoop();
        verify(operations, times(1)).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseReconnectsWhenConnectionCheckFails(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");
        when(operations.isConnected()).thenThrow(new GenericFileOperationFailedException("Connection reset"));

        consumer.connectIfNecessary();

        verify(operations, never()).sendNoop();
        verify(operations, times(1)).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseSendsNoopAfterIOFailure(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");
        // the client still regards itself as connected, as an ftp client does after the server closed the connection
        when(operations.isConnected()).thenReturn(true);
        when(operations.sendNoop()).thenReturn(false);

        consumer.handleException("Error processing file", null,
                new GenericFileOperationFailedException("Cannot retrieve file", new SocketException("Connection reset")));

        verify(operations, never()).forceDisconnect();

        // the next poll sends a noop, which fails, so it connects again
        consumer.connectIfNecessary();

        verify(operations, times(1)).sendNoop();
        verify(operations, never()).isConnected();
        verify(operations, times(1)).connect(any(), any());

        // and the poll after that only checks the connection state again
        consumer.connectIfNecessary();

        verify(operations, times(1)).sendNoop();
        verify(operations, times(1)).isConnected();
        verify(operations, times(1)).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseSendsNoopAfterPollFailure(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");
        // the client still regards itself as connected, as an ftp client does after the server closed the connection
        when(operations.isConnected()).thenReturn(true);
        when(operations.sendNoop()).thenReturn(false);
        // so the first command of the poll fails
        when(operations.getCurrentDirectory()).thenThrow(
                new GenericFileOperationFailedException(
                        "Cannot get current directory", new SocketException("Connection reset")));

        // the failure is thrown out of the poll, and a custom poll strategy does not disconnect
        assertThrows(GenericFileOperationFailedException.class, () -> consumer.poll(null));
        verify(operations, never()).sendNoop();
        verify(operations, never()).forceDisconnect();

        // the next poll sends a noop, which fails, so it connects again
        consumer.connectIfNecessary();

        verify(operations, times(1)).sendNoop();
        verify(operations, times(1)).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseSendsNoNoopAfterOtherPollFailure(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");
        when(operations.isConnected()).thenReturn(true);
        when(operations.getCurrentDirectory())
                .thenThrow(new GenericFileOperationFailedException("Cannot get current directory"));

        assertThrows(GenericFileOperationFailedException.class, () -> consumer.poll(null));

        consumer.connectIfNecessary();

        verify(operations, times(2)).isConnected();
        verify(operations, never()).sendNoop();
        verify(operations, never()).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseKeepsConnectionWhenNoopAfterIOFailureSucceeds(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");
        when(operations.sendNoop()).thenReturn(true);

        // an I/O failure that is not caused by the connection, such as a local file that cannot be written
        consumer.handleException("Error processing file", null,
                new GenericFileOperationFailedException("Cannot retrieve file", new IOException("No space left on device")));

        consumer.connectIfNecessary();

        verify(operations, times(1)).sendNoop();
        verify(operations, never()).forceDisconnect();
        verify(operations, never()).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseSendsNoNoopAfterOtherFailure(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");
        when(operations.isConnected()).thenReturn(true);

        consumer.handleException("Error processing file", null,
                new GenericFileOperationFailedException("Cannot retrieve file"));

        consumer.connectIfNecessary();

        verify(operations, times(1)).isConnected();
        verify(operations, never()).sendNoop();
        verify(operations, never()).forceDisconnect();
        verify(operations, never()).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseSendsNoNoopAfterIOFailureAndForceDisconnect(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false");

        // the poll fails with an I/O error, and the poll strategy disconnects
        consumer.handleException("Error processing file", null,
                new GenericFileOperationFailedException("Cannot retrieve file", new SocketException("Connection reset")));
        consumer.forceDisconnect();

        // the next poll connects again without a noop on the closed client
        consumer.connectIfNecessary();

        verify(operations, times(1)).forceDisconnect();
        verify(operations, never()).sendNoop();
        verify(operations, times(1)).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopFalseSendsNoNoopAfterIOFailureAndDisconnect(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox?sendNoop=false&disconnect=true");

        consumer.handleException("Error processing file", null,
                new GenericFileOperationFailedException("Cannot retrieve file", new SocketException("Connection reset")));
        // the consumer disconnects after the poll (disconnect=true)
        consumer.disconnect();

        // the next poll connects again without a noop on the closed client
        consumer.connectIfNecessary();

        verify(operations, never()).sendNoop();
        verify(operations, times(1)).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopDefaultUnchangedAfterIOFailure(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox");

        consumer.handleException("Error processing file", null,
                new GenericFileOperationFailedException("Cannot retrieve file", new SocketException("Connection reset")));

        // by default the noop before the next poll finds a closed connection
        verifyNoMoreInteractions(operations);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps" })
    void testSendNoopFalseSendsNoopAfterIgnoredIOFailure(String scheme) {
        FtpConsumer consumer = (FtpConsumer) createLoggedInConsumer(
                scheme + "://localhost/inbox?sendNoop=false&ignoreFileNotFoundOrPermissionError=true");
        when(operations.sendNoop()).thenReturn(false);

        // the client still has the reply code 550 of the previous command when the connection is found closed, so the
        // failure is ignored as a file that does not exist, as before
        assertTrue(consumer.ignoreCannotRetrieveFile("hello.txt", null, closedConnectionFailure()),
                "The failure should be ignored by its reply code");

        // but the next poll sends a noop, which fails, so it connects again
        consumer.connectIfNecessary();

        verify(operations, times(1)).sendNoop();
        verify(operations, times(1)).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps" })
    void testSendNoopFalseSendsNoNoopAfterFileNotFound(String scheme) {
        FtpConsumer consumer = (FtpConsumer) createLoggedInConsumer(
                scheme + "://localhost/inbox?sendNoop=false&ignoreFileNotFoundOrPermissionError=true");
        when(operations.isConnected()).thenReturn(true);

        assertTrue(consumer.ignoreCannotRetrieveFile("hello.txt", null, fileNotFoundFailure()),
                "A file that does not exist should be ignored");

        consumer.connectIfNecessary();

        verify(operations, times(1)).isConnected();
        verify(operations, never()).sendNoop();
        verify(operations, never()).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps" })
    void testSendNoopDefaultIgnoresFileNotFound(String scheme) {
        FtpConsumer consumer = (FtpConsumer) createConsumer(
                scheme + "://localhost/inbox?ignoreFileNotFoundOrPermissionError=true");

        // unchanged by default, as the noop before the next poll finds a closed connection
        assertTrue(consumer.ignoreCannotRetrieveFile("hello.txt", null, closedConnectionFailure()),
                "The failure should be ignored by its reply code by default");
        assertTrue(consumer.ignoreCannotRetrieveFile("hello.txt", null, fileNotFoundFailure()),
                "A file that does not exist should be ignored");
    }

    private static GenericFileOperationFailedException closedConnectionFailure() {
        return new GenericFileOperationFailedException(
                550, "No such file or directory", "Connection closed", new SocketException("Connection closed"));
    }

    private static GenericFileOperationFailedException fileNotFoundFailure() {
        return new GenericFileOperationFailedException(550, "No such file or directory");
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopDefaultSendsNoop(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox");
        when(operations.sendNoop()).thenReturn(true);

        consumer.connectIfNecessary();

        verify(operations, times(1)).sendNoop();
        verify(operations, never()).isConnected();
        verify(operations, never()).connect(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "ftps", "sftp" })
    void testSendNoopDefaultReconnectsWhenNoopFails(String scheme) {
        RemoteFileConsumer<?> consumer = createLoggedInConsumer(scheme + "://localhost/inbox");
        when(operations.sendNoop()).thenReturn(false);

        consumer.connectIfNecessary();

        verify(operations, times(1)).sendNoop();
        verify(operations, times(1)).connect(any(), any());
    }
}
