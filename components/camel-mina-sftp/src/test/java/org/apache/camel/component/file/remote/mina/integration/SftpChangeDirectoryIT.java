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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.apache.camel.component.file.remote.mina.MinaSftpOperations;
import org.apache.camel.component.file.remote.mina.sftp.SftpServerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for directory change operations in MinaSftpOperations.
 */
public class SftpChangeDirectoryIT extends SftpServerTestSupport {

    private MinaSftpOperations operations;

    @BeforeEach
    public void setupOperations() throws Exception {
        // Create the endpoint and operations
        String uri = "mina-sftp://localhost:" + service.getPort() + "/?username=admin&password=admin&strictHostKeyChecking=no";
        MinaSftpEndpoint endpoint = context.getEndpoint(uri, MinaSftpEndpoint.class);
        operations = (MinaSftpOperations) endpoint.createRemoteFileOperations();

        // Connect to the SFTP server
        operations.connect(endpoint.getConfiguration(), null);
    }

    /**
     * T006: Test that changing to an existing directory succeeds.
     */
    @Test
    public void testChangeToExistingDirectorySucceeds() throws Exception {
        // Create a test directory on the SFTP server
        Path testDir = ftpFile("existingDir");
        Files.createDirectories(testDir);

        // Get the absolute path as seen by the SFTP server
        String sftpPath = testDir.toAbsolutePath().toString();

        // Change to the directory using absolute path - should succeed
        operations.changeCurrentDirectory(sftpPath);

        // Verify the current directory is updated
        String currentDir = operations.getCurrentDirectory();
        assertTrue(currentDir.endsWith("existingDir"),
                "Current directory should end with 'existingDir', but was: " + currentDir);
    }

    /**
     * T007: Test that changing to a non-existent directory throws an exception.
     */
    @Test
    public void testChangeToNonExistentDirectoryThrowsException() {
        // Attempt to change to a non-existent directory - should throw exception
        GenericFileOperationFailedException exception = assertThrows(
                GenericFileOperationFailedException.class,
                () -> operations.changeCurrentDirectory("nonExistentDir"),
                "Should throw exception when changing to non-existent directory");

        // Verify error message contains the path
        assertTrue(exception.getMessage().contains("nonExistentDir"),
                "Error message should contain the directory name");
    }

    /**
     * T008: Test that changing to a file path (not a directory) throws an appropriate exception.
     */
    @Test
    public void testChangeToFilePathThrowsException() throws Exception {
        // Create a file (not a directory) on the SFTP server
        Path testFile = ftpFile("testFile.txt");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "This is a file, not a directory");

        // Get the absolute path as seen by the SFTP server
        String sftpPath = testFile.toAbsolutePath().toString();

        // Attempt to change to the file - should throw exception
        GenericFileOperationFailedException exception = assertThrows(
                GenericFileOperationFailedException.class,
                () -> operations.changeCurrentDirectory(sftpPath),
                "Should throw exception when changing to a file path");

        // Verify error message indicates path is not a directory
        assertTrue(exception.getMessage().contains("not a directory"),
                "Error message should indicate the path is not a directory");
    }

    /**
     * T013: Test that buildDirectory and changeCurrentDirectory use consistent verification. Both should use stat() to
     * verify directory existence.
     */
    @Test
    public void testBuildDirectoryAndChangeDirUseConsistentVerification() throws Exception {
        // Use buildDirectory to create a new directory via SFTP operations
        String newDirPath = service.getFtpRootDir().toAbsolutePath() + "/createdDir";
        boolean created = operations.buildDirectory(newDirPath, true);
        assertTrue(created, "buildDirectory should succeed");

        // Now change to the directory we just created - should succeed
        // This verifies both operations use compatible verification mechanisms
        operations.changeCurrentDirectory(newDirPath);

        // Verify we're in the correct directory
        String currentDir = operations.getCurrentDirectory();
        assertTrue(currentDir.endsWith("createdDir"),
                "Current directory should end with 'createdDir', but was: " + currentDir);
    }
}
