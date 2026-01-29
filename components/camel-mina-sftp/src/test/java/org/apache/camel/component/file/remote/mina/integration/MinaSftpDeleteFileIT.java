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

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP delete file operation.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpDeleteFileIT extends MinaSftpServerTestSupport {

    private String ftpRootDir;

    @BeforeEach
    public void doPostSetup() throws Exception {
        // Ensure the root directory exists
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
    }

    @Test
    public void testDeleteExistingFile() throws Exception {
        // Create a test file
        File testFile = ftpFile("delete-me.txt").toFile();
        Files.writeString(testFile.toPath(), "File to be deleted");
        assertTrue(testFile.exists(), "Test file should exist before deletion");

        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                                                        + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);

        RemoteFileOperations<SftpRemoteFile> operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);

        try {
            // Use absolute path for delete
            String absolutePath = ftpRootDir + "/delete-me.txt";
            boolean result = operations.deleteFile(absolutePath);
            assertTrue(result, "Delete operation should return true");
            assertFalse(testFile.exists(), "File should be deleted");
        } finally {
            operations.disconnect();
        }
    }

    @Test
    public void testDeleteNonExistentFile() throws Exception {
        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                                                        + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);

        RemoteFileOperations<SftpRemoteFile> operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);

        try {
            // Use absolute path for delete
            String absolutePath = ftpRootDir + "/non-existent-file.txt";

            // Attempting to delete a non-existent file should throw an exception
            assertThrows(GenericFileOperationFailedException.class,
                    () -> operations.deleteFile(absolutePath),
                    "Should throw exception when deleting non-existent file");
        } finally {
            operations.disconnect();
        }
    }

    @Test
    public void testDeleteMultipleFiles() throws Exception {
        // Create multiple test files
        File file1 = ftpFile("multi-delete-1.txt").toFile();
        File file2 = ftpFile("multi-delete-2.txt").toFile();
        File file3 = ftpFile("multi-delete-3.txt").toFile();

        Files.writeString(file1.toPath(), "File 1");
        Files.writeString(file2.toPath(), "File 2");
        Files.writeString(file3.toPath(), "File 3");

        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                                                        + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);

        RemoteFileOperations<SftpRemoteFile> operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);

        try {
            // Delete all three files using absolute paths
            assertTrue(operations.deleteFile(ftpRootDir + "/multi-delete-1.txt"));
            assertTrue(operations.deleteFile(ftpRootDir + "/multi-delete-2.txt"));
            assertTrue(operations.deleteFile(ftpRootDir + "/multi-delete-3.txt"));

            // Verify all are deleted
            assertFalse(file1.exists());
            assertFalse(file2.exists());
            assertFalse(file3.exists());
        } finally {
            operations.disconnect();
        }
    }

    @Test
    public void testDeleteFileInSubdirectory() throws Exception {
        // Create a subdirectory with a file
        File subdir = ftpFile("subdir-delete").toFile();
        subdir.mkdirs();
        File testFile = ftpFile("subdir-delete/nested-file.txt").toFile();
        Files.writeString(testFile.toPath(), "Nested file content");

        assertTrue(testFile.exists(), "Nested file should exist");

        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                                                        + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);

        RemoteFileOperations<SftpRemoteFile> operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);

        try {
            // Use absolute path for delete
            String absolutePath = ftpRootDir + "/subdir-delete/nested-file.txt";
            boolean result = operations.deleteFile(absolutePath);
            assertTrue(result, "Delete should succeed");
            assertFalse(testFile.exists(), "Nested file should be deleted");
            assertTrue(subdir.exists(), "Subdirectory should still exist");
        } finally {
            operations.disconnect();
        }
    }

    @Test
    public void testExistsFileAfterDelete() throws Exception {
        // Create a test file
        File testFile = ftpFile("exists-check.txt").toFile();
        Files.writeString(testFile.toPath(), "Check existence");

        MinaSftpEndpoint endpoint = context.getEndpoint(
                "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                                                        + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false",
                MinaSftpEndpoint.class);

        RemoteFileOperations<SftpRemoteFile> operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);

        try {
            String absolutePath = ftpRootDir + "/exists-check.txt";

            // File should exist before delete
            assertTrue(operations.existsFile(absolutePath), "File should exist before delete");

            // Delete the file
            operations.deleteFile(absolutePath);

            // File should not exist after delete
            assertFalse(operations.existsFile(absolutePath), "File should not exist after delete");
        } finally {
            operations.disconnect();
        }
    }
}
