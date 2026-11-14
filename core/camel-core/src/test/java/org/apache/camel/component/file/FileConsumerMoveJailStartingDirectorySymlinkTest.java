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

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With jailStartingDirectory (the default) the file consumer must not move a consumed file (move, preMove, moveFailed
 * and the default .camel folder) through a symbolic link that lives inside the starting directory and resolves outside
 * of it. Such a file is skipped before it is processed, so it is not processed again on every poll.
 */
class FileConsumerMoveJailStartingDirectorySymlinkTest extends ContextTestSupport {

    private static final String QUERY = "?initialDelay=0&delay=100&pollStrategy=#countPolls"
                                        + "&exceptionHandler=#moveErrors&onCompletionExceptionHandler=#moveErrors";

    private final Queue<Throwable> moveErrors = new ConcurrentLinkedQueue<>();
    private final AtomicInteger polls = new AtomicInteger();
    private Path inbox;
    private Path outside;
    private Path outsideFile;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @BeforeEach
    void createDirectories() throws IOException {
        inbox = Files.createDirectories(testDirectory("inbox"));
        outside = Files.createDirectories(testDirectory("outside"));
        outsideFile = Files.writeString(outside.resolve("hello.txt"), "Outside");
        context.getRegistry().bind("moveErrors", new CollectingExceptionHandler());
        context.getRegistry().bind("countPolls", new DefaultPollingConsumerPollStrategy() {
            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                polls.incrementAndGet();
            }
        });
    }

    @Test
    void defaultMoveFolderLinkingOutsideStartingDirectoryIsNotUsed() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve(".camel"), outside);

        consume(QUERY, false);

        assertSkipped(file);
    }

    @Test
    void moveFolderLinkingOutsideStartingDirectoryIsNotUsed() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("done"), outside);

        consume(QUERY + "&move=done", false);

        assertSkipped(file);
    }

    @Test
    void preMoveFolderLinkingOutsideStartingDirectoryIsNotUsed() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("inprogress"), outside);

        consume(QUERY + "&preMove=inprogress&noop=true", false);

        assertSkipped(file);
    }

    @Test
    void moveFolderBelowPreMoveFolderLinkingOutsideStartingDirectoryIsNotUsed() throws Exception {
        // the default .camel folder is relative to where preMove puts the file
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        Files.createDirectories(inbox.resolve("inprogress"));
        createSymbolicLink(inbox.resolve("inprogress/.camel"), outside);

        consume(QUERY + "&preMove=inprogress", false);

        assertSkipped(file);
    }

    @Test
    void moveFailedFolderLinkingOutsideStartingDirectoryIsNotUsed() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("failed"), outside);

        consume(QUERY + "&moveFailed=failed", true);

        assertSkipped(file);
    }

    @Test
    void moveFailedFolderLinkingOutsideStartingDirectoryIsNotUsedWithDelete() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("failed"), outside);

        consume(QUERY + "&delete=true&moveFailed=failed", true);

        assertSkipped(file);
    }

    @Test
    void recursiveSubDirectoryMoveFolderLinkingOutsideIsNotUsed() throws Exception {
        Path sub = Files.createDirectories(inbox.resolve("sub"));
        Path file = Files.writeString(sub.resolve("hello.txt"), "Hello World");
        createSymbolicLink(sub.resolve(".camel"), outside);

        consume(QUERY + "&recursive=true", false);

        assertSkipped(file);
    }

    @Test
    void renameUsingCopyMoveFolderLinkingOutsideIsNotUsed() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve(".camel"), outside);

        consume(QUERY + "&renameUsingCopy=true", false);

        assertSkipped(file);
    }

    @Test
    void moveTargetKnownOnlyWhenProcessedIsCheckedWhenMoved() throws Exception {
        // the route sets the folder, so the target is only known once the file is processed
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve("done"), outside);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(inbox, QUERY + "&move=${exchangeProperty.folder}/${file:onlyname}"))
                        .setProperty("folder", constant("done"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        });
        context.start();

        await().atMost(10, TimeUnit.SECONDS).until(() -> !moveErrors.isEmpty());

        assertEquals("Outside", readString(outsideFile), "the file outside the starting directory must not be replaced");
        assertTrue(Files.exists(file), "the consumed file must stay where it is");
        assertTrue(moveErrors.stream().anyMatch(e -> String.valueOf(e.getMessage())
                .contains("as it resolves outside the starting directory")), moveErrors.toString());
    }

    @Test
    void moveFolderLinkingInsideStartingDirectoryIsUsed() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        Path archive = Files.createDirectories(inbox.resolve("archive"));
        createSymbolicLink(inbox.resolve("done"), archive);

        consume(QUERY + "&move=done", false);

        await().atMost(10, TimeUnit.SECONDS).until(() -> Files.exists(archive.resolve("hello.txt")));
        assertFalse(Files.exists(file));
        assertEquals("Hello World", Files.readString(archive.resolve("hello.txt")));
        assertTrue(moveErrors.isEmpty(), moveErrors.toString());
    }

    @Test
    void moveOutsideStartingDirectoryConfiguredByRouteStillWorks() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        Path archive = testDirectory("archive");

        consume(QUERY + "&move=../archive/${file:onlyname}", false);

        await().atMost(10, TimeUnit.SECONDS).until(() -> Files.exists(archive.resolve("hello.txt")));
        assertFalse(Files.exists(file));
        assertEquals("Hello World", Files.readString(archive.resolve("hello.txt")));
        assertTrue(moveErrors.isEmpty(), moveErrors.toString());
    }

    @Test
    void jailStartingDirectoryDisabledMovesThroughLink() throws Exception {
        Path file = Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        createSymbolicLink(inbox.resolve(".camel"), outside);

        consume(QUERY + "&jailStartingDirectory=false", false);

        await().atMost(10, TimeUnit.SECONDS).until(() -> !Files.exists(file));
        assertEquals("Hello World", Files.readString(outsideFile));
        assertTrue(moveErrors.isEmpty(), moveErrors.toString());
    }

    private void assertSkipped(Path file) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(200);

        // the file is listed on every poll, and skipped each time
        await().atMost(10, TimeUnit.SECONDS).until(() -> polls.get() >= 5);

        mock.assertIsSatisfied();
        assertEquals("Outside", readString(outsideFile), "the file outside the starting directory must not be replaced");
        assertTrue(Files.exists(file), "the skipped file must stay where it is");
        assertTrue(moveErrors.isEmpty(), moveErrors.toString());
    }

    private void consume(String query, boolean fail) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (fail) {
                    from(fileUri(inbox, query)).convertBodyTo(String.class).to("mock:result")
                            .throwException(new IllegalArgumentException("Forced"));
                } else {
                    from(fileUri(inbox, query)).convertBodyTo(String.class).to("mock:result");
                }
            }
        });
        context.start();
    }

    private static String readString(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : null;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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

    private final class CollectingExceptionHandler implements ExceptionHandler {

        @Override
        public void handleException(Throwable exception) {
            moveErrors.add(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            moveErrors.add(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            moveErrors.add(exception);
        }
    }
}
