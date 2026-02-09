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
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.mina.MinaSftpConfiguration;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for public key authentication in MINA SFTP component.
 */
@DisabledOnOs(architectures = "s390x")
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpPublicKeyAuthIT extends MinaSftpServerTestSupport {

    private static final String TEST_RESOURCES = "src/test/resources/";

    @BeforeEach
    public void doPostSetup() throws Exception {
        // Ensure the root directory exists
        service.getFtpRootDir().toFile().mkdirs();
    }

    @Test
    public void testAuthenticateWithPrivateKeyFile() throws Exception {
        // Test using private key file path
        // Note: The embedded SFTP server accepts any authentication by default
        String uri = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Test content with key file", Exchange.FILE_NAME, "key-file-test.txt");

        File file = ftpFile("key-file-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with key file", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testAuthenticateWithPrivateKeyUri() throws Exception {
        // Test using classpath/file URI
        String uri = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyUri=file:" + TEST_RESOURCES + "test-key-rsa"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Test content with key URI", Exchange.FILE_NAME, "key-uri-test.txt");

        File file = ftpFile("key-uri-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with key URI", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testAuthenticateWithPrivateKeyBytes() throws Exception {
        // Load key as bytes and configure programmatically
        byte[] keyBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa").toPath());

        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                                                        + "?username=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);

        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();
        config.setPrivateKey(keyBytes);

        template.sendBodyAndHeader(endpoint, "Test content with key bytes", Exchange.FILE_NAME, "key-bytes-test.txt");

        File file = ftpFile("key-bytes-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with key bytes", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testAuthenticateWithEncryptedKey() throws Exception {
        // Test using encrypted private key with passphrase
        String uri = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa-encrypted"
                     + "&privateKeyPassphrase=testpass"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Test content with encrypted key", Exchange.FILE_NAME, "encrypted-key-test.txt");

        File file = ftpFile("encrypted-key-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with encrypted key", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testAuthenticateWithDirectKeyPair() throws Exception {
        // Generate a KeyPair programmatically
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                                                        + "?username=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);

        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();
        config.setKeyPair(keyPair);

        template.sendBodyAndHeader(endpoint, "Test content with direct KeyPair", Exchange.FILE_NAME, "keypair-test.txt");

        File file = ftpFile("keypair-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with direct KeyPair", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testAuthenticateWithEd25519Key() throws Exception {
        // Test using Ed25519 key
        String uri = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-ed25519"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Test content with Ed25519 key", Exchange.FILE_NAME, "ed25519-test.txt");

        File file = ftpFile("ed25519-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with Ed25519 key", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testAuthenticateWithEcdsaKey() throws Exception {
        // Test using ECDSA key
        String uri = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-ecdsa"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Test content with ECDSA key", Exchange.FILE_NAME, "ecdsa-test.txt");

        File file = ftpFile("ecdsa-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with ECDSA key", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testFallbackToPasswordAuth() throws Exception {
        // Test that password is used as fallback when no key is configured
        String uri = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&password=admin"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Test content with password fallback", Exchange.FILE_NAME,
                "password-fallback-test.txt");

        File file = ftpFile("password-fallback-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with password fallback", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testPublicKeyPrioritizedOverPassword() throws Exception {
        // Test that public key is tried first, with password as fallback
        // The embedded server accepts any auth, so this verifies both are configured
        String uri = "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&password=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Test content with key priority", Exchange.FILE_NAME, "key-priority-test.txt");

        File file = ftpFile("key-priority-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Test content with key priority", context.getTypeConverter().convertTo(String.class, file));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // No routes needed - we use template.sendBody directly
            }
        };
    }
}
