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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GenericFileHelperTest {

    private final File workDir = new File("target/localwork");

    @Test
    public void shouldAllowFilesWithinLocalWorkDirectory() {
        // a plain name, a nested name, and a ../ that still resolves within the work directory are all allowed
        assertDoesNotThrow(() -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "file.txt"), workDir));
        assertDoesNotThrow(() -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "sub/dir/file.txt"), workDir));
        assertDoesNotThrow(() -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "sub/../file.txt"), workDir));
    }

    @Test
    public void shouldRejectFilesEscapingLocalWorkDirectory() {
        // a remote file name that resolves outside the configured local work directory must be rejected
        assertThrows(GenericFileOperationFailedException.class,
                () -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "../escape.txt"), workDir));
        assertThrows(GenericFileOperationFailedException.class,
                () -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "../../etc/passwd"), workDir));
        assertThrows(GenericFileOperationFailedException.class,
                () -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "sub/../../escape.txt"), workDir));
        // a sibling directory whose name merely extends the work directory name must also be rejected
        assertThrows(GenericFileOperationFailedException.class,
                () -> GenericFileHelper.jailToLocalWorkDirectory(new File(workDir, "../localworkEVIL/file.txt"), workDir));
    }

    @Test
    public void shouldRejectSymlinkEscapingLocalWorkDirectory(@TempDir Path tmp) throws IOException {
        Path work = Files.createDirectories(tmp.resolve("work"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));

        // a symbolic link inside the work directory that points outside of it
        Path link = work.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("Symbolic links are not supported on this platform: " + e.getMessage());
        }

        // a file written through the symlink resolves outside the work directory and must be rejected, even though
        // it passes a lexical-only containment check
        File escaping = new File(link.toFile(), "evil.txt");
        assertThrows(GenericFileOperationFailedException.class,
                () -> GenericFileHelper.jailToLocalWorkDirectory(escaping, work.toFile()));

        // a legitimate file within a real (not-yet-existing) subdirectory of the work directory is still allowed
        File legit = new File(work.toFile(), "sub/ok.txt");
        assertDoesNotThrow(() -> GenericFileHelper.jailToLocalWorkDirectory(legit, work.toFile()));
    }

    @Test
    public void isWithinDirectoryRespectsPathBoundaries() {
        String sep = File.separator;
        String work = sep + "data" + sep + "work";

        // the directory itself and real children are contained
        assertTrue(GenericFileHelper.isWithinDirectory(work, work));
        assertTrue(GenericFileHelper.isWithinDirectory(work + sep + "a.txt", work));
        assertTrue(GenericFileHelper.isWithinDirectory(work + sep + "sub" + sep + "a.txt", work));

        // a trailing separator on the directory (as supplied by the file producer) is tolerated
        assertTrue(GenericFileHelper.isWithinDirectory(work + sep + "a.txt", work + sep));

        // a sibling whose name merely extends the directory name is NOT contained
        assertFalse(GenericFileHelper.isWithinDirectory(sep + "data" + sep + "workspace" + sep + "a.txt", work));
        assertFalse(GenericFileHelper.isWithinDirectory(sep + "data" + sep + "workspace" + sep + "a.txt", work + sep));

        // an empty directory imposes no boundary
        assertTrue(GenericFileHelper.isWithinDirectory("anything.txt", ""));
    }

    @Test
    public void isWithinDirectoryUsesTheGivenSeparator() {
        // remote paths always use '/', regardless of the platform Camel runs on
        assertTrue(GenericFileHelper.isWithinDirectory("poll/file.txt", "poll", '/'));
        assertTrue(GenericFileHelper.isWithinDirectory("poll/sub/file.txt", "poll", '/'));
        assertTrue(GenericFileHelper.isWithinDirectory("poll", "poll", '/'));
        assertTrue(GenericFileHelper.isWithinDirectory("/poll/file.txt", "/poll", '/'));

        // a trailing separator on the directory is tolerated
        assertTrue(GenericFileHelper.isWithinDirectory("poll/file.txt", "poll/", '/'));

        // a sibling whose name merely extends the directory name is NOT contained
        assertFalse(GenericFileHelper.isWithinDirectory("pollute/file.txt", "poll", '/'));
    }

    @Test
    public void isWithinDirectoryRejectsPathsResolvingOutsideTheDirectory() {
        // the compacted result of a listing name that navigates above the polled directory
        assertFalse(GenericFileHelper.isWithinDirectory("../secret.txt", "poll", '/'));
        assertFalse(GenericFileHelper.isWithinDirectory("../../etc/shadow", "poll", '/'));
        assertFalse(GenericFileHelper.isWithinDirectory("/secret.txt", "/poll", '/'));

        // a target that still resolves upwards escapes even when no directory boundary is configured
        assertFalse(GenericFileHelper.isWithinDirectory("..", "", '/'));
        assertFalse(GenericFileHelper.isWithinDirectory("../secret.txt", "", '/'));
    }
}
