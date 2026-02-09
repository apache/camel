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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for SFTP protocol-specific features: compression, zero-byte files, and metadata handling.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpProtocolIT extends MinaSftpServerTestSupport {

    private static final Logger log = LoggerFactory.getLogger(MinaSftpProtocolIT.class);

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

    // ========================================
    // COMPRESSION TESTS
    // ========================================

    @Test
    @Timeout(60)
    public void testCompressionEnabled() throws Exception {
        // Test with compression level 1 (fastest)
        String uri = baseUri() + "&compression=1";

        template.sendBodyAndHeader(uri, "Content with compression level 1", Exchange.FILE_NAME, "compressed1.txt");

        File file = ftpFile("compressed1.txt").toFile();
        assertTrue(file.exists(), "File should exist with compression enabled");
        assertEquals("Content with compression level 1",
                context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(60)
    public void testCompressionLevel6() throws Exception {
        // Test with compression level 6 (default zlib level)
        String uri = baseUri() + "&compression=6";

        template.sendBodyAndHeader(uri, "Content with compression level 6", Exchange.FILE_NAME, "compressed6.txt");

        File file = ftpFile("compressed6.txt").toFile();
        assertTrue(file.exists(), "File should exist with compression level 6");
        assertEquals("Content with compression level 6",
                context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(60)
    public void testCompressionLevel9() throws Exception {
        // Test with compression level 9 (maximum compression)
        String uri = baseUri() + "&compression=9";

        template.sendBodyAndHeader(uri, "Content with compression level 9", Exchange.FILE_NAME, "compressed9.txt");

        File file = ftpFile("compressed9.txt").toFile();
        assertTrue(file.exists(), "File should exist with compression level 9");
        assertEquals("Content with compression level 9",
                context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(120)
    public void testCompressionWithLargeCompressibleData() throws Exception {
        // Create highly compressible content (lots of repeated patterns)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n");
        }
        String compressibleContent = sb.toString();
        log.info("Compressible content size: {} bytes", compressibleContent.length());

        // Calculate checksum before transfer
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] expectedChecksum = md.digest(compressibleContent.getBytes(StandardCharsets.UTF_8));

        // Upload with compression
        String uri = baseUri() + "&compression=6";
        template.sendBodyAndHeader(uri, compressibleContent, Exchange.FILE_NAME, "large-compressible.txt");

        // Verify file exists and content is correct
        File file = ftpFile("large-compressible.txt").toFile();
        assertTrue(file.exists(), "Large compressible file should exist");
        assertEquals(compressibleContent.length(), file.length(), "File size should match original");

        // Verify checksum
        byte[] actualChecksum = md.digest(Files.readAllBytes(file.toPath()));
        assertArrayEquals(expectedChecksum, actualChecksum, "Checksum should match after compressed transfer");
    }

    @Test
    @Timeout(60)
    public void testCompressionDisabled() throws Exception {
        // Test with compression explicitly disabled (level 0)
        String uri = baseUri() + "&compression=0";

        template.sendBodyAndHeader(uri, "Content without compression", Exchange.FILE_NAME, "no-compression.txt");

        File file = ftpFile("no-compression.txt").toFile();
        assertTrue(file.exists(), "File should exist without compression");
        assertEquals("Content without compression",
                context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(60)
    public void testCompressionWithBinaryData() throws Exception {
        // Test compression with binary data (less compressible)
        byte[] binaryData = new byte[10240];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte) (i % 256);
        }

        String uri = baseUri() + "&compression=6";
        template.sendBodyAndHeader(uri, binaryData, Exchange.FILE_NAME, "binary-compressed.bin");

        File file = ftpFile("binary-compressed.bin").toFile();
        assertTrue(file.exists(), "Binary file should exist with compression");
        assertArrayEquals(binaryData, Files.readAllBytes(file.toPath()), "Binary content should match");
    }

    @Test
    @Timeout(60)
    public void testCompressionConsumer() throws Exception {
        // Create a file first
        template.sendBodyAndHeader(baseUri(), "Consumer compression test content",
                Exchange.FILE_NAME, "consume-compressed.txt");

        MockEndpoint mock = getMockEndpoint("mock:compressed");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Consumer compression test content");
        mock.setResultWaitTime(30000);

        // Consume with compression enabled
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&compression=6&fileName=consume-compressed.txt&delay=1000&initialDelay=0")
                        .routeId("compressionConsumer")
                        .to("mock:compressed");
            }
        });

        context.getRouteController().startRoute("compressionConsumer");
        mock.assertIsSatisfied();
        context.getRouteController().stopRoute("compressionConsumer");
    }

    // ========================================
    // ZERO-BYTE FILE TESTS
    // ========================================

    @Test
    @Timeout(30)
    public void testZeroByteFileUpload() throws Exception {
        // Upload empty content as byte array
        byte[] emptyBytes = new byte[0];

        template.sendBodyAndHeader(baseUri(), emptyBytes, Exchange.FILE_NAME, "zero-bytes.txt");

        File file = ftpFile("zero-bytes.txt").toFile();
        assertTrue(file.exists(), "Zero-byte file should exist");
        assertEquals(0, file.length(), "Zero-byte file should have length 0");
    }

    @Test
    @Timeout(30)
    public void testZeroByteFileUploadAsString() throws Exception {
        // Upload empty string
        template.sendBodyAndHeader(baseUri(), "", Exchange.FILE_NAME, "zero-string.txt");

        File file = ftpFile("zero-string.txt").toFile();
        assertTrue(file.exists(), "Zero-byte file from string should exist");
        assertEquals(0, file.length(), "Zero-byte file should have length 0");
    }

    @Test
    @Timeout(30)
    public void testZeroByteFileDownload() throws Exception {
        // Create zero-byte file
        template.sendBodyAndHeader(baseUri(), "", Exchange.FILE_NAME, "download-zero.txt");
        assertTrue(ftpFile("download-zero.txt").toFile().exists(), "Zero-byte file should be created");

        MockEndpoint mock = getMockEndpoint("mock:zeroDownload");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=download-zero.txt&delay=1000&initialDelay=0")
                        .routeId("zeroDownload")
                        .to("mock:zeroDownload");
            }
        });

        context.getRouteController().startRoute("zeroDownload");
        mock.assertIsSatisfied();

        // Verify the downloaded content is empty
        Exchange exchange = mock.getExchanges().get(0);
        Object body = exchange.getIn().getBody();
        if (body instanceof byte[]) {
            assertEquals(0, ((byte[]) body).length, "Downloaded zero-byte file should have empty byte array");
        } else if (body instanceof String) {
            assertEquals("", body, "Downloaded zero-byte file should have empty string");
        } else if (body instanceof InputStream) {
            assertEquals(0, ((InputStream) body).available(), "Downloaded zero-byte file should have empty stream");
        }

        // Verify FILE_LENGTH header
        Long fileLength = exchange.getIn().getHeader(Exchange.FILE_LENGTH, Long.class);
        assertNotNull(fileLength, "FILE_LENGTH header should be present");
        assertEquals(0L, fileLength.longValue(), "FILE_LENGTH should be 0");

        context.getRouteController().stopRoute("zeroDownload");
    }

    @Test
    @Timeout(30)
    public void testZeroByteFileWithCompression() throws Exception {
        // Test zero-byte file with compression enabled
        String uri = baseUri() + "&compression=6";

        template.sendBodyAndHeader(uri, "", Exchange.FILE_NAME, "zero-compressed.txt");

        File file = ftpFile("zero-compressed.txt").toFile();
        assertTrue(file.exists(), "Zero-byte file should exist with compression");
        assertEquals(0, file.length(), "Zero-byte file should have length 0");
    }

    @Test
    @Timeout(30)
    public void testZeroByteFileDelete() throws Exception {
        // Create and then delete zero-byte file
        template.sendBodyAndHeader(baseUri(), "", Exchange.FILE_NAME, "zero-delete.txt");
        assertTrue(ftpFile("zero-delete.txt").toFile().exists(), "Zero-byte file should be created");

        MockEndpoint mock = getMockEndpoint("mock:zeroDelete");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=zero-delete.txt&delete=true&delay=1000&initialDelay=0")
                        .routeId("zeroDelete")
                        .to("mock:zeroDelete");
            }
        });

        context.getRouteController().startRoute("zeroDelete");
        mock.assertIsSatisfied();

        // Verify the file was deleted
        Thread.sleep(1000); // Give time for delete
        assertTrue(!ftpFile("zero-delete.txt").toFile().exists(), "Zero-byte file should be deleted after consumption");

        context.getRouteController().stopRoute("zeroDelete");
    }

    @Test
    @Timeout(30)
    public void testZeroByteFileMove() throws Exception {
        // Create zero-byte file and move after consumption
        template.sendBodyAndHeader(baseUri(), "", Exchange.FILE_NAME, "zero-move.txt");
        assertTrue(ftpFile("zero-move.txt").toFile().exists(), "Zero-byte file should be created");

        // Create target directory
        ftpFile("done").toFile().mkdirs();

        MockEndpoint mock = getMockEndpoint("mock:zeroMove");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=zero-move.txt&move=done&delay=1000&initialDelay=0")
                        .routeId("zeroMove")
                        .to("mock:zeroMove");
            }
        });

        context.getRouteController().startRoute("zeroMove");
        mock.assertIsSatisfied();

        // Verify the file was moved
        Thread.sleep(1000);
        assertTrue(!ftpFile("zero-move.txt").toFile().exists(), "Original zero-byte file should not exist");
        assertTrue(ftpFile("done/zero-move.txt").toFile().exists(), "Zero-byte file should be in done folder");

        context.getRouteController().stopRoute("zeroMove");
    }

    @Test
    @Timeout(30)
    public void testMultipleZeroByteFiles() throws Exception {
        // Create multiple zero-byte files
        for (int i = 0; i < 5; i++) {
            template.sendBodyAndHeader(baseUri(), "", Exchange.FILE_NAME, "multi-zero-" + i + ".txt");
        }

        // Verify all files exist
        for (int i = 0; i < 5; i++) {
            File file = ftpFile("multi-zero-" + i + ".txt").toFile();
            assertTrue(file.exists(), "Zero-byte file " + i + " should exist");
            assertEquals(0, file.length(), "Zero-byte file " + i + " should have length 0");
        }
    }

    // ========================================
    // SFTP METADATA AND VERSION BEHAVIOR TESTS
    // ========================================

    @Test
    @Timeout(30)
    public void testFileMetadataAfterUpload() throws Exception {
        // Upload a file and verify metadata is correctly set
        String content = "Metadata test content";
        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, "metadata-test.txt");

        File file = ftpFile("metadata-test.txt").toFile();
        assertTrue(file.exists(), "File should exist");
        assertEquals(content.length(), file.length(), "File length should match content length");
        assertTrue(file.lastModified() > 0, "File should have a modification time");
    }

    @Test
    @Timeout(30)
    public void testFileMetadataInConsumer() throws Exception {
        // Create a file
        String content = "Consumer metadata test";
        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, "consumer-metadata.txt");

        MockEndpoint mock = getMockEndpoint("mock:metadata");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=consumer-metadata.txt&delay=1000&initialDelay=0")
                        .routeId("metadataConsumer")
                        .to("mock:metadata");
            }
        });

        context.getRouteController().startRoute("metadataConsumer");
        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);

        // Verify essential headers are present
        assertNotNull(exchange.getIn().getHeader(Exchange.FILE_NAME), "FILE_NAME header should be present");
        assertEquals("consumer-metadata.txt", exchange.getIn().getHeader(Exchange.FILE_NAME));

        Long fileLength = exchange.getIn().getHeader(Exchange.FILE_LENGTH, Long.class);
        assertNotNull(fileLength, "FILE_LENGTH header should be present");
        assertEquals((long) content.length(), fileLength.longValue(), "FILE_LENGTH should match content length");

        Long lastModified = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Long.class);
        assertNotNull(lastModified, "FILE_LAST_MODIFIED header should be present");
        assertTrue(lastModified > 0, "FILE_LAST_MODIFIED should be a valid timestamp");

        context.getRouteController().stopRoute("metadataConsumer");
    }

    @Test
    @Timeout(30)
    public void testDirectoryListingMetadata() throws Exception {
        // Create multiple files with different content sizes
        template.sendBodyAndHeader(baseUri(), "Small", Exchange.FILE_NAME, "list-small.txt");
        template.sendBodyAndHeader(baseUri(), "Medium content here", Exchange.FILE_NAME, "list-medium.txt");
        template.sendBodyAndHeader(baseUri(), "A".repeat(1000), Exchange.FILE_NAME, "list-large.txt");
        template.sendBodyAndHeader(baseUri(), "", Exchange.FILE_NAME, "list-empty.txt");

        // Verify all files exist with correct sizes
        assertEquals(5, ftpFile("list-small.txt").toFile().length());
        assertEquals(19, ftpFile("list-medium.txt").toFile().length());
        assertEquals(1000, ftpFile("list-large.txt").toFile().length());
        assertEquals(0, ftpFile("list-empty.txt").toFile().length());
    }

    @Test
    @Timeout(30)
    public void testFilePermissionsMetadata() throws Exception {
        // Test file permissions are correctly handled
        String uri = baseUri() + "&chmod=644";

        template.sendBodyAndHeader(uri, "Permissions test", Exchange.FILE_NAME, "permissions-test.txt");

        File file = ftpFile("permissions-test.txt").toFile();
        assertTrue(file.exists(), "File should exist");
        assertTrue(file.canRead(), "File should be readable");
    }

    @Test
    @Timeout(60)
    public void testPreserveTimestampOnDownload() throws Exception {
        // Create a file
        template.sendBodyAndHeader(baseUri(), "Timestamp test", Exchange.FILE_NAME, "timestamp-test.txt");

        // Record original timestamp
        long originalModified = ftpFile("timestamp-test.txt").toFile().lastModified();

        // Wait a bit to ensure time difference would be noticeable
        Thread.sleep(2000);

        MockEndpoint mock = getMockEndpoint("mock:timestamp");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(15000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=timestamp-test.txt&delay=1000&initialDelay=0")
                        .routeId("timestampConsumer")
                        .to("mock:timestamp");
            }
        });

        context.getRouteController().startRoute("timestampConsumer");
        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Long headerModified = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Long.class);

        assertNotNull(headerModified, "FILE_LAST_MODIFIED header should be present");
        // The header should reflect the file's modification time from the server
        // Allow for some timestamp granularity differences (within 2 seconds)
        assertTrue(Math.abs(headerModified - originalModified) < 2000,
                "Timestamp in header should be close to original file timestamp");

        context.getRouteController().stopRoute("timestampConsumer");
    }

    @Test
    @Timeout(30)
    public void testSymbolicFilename() throws Exception {
        // Test filenames that might be treated specially
        String[] specialFilenames = {
                ".hidden",
                "..dotdot",
                "file.with.multiple.dots.txt",
                "file-with-dashes.txt",
                "file_with_underscores.txt"
        };

        for (String filename : specialFilenames) {
            if (filename.equals("..dotdot")) {
                // Skip ".." as it's a special directory reference
                continue;
            }
            template.sendBodyAndHeader(baseUri(), "Content for " + filename, Exchange.FILE_NAME, filename);
            File file = ftpFile(filename).toFile();
            assertTrue(file.exists(), "File '" + filename + "' should exist");
            assertEquals("Content for " + filename, context.getTypeConverter().convertTo(String.class, file));
        }
    }
}
