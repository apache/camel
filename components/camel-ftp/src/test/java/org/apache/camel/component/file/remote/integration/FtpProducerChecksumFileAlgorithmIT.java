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
package org.apache.camel.component.file.remote.integration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConstants;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FTP producer writes the checksum file of checksumFileAlgorithm and sets the CamelFileChecksum header, with the
 * checksum of the file it uploaded, also when an earlier file producer of the route left its own checksum header.
 */
class FtpProducerChecksumFileAlgorithmIT extends FtpServerTestSupport {

    @TempDir
    Path archive;

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/checksum?password=admin&checksumFileAlgorithm=sha256";
    }

    private static String sha256(byte[] data) throws Exception {
        return StringHelper.bytesToHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private void assertChecksumOfUploadedFile(String name) throws Exception {
        File file = service.ftpFile("checksum/" + name).toFile();
        File checksum = service.ftpFile("checksum/" + name + ".sha256").toFile();
        assertTrue(file.exists(), "File should exist");
        assertTrue(checksum.exists(), "Checksum file should exist");
        assertEquals(sha256(Files.readAllBytes(file.toPath())), Files.readString(checksum.toPath()));
    }

    @Test
    void testChecksumFileIsWritten() throws Exception {
        Exchange out = template.request(getFtpUrl(), exchange -> {
            exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
            exchange.getIn().setBody("Hello World");
        });

        assertChecksumOfUploadedFile("hello.txt");
        assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
                out.getMessage().getHeader(FileConstants.FILE_CHECKSUM));
    }

    @Test
    void testChecksumOfStreamBody() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(),
                new ByteArrayInputStream("Hello Stream".getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public boolean markSupported() {
                        return false;
                    }
                }, Exchange.FILE_NAME, "stream.txt");

        assertEquals("Hello Stream", Files.readString(service.ftpFile("checksum/stream.txt")));
        assertChecksumOfUploadedFile("stream.txt");
    }

    @Test
    void testChecksumOfEarlierFileProducerIsNotUsed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("file:%s?checksumFileAlgorithm=sha256", archive)
                        .transform(constant("Bye World"))
                        .to(getFtpUrl());
            }
        });

        template.sendBodyAndHeader("direct:start", "Hello World", Exchange.FILE_NAME, "bye.txt");

        assertEquals("Bye World", Files.readString(service.ftpFile("checksum/bye.txt")));
        assertChecksumOfUploadedFile("bye.txt");
    }

    @Test
    void testChecksumOfFileEncodedWithCharset() throws Exception {
        String text = "Gr\u00fc\u00dfe";
        template.sendBodyAndHeader(getFtpUrl() + "&charset=ISO-8859-1", text, Exchange.FILE_NAME, "latin1.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&charset=ISO-8859-1",
                new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)) {
                    @Override
                    public boolean markSupported() {
                        return false;
                    }
                }, Exchange.FILE_NAME, "latin1-stream.txt");

        assertArrayEquals(text.getBytes(StandardCharsets.ISO_8859_1),
                Files.readAllBytes(service.ftpFile("checksum/latin1.txt")));
        assertChecksumOfUploadedFile("latin1.txt");
        assertArrayEquals(text.getBytes(StandardCharsets.ISO_8859_1),
                Files.readAllBytes(service.ftpFile("checksum/latin1-stream.txt")));
        assertChecksumOfUploadedFile("latin1-stream.txt");
    }

    @Test
    void testChecksumHeaderWithoutChecksumFile() throws Exception {
        Exchange out = template.request(getFtpUrl() + "&checksumWriteFile=false", exchange -> {
            exchange.getIn().setHeader(Exchange.FILE_NAME, "header.txt");
            exchange.getIn().setBody("Hello World");
        });

        assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
                out.getMessage().getHeader(FileConstants.FILE_CHECKSUM));
        assertFalse(service.ftpFile("checksum/header.txt.sha256").toFile().exists(), "No checksum file");
    }

    @Test
    void testChecksumOfReaderBody() throws Exception {
        // a body that can be read once and is not a stream: the checksum must not use it up before the upload
        template.sendBodyAndHeader(getFtpUrl(), new StringReader("Hello Reader"), Exchange.FILE_NAME, "reader.txt");
        template.sendBodyAndHeader(getFtpUrl() + "&charset=ISO-8859-1", new StringReader("Gr\u00fc\u00dfe"),
                Exchange.FILE_NAME, "reader-latin1.txt");

        assertEquals("Hello Reader", Files.readString(service.ftpFile("checksum/reader.txt")));
        assertChecksumOfUploadedFile("reader.txt");
        assertArrayEquals("Gr\u00fc\u00dfe".getBytes(StandardCharsets.ISO_8859_1),
                Files.readAllBytes(service.ftpFile("checksum/reader-latin1.txt")));
        assertChecksumOfUploadedFile("reader-latin1.txt");
    }

    @Test
    void testNoChecksumHeaderWhenTheExistingFileIsKept() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "kept.txt");

        // written to a temporary name, and not renamed as the target exists and fileExist=Ignore
        Exchange out = template.request(
                getFtpUrl() + "&tempFileName=tmp-${file:name}&fileExist=Ignore&eagerDeleteTargetFile=false", exchange -> {
                    exchange.getIn().setHeader(Exchange.FILE_NAME, "kept.txt");
                    exchange.getIn().setBody("Bye World");
                });

        assertEquals("Hello World", Files.readString(service.ftpFile("checksum/kept.txt")));
        assertNull(out.getMessage().getHeader(FileConstants.FILE_CHECKSUM),
                "no checksum of a file that was not written");
    }
}
