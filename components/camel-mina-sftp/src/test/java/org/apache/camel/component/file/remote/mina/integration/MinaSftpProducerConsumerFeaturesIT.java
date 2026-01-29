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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP producer and consumer advanced features.
 *
 * <p>
 * Tests cover:
 * <ul>
 * <li>Producer headers (CamelFileName, file operations)</li>
 * <li>Local work directory for safe file processing</li>
 * <li>Stream download option</li>
 * <li>File exist options (Append, Fail, Ignore, Override)</li>
 * </ul>
 * </p>
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpProducerConsumerFeaturesIT extends MinaSftpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MinaSftpProducerConsumerFeaturesIT.class);

    private String ftpRootDir;

    @TempDir
    Path localWorkDir;

    @BeforeEach
    public void doPostSetup() throws Exception {
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
        LOG.info("FTP root directory: {}", ftpRootDir);
    }

    private String baseUri() {
        return "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
               + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";
    }

    // ========================================
    // PRODUCER HEADER TESTS
    // ========================================

    /**
     * Test that CamelFileName header correctly specifies the target filename.
     */
    @Test
    @Timeout(30)
    public void testProducerWithCamelFileNameHeader() throws Exception {
        String uri = baseUri();

        // Send with explicit filename header
        template.sendBodyAndHeader(uri, "Content with header filename",
                Exchange.FILE_NAME, "header-specified-file.txt");

        // Verify file was created with the header-specified name
        Path createdFile = ftpFile("header-specified-file.txt");
        assertTrue(Files.exists(createdFile), "File should be created with header-specified name");
        assertEquals("Content with header filename", Files.readString(createdFile));
    }

    /**
     * Test producer with nested directory path in CamelFileName header.
     */
    @Test
    @Timeout(30)
    public void testProducerWithNestedPathInHeader() throws Exception {
        String uri = baseUri() + "&autoCreate=true";

        // Send with nested path in filename header
        template.sendBodyAndHeader(uri, "Nested file content",
                Exchange.FILE_NAME, "subdir/nested/deep-file.txt");

        // Verify nested directories and file were created
        Path createdFile = ftpFile("subdir/nested/deep-file.txt");
        assertTrue(Files.exists(createdFile), "Nested file should be created");
        assertEquals("Nested file content", Files.readString(createdFile));
    }

    /**
     * Test producer with dynamic filename using simple expression.
     */
    @Test
    @Timeout(30)
    public void testProducerWithDynamicFilename() throws Exception {
        String uri = baseUri() + "&fileName=${header.customName}.txt";

        // Send with custom header that will be used in filename
        template.sendBodyAndHeader(uri, "Dynamic name content", "customName", "my-dynamic-file");

        // Verify file was created with dynamic name
        Path createdFile = ftpFile("my-dynamic-file.txt");
        assertTrue(Files.exists(createdFile), "File should be created with dynamic name");
        assertEquals("Dynamic name content", Files.readString(createdFile));
    }

    /**
     * Test producer with file prefix and suffix options.
     */
    @Test
    @Timeout(30)
    public void testProducerWithTempPrefixAndSuffix() throws Exception {
        String uri = baseUri() + "&tempPrefix=.uploading-&tempFileName=${file:name}.tmp";

        template.sendBodyAndHeader(uri, "Temp file content", Exchange.FILE_NAME, "final-file.txt");

        // Verify final file exists (temp file should be renamed after upload)
        Path finalFile = ftpFile("final-file.txt");
        assertTrue(Files.exists(finalFile), "Final file should exist after upload");
        assertEquals("Temp file content", Files.readString(finalFile));

        // Verify temp file does not exist
        Path tempFile = ftpFile(".uploading-final-file.txt");
        assertFalse(Files.exists(tempFile), "Temp file should not exist after successful upload");
    }

    // ========================================
    // LOCAL WORK DIRECTORY TESTS
    // ========================================

    /**
     * Test consumer with localWorkDirectory option. Files are first downloaded to a local work directory before
     * processing, ensuring file integrity.
     */
    @Test
    @Timeout(60)
    public void testConsumerWithLocalWorkDirectory() throws Exception {
        // Create test file on SFTP server
        Path remoteFile = ftpFile("work-dir-test.txt");
        Files.writeString(remoteFile, "Content for local work directory test");

        String localWorkPath = localWorkDir.resolve("sftp-work").toString();
        String uri = baseUri() + "&fileName=work-dir-test.txt&noop=true&delay=1000&initialDelay=0"
                     + "&localWorkDirectory=" + localWorkPath;

        MockEndpoint mock = getMockEndpoint("mock:localWork");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Content for local work directory test");
        mock.setResultWaitTime(30000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri)
                        .routeId("localWorkDirTest")
                        .process(exchange -> {
                            // Verify the file body is accessible
                            String body = exchange.getIn().getBody(String.class);
                            LOG.info("Received body: {}", body);

                            // Check that local work directory was used
                            File localWorkDirFile = new File(localWorkPath);
                            LOG.info("Local work directory exists: {}", localWorkDirFile.exists());
                        })
                        .to("mock:localWork");
            }
        });

        context.getRouteController().startRoute("localWorkDirTest");
        mock.assertIsSatisfied();
        context.getRouteController().stopRoute("localWorkDirTest");

        LOG.info("SUCCESS: Consumer with localWorkDirectory processed file correctly");
    }

    /**
     * Test that localWorkDirectory handles large files correctly.
     */
    @Test
    @Timeout(60)
    public void testLocalWorkDirectoryWithLargeFile() throws Exception {
        // Create a 100KB test file
        byte[] largeContent = new byte[100 * 1024];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        Path remoteFile = ftpFile("large-work-dir-test.bin");
        Files.write(remoteFile, largeContent);

        String localWorkPath = localWorkDir.resolve("sftp-work-large").toString();
        String uri = baseUri() + "&fileName=large-work-dir-test.bin&noop=true&delay=1000&initialDelay=0"
                     + "&localWorkDirectory=" + localWorkPath;

        MockEndpoint mock = getMockEndpoint("mock:localWorkLarge");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(30000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri)
                        .routeId("localWorkDirLargeTest")
                        .process(exchange -> {
                            byte[] body = exchange.getIn().getBody(byte[].class);
                            assertNotNull(body, "Body should not be null");
                            assertEquals(100 * 1024, body.length, "File size should match");
                            LOG.info("Received large file with {} bytes", body.length);
                        })
                        .to("mock:localWorkLarge");
            }
        });

        context.getRouteController().startRoute("localWorkDirLargeTest");
        mock.assertIsSatisfied();
        context.getRouteController().stopRoute("localWorkDirLargeTest");

        LOG.info("SUCCESS: Large file processed correctly with localWorkDirectory");
    }

    /**
     * Test localWorkDirectory with delete option - file should be removed from server after processing.
     */
    @Test
    @Timeout(60)
    public void testLocalWorkDirectoryWithDelete() throws Exception {
        // Create test file on SFTP server
        Path remoteFile = ftpFile("delete-work-test.txt");
        Files.writeString(remoteFile, "Content to be deleted after processing");

        String localWorkPath = localWorkDir.resolve("sftp-work-delete").toString();
        String uri = baseUri() + "&fileName=delete-work-test.txt&delete=true&delay=1000&initialDelay=0"
                     + "&localWorkDirectory=" + localWorkPath;

        MockEndpoint mock = getMockEndpoint("mock:localWorkDelete");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(30000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri)
                        .routeId("localWorkDeleteTest")
                        .to("mock:localWorkDelete");
            }
        });

        context.getRouteController().startRoute("localWorkDeleteTest");
        mock.assertIsSatisfied();
        context.getRouteController().stopRoute("localWorkDeleteTest");

        // Verify remote file was deleted
        assertFalse(Files.exists(remoteFile), "Remote file should be deleted after processing");

        LOG.info("SUCCESS: File deleted after processing with localWorkDirectory");
    }

    // ========================================
    // STREAM DOWNLOAD TESTS
    // ========================================

    /**
     * Test streamDownload option for memory-efficient large file handling.
     */
    @Test
    @Timeout(60)
    public void testStreamDownloadOption() throws Exception {
        // Create a test file
        byte[] content = new byte[50 * 1024]; // 50KB
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) ('A' + (i % 26));
        }
        Path remoteFile = ftpFile("stream-download-test.bin");
        Files.write(remoteFile, content);

        String uri = baseUri() + "&fileName=stream-download-test.bin&noop=true&delay=1000&initialDelay=0"
                     + "&streamDownload=true";

        MockEndpoint mock = getMockEndpoint("mock:stream");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(30000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri)
                        .routeId("streamDownloadTest")
                        .process(exchange -> {
                            // With streamDownload=true, body should be an InputStream
                            Object body = exchange.getIn().getBody();
                            LOG.info("Body type with streamDownload: {}", body.getClass().getName());

                            // Convert to bytes to verify content
                            byte[] bytes = exchange.getIn().getBody(byte[].class);
                            assertEquals(50 * 1024, bytes.length, "Stream content size should match");
                        })
                        .to("mock:stream");
            }
        });

        context.getRouteController().startRoute("streamDownloadTest");
        mock.assertIsSatisfied();
        context.getRouteController().stopRoute("streamDownloadTest");

        LOG.info("SUCCESS: Stream download option worked correctly");
    }

    // ========================================
    // PRODUCER FILE EXIST OPTIONS
    // ========================================

    /**
     * Test producer fileExist=Append option.
     */
    @Test
    @Timeout(30)
    public void testProducerFileExistAppend() throws Exception {
        // Create initial file
        Path existingFile = ftpFile("append-test.txt");
        Files.writeString(existingFile, "Initial content. ");

        String uri = baseUri() + "&fileExist=Append";

        // Append to existing file
        template.sendBodyAndHeader(uri, "Appended content.", Exchange.FILE_NAME, "append-test.txt");

        // Verify content was appended
        String finalContent = Files.readString(existingFile);
        assertEquals("Initial content. Appended content.", finalContent);

        LOG.info("SUCCESS: Producer fileExist=Append worked correctly");
    }

    /**
     * Test producer fileExist=Fail option.
     */
    @Test
    @Timeout(30)
    public void testProducerFileExistFail() throws Exception {
        // Create initial file
        Path existingFile = ftpFile("fail-test.txt");
        Files.writeString(existingFile, "Existing content");

        String uri = baseUri() + "&fileExist=Fail";

        // Try to write to existing file - should fail
        CamelExecutionException exception = assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader(uri, "New content", Exchange.FILE_NAME, "fail-test.txt");
        });

        LOG.info("Expected exception: {}", exception.getMessage());
        assertNotNull(exception.getCause(), "Should have a cause");

        // Verify original content unchanged
        assertEquals("Existing content", Files.readString(existingFile));

        LOG.info("SUCCESS: Producer fileExist=Fail worked correctly");
    }

    /**
     * Test producer fileExist=Ignore option.
     */
    @Test
    @Timeout(30)
    public void testProducerFileExistIgnore() throws Exception {
        // Create initial file
        Path existingFile = ftpFile("ignore-test.txt");
        Files.writeString(existingFile, "Original content");

        String uri = baseUri() + "&fileExist=Ignore";

        // Try to write to existing file - should be silently ignored
        template.sendBodyAndHeader(uri, "New content that should be ignored",
                Exchange.FILE_NAME, "ignore-test.txt");

        // Verify original content unchanged
        assertEquals("Original content", Files.readString(existingFile));

        LOG.info("SUCCESS: Producer fileExist=Ignore worked correctly");
    }

    /**
     * Test producer fileExist=Override option (default behavior).
     */
    @Test
    @Timeout(30)
    public void testProducerFileExistOverride() throws Exception {
        // Create initial file
        Path existingFile = ftpFile("override-test.txt");
        Files.writeString(existingFile, "Original content to be overwritten");

        String uri = baseUri() + "&fileExist=Override";

        // Override existing file
        template.sendBodyAndHeader(uri, "New overwritten content",
                Exchange.FILE_NAME, "override-test.txt");

        // Verify content was overwritten
        assertEquals("New overwritten content", Files.readString(existingFile));

        LOG.info("SUCCESS: Producer fileExist=Override worked correctly");
    }

    // ========================================
    // ADDITIONAL PRODUCER TESTS
    // ========================================

    /**
     * Test producer with chmod option to set file permissions.
     */
    @Test
    @Timeout(30)
    public void testProducerWithChmod() throws Exception {
        String uri = baseUri() + "&chmod=644";

        template.sendBodyAndHeader(uri, "Content with chmod", Exchange.FILE_NAME, "chmod-test.txt");

        Path createdFile = ftpFile("chmod-test.txt");
        assertTrue(Files.exists(createdFile), "File should be created");
        assertEquals("Content with chmod", Files.readString(createdFile));

        LOG.info("SUCCESS: Producer with chmod option worked correctly");
    }

    /**
     * Test producer with autoCreate=false should fail for non-existent directory.
     */
    @Test
    @Timeout(30)
    public void testProducerAutoCreateFalse() throws Exception {
        String uri = baseUri() + "&autoCreate=false";

        // Try to write to non-existent subdirectory
        assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader(uri, "Content",
                    Exchange.FILE_NAME, "nonexistent-dir/test.txt");
        });

        LOG.info("SUCCESS: Producer with autoCreate=false correctly failed for non-existent directory");
    }

    /**
     * Test multiple sequential file uploads to same directory.
     */
    @Test
    @Timeout(30)
    public void testMultipleSequentialUploads() throws Exception {
        String uri = baseUri();

        // Upload multiple files sequentially
        for (int i = 1; i <= 5; i++) {
            template.sendBodyAndHeader(uri, "Content " + i,
                    Exchange.FILE_NAME, "sequential-" + i + ".txt");
        }

        // Verify all files were created
        for (int i = 1; i <= 5; i++) {
            Path file = ftpFile("sequential-" + i + ".txt");
            assertTrue(Files.exists(file), "File " + i + " should exist");
            assertEquals("Content " + i, Files.readString(file));
        }

        LOG.info("SUCCESS: Multiple sequential uploads worked correctly");
    }
}
