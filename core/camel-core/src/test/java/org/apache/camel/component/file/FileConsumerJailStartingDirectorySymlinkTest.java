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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With jailStartingDirectory (the default) the file consumer must not pick up a file through a symbolic link that lives
 * inside the starting directory and resolves outside of it.
 */
class FileConsumerJailStartingDirectorySymlinkTest extends ContextTestSupport {

    private static final String CONSUMER_LOGGER = GenericFileConsumer.class.getName();

    private final Queue<Level> skippedLinkLevels = new ConcurrentLinkedQueue<>();
    private Path inbox;
    private Path outside;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @BeforeEach
    void createDirectories() throws IOException {
        inbox = Files.createDirectories(testDirectory("inbox"));
        outside = Files.createDirectories(testDirectory("outside"));
        Files.writeString(outside.resolve("secret.txt"), "Outside");
    }

    @AfterEach
    void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(CONSUMER_LOGGER);
        ctx.updateLoggers();
    }

    @Test
    void fileLinkToOutsideIsSkippedAndReportedOnce() throws Exception {
        Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        Path link = inbox.resolve("link.txt");
        createSymbolicLink(link, outside.resolve("secret.txt"));
        ConsumingAppender.newAppender(CONSUMER_LOGGER, "FileConsumerJailStartingDirectorySymlinkTest", Level.DEBUG,
                event -> {
                    String message = event.getMessage().getFormattedMessage();
                    if (message.startsWith("Skipping file as it resolves outside the starting directory")
                            && message.endsWith(link.toString())) {
                        skippedLinkLevels.add(event.getLevel());
                    }
                });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        // the link is not part of the batch of the poll that picked up hello.txt
        mock.message(0).exchangeProperty(Exchange.BATCH_SIZE).isEqualTo(1);

        consume("?initialDelay=0&delay=10&noop=true");

        mock.assertIsSatisfied();
        // the link stays in place and is listed by every poll, but is reported at WARN only once
        await().atMost(10, TimeUnit.SECONDS).until(() -> skippedLinkLevels.size() >= 3);
        assertEquals(1, skippedLinkLevels.stream().filter(Level.WARN::equals).count(), skippedLinkLevels.toString());
    }

    @Test
    void directoryLinkToOutsideIsNotEnteredWhenRecursive() throws Exception {
        Files.createDirectories(inbox.resolve("sub"));
        Files.writeString(inbox.resolve("sub/hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("linkdir"), outside);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).exchangeProperty(Exchange.BATCH_SIZE).isEqualTo(1);

        consume("?initialDelay=0&delay=10&noop=true&recursive=true");

        mock.assertIsSatisfied();
    }

    @Test
    void linksInsideStartingDirectoryAreConsumed() throws Exception {
        Path data = Files.createDirectories(inbox.resolve("data"));
        Path hello = Files.writeString(data.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("current"), data);
        createSymbolicLink(inbox.resolve("latest.txt"), hello);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World", "Hello World", "Hello World");
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME,
                "current" + File.separator + "hello.txt", "data" + File.separator + "hello.txt", "latest.txt");

        consume("?initialDelay=0&delay=10&noop=true&recursive=true");

        mock.assertIsSatisfied();
    }

    @Test
    void startingDirectoryReachedThroughLinkIsConsumed() throws Exception {
        Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        Path inboxLink = testDirectory("inbox-link");
        createSymbolicLink(inboxLink, inbox);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(inboxLink, "?initialDelay=0&delay=10&noop=true")).convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
        context.start();

        mock.assertIsSatisfied();
    }

    @Test
    void fileLinkToOutsideIsConsumedWhenJailStartingDirectoryIsDisabled() throws Exception {
        createSymbolicLink(inbox.resolve("link.txt"), outside.resolve("secret.txt"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Outside");

        consume("?initialDelay=0&delay=10&noop=true&jailStartingDirectory=false");

        mock.assertIsSatisfied();
    }

    private void consume(String query) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(inbox, query)).convertBodyTo(String.class).to("mock:result");
            }
        });
        context.start();
    }

    private static void createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // such as Windows without the privilege to create symbolic links
            Assumptions.abort("Symbolic links cannot be created on this platform: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
