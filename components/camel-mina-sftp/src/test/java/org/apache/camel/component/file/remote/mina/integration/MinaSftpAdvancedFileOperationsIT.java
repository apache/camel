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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for advanced SFTP file operations: fileExist strategies, symbolic links, and server-side move
 * efficiency.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpAdvancedFileOperationsIT extends MinaSftpServerTestSupport {

    private static final Logger log = LoggerFactory.getLogger(MinaSftpAdvancedFileOperationsIT.class);

    private String ftpRootDir;
    private String testId;

    private Path brokenSymlink;

    @BeforeEach
    public void doPostSetup() {
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
        // Generate unique ID for each test run to avoid file conflicts between retries
        testId = String.valueOf(System.currentTimeMillis() % 100000);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (brokenSymlink != null) {
            Files.delete(brokenSymlink);
        }
    }

    private String baseUri() {
        return "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
               + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";
    }

    // ========================================
    // fileExist=Append TESTS
    // ========================================

    @Test
    @Timeout(30)
    public void testFileExistAppendToExistingFile() throws Exception {
        // Create initial file
        template.sendBodyAndHeader(baseUri(), "Initial content\n", Exchange.FILE_NAME, "append-test.txt");

        File file = ftpFile("append-test.txt").toFile();
        assertTrue(file.exists(), "Initial file should exist");
        assertEquals("Initial content\n", context.getTypeConverter().convertTo(String.class, file));

        // Append to existing file
        String uri = baseUri() + "&fileExist=Append";
        template.sendBodyAndHeader(uri, "Appended content\n", Exchange.FILE_NAME, "append-test.txt");

        // Verify content is appended
        String content = context.getTypeConverter().convertTo(String.class, file);
        assertEquals("Initial content\nAppended content\n", content, "Content should be appended");
    }

    @Test
    @Timeout(30)
    public void testFileExistAppendToNonExistingFile() throws Exception {
        // Append to non-existing file should create it
        String fileName = "append-new-" + testId + ".txt";
        String uri = baseUri() + "&fileExist=Append";
        template.sendBodyAndHeader(uri, "New content\n", Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "File should be created");
        assertEquals("New content\n", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFileExistAppendMultipleTimes() throws Exception {
        String fileName = "multi-append-" + testId + ".txt";
        String uri = baseUri() + "&fileExist=Append";

        // Append multiple times
        template.sendBodyAndHeader(uri, "Line 1\n", Exchange.FILE_NAME, fileName);
        template.sendBodyAndHeader(uri, "Line 2\n", Exchange.FILE_NAME, fileName);
        template.sendBodyAndHeader(uri, "Line 3\n", Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        String content = context.getTypeConverter().convertTo(String.class, file);
        assertEquals("Line 1\nLine 2\nLine 3\n", content, "All lines should be appended");
    }

    @Test
    @Timeout(30)
    public void testFileExistAppendWithBinaryData() throws Exception {
        // Create initial binary file
        byte[] initial = { 0x01, 0x02, 0x03 };
        template.sendBodyAndHeader(baseUri(), initial, Exchange.FILE_NAME, "binary-append.bin");

        // Append binary data
        byte[] append = { 0x04, 0x05, 0x06 };
        String uri = baseUri() + "&fileExist=Append";
        template.sendBodyAndHeader(uri, append, Exchange.FILE_NAME, "binary-append.bin");

        // Verify
        File file = ftpFile("binary-append.bin").toFile();
        byte[] result = Files.readAllBytes(file.toPath());
        assertEquals(6, result.length, "Binary file should have 6 bytes");
        assertEquals(0x01, result[0]);
        assertEquals(0x02, result[1]);
        assertEquals(0x03, result[2]);
        assertEquals(0x04, result[3]);
        assertEquals(0x05, result[4]);
        assertEquals(0x06, result[5]);
    }

    @Test
    @Timeout(30)
    public void testFileExistAppendLargeData() throws Exception {
        // Create initial file
        String initial = "A".repeat(10000);
        template.sendBodyAndHeader(baseUri(), initial, Exchange.FILE_NAME, "large-append.txt");

        // Append large data
        String append = "B".repeat(10000);
        String uri = baseUri() + "&fileExist=Append";
        template.sendBodyAndHeader(uri, append, Exchange.FILE_NAME, "large-append.txt");

        // Verify
        File file = ftpFile("large-append.txt").toFile();
        assertEquals(20000, file.length(), "File should have 20000 bytes after append");
        String content = context.getTypeConverter().convertTo(String.class, file);
        assertTrue(content.startsWith("AAAA"), "Content should start with A's");
        assertTrue(content.endsWith("BBBB"), "Content should end with B's");
    }

    // ========================================
    // fileExist=Ignore TESTS
    // ========================================

    @Test
    @Timeout(30)
    public void testFileExistIgnoreExistingFile() throws Exception {
        // Create initial file
        template.sendBodyAndHeader(baseUri(), "Original content", Exchange.FILE_NAME, "ignore-test.txt");

        File file = ftpFile("ignore-test.txt").toFile();
        assertTrue(file.exists(), "Initial file should exist");

        // Try to write with Ignore - should succeed without changing the file
        String uri = baseUri() + "&fileExist=Ignore";
        template.sendBodyAndHeader(uri, "New content", Exchange.FILE_NAME, "ignore-test.txt");

        // Verify original content is preserved
        assertEquals("Original content", context.getTypeConverter().convertTo(String.class, file),
                "Original content should be preserved with fileExist=Ignore");
    }

    @Test
    @Timeout(30)
    public void testFileExistIgnoreNonExistingFile() throws Exception {
        // Write to non-existing file with Ignore - should create it
        String uri = baseUri() + "&fileExist=Ignore";
        template.sendBodyAndHeader(uri, "New file content", Exchange.FILE_NAME, "ignore-new.txt");

        File file = ftpFile("ignore-new.txt").toFile();
        assertTrue(file.exists(), "New file should be created");
        assertEquals("New file content", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFileExistIgnoreNoExceptionThrown() throws Exception {
        // Create existing file
        template.sendBodyAndHeader(baseUri(), "Existing", Exchange.FILE_NAME, "ignore-no-error.txt");

        // Ignore should not throw exception
        String uri = baseUri() + "&fileExist=Ignore";
        // This should complete without exception
        template.sendBodyAndHeader(uri, "New", Exchange.FILE_NAME, "ignore-no-error.txt");

        // Verify the method returned successfully (no exception)
        assertTrue(true, "No exception should be thrown");
    }

    // ========================================
    // fileExist=Fail TESTS
    // ========================================

    @Test
    @Timeout(30)
    public void testFileExistFailOnExistingFile() throws Exception {
        // Create initial file
        template.sendBodyAndHeader(baseUri(), "Existing content", Exchange.FILE_NAME, "fail-test.txt");

        File file = ftpFile("fail-test.txt").toFile();
        assertTrue(file.exists(), "Initial file should exist");

        // Try to write with Fail - should throw exception
        String uri = baseUri() + "&fileExist=Fail";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "New content", Exchange.FILE_NAME, "fail-test.txt"));

        Throwable cause = exception.getCause();
        assertTrue(cause instanceof GenericFileOperationFailedException,
                "Should be GenericFileOperationFailedException but was: " + cause.getClass());
        assertTrue(cause.getMessage().contains("already exist"),
                "Error message should mention file already exists: " + cause.getMessage());
    }

    @Test
    @Timeout(30)
    public void testFileExistFailOnNonExistingFile() throws Exception {
        // Write to non-existing file with Fail - should succeed
        String fileName = "fail-new-" + testId + ".txt";
        String uri = baseUri() + "&fileExist=Fail";
        template.sendBodyAndHeader(uri, "New file content", Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "New file should be created");
        assertEquals("New file content", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFileExistFailPreservesOriginalContent() throws Exception {
        // Create file with specific content
        String originalContent = "Important data that should not be lost";
        template.sendBodyAndHeader(baseUri(), originalContent, Exchange.FILE_NAME, "fail-preserve.txt");

        // Try to overwrite with Fail
        String uri = baseUri() + "&fileExist=Fail";
        try {
            template.sendBodyAndHeader(uri, "Overwrite attempt", Exchange.FILE_NAME, "fail-preserve.txt");
        } catch (CamelExecutionException e) {
            // Expected
        }

        // Verify original content is preserved
        File file = ftpFile("fail-preserve.txt").toFile();
        assertEquals(originalContent, context.getTypeConverter().convertTo(String.class, file),
                "Original content should be preserved after failed write");
    }

    // ========================================
    // fileExist=Override TESTS (for completeness)
    // ========================================

    @Test
    @Timeout(30)
    public void testFileExistOverrideExistingFile() throws Exception {
        // Create initial file
        template.sendBodyAndHeader(baseUri(), "Original content", Exchange.FILE_NAME, "override-test.txt");

        // Override with new content (default behavior)
        String uri = baseUri() + "&fileExist=Override";
        template.sendBodyAndHeader(uri, "New content", Exchange.FILE_NAME, "override-test.txt");

        File file = ftpFile("override-test.txt").toFile();
        assertEquals("New content", context.getTypeConverter().convertTo(String.class, file),
                "Content should be overwritten");
    }

    // ========================================
    // SERVER-SIDE MOVE (RENAME) TESTS
    // ========================================

    @Test
    @Timeout(30)
    public void testServerSideRenameOperation() throws Exception {
        // Create initial file
        template.sendBodyAndHeader(baseUri(), "Rename test content", Exchange.FILE_NAME, "rename-source.txt");

        File sourceFile = ftpFile("rename-source.txt").toFile();
        assertTrue(sourceFile.exists(), "Source file should exist");

        MockEndpoint mock = getMockEndpoint("mock:renamed");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Rename test content");
        mock.setResultWaitTime(15000);

        // Consume with move (which uses server-side rename)
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=rename-source.txt&move=done/${file:name}&delay=1000&initialDelay=0")
                        .routeId("renameTest")
                        .to("mock:renamed");
            }
        });

        // Create the done directory
        ftpFile("done").toFile().mkdirs();

        context.getRouteController().startRoute("renameTest");
        mock.assertIsSatisfied();

        // Verify source file is gone and target file exists
        Thread.sleep(500);
        assertTrue(!sourceFile.exists(), "Source file should be moved");
        assertTrue(ftpFile("done/rename-source.txt").toFile().exists(), "Moved file should exist in done folder");

        // Verify content is intact (no download/upload)
        assertEquals("Rename test content",
                context.getTypeConverter().convertTo(String.class, ftpFile("done/rename-source.txt").toFile()));

        context.getRouteController().stopRoute("renameTest");
    }

    @Test
    @Timeout(30)
    public void testServerSideRenameWithSubdirectory() throws Exception {
        // Create file in subdirectory
        template.sendBodyAndHeader(baseUri(), "Subdirectory rename test",
                Exchange.FILE_NAME, "subdir/rename-sub.txt");

        assertTrue(ftpFile("subdir/rename-sub.txt").toFile().exists(), "File in subdirectory should exist");

        MockEndpoint mock = getMockEndpoint("mock:renamedSub");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);

        // Create target directory
        ftpFile("subdir/processed").toFile().mkdirs();

        // Need to construct URI properly for subdirectory
        String subDirUri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir + "/subdir"
                           + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                           + "&fileName=rename-sub.txt&move=processed/${file:name}&delay=1000&initialDelay=0";

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(subDirUri)
                        .routeId("renameSubTest")
                        .to("mock:renamedSub");
            }
        });

        context.getRouteController().startRoute("renameSubTest");
        mock.assertIsSatisfied();

        Thread.sleep(500);
        assertTrue(!ftpFile("subdir/rename-sub.txt").toFile().exists(), "Source should be moved");
        assertTrue(ftpFile("subdir/processed/rename-sub.txt").toFile().exists(), "Target should exist");

        context.getRouteController().stopRoute("renameSubTest");
    }

    @Test
    @Timeout(30)
    public void testRenameWithMoveExisting() throws Exception {
        // Create initial file with unique name
        String fileName = "move-exist-source-" + testId + ".txt";
        String renamedName = "renamed-move-exist-source-" + testId + ".txt";
        template.sendBodyAndHeader(baseUri(), "Original", Exchange.FILE_NAME, fileName);

        // Write with moveExisting using the same pattern as existing tests
        String uri = baseUri() + "&fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}";
        template.sendBodyAndHeader(uri, "New content", Exchange.FILE_NAME, fileName);

        // Verify new file has new content
        assertEquals("New content",
                context.getTypeConverter().convertTo(String.class, ftpFile(fileName).toFile()));

        // Verify renamed file exists with original content
        assertTrue(ftpFile(renamedName).toFile().exists(),
                "Renamed file should exist");
        assertEquals("Original",
                context.getTypeConverter().convertTo(String.class, ftpFile(renamedName).toFile()));
    }

    @Test
    @Timeout(30)
    public void testRenamePreservesFileSize() throws Exception {
        // Create a file with specific size
        String content = "X".repeat(12345);
        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, "size-preserve.txt");

        long originalSize = ftpFile("size-preserve.txt").toFile().length();
        assertEquals(12345, originalSize, "Original file should be 12345 bytes");

        // Move the file
        ftpFile("moved").toFile().mkdirs();

        MockEndpoint mock = getMockEndpoint("mock:sizePreserve");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=size-preserve.txt&move=moved/${file:name}&delay=1000&initialDelay=0")
                        .routeId("sizePreserveTest")
                        .to("mock:sizePreserve");
            }
        });

        context.getRouteController().startRoute("sizePreserveTest");
        mock.assertIsSatisfied();

        Thread.sleep(500);
        // Verify moved file has same size
        long movedSize = ftpFile("moved/size-preserve.txt").toFile().length();
        assertEquals(originalSize, movedSize, "Moved file should have same size (server-side rename)");

        context.getRouteController().stopRoute("sizePreserveTest");
    }

    // ========================================
    // SYMBOLIC LINKS TESTS
    // Note: Symlink read/write tests are in MinaSftpSymlinkIT.
    // This test only covers broken symlink handling to verify consumer error handling.
    // ========================================

    @Test
    @Timeout(30)
    @EnabledOnOs({ OS.LINUX, OS.MAC })
    public void testBrokenSymbolicLink() throws Exception {
        Path linkPath = ftpFile("broken-link.txt");
        Path nonExistentTarget = ftpFile("non-existent-target.txt");

        // Create symbolic link to non-existent target
        try {
            brokenSymlink = Files.createSymbolicLink(linkPath, nonExistentTarget);
        } catch (Exception e) {
            log.warn("Could not create broken symlink: {}", e.getMessage());
            return;
        }

        assertTrue(Files.isSymbolicLink(linkPath), "Broken symlink should be created");
        assertTrue(!Files.exists(linkPath), "Target should not exist (broken link)");

        // Consumer should handle broken symlink gracefully
        MockEndpoint mock = getMockEndpoint("mock:brokenLink");
        mock.expectedMessageCount(0); // Should not receive any message
        mock.setResultWaitTime(5000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=broken-link.txt&delay=1000&initialDelay=0")
                        .routeId("brokenLinkTest")
                        .to("mock:brokenLink");
            }
        });

        context.getRouteController().startRoute("brokenLinkTest");
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("brokenLinkTest");
    }

    // ========================================
    // COMBINED SCENARIOS
    // ========================================

    @Test
    @Timeout(30)
    public void testAppendWithCompression() throws Exception {
        // Test fileExist=Append combined with compression
        String fileName = "append-compressed-" + testId + ".txt";
        String uri = baseUri() + "&fileExist=Append&compression=6";

        template.sendBodyAndHeader(uri, "Compressed Line 1\n", Exchange.FILE_NAME, fileName);
        template.sendBodyAndHeader(uri, "Compressed Line 2\n", Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        String content = context.getTypeConverter().convertTo(String.class, file);
        assertEquals("Compressed Line 1\nCompressed Line 2\n", content);
    }

    @Test
    @Timeout(30)
    public void testFailWithTempFile() throws Exception {
        // Create existing file
        template.sendBodyAndHeader(baseUri(), "Existing", Exchange.FILE_NAME, "fail-temp.txt");

        // Try to write with Fail and tempFileName
        String uri = baseUri() + "&fileExist=Fail&tempFileName=${file:name}.tmp";

        CamelExecutionException exception = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "New", Exchange.FILE_NAME, "fail-temp.txt"));

        assertNotNull(exception.getCause(), "Should have cause exception");

        // Verify temp file doesn't remain
        assertTrue(!ftpFile("fail-temp.txt.tmp").toFile().exists(),
                "Temp file should not remain after failure");
    }

    @Test
    @Timeout(30)
    public void testIgnoreWithMultipleProducers() throws Exception {
        // Create initial file
        template.sendBodyAndHeader(baseUri(), "First", Exchange.FILE_NAME, "ignore-multi.txt");

        String uri = baseUri() + "&fileExist=Ignore";

        // Multiple attempts to write - all should be ignored
        for (int i = 0; i < 5; i++) {
            template.sendBodyAndHeader(uri, "Attempt " + i, Exchange.FILE_NAME, "ignore-multi.txt");
        }

        // Verify only first content remains
        assertEquals("First", context.getTypeConverter().convertTo(String.class, ftpFile("ignore-multi.txt").toFile()));
    }
}
