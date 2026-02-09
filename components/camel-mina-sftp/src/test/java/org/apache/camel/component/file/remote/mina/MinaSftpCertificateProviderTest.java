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

import java.io.File;
import java.nio.file.Files;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MinaSftpCertificateProvider.
 */
public class MinaSftpCertificateProviderTest {

    private static final String TEST_RESOURCES = "src/test/resources/";

    private CamelContext context;

    @BeforeEach
    public void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void testNullWhenNoCertificateConfigured() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();

        OpenSshCertificate cert = MinaSftpCertificateProvider.loadCertificate(config, context);

        assertNull(cert, "Should return null when no certificate is configured");
    }

    @Test
    public void testLoadFromFile() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertFile(TEST_RESOURCES + "test-key-rsa-cert.pub");

        OpenSshCertificate cert = MinaSftpCertificateProvider.loadCertificate(config, context);

        assertNotNull(cert, "Certificate should not be null");
        assertEquals(OpenSshCertificate.Type.USER, cert.getType(), "Should be USER certificate");
    }

    @Test
    public void testLoadFromBytes() throws Exception {
        byte[] certBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa-cert.pub").toPath());

        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertBytes(certBytes);

        OpenSshCertificate cert = MinaSftpCertificateProvider.loadCertificate(config, context);

        assertNotNull(cert, "Certificate should not be null");
        assertEquals(OpenSshCertificate.Type.USER, cert.getType(), "Should be USER certificate");
    }

    @Test
    public void testLoadFromUri() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertUri("file:" + TEST_RESOURCES + "test-key-rsa-cert.pub");

        OpenSshCertificate cert = MinaSftpCertificateProvider.loadCertificate(config, context);

        assertNotNull(cert, "Certificate should not be null");
        assertEquals(OpenSshCertificate.Type.USER, cert.getType(), "Should be USER certificate");
    }

    @Test
    public void testLoadFromNonExistentFile() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertFile(TEST_RESOURCES + "non-existent-cert.pub");

        GenericFileOperationFailedException exception = assertThrows(
                GenericFileOperationFailedException.class,
                () -> MinaSftpCertificateProvider.loadCertificate(config, context));

        assertTrue(exception.getMessage().contains("does not exist"),
                "Should indicate file does not exist");
    }

    @Test
    public void testLoadRegularPublicKeyFails() {
        // A regular public key (not a certificate) should fail
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertFile(TEST_RESOURCES + "test-key-rsa.pub");

        GenericFileOperationFailedException exception = assertThrows(
                GenericFileOperationFailedException.class,
                () -> MinaSftpCertificateProvider.loadCertificate(config, context));

        assertTrue(exception.getMessage().contains("not an OpenSSH certificate"),
                "Should indicate file is not a certificate, got: " + exception.getMessage());
    }

    @Test
    public void testPriorityCertBytesOverFile() throws Exception {
        // certBytes should take priority over certFile
        byte[] certBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa-cert.pub").toPath());

        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertBytes(certBytes);
        config.setCertFile(TEST_RESOURCES + "non-existent-cert.pub"); // This should be ignored

        // Should succeed because certBytes takes priority
        OpenSshCertificate cert = MinaSftpCertificateProvider.loadCertificate(config, context);

        assertNotNull(cert, "Certificate should not be null");
        assertEquals(OpenSshCertificate.Type.USER, cert.getType(), "Should be USER certificate");
    }

    @Test
    public void testPriorityCertUriOverFile() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertUri("file:" + TEST_RESOURCES + "test-key-rsa-cert.pub");
        config.setCertFile(TEST_RESOURCES + "non-existent-cert.pub"); // This should be ignored

        // Should succeed because certUri takes priority over certFile
        OpenSshCertificate cert = MinaSftpCertificateProvider.loadCertificate(config, context);

        assertNotNull(cert, "Certificate should not be null");
        assertEquals(OpenSshCertificate.Type.USER, cert.getType(), "Should be USER certificate");
    }

    @Test
    public void testEmptyBytesIgnored() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertBytes(new byte[0]); // Empty array should be ignored
        config.setCertFile(TEST_RESOURCES + "test-key-rsa-cert.pub");

        // Should fall through to certFile
        OpenSshCertificate cert = MinaSftpCertificateProvider.loadCertificate(config, context);

        assertNotNull(cert, "Certificate should not be null");
        assertEquals(OpenSshCertificate.Type.USER, cert.getType(), "Should be USER certificate");
    }

    @Test
    public void testEmptyUriIgnored() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setCertUri(""); // Empty string should be ignored
        config.setCertFile(TEST_RESOURCES + "test-key-rsa-cert.pub");

        // Should fall through to certFile
        OpenSshCertificate cert = MinaSftpCertificateProvider.loadCertificate(config, context);

        assertNotNull(cert, "Certificate should not be null");
        assertEquals(OpenSshCertificate.Type.USER, cert.getType(), "Should be USER certificate");
    }
}
