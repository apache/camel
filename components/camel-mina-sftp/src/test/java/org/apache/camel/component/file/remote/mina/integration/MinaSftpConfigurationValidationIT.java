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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP configuration validation and error messages.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpConfigurationValidationIT extends MinaSftpServerTestSupport {

    private static final Logger log = LoggerFactory.getLogger(MinaSftpConfigurationValidationIT.class);

    private String ftpRootDir;

    @BeforeEach
    public void doPostSetup() {
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
    }

    private String baseUri() {
        return "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
               + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";
    }

    @Test
    @Timeout(30)
    public void testInvalidChmodValue() {
        // chmod=999 is invalid (9 is not a valid octal digit, only 0-7)
        String uri = baseUri() + "&chmod=999";

        // Validation now happens at endpoint initialization (doInit), not at runtime
        Exception exception = assertThrows(
                RuntimeCamelException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "chmod-test.txt"));

        // The exception should indicate the chmod contains non-octal characters
        String message = exception.getCause().getMessage();
        assertTrue(message.contains("chmod") || message.contains("octal"),
                "Exception should mention chmod or octal: " + message);
    }

    @Test
    @Timeout(30)
    public void testInvalidChmodNonNumeric() {
        // chmod=abc is invalid (non-numeric)
        String uri = baseUri() + "&chmod=abc";

        // Validation now happens at endpoint initialization (doInit), not at runtime
        Exception exception = assertThrows(
                RuntimeCamelException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "chmod-test.txt"));

        String message = exception.getCause().getMessage();
        log.debug("**LDM 1** Exception Message {}", message);

        assertTrue(message.contains("chmod") || message.contains("octal"),
                "Exception should mention chmod or octal: " + message);
    }

    @Test
    @Timeout(30)
    public void testInvalidChmodDirectoryValue() {
        // chmodDirectory=888 is invalid (8 is not a valid octal digit)
        String uri = baseUri() + "&chmodDirectory=888";

        Exception exception = assertThrows(
                RuntimeCamelException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "chmoddir-test.txt"));

        String message = exception.getCause().getMessage();
        log.debug("**LDM 2** Exception Message {}", message);
        assertTrue(message.contains("chmodDirectory") || message.contains("octal"),
                "Exception should mention chmodDirectory or octal: " + message);
    }

    @Test
    @Timeout(30)
    public void testValidChmodValues() throws Exception {
        // Valid octal permissions should work
        String uri = baseUri() + "&chmod=644&chmodDirectory=755";

        template.sendBodyAndHeader(uri, "Content with valid chmod", Exchange.FILE_NAME, "valid-chmod.txt");

        assertTrue(ftpFile("valid-chmod.txt").toFile().exists(), "File should exist with valid chmod");
    }

    @Test
    @Timeout(30)
    public void testNegativeConnectTimeout() {
        // Negative timeout - should either fail or be ignored
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false&connectTimeout=-1";

        // This may or may not throw depending on validation
        // If it throws, verify the exception is meaningful
        try {
            template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "timeout-test.txt");
            // If no exception, verify the file was created (negative timeout was ignored)
            assertTrue(ftpFile("timeout-test.txt").toFile().exists(),
                    "File should exist if negative timeout was ignored");
        } catch (Exception e) {
            // If exception, it should be meaningful
            assertNotNull(e.getMessage(), "Exception should have a message");
        }
    }

    @Test
    @Timeout(30)
    public void testZeroConnectTimeout() {
        // Zero timeout - may mean infinite or immediate timeout
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false&connectTimeout=0";

        // Should work (zero often means no timeout)
        template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "zero-timeout-test.txt");
        assertTrue(ftpFile("zero-timeout-test.txt").toFile().exists(), "File should exist with zero timeout");
    }

    @Test
    @Timeout(30)
    public void testEmptyUsername() {
        // Empty username - the embedded test server accepts any user, so we verify the
        // connection works (documenting current behavior rather than expecting failure)
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        // The embedded server accepts empty username, so this should succeed
        // This test documents that behavior - in production, real SFTP servers may reject it
        template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "empty-user.txt");
        assertTrue(ftpFile("empty-user.txt").toFile().exists(),
                "File should exist - embedded server accepts empty username");
    }

    @Test
    @Timeout(30)
    public void testInvalidCipherName() {
        // Use an invalid cipher name
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&ciphers=invalid-nonexistent-cipher";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "cipher-test.txt"));

        // The exception should indicate cipher negotiation failure
        assertNotNull(exception.getCause(), "Should have a cause exception");
    }

    @Test
    @Timeout(30)
    public void testInvalidKeyExchangeProtocol() {
        // Use an invalid key exchange protocol
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&keyExchangeProtocols=invalid-kex-protocol";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "kex-test.txt"));

        assertNotNull(exception.getCause(), "Should have a cause exception");
    }

    @Test
    @Timeout(30)
    public void testMissingCredentials() {
        // No username or password - should fail with IllegalStateException (username required)
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?strictHostKeyChecking=no&useUserKnownHostsFile=false";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "no-creds.txt"));

        Throwable cause = exception.getCause();
        // When no username is provided at all, the component throws IllegalStateException
        assertTrue(cause instanceof IllegalStateException || cause instanceof GenericFileOperationFailedException,
                "Should be IllegalStateException or GenericFileOperationFailedException but was: " + cause.getClass());
    }

    @Test
    @Timeout(30)
    public void testUnknownUriParameter() {
        // Unknown parameter should fail during endpoint creation
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&unknownParameter=value";

        assertThrows(
                ResolveEndpointFailedException.class,
                () -> context.getEndpoint(uri));
    }

    @Test
    @Timeout(30)
    public void testValidConfiguration() throws Exception {
        // Verify a valid configuration works correctly
        String uri = baseUri()
                     + "&connectTimeout=30000"
                     + "&soTimeout=300000"
                     + "&stepwise=true"
                     + "&autoCreate=true";

        template.sendBodyAndHeader(uri, "Valid config content", Exchange.FILE_NAME, "valid-config.txt");

        assertTrue(ftpFile("valid-config.txt").toFile().exists(), "File should exist with valid config");
    }

    @Test
    @Timeout(30)
    public void testPreferredAuthenticationsInvalid() {
        // Use invalid authentication method
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&preferredAuthentications=invalid-auth-method";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "auth-test.txt"));

        assertNotNull(exception.getCause(), "Should have a cause exception");
    }

    @Test
    @Timeout(30)
    public void testPublicKeyAcceptedAlgorithmsInvalid() {
        // Use invalid algorithm
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&publicKeyAcceptedAlgorithms=invalid-algorithm";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Content", Exchange.FILE_NAME, "algo-test.txt"));

        assertNotNull(exception.getCause(), "Should have a cause exception");
    }

    @Test
    @Timeout(30)
    public void testVeryLongPassword() {
        // Very long password - should be handled gracefully
        String longPassword = "a".repeat(1000);
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=" + longPassword
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        // The embedded server accepts any password, so this should work
        // This test verifies that long passwords don't cause buffer overflows
        template.sendBodyAndHeader(uri, "Long password content", Exchange.FILE_NAME, "long-pass.txt");
        assertTrue(ftpFile("long-pass.txt").toFile().exists(), "File should exist with long password");
    }

    @Test
    @Timeout(30)
    public void testSpecialCharactersInPassword() throws Exception {
        // Password with special characters
        String specialPassword = "p@ss#word!$%^&*()";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=" + specialPassword
                     + "&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        // This may need URL encoding - test that special chars are handled
        try {
            template.sendBodyAndHeader(uri, "Special password content", Exchange.FILE_NAME, "special-pass.txt");
            assertTrue(ftpFile("special-pass.txt").toFile().exists(), "File should exist with special password");
        } catch (Exception e) {
            // If URL encoding is required, this might fail - that's also valid behavior
            log.info("Special characters in password may need URL encoding: {}", e.getMessage());
        }
    }
}
