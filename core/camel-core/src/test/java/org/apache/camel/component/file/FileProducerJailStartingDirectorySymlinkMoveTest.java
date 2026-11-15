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
 * With jailStartingDirectory (the default) the file producer must not delete or rename onto a file outside the starting
 * directory through a symbolic link that lives inside it, when it moves an existing file (moveExisting) or renames the
 * file it wrote with a temporary name (tempFileName).
 */
class FileProducerJailStartingDirectorySymlinkMoveTest extends ContextTestSupport {

    private static final String MOVE_EXISTING = "?fileExist=Move&moveExisting=archive/${file:onlyname}";
    private static final String TEMP_FILE_NAME = "?tempFileName=../tmp-${file:onlyname}";

    private Path out;
    private Path elsewhere;

    @BeforeEach
    void createDirectories() throws IOException {
        out = Files.createDirectories(testDirectory("out"));
        elsewhere = Files.createDirectories(testDirectory("elsewhere"));
    }

    @Test
    void moveExistingOntoFileThroughLinkToOutsideIsRefused() throws Exception {
        Path existing = Files.writeString(out.resolve("cfg.txt"), "Old");
        Path outsideFile = Files.writeString(elsewhere.resolve("cfg.txt"), "Outside");
        createSymbolicLink(out.resolve("archive"), elsewhere);

        assertRefused(fileUri(out, MOVE_EXISTING), "cfg.txt");

        assertEquals("Outside", Files.readString(outsideFile));
        assertEquals("Old", Files.readString(existing));
    }

    @Test
    void moveExistingThroughLinkToOutsideIsRefused() throws Exception {
        Path existing = Files.writeString(out.resolve("cfg.txt"), "Old");
        createSymbolicLink(out.resolve("archive"), elsewhere);

        assertRefused(fileUri(out, MOVE_EXISTING), "cfg.txt");

        assertFalse(Files.exists(elsewhere.resolve("cfg.txt")));
        assertEquals("Old", Files.readString(existing));
    }

    @Test
    void moveExistingThroughLinkInsideStartingDirectoryIsUsed() throws Exception {
        Files.writeString(out.resolve("cfg.txt"), "Old");
        Path archive = Files.createDirectories(out.resolve("real-archive"));
        createSymbolicLink(out.resolve("archive"), archive);

        template.sendBodyAndHeader(fileUri(out, MOVE_EXISTING), "New", Exchange.FILE_NAME, "cfg.txt");

        assertEquals("Old", Files.readString(archive.resolve("cfg.txt")));
        assertEquals("New", Files.readString(out.resolve("cfg.txt")));
    }

    @Test
    void moveExistingThroughLinkWhenJailStartingDirectoryIsDisabled() throws Exception {
        Files.writeString(out.resolve("cfg.txt"), "Old");
        Path outsideFile = Files.writeString(elsewhere.resolve("cfg.txt"), "Outside");
        createSymbolicLink(out.resolve("archive"), elsewhere);

        template.sendBodyAndHeader(fileUri(out, MOVE_EXISTING + "&jailStartingDirectory=false"), "New",
                Exchange.FILE_NAME, "cfg.txt");

        assertEquals("Old", Files.readString(outsideFile));
        assertEquals("New", Files.readString(out.resolve("cfg.txt")));
    }

    @Test
    void tempFileNameRenameOntoFileThroughLinkToOutsideIsRefused() throws Exception {
        Path outsideFile = Files.writeString(elsewhere.resolve("hello.txt"), "Outside");
        createSymbolicLink(out.resolve("sub"), elsewhere);

        assertRefused(fileUri(out, TEMP_FILE_NAME), "sub/hello.txt");

        assertEquals("Outside", Files.readString(outsideFile));
    }

    @Test
    void tempFileNameRenameThroughLinkToOutsideIsRefused() throws Exception {
        createSymbolicLink(out.resolve("sub"), elsewhere);

        assertRefused(fileUri(out, TEMP_FILE_NAME), "sub/hello.txt");

        assertFalse(Files.exists(elsewhere.resolve("hello.txt")));
    }

    @Test
    void tempFileNameRenameThroughLinkInsideStartingDirectoryIsUsed() throws Exception {
        Path real = Files.createDirectories(out.resolve("real"));
        Files.writeString(real.resolve("hello.txt"), "Old");
        createSymbolicLink(out.resolve("sub"), real);

        template.sendBodyAndHeader(fileUri(out, TEMP_FILE_NAME), "Hello World", Exchange.FILE_NAME, "sub/hello.txt");

        assertEquals("Hello World", Files.readString(real.resolve("hello.txt")));
    }

    @Test
    void tempFileNameRenameThroughLinkWhenJailStartingDirectoryIsDisabled() throws Exception {
        Path outsideFile = Files.writeString(elsewhere.resolve("hello.txt"), "Outside");
        createSymbolicLink(out.resolve("sub"), elsewhere);

        template.sendBodyAndHeader(fileUri(out, TEMP_FILE_NAME + "&jailStartingDirectory=false"), "Hello World",
                Exchange.FILE_NAME, "sub/hello.txt");

        assertEquals("Hello World", Files.readString(outsideFile));
    }

    private void assertRefused(String uri, String fileName) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "New", Exchange.FILE_NAME, fileName));

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
