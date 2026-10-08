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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With jailStartingDirectory (the default) the file producer must not write outside the starting directory through a
 * symbolic link that lives inside it.
 */
class FileProducerJailStartingDirectorySymlinkTest extends ContextTestSupport {

    private Path out;
    private Path elsewhere;

    @BeforeEach
    void createDirectories() throws IOException {
        out = Files.createDirectories(testDirectory("out"));
        elsewhere = Files.createDirectories(testDirectory("elsewhere"));
    }

    @Test
    void directoryLinkToOutsideIsRejected() {
        createSymbolicLink(out.resolve("link"), elsewhere);

        assertRejected(fileUri(out, ""), "link/hello.txt");

        assertFalse(Files.exists(elsewhere.resolve("hello.txt")));
    }

    @Test
    void missingDirectoryBelowDirectoryLinkToOutsideIsNotCreated() {
        // autoCreate (the default) creates the missing parent directories before the file is written
        createSymbolicLink(out.resolve("link"), elsewhere);

        assertRejected(fileUri(out, ""), "link/new/hello.txt");

        assertFalse(Files.exists(elsewhere.resolve("new")));
    }

    @Test
    void parentSegmentAfterDirectoryLinkIsRejected() {
        // lexically out/hello.txt, but the filesystem resolves link/.. to the parent of the link target
        createSymbolicLink(out.resolve("link"), elsewhere);

        assertRejected(fileUri(out, ""), "link/../hello.txt");

        assertFalse(Files.exists(testDirectory("hello.txt")));
    }

    @Test
    void fileLinkToOutsideIsRejected() throws Exception {
        Path target = Files.writeString(elsewhere.resolve("target.txt"), "Original");
        createSymbolicLink(out.resolve("hello.txt"), target);

        assertRejected(fileUri(out, ""), "hello.txt");

        assertEquals("Original", Files.readString(target));
    }

    @Test
    void danglingLinkToOutsideIsRejected() {
        Path target = elsewhere.resolve("missing.txt");
        createSymbolicLink(out.resolve("hello.txt"), target);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(fileUri(out, ""), "Hello World", Exchange.FILE_NAME, "hello.txt"));

        GenericFileOperationFailedException cause
                = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("Cannot verify file:"), cause.getMessage());
        assertFalse(Files.exists(target));
    }

    @Test
    void checksumFileLinkToOutsideIsRejected() throws Exception {
        Path target = Files.writeString(elsewhere.resolve("target.txt"), "Original");
        createSymbolicLink(out.resolve("hello.txt.md5"), target);

        assertRejected(fileUri(out, "?checksumFileAlgorithm=md5"), "hello.txt");

        assertEquals("Original", Files.readString(target));
    }

    @Test
    void linksInsideStartingDirectoryAreFollowed() throws Exception {
        Path archive = Files.createDirectories(out.resolve("archive"));
        createSymbolicLink(out.resolve("current"), archive);
        Path latest = Files.writeString(archive.resolve("latest.txt"), "Old");
        createSymbolicLink(out.resolve("latest.txt"), latest);

        template.sendBodyAndHeader(fileUri(out, ""), "Hello World", Exchange.FILE_NAME, "current/hello.txt");
        template.sendBodyAndHeader(fileUri(out, ""), "New", Exchange.FILE_NAME, "latest.txt");

        assertEquals("Hello World", Files.readString(archive.resolve("hello.txt")));
        assertEquals("New", Files.readString(latest));
        assertTrue(Files.isSymbolicLink(out.resolve("latest.txt")));
    }

    @Test
    void parentDirectoryIsStillRejectedLexically() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(fileUri(out, ""), "Hello World", Exchange.FILE_NAME, "../hello.txt"));

        IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("as the filename is jailed to the starting directory"), cause.getMessage());
        assertFalse(Files.exists(testDirectory("hello.txt")));
    }

    @Test
    void directoryLinkIsFollowedWhenJailStartingDirectoryIsDisabled() throws Exception {
        createSymbolicLink(out.resolve("link"), elsewhere);

        template.sendBodyAndHeader(fileUri(out, "?jailStartingDirectory=false"), "Hello World", Exchange.FILE_NAME,
                "link/hello.txt");

        assertEquals("Hello World", Files.readString(elsewhere.resolve("hello.txt")));
    }

    private void assertRejected(String uri, String fileName) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, fileName));

        GenericFileOperationFailedException cause
                = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().contains("as it resolves outside the starting directory"), cause.getMessage());
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
