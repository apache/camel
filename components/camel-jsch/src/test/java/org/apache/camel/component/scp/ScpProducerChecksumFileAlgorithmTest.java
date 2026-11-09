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
package org.apache.camel.component.scp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConstants;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that the SCP producer correctly writes the checksum file when {@code checksumFileAlgorithm} is set
 * (CAMEL-26672). {@link ScpOperations#storeFileDirectly} was opening the channel but never setting the scp command or
 * connecting it, so every exchange with the option set failed.
 *
 * <p>
 * Note: on this branch (camel-4.18.x) CAMEL-25906 has not been backported, so {@code GenericFileProducer} does not
 * compute the upload digest automatically. The {@code CamelFileChecksum} header is set by the caller before the
 * exchange is sent; {@code GenericFileProducer.writeChecksumFile()} then picks it up and calls
 * {@code storeFileDirectly} — which is the path this test exercises.
 * </p>
 */
class ScpProducerChecksumFileAlgorithmTest extends ScpServerTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no consumer route needed: we verify the files on the server filesystem directly
            }
        };
    }

    private String getScpUriWithChecksum() {
        return getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile()
               + "&checksumFileAlgorithm=md5";
    }

    private static String md5Hex(byte[] data) throws Exception {
        return StringHelper.bytesToHex(MessageDigest.getInstance("MD5").digest(data));
    }

    /**
     * Sending a file with {@code checksumFileAlgorithm=md5} must succeed: the main file is written, the {@code .md5}
     * checksum file is created with the hex MD5 of the uploaded bytes, and the {@code CamelFileChecksum} header is
     * carried through the exchange.
     *
     * <p>
     * The {@code CamelFileChecksum} header is set by the caller before sending (since CAMEL-25906 is not on this
     * branch). {@code GenericFileProducer.writeChecksumFile()} reads that header and delegates to
     * {@code storeFileDirectly} — the method fixed by CAMEL-26672.
     * </p>
     */
    @Test
    void testChecksumFileIsWritten() throws Exception {
        assumeTrue(isSetupComplete());

        String body = "Hello World";
        String expectedMd5 = md5Hex(body.getBytes(StandardCharsets.UTF_8));
        Exchange out = template.request(getScpUriWithChecksum(), exchange -> {
            exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
            exchange.getIn().setHeader(FileConstants.FILE_CHECKSUM, expectedMd5);
            exchange.getIn().setBody(body);
        });

        assertNull(out.getException(), "Exchange must not fail: " + out.getException());

        // the main file must exist and contain the uploaded body
        Path mainFile = Path.of(getScpPath(), "hello.txt");
        assertTrue(mainFile.toFile().exists(), "hello.txt must exist on the server");
        assertEquals(body, Files.readString(mainFile));

        // the checksum file must exist and contain the hex MD5 of the uploaded bytes
        Path checksumFile = Path.of(getScpPath(), "hello.txt.md5");
        assertTrue(checksumFile.toFile().exists(), "hello.txt.md5 must exist on the server");
        assertEquals(expectedMd5, Files.readString(checksumFile));

        // the CamelFileChecksum header must carry the same value
        String checksumHeader = out.getMessage().getHeader(FileConstants.FILE_CHECKSUM, String.class);
        assertNotNull(checksumHeader, "CamelFileChecksum header must be set");
        assertEquals(expectedMd5, checksumHeader);
    }

    /**
     * Same scenario with the file name containing a sub-directory path, verifying that {@code storeFileDirectly}
     * correctly resolves the remote name when the name includes the base directory prefix.
     */
    @Test
    void testChecksumFileIsWrittenInSubDirectory() throws Exception {
        assumeTrue(isSetupComplete());

        String body = "Bye World";
        String expectedMd5 = md5Hex(body.getBytes(StandardCharsets.UTF_8));
        Exchange out = template.request(getScpUriWithChecksum(), exchange -> {
            exchange.getIn().setHeader(Exchange.FILE_NAME, "subdir/bye.txt");
            exchange.getIn().setHeader(FileConstants.FILE_CHECKSUM, expectedMd5);
            exchange.getIn().setBody(body);
        });

        assertNull(out.getException(), "Exchange must not fail: " + out.getException());

        Path mainFile = Path.of(getScpPath(), "subdir", "bye.txt");
        assertTrue(mainFile.toFile().exists(), "subdir/bye.txt must exist on the server");
        assertEquals(body, Files.readString(mainFile));

        Path checksumFile = Path.of(getScpPath(), "subdir", "bye.txt.md5");
        assertTrue(checksumFile.toFile().exists(), "subdir/bye.txt.md5 must exist on the server");
        assertEquals(expectedMd5, Files.readString(checksumFile));
    }

    /**
     * Without {@code checksumFileAlgorithm}, no {@code .md5} file is written and no checksum header is set (control).
     */
    @Test
    void testNoChecksumFileWithoutOption() throws Exception {
        assumeTrue(isSetupComplete());

        String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        Exchange out = template.request(uri, exchange -> {
            exchange.getIn().setHeader(Exchange.FILE_NAME, "control.txt");
            exchange.getIn().setBody("Control");
        });

        assertNull(out.getException(), "Exchange must not fail");
        assertTrue(Path.of(getScpPath(), "control.txt").toFile().exists(), "control.txt must exist");
        assertFalse(Path.of(getScpPath(), "control.txt.md5").toFile().exists(), "control.txt.md5 must not exist");
        assertNull(out.getMessage().getHeader(FileConstants.FILE_CHECKSUM));
    }
}
