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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP authentication scenarios.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpAuthenticationIT extends MinaSftpServerTestSupport {

    private String ftpRootDir;

    @BeforeEach
    public void doPostSetup() throws Exception {
        // Ensure the root directory exists
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
    }

    @Test
    public void testAuthenticationSuccess() throws Exception {
        // Valid credentials should work
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Auth success test", Exchange.FILE_NAME, "auth-success.txt");

        File file = ftpFile("auth-success.txt").toFile();
        assertTrue(file.exists(), "File should exist after successful auth");
    }

    @Test
    public void testAuthenticationWithDifferentUser() throws Exception {
        // The embedded server accepts any user/password for testing
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=testuser&password=testpass&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Different user test", Exchange.FILE_NAME, "different-user.txt");

        File file = ftpFile("different-user.txt").toFile();
        assertTrue(file.exists(), "File should exist");
    }

    @Test
    public void testConnectionToWrongPort() {
        // Connecting to a wrong port should fail with clear error
        String uri = "mina-sftp://localhost:19999/test"
                     + "?username=admin&password=admin&strictHostKeyChecking=no&connectTimeout=5000";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail", Exchange.FILE_NAME, "fail.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());
        assertTrue(cause.getMessage().contains("Cannot connect") || cause.getMessage().contains("Connection refused"),
                "Error message should indicate connection failure: " + cause.getMessage());
    }

    @Test
    public void testConnectionTimeout() {
        // Connection to non-routable IP should timeout
        // Using 10.255.255.1 which is typically non-routable
        String uri = "mina-sftp://10.255.255.1:22/test"
                     + "?username=admin&password=admin&strictHostKeyChecking=no&connectTimeout=2000";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should timeout", Exchange.FILE_NAME, "timeout.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException");
    }

    @Test
    public void testReconnectAfterDisconnect() throws Exception {
        // First connection
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "First connection", Exchange.FILE_NAME, "reconnect1.txt");
        assertTrue(ftpFile("reconnect1.txt").toFile().exists());

        // Second connection should also work (tests reconnection)
        template.sendBodyAndHeader(uri, "Second connection", Exchange.FILE_NAME, "reconnect2.txt");
        assertTrue(ftpFile("reconnect2.txt").toFile().exists());

        // Third connection
        template.sendBodyAndHeader(uri, "Third connection", Exchange.FILE_NAME, "reconnect3.txt");
        assertTrue(ftpFile("reconnect3.txt").toFile().exists());
    }

    @Test
    public void testUsernameInUri() throws Exception {
        // Username can be specified in the URI authority section
        String uri = "mina-sftp://admin@localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Username in URI", Exchange.FILE_NAME, "username-uri.txt");

        File file = ftpFile("username-uri.txt").toFile();
        assertTrue(file.exists(), "File should exist");
    }
}
