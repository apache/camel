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
package org.apache.camel.component.smb;

import java.net.ConnectException;
import java.util.List;

import com.hierynomus.mserref.NtStatus;
import com.hierynomus.mssmb2.SMB2MessageCommandCode;
import com.hierynomus.mssmb2.SMBApiException;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WARN of the smb consumer when it skips a poll: it tells a starting directory that cannot be created apart from a
 * server that cannot be connected to, and gives the reason.
 */
class SmbConsumerSkipPollWarnTest extends CamelTestSupport {

    private final SmbConsumerWarnCapture warnings = new SmbConsumerWarnCapture();
    private StubSmbOperations operations;
    private SmbConsumer consumer;

    @BeforeEach
    void createConsumer() {
        warnings.attach();
        SmbEndpoint endpoint = context.getEndpoint(
                "smb:localhost:4450/data-rw/inbox?username=camel&password=secret", SmbEndpoint.class);
        operations = new StubSmbOperations(endpoint.getConfiguration());
        consumer = new SmbConsumer(endpoint, exchange -> {
        }, operations, null);
    }

    @AfterEach
    void releaseWarnings() {
        warnings.detach();
        operations.forceDisconnect();
    }

    @Test
    void autoCreateFailureIsLoggedWithTheDirectoryAndTheReason() throws Exception {
        // as SmbOperations.buildDirectory throws it
        SMBApiException denied = new SMBApiException(
                NtStatus.STATUS_ACCESS_DENIED.getValue(), SMB2MessageCommandCode.SMB2_CREATE,
                "Create failed for \\\\localhost\\data-rw\\inbox", null);
        operations.buildDirectoryFailure = new GenericFileOperationFailedException(denied.getMessage(), denied);

        assertFalse(consumer.prePollCheck());
        assertFalse(consumer.prePollCheck());

        // logged once, until the directory is created
        assertEquals(List.of("Cannot auto-create starting directory inbox at: smb://camel@localhost:4450"
                             + " due to: STATUS_ACCESS_DENIED (0xc0000022): Create failed for \\\\localhost\\data-rw\\inbox."
                             + " Will skip this poll."),
                warnings.messages());

        // the next poll creates the starting directory, and polls
        operations.buildDirectoryFailure = null;

        assertTrue(consumer.prePollCheck());
        assertTrue(consumer.prePollCheck());
        assertEquals(1, warnings.messages().size());
        assertEquals(3, operations.buildDirectoryCalls);
    }

    @Test
    void connectFailureIsLoggedWithTheReason() throws Exception {
        operations.connectFailure = new GenericFileOperationFailedException(
                "Cannot connect to: localhost:4450 due to: Connection refused", new ConnectException("Connection refused"));

        assertFalse(consumer.prePollCheck());

        assertEquals(List.of("Cannot connect/login to: smb://camel@localhost:4450 due to: Connection refused."
                             + " Will skip this poll."),
                warnings.messages());
        // no connection, so no attempt to create the starting directory
        assertEquals(0, operations.buildDirectoryCalls);

        // connected again, the starting directory is created
        operations.connectFailure = null;

        assertTrue(consumer.prePollCheck());
        assertEquals(1, warnings.messages().size());
        assertEquals(1, operations.buildDirectoryCalls);
    }

    /**
     * Operations that connect, or fail to, without a server.
     */
    private static final class StubSmbOperations extends SmbOperations {

        private RuntimeException connectFailure;
        private RuntimeException buildDirectoryFailure;
        private int buildDirectoryCalls;

        StubSmbOperations(SmbConfiguration configuration) {
            super(configuration);
        }

        @Override
        protected void connectIfNecessary() {
            if (connectFailure != null) {
                throw connectFailure;
            }
        }

        @Override
        public boolean buildDirectory(String directory, boolean absolute) {
            buildDirectoryCalls++;
            if (buildDirectoryFailure != null) {
                throw buildDirectoryFailure;
            }
            return true;
        }
    }
}
