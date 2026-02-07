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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for custom ServerKeyVerifier support in MINA SFTP.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpCustomServerKeyVerifierIT extends MinaSftpServerTestSupport {

    private String ftpRootDir;

    @BeforeEach
    public void doPostSetup() throws Exception {
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
    }

    // T006: Test custom verifier that returns true allows connection
    @Test
    public void testCustomVerifierAcceptsAllKeys() throws Exception {
        // Create a verifier that always accepts
        ServerKeyVerifier acceptAllVerifier = (session, remoteAddress, serverKey) -> true;

        // Register in Camel registry
        context.getRegistry().bind("acceptAllVerifier", acceptAllVerifier);

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&serverKeyVerifier=#acceptAllVerifier&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Custom verifier test", Exchange.FILE_NAME, "custom-accept.txt");

        File file = ftpFile("custom-accept.txt").toFile();
        assertTrue(file.exists(), "File should exist when custom verifier accepts");
    }

    // T007: Test custom verifier that returns false rejects connection
    @Test
    public void testCustomVerifierRejectsAllKeys() {
        // Create a verifier that always rejects
        ServerKeyVerifier rejectAllVerifier = (session, remoteAddress, serverKey) -> false;

        // Register in Camel registry
        context.getRegistry().bind("rejectAllVerifier", rejectAllVerifier);

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&serverKeyVerifier=#rejectAllVerifier&useUserKnownHostsFile=false";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail", Exchange.FILE_NAME, "should-fail.txt"));

        Throwable cause = getRootCause(exception);
        assertTrue(cause instanceof GenericFileOperationFailedException
                || cause.getMessage().contains("Server key did not validate")
                || cause.getMessage().contains("Cannot connect"),
                "Should fail with connection error: " + cause.getMessage());
    }

    // T008: Test custom verifier that throws exception
    @Test
    public void testCustomVerifierThrowsException() {
        // Create a verifier that throws an exception
        ServerKeyVerifier throwingVerifier = (session, remoteAddress, serverKey) -> {
            throw new RuntimeException("Custom verification error");
        };

        // Register in Camel registry
        context.getRegistry().bind("throwingVerifier", throwingVerifier);

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&serverKeyVerifier=#throwingVerifier&useUserKnownHostsFile=false";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail", Exchange.FILE_NAME, "should-fail.txt"));

        Throwable cause = getRootCause(exception);
        assertTrue(cause.getMessage().contains("Custom verification error")
                || cause.getMessage().contains("Cannot connect"),
                "Should contain custom error message: " + cause.getMessage());
    }

    // T009: Test custom verifier receives correct parameters
    @Test
    public void testCustomVerifierReceivesCorrectParameters() throws Exception {
        // Create a verifier that captures parameters
        AtomicReference<ClientSession> capturedSession = new AtomicReference<>();
        AtomicReference<SocketAddress> capturedAddress = new AtomicReference<>();
        AtomicReference<PublicKey> capturedKey = new AtomicReference<>();

        ServerKeyVerifier capturingVerifier = (session, remoteAddress, serverKey) -> {
            capturedSession.set(session);
            capturedAddress.set(remoteAddress);
            capturedKey.set(serverKey);
            return true;
        };

        // Register in Camel registry
        context.getRegistry().bind("capturingVerifier", capturingVerifier);

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&serverKeyVerifier=#capturingVerifier&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Parameter test", Exchange.FILE_NAME, "param-test.txt");

        // Verify all parameters were captured
        assertNotNull(capturedSession.get(), "ClientSession should not be null");
        assertNotNull(capturedAddress.get(), "SocketAddress should not be null");
        assertNotNull(capturedKey.get(), "PublicKey should not be null");

        // Verify address contains expected host/port
        assertTrue(capturedAddress.get() instanceof InetSocketAddress, "Should be InetSocketAddress");
        InetSocketAddress inetAddr = (InetSocketAddress) capturedAddress.get();
        assertEquals(service.getPort(), inetAddr.getPort(), "Port should match");
    }

    // T010: Test fallback to built-in verifier when no custom verifier configured
    @Test
    public void testFallbackToBuiltInVerifier() throws Exception {
        // Configure endpoint WITHOUT serverKeyVerifier parameter
        // With strictHostKeyChecking=no, should use AcceptAllServerKeyVerifier
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Fallback test", Exchange.FILE_NAME, "fallback.txt");

        File file = ftpFile("fallback.txt").toFile();
        assertTrue(file.exists(), "File should exist when using default behavior");
    }

    // T011: Test custom verifier ignores strictHostKeyChecking
    @Test
    public void testCustomVerifierIgnoresStrictHostKeyChecking() throws Exception {
        // Create a verifier that always accepts
        AtomicBoolean verifierCalled = new AtomicBoolean(false);
        ServerKeyVerifier acceptVerifier = (session, remoteAddress, serverKey) -> {
            verifierCalled.set(true);
            return true;
        };

        // Register in Camel registry
        context.getRegistry().bind("acceptVerifier", acceptVerifier);

        // Configure with BOTH custom verifier AND strictHostKeyChecking=yes
        // Without custom verifier, this would fail because there's no known_hosts file
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&serverKeyVerifier=#acceptVerifier"
                     + "&strictHostKeyChecking=yes&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Precedence test", Exchange.FILE_NAME, "precedence.txt");

        File file = ftpFile("precedence.txt").toFile();
        assertTrue(file.exists(), "File should exist - custom verifier should take precedence");
        assertTrue(verifierCalled.get(), "Custom verifier should have been called");
    }

    // T012: Test custom verifier ignores knownHostsFile
    @Test
    public void testCustomVerifierIgnoresKnownHostsFile() throws Exception {
        // Create a verifier that tracks if called
        AtomicBoolean verifierCalled = new AtomicBoolean(false);
        ServerKeyVerifier trackingVerifier = (session, remoteAddress, serverKey) -> {
            verifierCalled.set(true);
            return true;
        };

        // Register in Camel registry
        context.getRegistry().bind("trackingVerifier", trackingVerifier);

        // Configure with custom verifier AND a non-existent knownHostsFile
        // If knownHostsFile was used, it would fail because the file doesn't exist
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&serverKeyVerifier=#trackingVerifier"
                     + "&knownHostsFile=/non/existent/known_hosts&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Ignore knownHostsFile test", Exchange.FILE_NAME, "ignore-known-hosts.txt");

        File file = ftpFile("ignore-known-hosts.txt").toFile();
        assertTrue(file.exists(), "File should exist - custom verifier should ignore knownHostsFile");
        assertTrue(verifierCalled.get(), "Custom verifier should have been called");
    }

    private Throwable getRootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
