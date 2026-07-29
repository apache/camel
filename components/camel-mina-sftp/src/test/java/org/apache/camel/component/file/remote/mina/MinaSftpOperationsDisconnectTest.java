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

import java.lang.reflect.Field;

import org.apache.sshd.client.SshClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test that verifies disconnect() stops the SshClient and releases its daemon threads (CAMEL-24273).
 * <p/>
 * Before the fix, disconnect() only closed the SFTP client and session but left the SshClient running, which leaked
 * NIO2 and timer daemon threads on every connect/disconnect cycle.
 */
class MinaSftpOperationsDisconnectTest {

    /**
     * Verifies that disconnect() stops the SshClient and nulls the field so its internal thread pool is shut down.
     * <p/>
     * This is the core fix for CAMEL-24273: without sshClient.stop(), the NIO2/timer daemon threads from
     * SshClient.start() are never released, accumulating with each connect/disconnect cycle.
     */
    @Test
    void testDisconnectStopsSshClient() throws Exception {
        MinaSftpOperations operations = new MinaSftpOperations();

        // Create and start an SshClient
        SshClient sshClient = SshClient.setUpDefaultClient();
        sshClient.start();

        // Verify the client is running
        assertFalse(sshClient.isClosed(), "SshClient should be open after start()");
        assertFalse(sshClient.isClosing(), "SshClient should not be closing after start()");

        // Inject the sshClient into operations
        setField(operations, "sshClient", sshClient);

        // Call disconnect — this should stop the sshClient
        operations.disconnect();

        // Verify the SshClient is stopped
        assertTrue(sshClient.isClosed() || sshClient.isClosing(),
                "SshClient should be stopped after disconnect()");

        // Verify the field is nulled so a fresh client is created on next connect
        assertNull(getField(operations, "sshClient"),
                "sshClient field should be null after disconnect()");
    }

    /**
     * Verifies that disconnect() nulls all connection-related fields, ensuring a clean state for reconnection.
     */
    @Test
    void testDisconnectNullsAllConnectionFields() throws Exception {
        MinaSftpOperations operations = new MinaSftpOperations();

        // Create and start an SshClient
        SshClient sshClient = SshClient.setUpDefaultClient();
        sshClient.start();

        // Inject fields
        setField(operations, "sshClient", sshClient);

        // Call disconnect
        operations.disconnect();

        // Verify all connection fields are nulled
        assertNull(getField(operations, "sshClient"), "sshClient should be null after disconnect()");
        assertNull(getField(operations, "session"), "session should be null after disconnect()");
        assertNull(getField(operations, "sftpClient"), "sftpClient should be null after disconnect()");
    }

    /**
     * Verifies that calling disconnect() multiple times does not throw exceptions (idempotent behavior).
     */
    @Test
    void testDisconnectIsIdempotent() throws Exception {
        MinaSftpOperations operations = new MinaSftpOperations();

        // Create and start an SshClient
        SshClient sshClient = SshClient.setUpDefaultClient();
        sshClient.start();
        setField(operations, "sshClient", sshClient);

        // First disconnect
        operations.disconnect();

        // Second disconnect should not throw
        operations.disconnect();

        // Verify still clean
        assertNull(getField(operations, "sshClient"), "sshClient should be null after double disconnect()");
    }

    /**
     * Verifies that disconnect() on a never-connected MinaSftpOperations does not throw.
     */
    @Test
    void testDisconnectWhenNeverConnected() {
        MinaSftpOperations operations = new MinaSftpOperations();

        // Should not throw when no connection was ever established
        operations.disconnect();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
