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
import java.util.List;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The WARN of the remote file consumer when it skips a poll: it gives the reason on one line, and tells a starting
 * directory that cannot be created apart from a server that cannot be connected to.
 */
class RemoteFileConsumerSkipPollWarnTest extends CamelTestSupport {

    private final RemoteFileConsumerWarnCapture warnings = new RemoteFileConsumerWarnCapture();
    private RemoteFileOperations<?> operations;

    @BeforeEach
    void captureWarnings() {
        warnings.attach();
    }

    @AfterEach
    void releaseWarnings() {
        warnings.detach();
    }

    @SuppressWarnings("unchecked")
    private RemoteFileConsumer<?> createConsumer(String uri) {
        RemoteFileEndpoint<?> endpoint = context.getEndpoint(uri, RemoteFileEndpoint.class);
        if (endpoint instanceof SftpEndpoint sftpEndpoint) {
            RemoteFileOperations<SftpRemoteFile> sftpOperations = mock(RemoteFileOperations.class);
            operations = sftpOperations;
            return new SftpConsumer(sftpEndpoint, exchange -> {
            }, sftpOperations, mock(GenericFileProcessStrategy.class));
        }
        FtpOperations ftpOperations = mock(FtpOperations.class);
        operations = ftpOperations;
        return new FtpConsumer((RemoteFileEndpoint<FTPFile>) endpoint, exchange -> {
        }, ftpOperations, mock(GenericFileProcessStrategy.class));
    }

    @Test
    void loginFailureWithReplyOfSeveralLinesIsLoggedOnOneLine() throws Exception {
        RemoteFileConsumer<?> consumer = createConsumer("ftp://admin@localhost/inbox?autoCreate=false");
        // commons-net joins the lines of a reply with CRLF
        when(operations.connect(any(), any())).thenThrow(new GenericFileOperationFailedException(
                530, "530-Login incorrect.\r\n530 The account is locked after 3 failures"));

        assertFalse(consumer.prePollCheck());

        assertEquals(List.of("Cannot connect/login to: ftp://admin@localhost:21 due to: File operation failed:"
                             + " 530-Login incorrect. 530 The account is locked after 3 failures. Code: 530."
                             + " Will skip this poll."),
                warnings.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "sftp" })
    void autoCreateFailureIsLoggedWithItsReason(String scheme) throws Exception {
        RemoteFileConsumer<?> consumer = createConsumer(scheme + "://admin@localhost:2121/inbox");
        when(operations.connect(any(), any())).thenReturn(true);
        when(operations.buildDirectory(anyString(), anyBoolean())).thenThrow(
                new GenericFileOperationFailedException("Cannot build directory: inbox", new IOException("Permission denied")));

        assertFalse(consumer.prePollCheck());

        assertEquals(List.of("Cannot auto-create starting directory inbox at: " + scheme + "://admin@localhost:2121"
                             + " due to: Permission denied. Will skip this poll."),
                warnings.messages());

        // the next poll creates the starting directory, and polls
        doReturn(true).when(operations).buildDirectory(anyString(), anyBoolean());

        assertTrue(consumer.prePollCheck());
        assertEquals(1, warnings.messages().size());
    }
}
