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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name of the file to upload is resolved by the server, which gives no special meaning to a scheme or a drive at
 * the start of it, and may take a backslash in it as part of a name unless the separator option replaces it by /.
 * Verifies that a name that the server resolves outside the starting directory is refused with jailStartingDirectory
 * (the default), whether a directory is configured or not.
 */
class RemoteFileProducerStartingDirectoryJailTest extends CamelTestSupport {

    private String createFileName(String uri, String name) throws Exception {
        RemoteFileEndpoint<?> endpoint = context.getEndpoint(uri, RemoteFileEndpoint.class);
        GenericFileProducer<?> producer = (GenericFileProducer<?>) endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.FILE_NAME, name);
        return producer.createFileName(exchange);
    }

    private void assertRefused(String uri, String name) {
        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> createFileName(uri, name), uri + " " + name);
        assertTrue(e.getMessage().contains("as the filename is jailed to the starting directory"), e.getMessage());
    }

    @Test
    void shouldRefuseNamesEscapingTheSessionRoot() {
        for (String uri : new String[] { "ftp://hostname", "ftps://hostname", "sftp://hostname" }) {
            assertRefused(uri, "../planted.txt");
            assertRefused(uri, "sub/../../planted.txt");
            assertRefused(uri, "http:/../../other/planted.txt");
            assertRefused(uri, "https:/../../other/planted.txt");
            assertRefused(uri, "file:/../../other/planted.txt");
            assertRefused(uri, "classpath:/../../other/planted.txt");
            assertRefused(uri, "a:/../../other/planted.txt");
            assertRefused(uri, "http:\\..\\..\\other\\planted.txt");
        }
    }

    @Test
    void shouldRefuseNamesEscapingTheStartingDirectory() {
        assertRefused("ftp://hostname/upload", "http:/../../planted.txt");
        assertRefused("ftp://hostname/upload", "a:/../../planted.txt");
    }

    @Test
    void shouldRefuseNamesWithBackslashAndParentSegmentUnlessSeparatorIsUnix() throws Exception {
        // the separator option UNIX (the default) replaces a backslash by / before the name is used
        assertEquals("upload/sub\\..\\x.txt", createFileName("ftp://hostname/upload", "sub\\..\\x.txt"));
        assertEquals("upload/sub\\..\\x.txt", createFileName("sftp://hostname/upload?separator=UNIX", "sub\\..\\x.txt"));
        assertEquals("upload/a\\b/../c.txt", createFileName("ftp://hostname/upload", "a\\b/../c.txt"));
        // otherwise the FTP client compacts a\b as one segment on Linux, and a/b as one segment on Windows
        for (String uri : new String[] {
                "ftp://hostname/upload?separator=Auto", "ftp://hostname/upload?separator=Windows",
                "sftp://hostname/upload?separator=Auto" }) {
            assertRefused(uri, "sub\\..\\x.txt");
            assertRefused(uri, "a\\b/../../planted.txt");
            assertRefused(uri, "a/b\\..\\..\\planted.txt");
            // a backslash without a .. segment
            assertEquals("upload/a\\b.txt", createFileName(uri, "a\\b.txt"));
        }
        // with the separator option UNIX, but a temp file, a done file or a checksum file is checked, deleted, renamed
        // or written with the name as given
        for (String uri : new String[] {
                "ftp://hostname/upload?tempPrefix=.uploading", "ftp://hostname/upload?doneFileName=${file:name}.done",
                "ftp://hostname/upload?checksumFileAlgorithm=md5", "sftp://hostname/upload?tempFileName=${file:name}.tmp" }) {
            assertRefused(uri, "sub\\..\\x.txt");
            assertEquals("upload/a\\b.txt", createFileName(uri, "a\\b.txt"));
        }
    }

    @Test
    void shouldAcceptNamesStayingInside() throws Exception {
        assertEquals("hello.txt", createFileName("ftp://hostname", "hello.txt"));
        assertEquals("http:/hello.txt", createFileName("ftp://hostname", "http:/hello.txt"));
        assertEquals("report 10:30.txt", createFileName("ftp://hostname", "report 10:30.txt"));
        assertEquals("sub/../hello.txt", createFileName("sftp://hostname", "sub/../hello.txt"));
        assertEquals("upload/sub/hello.txt", createFileName("ftp://hostname/upload", "sub/hello.txt"));
        assertEquals("upload/a\\b.txt", createFileName("ftp://hostname/upload", "a\\b.txt"));
    }

    @Test
    void shouldAcceptAnyNameWithoutJail() throws Exception {
        assertEquals("http:/../../other/planted.txt",
                createFileName("ftp://hostname?jailStartingDirectory=false", "http:/../../other/planted.txt"));
    }
}
