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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recursive stepwise poll changes into a listed directory, and back to its parent afterwards. Verifies that a listed
 * directory name that is not the name of one directory in the listed directory, such as sub/.. or a blank name, is
 * skipped rather than leaving the poll in another directory.
 */
class FtpConsumerListedDirectoryNameTest extends CamelTestSupport {

    private FakeFtpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeFtpServer();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    @Test
    void skipsListedDirectoryThatIsNotADirectoryInTheListedDirectory() throws Exception {
        server.addFile("/inbox/hello.txt", "hello".getBytes(StandardCharsets.UTF_8));
        server.addFile("/inbox/real/deep.txt", "deep".getBytes(StandardCharsets.UTF_8));
        server.addFile("/outside.txt", "outside".getBytes(StandardCharsets.UTF_8));
        server.addListedDirectory("/inbox", "sub/..");
        server.addListedDirectory("/inbox", "sub\\..");
        server.addListedDirectory("/inbox", " ");
        server.addListedDirectory("/inbox", "real");
        List<String> received = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://camel@127.0.0.1:" + server.port() + "/inbox?password=secret&passiveMode=true"
                     + "&recursive=true&maxDepth=3&noop=true&initialDelay=0&delay=600000")
                        .process(e -> received.add(e.getMessage().getHeader(Exchange.FILE_NAME, String.class)));
            }
        });

        await().atMost(10, TimeUnit.SECONDS).until(() -> received.size() >= 2);
        // nothing more arrives: the poll does not list the inbox again through sub/..
        TimeUnit.MILLISECONDS.sleep(500);

        assertEquals(2, received.size(), received.toString());
        assertTrue(received.contains("hello.txt"), received.toString());
        assertTrue(received.contains("real/deep.txt"), received.toString());
        // the poll never changes into the listed names
        assertFalse(server.commands().stream().anyMatch(c -> c.startsWith("CWD sub")), server.commands().toString());
    }

    @Test
    void pollsListedDirectoryWithAbsoluteNameOfServerListingAbsolutePaths() throws Exception {
        // a server that lists the absolute path of each entry
        server.addFile("/inbox/real/deep.txt", "deep".getBytes(StandardCharsets.UTF_8));
        server.addListedDirectory("/inbox", "/inbox/real");
        List<String> listed = new CopyOnWriteArrayList<>();
        context.getRegistry().bind("listed", (GenericFileFilter<Object>) file -> {
            listed.add(file.getFileNameOnly());
            return true;
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://camel@127.0.0.1:" + server.port() + "/inbox?password=secret&passiveMode=true"
                     + "&recursive=true&handleDirectoryParserAbsoluteResult=true&filter=#listed&noop=true"
                     + "&initialDelay=0&delay=600000")
                        .to("mock:result");
            }
        });

        // the poll changes into the listed directory and lists its file
        await().atMost(10, TimeUnit.SECONDS).until(() -> listed.contains("deep.txt"));
    }

    @Test
    void subDirectoryNames() {
        assertTrue(FtpUtils.isSubDirectoryName("sub"));
        assertTrue(FtpUtils.isSubDirectoryName("a..b"));
        assertTrue(FtpUtils.isSubDirectoryName("...dir"));
        assertTrue(FtpUtils.isSubDirectoryName("http:"));
        assertTrue(FtpUtils.isSubDirectoryName(" a "));
        assertFalse(FtpUtils.isSubDirectoryName(null));
        assertFalse(FtpUtils.isSubDirectoryName(""));
        assertFalse(FtpUtils.isSubDirectoryName("."));
        assertFalse(FtpUtils.isSubDirectoryName(".."));
        assertFalse(FtpUtils.isSubDirectoryName("sub/.."));
        assertFalse(FtpUtils.isSubDirectoryName("sub/dir"));
        assertFalse(FtpUtils.isSubDirectoryName("sub\\.."));
        assertFalse(FtpUtils.isSubDirectoryName("\\sub"));
        assertFalse(FtpUtils.isSubDirectoryName("a\\b"));
        assertFalse(FtpUtils.isSubDirectoryName(" "));
    }
}
