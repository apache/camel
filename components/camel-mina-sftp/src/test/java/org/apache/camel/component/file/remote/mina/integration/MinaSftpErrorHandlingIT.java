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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP error handling scenarios.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpErrorHandlingIT extends MinaSftpServerTestSupport {

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
    public void testConnectionToStoppedServer() throws Exception {
        // First, verify the server is working
        template.sendBodyAndHeader(baseUri(), "Initial test", Exchange.FILE_NAME, "initial.txt");
        assertTrue(ftpFile("initial.txt").toFile().exists(), "Initial file should exist");

        // Stop the server
        service.tearDownServer();

        // Attempt to connect should fail
        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(baseUri(), "Should fail", Exchange.FILE_NAME, "fail.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());
    }

    @Test
    @Timeout(30)
    public void testReconnectAfterServerRestart() throws Exception {
        // First connection
        template.sendBodyAndHeader(baseUri(), "Before restart", Exchange.FILE_NAME, "before-restart.txt");
        assertTrue(ftpFile("before-restart.txt").toFile().exists(), "File should exist before restart");

        // Restart the server
        service.tearDownServer();
        service.setUpServer();

        // Recreate the root directory after restart
        service.getFtpRootDir().toFile().mkdirs();

        // Connection after restart should work
        String newUri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                        + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(newUri, "After restart", Exchange.FILE_NAME, "after-restart.txt");
        assertTrue(service.getFtpRootDir().resolve("after-restart.txt").toFile().exists(),
                "File should exist after restart");
    }

    @Test
    @Timeout(30)
    public void testTransferInterruptedByDisconnect() throws Exception {
        // Create a file to consume
        template.sendBodyAndHeader(baseUri(), "Content to consume", Exchange.FILE_NAME, "to-consume.txt");
        assertTrue(ftpFile("to-consume.txt").toFile().exists(), "File should exist");

        // Disconnect all sessions
        service.disconnectAllSessions();

        // Next operation should handle the disconnect gracefully or reconnect
        // Depending on configuration, this may succeed (reconnect) or fail
        try {
            template.sendBodyAndHeader(baseUri(), "After disconnect", Exchange.FILE_NAME, "after-disconnect.txt");
            // If it succeeds, the component handled reconnection
            assertTrue(ftpFile("after-disconnect.txt").toFile().exists(), "File should exist after reconnect");
        } catch (CamelExecutionException e) {
            // If it fails, verify it's the expected exception type
            assertTrue(e.getCause() instanceof GenericFileOperationFailedException,
                    "Should be GenericFileOperationFailedException");
        }
    }

    @Test
    @Timeout(30)
    public void testReadNonExistentFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setResultWaitTime(3000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=non-existent-file.txt&delay=1000&initialDelay=0")
                        .routeId("testNonExistent")
                        .to("mock:result");
            }
        });

        context.getRouteController().startRoute("testNonExistent");

        // Wait and verify no messages were received
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("testNonExistent");
    }

    @Test
    @Timeout(30)
    public void testConnectionTimeoutHandling() {
        // Connection to non-routable IP should timeout
        // Using 10.255.255.1 which is typically non-routable
        String uri = "mina-sftp://10.255.255.1:22/test"
                     + "?username=admin&password=admin&strictHostKeyChecking=no&connectTimeout=2000";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should timeout", Exchange.FILE_NAME, "timeout.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());
    }

    @Test
    @Timeout(30)
    public void testWriteToNonExistentDirectory() throws Exception {
        // Try to write to a deeply nested directory that doesn't exist
        // The component should create it automatically
        String uri = baseUri() + "&autoCreate=true";

        template.sendBodyAndHeader(uri, "Content in nested dir", Exchange.FILE_NAME,
                "nested/deep/path/file.txt");

        File file = ftpFile("nested/deep/path/file.txt").toFile();
        assertTrue(file.exists(), "File should be created in nested directory");
        assertEquals("Content in nested dir", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testWriteToNonExistentDirectoryWithoutAutoCreate() {
        // Try to write to a non-existent directory without autoCreate
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir + "/nonexistent"
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false&autoCreate=false";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Should fail", Exchange.FILE_NAME, "fail.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());
    }

    @Test
    @Timeout(30)
    public void testMultipleFailuresDoNotLeakResources() throws Exception {
        // Perform multiple failed operations and verify no resource leaks
        String badUri = "mina-sftp://localhost:19999/test"
                        + "?username=admin&password=admin&strictHostKeyChecking=no&connectTimeout=1000";

        for (int i = 0; i < 5; i++) {
            assertThrows(
                    CamelExecutionException.class,
                    () -> template.sendBodyAndHeader(badUri, "Fail", Exchange.FILE_NAME, "fail.txt"));
        }

        // After multiple failures, a valid operation should still work
        template.sendBodyAndHeader(baseUri(), "Success after failures", Exchange.FILE_NAME, "success.txt");
        assertTrue(ftpFile("success.txt").toFile().exists(), "File should exist after multiple failures");
    }
}
