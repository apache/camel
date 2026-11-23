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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.apache.camel.component.file.remote.mina.MinaSftpOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stepwise operation changes into the directory of a file one segment at a time. Verifies that a directory that is
 * the current directory once compacted, such as sub/.., is not taken as a parent of the current directory, on a server
 * whose login directory is not its root.
 */
class MinaSftpStepwiseChangeDirectoryIT extends CamelTestSupport {

    private final Set<String> listed = ConcurrentHashMap.newKeySet();
    private Path root;
    private SshServer sshd;

    private void startServer(Map<String, String> renames) throws Exception {
        root = Paths.get("target", "sftp-home", getClass().getSimpleName() + "-" + System.nanoTime(), "root")
                .toAbsolutePath();
        Files.createDirectories(root.resolve("home/camel/inbox/sub"));
        Files.createDirectories(root.resolve("home/cam"));
        Files.createDirectories(root.resolve("home/camel/inbox/real"));
        Files.createDirectories(root.resolve("home/camel/inbox/blank"));
        Files.writeString(root.resolve("home/camel/inbox/real/deep.txt"), "Deep in the inbox");
        Files.writeString(root.resolve("home/camel/inbox/hello.txt"), "Hello from the inbox");
        Files.writeString(root.resolve("home/camel/listed.txt"), "Hello from the login directory");
        Files.writeString(root.resolve("outside.txt"), "Outside the login directory");
        sshd = HomeDirectorySftpServer.start(root, "/home/camel", renames);
    }

    @AfterEach
    void stopServer() throws Exception {
        if (sshd != null) {
            sshd.stop();
        }
    }

    private String uri(String directory, String options) {
        return "mina-sftp://camel@localhost:" + sshd.getPort() + "/" + directory
               + "?password=secret&strictHostKeyChecking=no&useUserKnownHostsFile=false" + options;
    }

    @Test
    void changeIntoPathThatIsTheCurrentDirectory() throws Exception {
        startServer(Map.of());
        MinaSftpEndpoint endpoint = context.getEndpoint(uri("", ""), MinaSftpEndpoint.class);
        MinaSftpOperations operations = (MinaSftpOperations) endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        try {
            // a relative current directory
            operations.changeCurrentDirectory("inbox");
            operations.changeCurrentDirectory("sub/..");
            assertEquals("inbox", operations.getCurrentDirectory());

            // an absolute current directory
            operations.changeCurrentDirectory("/home/camel/inbox");
            String inbox = operations.getCurrentDirectory();
            assertTrue(inbox.endsWith("/home/camel/inbox"), inbox);
            operations.changeCurrentDirectory("sub/..");
            assertEquals(inbox, operations.getCurrentDirectory());

            // a parent directory, and a directory whose name only starts like the current one
            operations.changeCurrentDirectory("/home/camel");
            assertTrue(operations.getCurrentDirectory().endsWith("/home/camel"), operations.getCurrentDirectory());
            operations.changeCurrentDirectory("/home/cam");
            assertTrue(operations.getCurrentDirectory().endsWith("/home/cam"), operations.getCurrentDirectory());
        } finally {
            operations.disconnect();
        }
    }

    @Test
    void uploadIntoCurrentDirectory() throws Exception {
        startServer(Map.of());

        template.sendBodyAndHeader(uri("", ""), "Hello", Exchange.FILE_NAME, "sub/../uploaded.txt");

        assertTrue(Files.exists(root.resolve("home/camel/uploaded.txt")));
        assertFalse(Files.exists(root.resolve("uploaded.txt")));
    }

    @Test
    void downloadFromCurrentDirectory() throws Exception {
        // the server lists listed.txt as sub/../outside.txt, which is not in the login directory
        startServer(Map.of("listed.txt", "sub/../outside.txt"));
        Files.writeString(root.resolve("home/camel/real.txt"), "Real file in the login directory");
        MockEndpoint mock = getMockEndpoint("mock:result");
        // the real file shows the poll was done, and nothing else is downloaded
        mock.expectedBodiesReceived("Real file in the login directory");
        mock.setAssertPeriod(1000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri("", "&delete=true&initialDelay=0&delay=60000")).convertBodyTo(String.class).to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        assertTrue(Files.exists(root.resolve("outside.txt")));
    }

    @Test
    void pollSubDirectoryThatIsTheCurrentDirectory() throws Exception {
        // the server lists the sub directory of the inbox as sub/.., which is the inbox, and another one with a blank
        // name, and the poll skips both
        startServer(Map.of("sub", "sub/..", "blank", " "));
        context.getRegistry().bind("listed", (GenericFileFilter<Object>) file -> {
            listed.add(file.getFileNameOnly());
            return true;
        });
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello from the inbox", "Deep in the inbox");
        mock.setAssertPeriod(1000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri("inbox", "&recursive=true&maxDepth=3&noop=true&filter=#listed&initialDelay=0&delay=60000"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        // nothing is listed outside the inbox
        assertFalse(listed.contains("outside.txt"), listed.toString());
        assertFalse(listed.contains("home"), listed.toString());
        assertFalse(listed.contains("listed.txt"), listed.toString());
    }
}
