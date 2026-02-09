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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.mina.MinaSftpConfiguration;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP security-related edge cases.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpSecurityEdgeCasesIT extends MinaSftpServerTestSupport {

    private static final String TEST_RESOURCES = "src/test/resources/";

    private String ftpRootDir;

    @BeforeEach
    public void doPostSetup() {
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
    }

    private String baseUri() {
        return "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
               + "?username=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";
    }

    @Test
    @Timeout(30)
    public void testWrongPrivateKeyPassphrase() {
        // Use encrypted key with wrong passphrase
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa-encrypted"
                     + "&privateKeyPassphrase=wrongpassphrase"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "wrong-pass.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());

        // The error should indicate passphrase/key issue
        String message = cause.getMessage().toLowerCase();
        assertTrue(message.contains("password") || message.contains("passphrase")
                || message.contains("key") || message.contains("decrypt")
                || message.contains("mac check") || message.contains("cannot load"),
                "Error should indicate passphrase problem: " + cause.getMessage());
    }

    @Test
    @Timeout(30)
    public void testEncryptedKeyWithoutPassphrase() {
        // Use encrypted key without providing passphrase
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa-encrypted"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "no-pass.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());
    }

    @Test
    @Timeout(30)
    public void testCorruptedPrivateKeyBytes() throws Exception {
        // Create endpoint with corrupted key bytes
        MinaSftpEndpoint endpoint = context.getEndpoint(baseUri(), MinaSftpEndpoint.class);
        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();

        // Set corrupted/truncated key bytes
        byte[] corruptedKey = "-----BEGIN PRIVATE KEY-----\nINVALID_KEY_DATA\n-----END PRIVATE KEY-----".getBytes();
        config.setPrivateKey(corruptedKey);

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(endpoint, "Content", Exchange.FILE_NAME, "corrupted.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());
    }

    @Test
    @Timeout(30)
    public void testEmptyPrivateKeyBytes() throws Exception {
        // Create endpoint with empty key bytes - the embedded server accepts any public key,
        // so empty key bytes are effectively ignored and the connection succeeds
        MinaSftpEndpoint endpoint = context.getEndpoint(baseUri(), MinaSftpEndpoint.class);
        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();

        // Set empty key bytes - should be ignored or cause fallback to other auth
        config.setPrivateKey(new byte[0]);

        // The embedded test server is permissive, so this may succeed
        // This documents the current behavior
        try {
            template.sendBodyAndHeader(endpoint, "Content", Exchange.FILE_NAME, "empty-key.txt");
            // If it succeeds, verify the file was created
            assertTrue(ftpFile("empty-key.txt").toFile().exists(),
                    "File should exist if empty key was ignored");
        } catch (CamelExecutionException e) {
            // If it fails, verify it's the expected exception type
            Throwable cause = e.getCause();
            assertTrue(cause instanceof GenericFileOperationFailedException,
                    "Should be GenericFileOperationFailedException but was: " + cause.getClass());
        }
    }

    @Test
    @Timeout(30)
    public void testNonExistentPrivateKeyFile() {
        // Use non-existent private key file
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin"
                     + "&privateKeyFile=/nonexistent/path/private_key"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "missing-key.txt"));

        assertNotNull(exception.getCause(), "Should have a cause exception");
    }

    @Test
    @Timeout(30)
    public void testStrictHostKeyCheckingWithWrongHost() {
        // Create a known_hosts file that doesn't match the server's host key
        String wrongKnownHosts = "[localhost]:" + service.getPort() + " ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDWrongKeyData";

        try {
            // Write wrong known_hosts to a temp file
            File tempKnownHosts = Files.createTempFile("wrong-known-hosts", ".txt").toFile();
            tempKnownHosts.deleteOnExit();
            Files.writeString(tempKnownHosts.toPath(), wrongKnownHosts);

            String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                         + "?username=admin&password=admin"
                         + "&strictHostKeyChecking=yes"
                         + "&useUserKnownHostsFile=false"
                         + "&knownHostsFile=" + tempKnownHosts.getAbsolutePath();

            CamelExecutionException exception = assertThrows(
                    CamelExecutionException.class,
                    () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "strict-check.txt"));

            Throwable cause = exception.getCause();
            assertTrue(cause instanceof GenericFileOperationFailedException,
                    "Should be GenericFileOperationFailedException but was: " + cause.getClass());

            // The error should indicate host key verification failure
            String message = cause.getMessage().toLowerCase();
            assertTrue(message.contains("host") || message.contains("key") || message.contains("verification")
                    || message.contains("known") || message.contains("fingerprint"),
                    "Error should indicate host key verification issue: " + cause.getMessage());

        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test", e);
        }
    }

    @Test
    @Timeout(30)
    public void testEmptyKnownHostsFile() throws Exception {
        // Create an empty known_hosts file
        File emptyKnownHosts = Files.createTempFile("empty-known-hosts", ".txt").toFile();
        emptyKnownHosts.deleteOnExit();

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin"
                     + "&strictHostKeyChecking=yes"
                     + "&useUserKnownHostsFile=false"
                     + "&knownHostsFile=" + emptyKnownHosts.getAbsolutePath();

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "empty-known.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());
    }

    @Test
    @Timeout(30)
    public void testNonExistentKnownHostsFile() {
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin"
                     + "&strictHostKeyChecking=yes"
                     + "&useUserKnownHostsFile=false"
                     + "&knownHostsFile=/nonexistent/known_hosts";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "missing-known.txt"));

        assertNotNull(exception.getCause(), "Should have a cause exception");
    }

    @Test
    @Timeout(30)
    public void testValidKeyWithCorrectPassphrase() throws Exception {
        // Verify that the correct passphrase works (positive test)
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa-encrypted"
                     + "&privateKeyPassphrase=testpass"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Content with correct passphrase", Exchange.FILE_NAME, "correct-pass.txt");

        assertTrue(ftpFile("correct-pass.txt").toFile().exists(),
                "File should exist when using correct passphrase");
    }

    @Test
    @Timeout(30)
    public void testUnencryptedKeyWithPassphrase() throws Exception {
        // Use unencrypted key with passphrase (should work, passphrase is ignored)
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa"
                     + "&privateKeyPassphrase=ignoredpassphrase"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Content with unencrypted key", Exchange.FILE_NAME, "unencrypted-with-pass.txt");

        assertTrue(ftpFile("unencrypted-with-pass.txt").toFile().exists(),
                "File should exist when using unencrypted key with passphrase");
    }

    @Test
    @Timeout(30)
    public void testPrivateKeyBytesWithValidKey() throws Exception {
        // Read valid key and set as bytes
        byte[] keyBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa").toPath());

        MinaSftpEndpoint endpoint = context.getEndpoint(baseUri(), MinaSftpEndpoint.class);
        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();
        config.setPrivateKey(keyBytes);

        template.sendBodyAndHeader(endpoint, "Content with key bytes", Exchange.FILE_NAME, "key-bytes-valid.txt");

        assertTrue(ftpFile("key-bytes-valid.txt").toFile().exists(),
                "File should exist when using valid key bytes");
    }

    @Test
    @Timeout(30)
    public void testMultipleAuthMethodsFallback() throws Exception {
        // Configure multiple authentication methods - should try them in order
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin"
                     + "&password=admin"
                     + "&privateKeyFile=" + TEST_RESOURCES + "test-key-rsa"
                     + "&preferredAuthentications=publickey,password"
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Content with fallback auth", Exchange.FILE_NAME, "fallback-auth.txt");

        assertTrue(ftpFile("fallback-auth.txt").toFile().exists(),
                "File should exist with multiple auth methods");
    }
}
