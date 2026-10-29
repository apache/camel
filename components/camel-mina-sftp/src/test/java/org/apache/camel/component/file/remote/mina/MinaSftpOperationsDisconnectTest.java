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

import java.io.IOException;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MinaSftpOperations transport error classification.
 */
class MinaSftpOperationsDisconnectTest {

    @Test
    void testIsTransportErrorClassification() {
        MinaSftpOperations disconnected = new MinaSftpOperations();
        assertTrue(disconnected.isTransportError(new GenericFileOperationFailedException("disconnected")),
                "Disconnected operations should classify any failure as transport error");

        MinaSftpOperations connected = new MinaSftpOperations() {
            @Override
            public boolean isConnected() {
                return true;
            }
        };

        // Whitelisted transport errors
        assertTrue(connected.isTransportError(new GenericFileOperationFailedException(
                "connection lost", new SftpException(SftpConstants.SSH_FX_CONNECTION_LOST, "connection lost"))));
        assertTrue(connected.isTransportError(new GenericFileOperationFailedException(
                "no connection", new SftpException(SftpConstants.SSH_FX_NO_CONNECTION, "no connection"))));
        assertTrue(connected.isTransportError(new GenericFileOperationFailedException(
                "io exception", new IOException("broken pipe"))));

        // Application-level errors are not transport errors
        assertFalse(connected.isTransportError(new GenericFileOperationFailedException(
                "no such file", new SftpException(SftpConstants.SSH_FX_NO_SUCH_FILE, "no such file"))));
        assertFalse(connected.isTransportError(new GenericFileOperationFailedException(
                "permission denied", new SftpException(SftpConstants.SSH_FX_PERMISSION_DENIED, "permission denied"))));
        assertFalse(connected.isTransportError(new GenericFileOperationFailedException(
                "failure", new SftpException(SftpConstants.SSH_FX_FAILURE, "failure"))));
        assertFalse(connected.isTransportError(new GenericFileOperationFailedException(
                "bad message", new SftpException(SftpConstants.SSH_FX_BAD_MESSAGE, "bad message"))));
        assertFalse(connected.isTransportError(new GenericFileOperationFailedException(
                "op unsupported", new SftpException(SftpConstants.SSH_FX_OP_UNSUPPORTED, "op unsupported"))));
    }
}
