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
package org.apache.camel.component.file.remote.mina.integration;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.mina.MinaSftpConfiguration;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.apache.camel.component.file.remote.mina.MinaSftpOperations;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.sshd.common.SshException;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for CAMEL-27031: a rejected login must not be retried maximumReconnectAttempts times.
 * <p/>
 * Before the fix, MinaSftpOperations.tryConnect() caught any IOException from createSession(), including the
 * SshException that AuthFuture.verify() throws when the server rejects the credentials. The reconnect loop then sent
 * the wrong password again on each of its maximumReconnectAttempts (default 3) iterations.
 * <p/>
 * After the fix, SshException is caught before the generic IOException handler. Auth-rejection SshExceptions (detected
 * via disconnect code {@code SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE} or the \"Authentication failed\" message)
 * are rethrown immediately as GenericFileOperationFailedException, stopping the reconnect loop. Non-auth SshExceptions
 * (e.g. connection refused) are still retried.
 */
class MinaSftpAuthRetryIT {

    private SshServer sshd;
    private int port;
    /** Counts how many times the server's password authenticator is called per connect(). */
    private final AtomicInteger passwordAttempts = new AtomicInteger();
    private Path tempDir;
    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        passwordAttempts.set(0);
        tempDir = Files.createTempDirectory("camel-mina-sftp-auth-retry-it");

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0); // OS-assigned port
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("host.ser")));
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));

        // Always reject: count attempts and return false
        sshd.setPasswordAuthenticator((username, password, session) -> {
            passwordAttempts.incrementAndGet();
            return false;
        });

        sshd.start();
        port = ((InetSocketAddress) sshd.getBoundAddresses().iterator().next()).getPort();

        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
        if (sshd != null) {
            sshd.stop(true);
        }
        deleteDir(tempDir);
    }

    /**
     * A rejected login must throw GenericFileOperationFailedException immediately, not loop over
     * maximumReconnectAttempts.
     * <p/>
     * With the default maximumReconnectAttempts=3 and a server offering only password auth, the unfixed code sends the
     * wrong password 3 times. With the fix it is sent at most twice (once per auth method offered by the server:
     * password and keyboard-interactive), not once per reconnect attempt.
     */
    @Test
    void testRejectedLoginIsNotRetried() {
        MinaSftpConfiguration config = buildConfiguration();
        MinaSftpEndpoint endpoint = buildEndpoint(config, 3, 0);
        MinaSftpOperations operations = new MinaSftpOperations();
        operations.setEndpoint(endpoint);

        GenericFileOperationFailedException ex = assertThrows(
                GenericFileOperationFailedException.class,
                () -> operations.connect(config, null),
                "connect() must throw when login is rejected");

        // The error must mention the rejection, not just "Cannot connect"
        String msg = ex.getMessage();
        assertTrue(msg != null && msg.contains("Login rejected"),
                "Exception message should say 'Login rejected' but was: " + msg);

        // The underlying cause must be the SshException from AuthFuture.verify()
        assertInstanceOf(SshException.class, ex.getCause(),
                "Cause must be SshException");

        // The server must have checked the password at most twice (once for each auth method the
        // MINA SSHD client tries: password and keyboard-interactive). Without the fix the client
        // would open a new session for each of the maximumReconnectAttempts (3), tripling the count.
        int attempts = passwordAttempts.get();
        assertTrue(attempts <= 2,
                "Password must be checked at most twice (once per auth method per session); got " + attempts);
    }

    /**
     * A transient connection failure (server stopped) must still be retried up to maximumReconnectAttempts.
     * <p/>
     * The connection failure surfaces as an {@link org.apache.sshd.common.SshException} wrapping the underlying
     * {@link java.net.ConnectException}, which exercises the non-auth {@code SshException} branch in
     * {@code MinaSftpOperations.tryConnect()}. This verifies that the fix does not break the existing retry behaviour
     * for failures that are not authentication rejections.
     */
    @Test
    void testTransientFailureIsStillRetried() throws Exception {
        // Stop the server so the client gets a connection failure (SshException wrapping ConnectException)
        sshd.stop(true);

        // maximumReconnectAttempts=2, reconnectDelay=0 → task runs atLeastOnce(2) = max 2 iterations
        MinaSftpConfiguration config = buildConfiguration();
        MinaSftpEndpoint endpoint = buildEndpoint(config, 2, 0);
        MinaSftpOperations operations = new MinaSftpOperations();
        operations.setEndpoint(endpoint);

        GenericFileOperationFailedException ex = assertThrows(
                GenericFileOperationFailedException.class,
                () -> operations.connect(config, null),
                "connect() must throw when server is unreachable");

        // "Cannot connect" message means the budget was exhausted (not an immediate rethrow)
        assertTrue(ex.getMessage().contains("Cannot connect"),
                "Exception message should say 'Cannot connect' but was: " + ex.getMessage());

        // No password check at all — the connection failed before auth
        assertEquals(0, passwordAttempts.get(), "No password check expected for connection failure");
    }

    // ---- helpers ----

    private MinaSftpEndpoint buildEndpoint(
            MinaSftpConfiguration config, int maximumReconnectAttempts,
            long reconnectDelay) {
        MinaSftpEndpoint endpoint = new MinaSftpEndpoint(
                "mina-sftp://localhost:" + port + "/tmp", null, config);
        endpoint.setMaximumReconnectAttempts(maximumReconnectAttempts);
        endpoint.setReconnectDelay(reconnectDelay);
        endpoint.setCamelContext(camelContext);
        return endpoint;
    }

    private MinaSftpConfiguration buildConfiguration() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setHost("localhost");
        config.setPort(port);
        config.setUsername("admin");
        config.setPassword("wrong-password");
        config.setStrictHostKeyChecking("no");
        config.setUseUserKnownHostsFile(false);
        return config;
    }

    private static void deleteDir(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
