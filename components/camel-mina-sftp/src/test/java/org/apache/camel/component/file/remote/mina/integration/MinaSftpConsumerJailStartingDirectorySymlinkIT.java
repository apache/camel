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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * With jailStartingDirectory (the default) the mina-sftp consumer resolves a listed symbolic link on the server, and
 * only consumes it when its target is inside the starting directory.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpConsumerJailStartingDirectorySymlinkIT extends MinaSftpServerTestSupport {

    private Path base;

    @BeforeEach
    void createTestDirectory() throws IOException {
        // the root directory of the server is shared by the tests of this class and kept between runs
        base = Files.createTempDirectory(Files.createDirectories(service.getFtpRootDir()), "test");
    }

    @Test
    void consumesLinkResolvingInsideStartingDirectory() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("latest.txt"), Path.of("hello.txt"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World", "Hello World");
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME, "hello.txt", "latest.txt");

        consume("inbox", "");

        mock.assertIsSatisfied();
    }

    @Test
    void skipsLinkResolvingOutsideStartingDirectory() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Path archive = Files.createDirectories(base.resolve("archive"));
        Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        Files.writeString(archive.resolve("report.txt"), "Archived");
        createSymbolicLink(inbox.resolve("report.txt"), Path.of("..", "archive", "report.txt"));
        createSymbolicLink(inbox.resolve("absolute.txt"), archive.resolve("report.txt").toAbsolutePath());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        // the links are not part of the batch of the poll that picked up hello.txt
        mock.message(0).exchangeProperty(Exchange.BATCH_SIZE).isEqualTo(1);

        consume("inbox", "");

        mock.assertIsSatisfied();
    }

    @Test
    void followsLinkOutsideStartingDirectoryWhenJailDisabled() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Path archive = Files.createDirectories(base.resolve("archive"));
        Files.writeString(archive.resolve("report.txt"), "Archived");
        createSymbolicLink(inbox.resolve("report.txt"), Path.of("..", "archive", "report.txt"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Archived");

        consume("inbox", "&jailStartingDirectory=false");

        mock.assertIsSatisfied();
    }

    @Test
    void skipsDanglingLink() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("missing.txt"), Path.of("gone.txt"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        // the link is not part of the batch of the poll that picked up hello.txt
        mock.message(0).exchangeProperty(Exchange.BATCH_SIZE).isEqualTo(1);

        consume("inbox", "");

        mock.assertIsSatisfied();
    }

    @Test
    void consumesLinkInStartingDirectoryReachedThroughLink() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("latest.txt"), Path.of("hello.txt"));
        createSymbolicLink(base.resolve("inbox-link"), Path.of("inbox"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World", "Hello World");
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME, "hello.txt", "latest.txt");

        consume("inbox-link", "");

        mock.assertIsSatisfied();
    }

    private void consume(String directory, String query) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mina-sftp://localhost:" + service.getPort() + "/" + base.resolve(directory)
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&initialDelay=0&delay=100&noop=true" + query)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
    }

    private static void createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException(e);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // such as Windows when the user may not create symbolic links
            Assumptions.abort("Symbolic links cannot be created on this platform: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
