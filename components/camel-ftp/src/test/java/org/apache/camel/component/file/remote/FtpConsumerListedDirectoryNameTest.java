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
import org.apache.camel.test.junit6.CamelTestSupport;
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
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).until(() -> received.size() == 2);

        assertEquals(2, received.size(), received.toString());
        assertTrue(received.contains("hello.txt"), received.toString());
        assertTrue(received.contains("real/deep.txt"), received.toString());
        // the poll never changes into the listed names
        assertFalse(server.commands().stream().anyMatch(c -> c.startsWith("CWD sub")), server.commands().toString());
    }

    @Test
    void pollsListedDirectoryWithAbsoluteNameOfServerListingAbsolutePaths() throws Exception {
        assertPollsListedDirectoryWithAbsoluteName("");
    }

    @Test
    void pollsListedDirectoryWithAbsoluteNameOfServerListingAbsolutePathsNotStepwise() throws Exception {
        assertPollsListedDirectoryWithAbsoluteName("&stepwise=false");
    }

    private void assertPollsListedDirectoryWithAbsoluteName(String options) throws Exception {
        // a server that lists the absolute path of each entry, in equivalent forms
        server.listAbsoluteNames();
        server.addFile("/inbox/real/deep.txt", "deep".getBytes(StandardCharsets.UTF_8));
        server.addFile("/inbox/second/two.txt", "two".getBytes(StandardCharsets.UTF_8));
        server.addFile("/inbox/third/three.txt", "three".getBytes(StandardCharsets.UTF_8));
        server.addListedDirectory("/inbox", "/inbox/real");
        server.addListedDirectory("/inbox", "/inbox/second//");
        server.addListedDirectory("/inbox", "/inbox/./third");
        server.addFile("/inbox/fourth/four.txt", "four".getBytes(StandardCharsets.UTF_8));
        server.addFile("/inbox/fifth/five.txt", "five".getBytes(StandardCharsets.UTF_8));
        // a path with a drive, and with backslashes, as a server on Windows can list
        server.addListedDirectory("/inbox", "C:/inbox/fourth");
        server.addListedDirectory("/inbox", "\\inbox\\fifth");
        List<String> received = pollInbox(options);

        // the poll changes into each listed directory by its name, and downloads its file
        await().atMost(10, TimeUnit.SECONDS).until(() -> received.size() >= 5);
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).until(() -> received.size() == 5);
        assertEquals(List.of("fifth/five.txt=five", "fourth/four.txt=four", "real/deep.txt=deep", "second/two.txt=two",
                "third/three.txt=three"), received.stream().sorted().toList());
    }

    @Test
    void pollsListedRelativeDirectoryWithTrailingSeparator() throws Exception {
        server.addFile("/inbox/child/report.txt", "report".getBytes(StandardCharsets.UTF_8));
        server.addListedDirectory("/inbox", "child/");
        List<String> received = pollInbox("");

        await().atMost(10, TimeUnit.SECONDS).until(() -> received.contains("child/report.txt=report"));
    }

    @Test
    void pollsListedRelativeDirectoryWithTrailingSeparatorStepwise() throws Exception {
        // a server can list a directory with a trailing /, which a stepwise poll changes into by its name
        server.addFile("/inbox/child/report.txt", "report".getBytes(StandardCharsets.UTF_8));
        server.addListedDirectory("/inbox", "child/");
        List<String> received = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://camel@127.0.0.1:" + server.port() + "/inbox?password=secret&passiveMode=true"
                     + "&recursive=true&noop=true&initialDelay=0&delay=600000")
                        .process(e -> received.add(e.getMessage().getHeader(Exchange.FILE_NAME, String.class)));
            }
        });

        await().atMost(10, TimeUnit.SECONDS).until(() -> !received.isEmpty());
        assertEquals(List.of("child/report.txt"), received);
    }

    @Test
    void entersListedAbsoluteDirectoryByItsNameInTheListedDirectory() throws Exception {
        // /x/inbox/real ends like /inbox/real, but the poll changes into real in the listed directory
        server.listAbsoluteNames();
        server.addFile("/inbox/real/deep.txt", "deep".getBytes(StandardCharsets.UTF_8));
        server.addFile("/x/inbox/real/other.txt", "other".getBytes(StandardCharsets.UTF_8));
        server.addListedDirectory("/inbox", "/x/inbox/real");
        List<String> received = pollInbox("");

        await().atMost(10, TimeUnit.SECONDS).until(() -> !received.isEmpty());
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).until(() -> received.size() == 1);
        assertEquals(List.of("real/deep.txt=deep"), received);
    }

    @Test
    void skipsListedDirectoriesThatAreNotOneDirectoryOfTheListedDirectory() throws Exception {
        server.addFile("/inbox/hello.txt", "hello".getBytes(StandardCharsets.UTF_8));
        server.addFile("/inbox/.camel/archived.txt", "archived".getBytes(StandardCharsets.UTF_8));
        server.addFile("/inbox/group/child/grouped.txt", "grouped".getBytes(StandardCharsets.UTF_8));
        server.addFile("/inbox/child/single.txt", "single".getBytes(StandardCharsets.UTF_8));
        server.addFile("/inbox/real/deep.txt", "deep".getBytes(StandardCharsets.UTF_8));
        // a hidden directory by its absolute path, relative paths, and . and .. by their absolute paths
        server.addListedDirectory("/inbox", "/inbox/.camel");
        server.addListedDirectory("/inbox", "group/child");
        server.addListedDirectory("/inbox", "missing/../child");
        // a relative name that ends with a backslash, and a drive root
        server.addListedDirectory("/inbox", "child\\");
        server.addListedDirectory("/inbox", "C:/");
        server.addListedDirectory("/inbox", "/inbox/.");
        server.addListedDirectory("/inbox", "/inbox/real/..");
        for (String options : new String[] { "", "&stepwise=false" }) {
            List<String> listed = new CopyOnWriteArrayList<>();
            context.getRegistry().bind("listed", (GenericFileFilter<Object>) file -> {
                listed.add(file.getFileNameOnly());
                return true;
            });
            List<String> received = pollInbox(options + "&filter=#listed");

            // the poll lists every directory it changes into before it hands over the first file
            await().atMost(10, TimeUnit.SECONDS).until(() -> !received.isEmpty());
            assertEquals(List.of("hello.txt"), listed, options);
            assertEquals(List.of("hello.txt=hello"), received, options);
            context.getRouteController().stopRoute("poll");
            context.removeRoute("poll");
        }
    }

    @Test
    void skipsListedAbsoluteDirectoryThatIsNotInTheListedDirectory() throws Exception {
        // a server that lists the absolute path of each entry, and lists /etc in /inbox
        server.addFile("/inbox/hello.txt", "hello".getBytes(StandardCharsets.UTF_8));
        server.addFile("/etc/secret.txt", "secret".getBytes(StandardCharsets.UTF_8));
        server.addListedDirectory("/inbox", "/etc");
        List<String> listed = new CopyOnWriteArrayList<>();
        context.getRegistry().bind("listed", (GenericFileFilter<Object>) file -> {
            listed.add(file.getFileNameOnly());
            return true;
        });
        List<String> received = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://camel@127.0.0.1:" + server.port() + "/inbox?password=secret&passiveMode=true"
                     + "&recursive=true&handleDirectoryParserAbsoluteResult=true&filter=#listed&noop=true"
                     + "&initialDelay=0&delay=600000")
                        .process(e -> received.add(e.getMessage().getHeader(Exchange.FILE_NAME, String.class)));
            }
        });

        await().atMost(10, TimeUnit.SECONDS).until(() -> received.contains("hello.txt"));
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).until(() -> received.size() == 1);

        assertEquals(List.of("hello.txt"), received);
        assertFalse(listed.contains("secret.txt"), listed.toString());
        assertFalse(server.commands().stream().anyMatch(c -> c.equals("CWD /etc") || c.equals("CWD etc")),
                server.commands().toString());
    }

    @Test
    void downloadsNamedFileWithoutListing() throws Exception {
        // the name of the file to download is not from a listing, and is matched as given
        server.addFile("/inbox/report.txt", "report".getBytes(StandardCharsets.UTF_8));
        List<String> received = pollInbox("&useList=false&fileName=/inbox/report.txt");

        await().atMost(10, TimeUnit.SECONDS).until(() -> received.contains("report.txt=report"));
    }

    private List<String> pollInbox(String options) throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://camel@127.0.0.1:" + server.port() + "/inbox?password=secret&passiveMode=true"
                     + "&recursive=true&handleDirectoryParserAbsoluteResult=true&noop=true&initialDelay=0&delay=600000"
                     + options)
                        .routeId("poll")
                        .convertBodyTo(String.class)
                        .process(e -> received.add(e.getMessage().getHeader(Exchange.FILE_NAME, String.class) + "="
                                                   + e.getMessage().getBody(String.class)));
            }
        });
        return received;
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
        // a trailing / is not part of the name, as some servers list a directory that way
        assertTrue(FtpUtils.isSubDirectoryName("sub/"));
        assertTrue(FtpUtils.isSubDirectoryName("sub//"));
        assertFalse(FtpUtils.isSubDirectoryName("/"));
        assertFalse(FtpUtils.isSubDirectoryName("//"));
        assertFalse(FtpUtils.isSubDirectoryName("./"));
        assertFalse(FtpUtils.isSubDirectoryName("../"));
        assertFalse(FtpUtils.isSubDirectoryName(" /"));
        assertFalse(FtpUtils.isSubDirectoryName("sub/../"));
        assertFalse(FtpUtils.isSubDirectoryName("sub\\"));
        assertFalse(FtpUtils.isSubDirectoryName("sub\\/"));
    }

    @Test
    void listedAbsolutePathsInTheListedDirectory() {
        assertTrue(FtpConsumer.isListedIn("/data/in/sub", "data/in", "sub"));
        assertTrue(FtpConsumer.isListedIn("/x/data/in/sub", "data/in", "sub"));
        assertTrue(FtpConsumer.isListedIn("C:/data/in/sub", "data/in", "sub"));
        // the listed directory starts with the same drive as the listed path
        assertTrue(FtpConsumer.isListedIn("C:/data/in/sub", "C:/data/in", "sub"));
        assertTrue(FtpConsumer.isListedIn("C:\\data\\in\\sub\\", "C:/data/in", "sub"));
        assertFalse(FtpConsumer.isListedIn("D:/data/in/sub", "C:/data/in", "sub"));
        assertFalse(FtpConsumer.isListedIn("/data/other/sub", "data/in", "sub"));
        // a path whose .. goes above its drive is not an absolute path in the listed directory
        assertFalse(FtpConsumer.isListedIn("C:/../sub", "", "sub"));
        assertFalse(FtpConsumer.isListedIn("C:/../data/in/sub", "data/in", "sub"));
    }

    @Test
    void directoryOrParentOf() {
        assertTrue(FtpUtils.isDirectoryOrParentOf("/home/camel", "/home/camel"));
        assertTrue(FtpUtils.isDirectoryOrParentOf("/home", "/home/camel"));
        assertTrue(FtpUtils.isDirectoryOrParentOf("/home/", "/home/camel"));
        assertTrue(FtpUtils.isDirectoryOrParentOf("/", "/home/camel"));
        assertFalse(FtpUtils.isDirectoryOrParentOf("/home/cam", "/home/camel"));
        assertFalse(FtpUtils.isDirectoryOrParentOf("/home/camel/sub", "/home/camel"));
    }
}
