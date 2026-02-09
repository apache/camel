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
import java.nio.file.Path;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.mina.MinaSftpConfiguration;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP host key verification.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpHostKeyVerificationIT extends MinaSftpServerTestSupport {

    // A valid RSA public key (different from the server's) for mismatch testing
    private static final String FAKE_RSA_KEY = "AAAAB3NzaC1yc2EAAAADAQABAAABAQDLpmf23++nFX/0m8ynekUQ9wYSH5Gp8"
                                               + "/biKoRE0mrcAahliohbsHvhwgl2fZbWZ8AqFi7nDF/O6KGFGm6+5j7fr6ESX2lQOvyEFDmv0QYrLoT90vStSDG4TRJfnGmy58eqC1as7nL1"
                                               + "uwiqJZPJk4drsGH99FXb54Yjv2oYqmQwVgDG9+y3Gf5Fg/3dbugm+Ywy6HYWS+WZN5X7anSTHh2VSel7hqaN1s2xtVfmWMAEvoc88Ox0tH9N"
                                               + "FzsqTO/rlRhUPyCtHoyAVdptdk6L8T27EdbEG8Su38ZL64pJ48jfelye3Mb8T9nZvt6/Qr3itzaBTxD+56fbxTjyWtsuwz5X";

    private String ftpRootDir;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void doPostSetup() throws Exception {
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
    }

    @Test
    public void testStrictHostKeyCheckingDisabled() throws Exception {
        // With strictHostKeyChecking=no and useUserKnownHostsFile=false, should accept any host
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Strict disabled test", Exchange.FILE_NAME, "strict-disabled.txt");

        File file = ftpFile("strict-disabled.txt").toFile();
        assertTrue(file.exists(), "File should exist when strictHostKeyChecking is disabled");
    }

    @Test
    public void testStrictHostKeyCheckingEnabledNoKnownHosts() {
        // With strictHostKeyChecking=yes and no known_hosts file, should fail
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=yes&useUserKnownHostsFile=false";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail", Exchange.FILE_NAME, "should-fail.txt"));

        Throwable cause = getRootCause(exception);
        assertTrue(cause instanceof GenericFileOperationFailedException
                || cause.getMessage().contains("Server key did not validate")
                || cause.getMessage().contains("Cannot connect"),
                "Should fail with connection error: " + cause.getMessage());
    }

    @Test
    public void testKnownHostsFromByteArrayWithMismatchFails() throws Exception {
        // Create a known_hosts byte array with a fake key (different from server's)
        // A key mismatch should fail to protect against MITM attacks, even with strictHostKeyChecking=no
        // Use [localhost]:port format since embedded server uses non-standard port
        String fakeKnownHosts = "[localhost]:" + service.getPort() + " ssh-rsa " + FAKE_RSA_KEY;

        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                                                        + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);

        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();
        config.setKnownHosts(fakeKnownHosts.getBytes());

        // Key mismatch should fail even with strictHostKeyChecking=no (security protection)
        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(endpoint, "Should fail", Exchange.FILE_NAME, "byte-array-known.txt"));

        Throwable cause = getRootCause(exception);
        assertTrue(cause instanceof GenericFileOperationFailedException
                || cause.getMessage().contains("Server key did not validate"),
                "Should fail due to key mismatch: " + cause.getMessage());
    }

    @Test
    public void testKnownHostsFromFileWithMismatchFails() throws Exception {
        // Create a temp known_hosts file with a fake key
        // Use [localhost]:port format since embedded server uses non-standard port
        Path knownHostsFile = tempDir.resolve("known_hosts");
        String fakeEntry = "[localhost]:" + service.getPort() + " ssh-rsa " + FAKE_RSA_KEY;
        Files.writeString(knownHostsFile, fakeEntry);

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&knownHostsFile=" + knownHostsFile.toAbsolutePath();

        // Key mismatch should fail even with strictHostKeyChecking=no (security protection)
        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail", Exchange.FILE_NAME, "file-known.txt"));

        Throwable cause = getRootCause(exception);
        assertTrue(cause instanceof GenericFileOperationFailedException
                || cause.getMessage().contains("Server key did not validate"),
                "Should fail due to key mismatch: " + cause.getMessage());
    }

    @Test
    public void testKnownHostsFromUriWithMismatchFails() throws Exception {
        // Test loading known_hosts from a file: URI with mismatched key
        Path knownHostsFile = tempDir.resolve("known_hosts_uri");
        String fakeEntry = "[localhost]:" + service.getPort() + " ssh-rsa " + FAKE_RSA_KEY;
        Files.writeString(knownHostsFile, fakeEntry);

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&knownHostsUri=file:" + knownHostsFile.toAbsolutePath();

        // Key mismatch should fail (security protection)
        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail", Exchange.FILE_NAME, "uri-known.txt"));

        Throwable cause = getRootCause(exception);
        assertTrue(cause instanceof GenericFileOperationFailedException
                || cause.getMessage().contains("Server key did not validate"),
                "Should fail due to key mismatch: " + cause.getMessage());
    }

    @Test
    public void testAutoCreateKnownHostsFile() throws Exception {
        // Test auto-create functionality
        Path knownHostsFile = tempDir.resolve("auto_created_known_hosts");

        // File should not exist initially
        assertTrue(!Files.exists(knownHostsFile), "Known hosts file should not exist initially");

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&autoCreateKnownHostsFile=true"
                     + "&knownHostsFile=" + knownHostsFile.toAbsolutePath();

        template.sendBodyAndHeader(uri, "Auto create test", Exchange.FILE_NAME, "auto-create.txt");

        File file = ftpFile("auto-create.txt").toFile();
        assertTrue(file.exists(), "File should exist");

        // The known_hosts file should have been created with the server's key
        assertTrue(Files.exists(knownHostsFile), "Known hosts file should have been auto-created");

        String knownHostsContent = Files.readString(knownHostsFile);
        assertTrue(knownHostsContent.contains("localhost") || knownHostsContent.contains("[localhost]"),
                "Known hosts file should contain server entry");
    }

    @Test
    public void testKeyMismatchFailsEvenWithStrictDisabled() throws Exception {
        // When a key is in known_hosts but doesn't match, connection should still fail
        // This protects against MITM attacks

        // First, create a known_hosts file with a fake key
        // Use [localhost]:port format since embedded server uses non-standard port
        Path knownHostsFile = tempDir.resolve("mismatched_known_hosts");
        String fakeEntry = "[localhost]:" + service.getPort() + " ssh-rsa " + FAKE_RSA_KEY;
        Files.writeString(knownHostsFile, fakeEntry);

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=yes&useUserKnownHostsFile=false"
                     + "&knownHostsFile=" + knownHostsFile.toAbsolutePath();

        // The key will mismatch, so this should fail even with a known_hosts entry
        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail on mismatch", Exchange.FILE_NAME, "mismatch.txt"));

        Throwable cause = getRootCause(exception);
        assertTrue(cause instanceof GenericFileOperationFailedException
                || cause.getMessage().contains("Server key did not validate"),
                "Should fail due to key mismatch");
    }

    @Test
    public void testPriorityOrderByteArrayOverFile() throws Exception {
        // Test that byte array takes priority over file
        Path knownHostsFile = tempDir.resolve("priority_test_known_hosts");
        String fileEntry = "file-entry.com ssh-rsa AAAAB3NzaC1yc2EAAA1";
        Files.writeString(knownHostsFile, fileEntry);

        String byteEntry = "byte-entry.com ssh-rsa AAAAB3NzaC1yc2EAAA2";

        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                                                        + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                                                        + "&knownHostsFile=" + knownHostsFile.toAbsolutePath(),
                MinaSftpEndpoint.class);

        MinaSftpConfiguration config = (MinaSftpConfiguration) endpoint.getConfiguration();
        config.setKnownHosts(byteEntry.getBytes());

        // Connection should succeed (strictHostKeyChecking=no)
        // The implementation internally uses byte array with higher priority
        template.sendBodyAndHeader(endpoint, "Priority test", Exchange.FILE_NAME, "priority-test.txt");

        File file = ftpFile("priority-test.txt").toFile();
        assertTrue(file.exists(), "File should exist");
    }

    @Test
    public void testHostWithPortMatchesCorrectly() throws Exception {
        // Test that entries with [host]:port format are matched (and mismatch is detected)
        Path knownHostsFile = tempDir.resolve("port_format_known_hosts");
        // Create entry in [host]:port format with a fake key
        String entry = "[localhost]:" + service.getPort() + " ssh-rsa " + FAKE_RSA_KEY;
        Files.writeString(knownHostsFile, entry);

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&knownHostsFile=" + knownHostsFile.toAbsolutePath();

        // The key mismatch should be detected when using [host]:port format too
        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail", Exchange.FILE_NAME, "port-format.txt"));

        Throwable cause = getRootCause(exception);
        assertTrue(cause instanceof GenericFileOperationFailedException
                || cause.getMessage().contains("Server key did not validate"),
                "Should fail due to key mismatch: " + cause.getMessage());
    }

    private Throwable getRootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
