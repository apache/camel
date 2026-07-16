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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FtpOperationsStreamDownloadTest {

    @Mock
    private FTPClient ftpClient;

    @Mock
    private FtpEndpoint endpoint;

    @Mock
    private FtpConfiguration configuration;

    @Mock
    private Exchange exchange;

    @Mock
    private Message message;

    @Mock
    private FtpClientActivityListener activityListener;

    private FtpOperations ftpOperations;

    @BeforeEach
    void setUp() {
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(configuration.remoteServerInformation()).thenReturn("localhost");

        ftpOperations = new FtpOperations(ftpClient, null);
        ftpOperations.setEndpoint(endpoint);
        ftpOperations.setClientActivityListener(activityListener);

        when(exchange.getIn()).thenReturn(message);
    }

    private void setUpStreamDownload() {
        when(configuration.isStreamDownload()).thenReturn(true);
        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE)).thenReturn(new GenericFile<>());
    }

    @Test
    void testRetrieveFileStreamReturnsNullOnServerError() throws IOException {
        setUpStreamDownload();
        when(ftpClient.retrieveFileStream(anyString())).thenReturn(null);
        when(ftpClient.getReplyCode()).thenReturn(550);
        when(ftpClient.getReplyString()).thenReturn("550 File not found");

        boolean result = ftpOperations.retrieveFile("test.txt", exchange, -1);

        assertFalse(result, "retrieveFile should return false when server refuses RETR");
        verify(message, never()).setHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, null);
    }

    @Test
    void testRetrieveFileStreamSuccessReturnsTrue() throws IOException {
        setUpStreamDownload();
        InputStream stream = new ByteArrayInputStream("content".getBytes());
        when(ftpClient.retrieveFileStream(anyString())).thenReturn(stream);
        when(ftpClient.getReplyCode()).thenReturn(150);
        when(ftpClient.getReplyString()).thenReturn("150 Opening data connection");

        boolean result = ftpOperations.retrieveFile("test.txt", exchange, -1);

        assertTrue(result, "retrieveFile should return true when stream is available");
    }

    @Test
    void testReleaseResourcesThrowsOnFailedCompletion() throws IOException {
        InputStream stream = new ByteArrayInputStream("content".getBytes());
        when(message.getHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, InputStream.class)).thenReturn(stream);
        when(ftpClient.completePendingCommand()).thenReturn(false);
        when(ftpClient.getReplyCode()).thenReturn(426);
        when(ftpClient.getReplyString()).thenReturn("426 Transfer aborted");

        assertThrows(GenericFileOperationFailedException.class,
                () -> ftpOperations.releaseRetrievedFileResources(exchange));
    }

    @Test
    void testReleaseResourcesSucceedsOnCompletedTransfer() throws IOException {
        InputStream stream = new ByteArrayInputStream("content".getBytes());
        when(message.getHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, InputStream.class)).thenReturn(stream);
        when(ftpClient.completePendingCommand()).thenReturn(true);

        ftpOperations.releaseRetrievedFileResources(exchange);
    }

    @Test
    void testReleaseResourcesSkipsWhenNoStream() {
        when(message.getHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, InputStream.class)).thenReturn(null);

        ftpOperations.releaseRetrievedFileResources(exchange);
    }
}
