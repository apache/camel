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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP large file handling, binary content, and special filenames.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpLargeFileIT extends MinaSftpServerTestSupport {

    private static final int ONE_MB = 1024 * 1024;

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
    @Timeout(120)
    public void testUploadLargeFile1MB() throws Exception {
        // Create 1MB of content
        StringBuilder sb = new StringBuilder(ONE_MB);
        String line = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\n";
        while (sb.length() < ONE_MB) {
            sb.append(line);
        }
        String content = sb.substring(0, ONE_MB);

        // Calculate checksum before upload
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] expectedChecksum = md.digest(content.getBytes(StandardCharsets.UTF_8));

        // Upload
        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, "large-1mb.txt");

        // Verify file exists and has correct size
        File file = ftpFile("large-1mb.txt").toFile();
        assertTrue(file.exists(), "Large file should exist");
        assertEquals(ONE_MB, file.length(), "File size should be 1MB");

        // Verify checksum
        byte[] fileContent = Files.readAllBytes(file.toPath());
        byte[] actualChecksum = md.digest(fileContent);
        assertArrayEquals(expectedChecksum, actualChecksum, "Checksum should match");
    }

    @Test
    @Timeout(120)
    public void testStreamingDownloadLargeFile() throws Exception {
        // Create a large file on the server
        StringBuilder sb = new StringBuilder(ONE_MB);
        String line = "STREAMING-TEST-LINE-DATA-0123456789\n";
        while (sb.length() < ONE_MB) {
            sb.append(line);
        }
        String content = sb.substring(0, ONE_MB);

        // Upload the file first
        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, "stream-download.txt");

        MockEndpoint mock = getMockEndpoint("mock:streamed");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(60000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=stream-download.txt&streamDownload=true&delay=1000&initialDelay=0")
                        .routeId("streamDownload")
                        .to("mock:streamed");
            }
        });

        context.getRouteController().startRoute("streamDownload");
        mock.assertIsSatisfied();

        // Verify the body is an InputStream or can be converted to the original content
        Exchange exchange = mock.getExchanges().get(0);
        Object body = exchange.getIn().getBody();
        assertNotNull(body, "Body should not be null");

        // Convert to string and verify
        String receivedContent;
        if (body instanceof InputStream) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ((InputStream) body).transferTo(baos);
            receivedContent = baos.toString(StandardCharsets.UTF_8);
        } else {
            receivedContent = context.getTypeConverter().convertTo(String.class, body);
        }

        assertEquals(content.length(), receivedContent.length(), "Content length should match");

        context.getRouteController().stopRoute("streamDownload");
    }

    @Test
    @Timeout(30)
    public void testZeroByteFile() throws Exception {
        // Upload empty content
        template.sendBodyAndHeader(baseUri(), "", Exchange.FILE_NAME, "empty.txt");

        // Verify file exists with size 0
        File file = ftpFile("empty.txt").toFile();
        assertTrue(file.exists(), "Empty file should exist");
        assertEquals(0, file.length(), "File should be zero bytes");

        // Consume and verify
        MockEndpoint mock = getMockEndpoint("mock:empty");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("");
        mock.setResultWaitTime(10000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=empty.txt&delay=1000&initialDelay=0")
                        .routeId("consumeEmpty")
                        .to("mock:empty");
            }
        });

        context.getRouteController().startRoute("consumeEmpty");
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("consumeEmpty");
    }

    @Test
    @Timeout(60)
    public void testBinaryFileContent() throws Exception {
        // Create random binary content
        byte[] binaryContent = new byte[10240]; // 10KB binary
        new Random(42).nextBytes(binaryContent); // Use seed for reproducibility

        // Upload binary content
        template.sendBodyAndHeader(baseUri(), binaryContent, Exchange.FILE_NAME, "binary.bin");

        // Verify file exists
        File file = ftpFile("binary.bin").toFile();
        assertTrue(file.exists(), "Binary file should exist");
        assertEquals(binaryContent.length, file.length(), "Binary file size should match");

        // Verify byte-for-byte match
        byte[] readContent = Files.readAllBytes(file.toPath());
        assertArrayEquals(binaryContent, readContent, "Binary content should match byte-for-byte");
    }

    @Test
    @Timeout(30)
    public void testFilenameWithSpaces() throws Exception {
        String fileName = "hello world file.txt";
        String content = "Content with spaces in filename";

        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "File with spaces should exist: " + fileName);
        assertEquals(content, context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFilenameWithSpecialCharactersAt() throws Exception {
        String fileName = "file@test.txt";
        String content = "Content with @ in filename";

        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "File with @ should exist: " + fileName);
        assertEquals(content, context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFilenameWithSpecialCharactersHash() throws Exception {
        String fileName = "file#test.txt";
        String content = "Content with # in filename";

        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "File with # should exist: " + fileName);
        assertEquals(content, context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFilenameWithSpecialCharactersPlus() throws Exception {
        String fileName = "file+test.txt";
        String content = "Content with + in filename";

        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "File with + should exist: " + fileName);
        assertEquals(content, context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFilenameWithUnicode() throws Exception {
        // Test with Japanese characters
        String fileName = "test.txt";
        String content = "Content for unicode filename";

        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "File with unicode name should exist");
        assertEquals(content, context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFilenameWithUnderscoresAndDashes() throws Exception {
        String fileName = "test_file-with-mixed_separators.txt";
        String content = "Content with underscores and dashes";

        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "File should exist: " + fileName);
        assertEquals(content, context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(30)
    public void testFilenameWithNumbers() throws Exception {
        String fileName = "file123456789.txt";
        String content = "Content with numbers in filename";

        template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, fileName);

        File file = ftpFile(fileName).toFile();
        assertTrue(file.exists(), "File with numbers should exist: " + fileName);
        assertEquals(content, context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Timeout(60)
    public void testMultipleLargeFilesSequential() throws Exception {
        int fileCount = 5;
        int fileSize = 100 * 1024; // 100KB each

        for (int i = 0; i < fileCount; i++) {
            StringBuilder sb = new StringBuilder(fileSize);
            String line = "FILE-" + i + "-LINE-DATA-0123456789\n";
            while (sb.length() < fileSize) {
                sb.append(line);
            }
            String content = sb.substring(0, fileSize);

            template.sendBodyAndHeader(baseUri(), content, Exchange.FILE_NAME, "multi-large-" + i + ".txt");
        }

        // Verify all files exist with correct size
        for (int i = 0; i < fileCount; i++) {
            File file = ftpFile("multi-large-" + i + ".txt").toFile();
            assertTrue(file.exists(), "File " + i + " should exist");
            assertEquals(fileSize, file.length(), "File " + i + " should be 100KB");
        }
    }
}
