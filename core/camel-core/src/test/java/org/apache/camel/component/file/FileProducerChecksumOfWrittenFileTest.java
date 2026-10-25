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
package org.apache.camel.component.file;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The checksum of checksumFileAlgorithm is the checksum of the file that has been written, also when the producer
 * encodes the body with its charset or adds the appendChars, and adding a checksum does not change how the body is
 * decoded.
 */
class FileProducerChecksumOfWrittenFileTest extends ContextTestSupport {

    private static final String TEXT = "Grüße aus Köln";

    private String sha256OfFile(String name) throws Exception {
        byte[] data = Files.readAllBytes(testFile(name));
        return StringHelper.bytesToHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private String checksumFile(String name) throws Exception {
        return Files.readString(testFile(name + ".sha256"));
    }

    @Test
    void transcodedFileHasItsOwnChecksumAndContent() throws Exception {
        // the bytes of an ISO-8859-1 file, as a file consumer with charset=ISO-8859-1 hands them over
        Exchange out = template.request(fileUri("?charset=UTF-8&checksumFileAlgorithm=sha256"), exchange -> {
            exchange.setProperty(Exchange.CHARSET_NAME, "ISO-8859-1");
            exchange.getIn().setHeader(Exchange.FILE_NAME, "transcoded.txt");
            exchange.getIn().setBody(TEXT.getBytes(StandardCharsets.ISO_8859_1));
        });

        assertArrayEquals(TEXT.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(testFile("transcoded.txt")));
        assertEquals(sha256OfFile("transcoded.txt"), checksumFile("transcoded.txt"));
        assertEquals(sha256OfFile("transcoded.txt"), out.getMessage().getHeader(FileConstants.FILE_CHECKSUM));
    }

    @Test
    void stringWrittenWithCharsetHasTheChecksumOfTheFile() throws Exception {
        template.sendBodyAndHeader(fileUri("?charset=ISO-8859-1&checksumFileAlgorithm=sha256"), TEXT,
                Exchange.FILE_NAME, "latin1.txt");

        assertArrayEquals(TEXT.getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(testFile("latin1.txt")));
        assertEquals(sha256OfFile("latin1.txt"), checksumFile("latin1.txt"));
    }

    @Test
    void appendCharsArePartOfTheChecksum() throws Exception {
        template.sendBodyAndHeader(fileUri("?fileExist=Append&appendChars=\\n&checksumFileAlgorithm=sha256"),
                "Hello World", Exchange.FILE_NAME, "appended.txt");

        assertEquals("Hello World\n", Files.readString(testFile("appended.txt")));
        assertEquals(sha256OfFile("appended.txt"), checksumFile("appended.txt"));
    }

    @Test
    void bodyWithoutCharsetKeepsItsChecksum() throws Exception {
        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=sha256"), TEXT, Exchange.FILE_NAME, "plain.txt");

        assertArrayEquals(TEXT.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(testFile("plain.txt")));
        assertEquals(sha256OfFile("plain.txt"), checksumFile("plain.txt"));
    }
}
