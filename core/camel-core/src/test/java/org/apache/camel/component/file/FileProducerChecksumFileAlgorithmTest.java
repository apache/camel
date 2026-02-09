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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.FileInputStreamCache;
import org.apache.camel.converter.stream.InputStreamCache;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FileProducerChecksumFileAlgorithmTest extends ContextTestSupport {

    @Test
    public void testProducerChecksumFileMd5() throws Exception {
        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=md5"), "Hello World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.md5"), "b10a8db164e0754105b7a99be72e3fe5");
    }

    @Test
    public void testProducerChecksumFileSha256() throws Exception {
        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=sha256"), "Hello World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.sha256"), "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
    }

    @Test
    public void testProducerChecksumFileSha256WithNonRepeatableInputStream() throws Exception {
        // we can only read from this once, but we are able to write this as a file
        // and also calculate the checksum from the same stream
        InputStream is = new ByteArrayInputStream("Hello World".getBytes()) {
            @Override
            public boolean markSupported() {
                return false;
            }

            @Override
            public synchronized void reset() {
                // noop
            }
        };
        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=sha256"), is, Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.sha256"), "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");

        // cannot read more from the is
        Assertions.assertEquals(-1, is.read());
        is.reset();
        Assertions.assertEquals(-1, is.read());
    }

    @Test
    public void testProducerChecksumFileSha256WithStreamCaching() throws Exception {
        InputStreamCache cache = new InputStreamCache("Hello World".getBytes());
        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=sha256"), cache, Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.sha256"), "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
    }

    @Test
    public void testProducerChecksumFileSha256WithFileStreamCaching() throws Exception {
        Path p = testDirectory("mydata.txt");
        Files.write(p, "Hello World".getBytes());
        File f = p.toFile();
        FileInputStreamCache cache = new FileInputStreamCache(f);
        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=sha256"), cache, Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.sha256"), "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
    }

    @Test
    public void testProducerChecksumFileSha256WithFile() throws Exception {
        Path p = testDirectory("mydata.txt");
        Files.write(p, "Hello World".getBytes());
        File f = p.toFile();

        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=sha256"), f, Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.sha256"), "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
    }

    @Test
    public void testProducerChecksumFileSha256WithFileInputStream() throws Exception {
        Path p = testDirectory("mydata.txt");
        Files.write(p, "Hello World".getBytes());
        FileInputStream fis = new FileInputStream(p.toFile());

        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=sha256"), fis, Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.sha256"), "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
    }

    @Test
    public void testProducerChecksumFileSha256WithPath() throws Exception {
        Path p = testDirectory("mydata.txt");
        Files.write(p, "Hello World".getBytes());

        template.sendBodyAndHeader(fileUri("?checksumFileAlgorithm=sha256"), p, Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.sha256"), "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
    }

    @Test
    public void testProducerChecksumFileNoWriteFile() throws Exception {
        Exchange out = template.request(fileUri("?checksumFileAlgorithm=sha256&checksumWriteFile=false"), in -> {
            in.getMessage().setHeader(Exchange.FILE_NAME, "hello.txt");
            in.getMessage().setBody("Hello World");
        });
        // the checksum header should be there
        Assertions.assertFalse(out.isFailed());
        Assertions.assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
                out.getMessage().getHeader(FileConstants.FILE_CHECKSUM));

        assertFileExists(testFile("hello.txt"));
        // no checksum file
        assertFileNotExists(testFile("hello.txt.sha256"));
    }

}
