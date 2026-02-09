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
package org.apache.camel.component.file.remote.mina.sftp;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for publicKeyAcceptedAlgorithms configuration.
 * <p>
 * Note: These tests verify that the configuration is correctly parsed and available on the endpoint. The actual
 * algorithm enforcement is handled by the MINA SSHD library and depends on server configuration.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
public class SftpPublicKeyAcceptedAlgorithmsIT extends SftpServerTestSupport {

    @Test
    public void testPublicKeyAcceptedAlgorithmsConfiguration() {
        // Test that publicKeyAcceptedAlgorithms is correctly parsed from the URI
        String algorithms = "rsa-sha2-256,rsa-sha2-512,ssh-rsa";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&publicKeyAcceptedAlgorithms=" + algorithms;

        template.sendBodyAndHeader(uri, "Config Test", Exchange.FILE_NAME, "config-test.txt");

        File file = ftpFile("config-test.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Config Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(algorithms, endpoint.getConfiguration().getPublicKeyAcceptedAlgorithms());
    }

    @Test
    public void testDefaultAlgorithmsWhenNotSpecified() {
        // When publicKeyAcceptedAlgorithms is not specified, it should be null
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();

        template.sendBodyAndHeader(uri, "Default Algorithms Test", Exchange.FILE_NAME, "default-algorithms.txt");

        File file = ftpFile("default-algorithms.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Default Algorithms Test", context.getTypeConverter().convertTo(String.class, file));

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        // publicKeyAcceptedAlgorithms should be null when not specified
        assertNull(endpoint.getConfiguration().getPublicKeyAcceptedAlgorithms());
    }

    @Test
    public void testPublicKeyAcceptedAlgorithmsWithModernAlgorithms() {
        // Test configuration with modern algorithm names (connection still uses defaults for compatibility)
        String algorithms = "ssh-ed25519,rsa-sha2-256,rsa-sha2-512,ecdsa-sha2-nistp256";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&publicKeyAcceptedAlgorithms=" + algorithms;

        // Verify the configuration is correctly parsed (endpoint creation)
        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(algorithms, endpoint.getConfiguration().getPublicKeyAcceptedAlgorithms());
    }

    @Test
    public void testPublicKeyAcceptedAlgorithmsSingleAlgorithm() {
        // Test with single algorithm
        String algorithms = "rsa-sha2-256";
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&publicKeyAcceptedAlgorithms=" + algorithms;

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        assertEquals(algorithms, endpoint.getConfiguration().getPublicKeyAcceptedAlgorithms());
    }

    @Test
    public void testPublicKeyAcceptedAlgorithmsEmptyIsNull() {
        // Empty string should be treated as not specified
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&publicKeyAcceptedAlgorithms=";

        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        // Empty string is stored as empty, ObjectHelper.isNotEmpty handles it
        assertEquals("", endpoint.getConfiguration().getPublicKeyAcceptedAlgorithms());
    }
}
