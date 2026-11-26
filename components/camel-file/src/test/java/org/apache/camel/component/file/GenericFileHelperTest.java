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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    public void isWithinDirectoryRejectsAParentSegmentAnywhere() {
        assertFalse(GenericFileHelper.isWithinDirectory("file:/../secret.txt", "", '/'));
        assertFalse(GenericFileHelper.isWithinDirectory("poll/../secret.txt", "poll", '/'));
        assertFalse(GenericFileHelper.isWithinDirectory("poll\\..\\secret.txt", "poll", '/'));
        // a name that only contains two dots is not a parent segment
        assertTrue(GenericFileHelper.isWithinDirectory("poll/a..b", "poll", '/'));
        assertTrue(GenericFileHelper.isWithinDirectory("poll/...txt", "poll", '/'));
        assertTrue(GenericFileHelper.isWithinDirectory("poll/..txt", "poll", '/'));
    }

    @Test
    public void compactPathForContainmentGivesNoMeaningToPrefixes() {
        // a scheme or a drive is a segment like any other, as it is to a server
        assertEquals("../other/secret.txt", GenericFileHelper.compactPathForContainment("http:/../../other/secret.txt", '/'));
        assertEquals("../other/secret.txt", GenericFileHelper.compactPathForContainment("https:/../../other/secret.txt", '/'));
        assertEquals("../other/secret.txt", GenericFileHelper.compactPathForContainment("file:/../../other/secret.txt", '/'));
        // file:.. is a name, not a .. segment, as it is to a server
        assertEquals("secret.txt", GenericFileHelper.compactPathForContainment("file:../../secret.txt", '/'));
        assertEquals("../secret.txt", GenericFileHelper.compactPathForContainment("file:../../../secret.txt", '/'));
        assertEquals("../other/secret.txt",
                GenericFileHelper.compactPathForContainment("classpath:/../../other/secret.txt", '/'));
        assertEquals("../other/secret.txt", GenericFileHelper.compactPathForContainment("a:/../../other/secret.txt", '/'));
        assertEquals("../other/secret.txt", GenericFileHelper.compactPathForContainment("C:/../../other/secret.txt", '/'));
        assertEquals("../other/secret.txt", GenericFileHelper.compactPathForContainment("HTTP:/../../other/secret.txt", '/'));
        // both separators separate segments
        assertEquals("../other/secret.txt",
                GenericFileHelper.compactPathForContainment("http:\\..\\..\\other\\secret.txt", '/'));
        assertEquals("..\\other\\secret.txt",
                GenericFileHelper.compactPathForContainment("sub/../../other/secret.txt", '\\'));

        // a .. that stays inside, and . and empty segments
        assertEquals("poll/secret.txt", GenericFileHelper.compactPathForContainment("poll/sub/../secret.txt", '/'));
        assertEquals("poll/secret.txt", GenericFileHelper.compactPathForContainment("poll/./sub//../secret.txt", '/'));
        assertEquals("http:/secret.txt", GenericFileHelper.compactPathForContainment("http:/sub/../secret.txt", '/'));
        // a leading separator is kept, and a .. above it too
        assertEquals("/poll/secret.txt", GenericFileHelper.compactPathForContainment("/poll/secret.txt", '/'));
        assertEquals("/../secret.txt", GenericFileHelper.compactPathForContainment("/../secret.txt", '/'));
        assertEquals("", GenericFileHelper.compactPathForContainment("", '/'));
        assertEquals("", GenericFileHelper.compactPathForContainment("sub/..", '/'));
        assertNull(GenericFileHelper.compactPathForContainment(null, '/'));
    }

    @Test
    public void prefixedPathsEscapingTheDirectoryAreNotContained() {
        String[] escaping = {
                "http:/../../other/secret.txt", "https:/../../other/secret.txt", "file:/../../other/secret.txt",
                "file:../../../other/secret.txt", "classpath:/../../other/secret.txt", "a:/../../other/secret.txt",
                "C:/../../other/secret.txt", "HTTP:/../../other/secret.txt", "http:\\..\\..\\other\\secret.txt",
                "sub/../../other/secret.txt", "../secret.txt" };
        for (String name : escaping) {
            // no directory configured
            assertFalse(GenericFileHelper.isWithinRemoteDirectory(name, ""), name);
            assertFalse(GenericFileHelper.isWithinRemoteDirectory(name, null), name);
            assertFalse(GenericFileHelper.isWithinDirectory(
                    GenericFileHelper.compactPathForContainment(name, '/'), "", '/'), name);
            // a directory configured
            assertFalse(GenericFileHelper.isWithinRemoteDirectory("poll/" + name, "poll"), name);
        }
    }

    @Test
    public void namesThatStayInsideAreContained() {
        String[] inside = {
                "secret.txt", "a:b.txt", "report 10:30.txt", "file:x.txt", "http:x.txt", "a..b", "...txt", "sub/../x",
                "sub/file.txt", "http:/secret.txt", "C:/data/x.txt", "sub\\file.txt", "sub/a\\b.txt" };
        for (String name : inside) {
            assertTrue(GenericFileHelper.isWithinRemoteDirectory(name, ""), name);
            assertTrue(GenericFileHelper.isWithinRemoteDirectory("poll/" + name, "poll"), name);
            assertTrue(GenericFileHelper.isWithinRemoteDirectory("/poll/" + name, "/poll/"), name);
        }
        // a sibling whose name merely extends the directory name is not contained
        assertFalse(GenericFileHelper.isWithinRemoteDirectory("pollute/file.txt", "poll"));
        assertFalse(GenericFileHelper.isWithinRemoteDirectory(null, "poll"));
    }

    @Test
    public void remotePathWithBackslashAndParentSegmentIsAmbiguous() {
        // the FTP client on Linux compacts a path on / only, so a\b is one segment that one .. removes, and a server
        // may take a\b as one name too, but a server on Windows takes it as two segments
        assertTrue(GenericFileHelper.isAmbiguousRemotePath("poll/a\\b/../../secret.txt"));
        assertTrue(GenericFileHelper.isAmbiguousRemotePath("a\\b/../../secret.txt"));
        // the FTP client on Windows compacts a path on \ only, so poll/a/b is one segment that one .. removes
        assertTrue(GenericFileHelper.isAmbiguousRemotePath("poll/a/b\\..\\secret.txt"));
        assertTrue(GenericFileHelper.isAmbiguousRemotePath("sub\\..\\x.txt"));
        // a backslash without a .. segment, and a .. segment without a backslash
        assertFalse(GenericFileHelper.isAmbiguousRemotePath("poll/a\\b.txt"));
        assertFalse(GenericFileHelper.isAmbiguousRemotePath("poll/a\\b/c.txt"));
        assertFalse(GenericFileHelper.isAmbiguousRemotePath("poll/a/../b.txt"));
        assertFalse(GenericFileHelper.isAmbiguousRemotePath("poll/a\\b..c.txt"));
        assertFalse(GenericFileHelper.isAmbiguousRemotePath(null));

        // a backslash separates segments, as / does, when the backslashes are replaced by / before the path is used
        assertTrue(GenericFileHelper.isWithinRemoteDirectory("poll/a\\b/../../poll/secret.txt", "poll"));
        assertTrue(GenericFileHelper.isWithinRemoteDirectory("poll/sub\\..\\x.txt", "poll"));
        assertFalse(GenericFileHelper.isWithinRemoteDirectory("poll/a\\..\\..\\b.txt", "poll"));
    }

    @Test
    public void shouldEncodeStreamBodyWithCharsetWhileReading() throws Exception {
        // longer than the internal character buffer, with characters that are encoded differently in the charsets
        String text = "\u00e6\u00f8\u00e5 \u00a9 ".repeat(2000);

        try (CamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty(Exchange.CHARSET_NAME, StandardCharsets.UTF_8.name());
            exchange.getIn().setBody(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

            try (InputStream is = GenericFileHelper.toInputStream(exchange, StandardCharsets.ISO_8859_1.name())) {
                // the body is encoded while it is read, instead of being loaded into memory as a whole
                assertInstanceOf(IOHelper.ReaderInputStream.class, is);
                assertArrayEquals(text.getBytes(StandardCharsets.ISO_8859_1), is.readAllBytes());
            }
        }
    }

    @Test
    public void shouldEncodeStreamBodyWithCharsetThatWritesByteOrderMark() throws Exception {
        // longer than the internal character buffer, so the body is encoded in several chunks
        String text = "a\u00e6".repeat(4000);

        try (CamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty(Exchange.CHARSET_NAME, StandardCharsets.UTF_8.name());
            exchange.getIn().setBody(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

            try (InputStream is = GenericFileHelper.toInputStream(exchange, StandardCharsets.UTF_16.name())) {
                // the byte order mark is written once, at the start
                assertArrayEquals(text.getBytes(StandardCharsets.UTF_16), is.readAllBytes());
            }
        }
    }

    @Test
    public void pathWithoutLinksIsBelowTheDirectoryWithoutResolving(@TempDir Path tmp) throws IOException {
        Path inbox = Files.createDirectories(tmp.resolve("inbox"));
        Files.createFile(inbox.resolve("hello.txt"));
        Files.createDirectories(inbox.resolve("sub"));

        assertTrue(GenericFileHelper.isBelowWithoutLinks(inbox, inbox));
        assertTrue(GenericFileHelper.isBelowWithoutLinks(inbox.resolve("hello.txt"), inbox));
        assertTrue(GenericFileHelper.isBelowWithoutLinks(inbox.resolve("sub/hello.txt"), inbox));
        // the default move folder that does not exist yet
        assertTrue(GenericFileHelper.isBelowWithoutLinks(inbox.resolve(".camel/hello.txt"), inbox));

        // not below the directory as written, or with a . or .. segment: the links must be resolved to tell
        assertFalse(GenericFileHelper.isBelowWithoutLinks(tmp.resolve("outside/hello.txt"), inbox));
        assertFalse(GenericFileHelper.isBelowWithoutLinks(inbox.resolve("sub/../hello.txt"), inbox));
        assertFalse(GenericFileHelper.isBelowWithoutLinks(inbox.resolve("./hello.txt"), inbox));
    }

    @Test
    public void pathThroughLinkIsResolved(@TempDir Path tmp) throws IOException {
        Path inbox = Files.createDirectories(tmp.resolve("inbox"));
        Path sub = Files.createDirectories(inbox.resolve("sub"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Path camel = inbox.resolve(".camel");
        Path inner = inbox.resolve("inner");
        Path startLink = tmp.resolve("start");
        try {
            Files.createSymbolicLink(camel, outside);
            Files.createSymbolicLink(inner, sub);
            Files.createSymbolicLink(startLink, inbox);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("Symbolic links are not supported on this platform: " + e.getMessage());
        }

        // a link below the directory needs resolving: one resolving outside is refused, one resolving inside is not
        assertFalse(GenericFileHelper.isBelowWithoutLinks(camel.resolve("hello.txt"), inbox));
        assertFalse(GenericFileHelper.isWithinDirectoryResolvingLinks(camel.resolve("hello.txt"), inbox));
        assertFalse(GenericFileHelper.isBelowWithoutLinks(inner.resolve("hello.txt"), inbox));
        assertTrue(GenericFileHelper.isWithinDirectoryResolvingLinks(inner.resolve("hello.txt"), inbox));

        // a directory that is itself a link: the paths below it resolve within it
        assertTrue(GenericFileHelper.isBelowWithoutLinks(startLink.resolve("hello.txt"), startLink));
        assertTrue(GenericFileHelper.isWithinDirectoryResolvingLinks(startLink.resolve("hello.txt"), startLink));
        assertFalse(GenericFileHelper.isWithinDirectoryResolvingLinks(startLink.resolve(".camel/hello.txt"), startLink));
    }
}
