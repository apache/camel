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
package org.apache.camel.component.google.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GoogleCloudStorageFileNameHelperTest {

    private static final String DIR = "target/gcs-download";

    @Test
    void plainObjectNameIsAccepted() {
        assertThatCode(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(DIR, DIR + "/file.txt", "file.txt"))
                .doesNotThrowAnyException();
    }

    @Test
    void nestedObjectNameIsAccepted() {
        // GCS object names commonly use / as a pseudo-directory separator, so nesting must keep working
        assertThatCode(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(DIR, DIR + "/a/b/c.txt", "a/b/c.txt"))
                .doesNotThrowAnyException();
    }

    @Test
    void objectNameNormalizingBackInsideIsAccepted() {
        assertThatCode(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(DIR, DIR + "/a/../b.txt", "a/../b.txt"))
                .doesNotThrowAnyException();
    }

    @Test
    void downloadDirectoryItselfIsAccepted() {
        assertThatCode(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(DIR, DIR, ""))
                .doesNotThrowAnyException();
    }

    @Test
    void parentDirectorySegmentIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(DIR, DIR + "/../escape.txt",
                        "../escape.txt"))
                .withMessageContaining("../escape.txt")
                .withMessageContaining(DIR);
    }

    @Test
    void repeatedParentDirectorySegmentsAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(DIR,
                        DIR + "/../../../etc/pwn.txt", "../../../etc/pwn.txt"));
    }

    @Test
    void parentDirectorySegmentNestedInsideObjectNameIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(DIR,
                        DIR + "/a/../../escape.txt", "a/../../escape.txt"));
    }

    @Test
    void siblingDirectoryMerelyExtendingTheNameIsRejected() {
        // guards against a plain String.startsWith check, which would wrongly accept this
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(DIR,
                        DIR + "-evil/file.txt", "../gcs-download-evil/file.txt"));
    }

    @Test
    void staticDirectoryPrefixIsTheDirectoryBeforeTheExpression() {
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("/tmp/downloads/${file:name}"))
                .isEqualTo("/tmp/downloads");
    }

    @Test
    void staticDirectoryPrefixIsTrimmedBackToARealDirectory() {
        // /tmp/down is a partial path segment, not a directory, so it must not be used as the containment directory
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("/tmp/down${file:name}"))
                .isEqualTo("/tmp");
    }

    @Test
    void staticDirectoryPrefixCanBeTheFilesystemRoot() {
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("/${file:name}"))
                .isEqualTo("/");
    }

    @Test
    void staticDirectoryPrefixOfAFullyDynamicValueIsEmpty() {
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("${file:name}")).isEmpty();
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("${header.dir}/file.txt")).isEmpty();
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("prefix-${file:name}")).isEmpty();
    }

    @Test
    void staticDirectoryPrefixStopsAtTheFirstExpressionToken() {
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("target/a/${header.dir}/${file:name}"))
                .isEqualTo("target/a");
    }

    @Test
    void staticDirectoryPrefixKeepsTheSeparatorOfAWindowsDriveRoot() {
        // C: alone is drive-relative (the current directory on that drive), not the root
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("C:\\${file:name}")).isEqualTo("C:\\");
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("C:/${file:name}")).isEqualTo("C:/");
        assertThat(GoogleCloudStorageFileNameHelper.staticDirectoryPrefix("C:/data/${file:name}")).isEqualTo("C:/data");
    }

    @Test
    void relativeObjectNamesAreSafe() {
        assertThatCode(() -> {
            GoogleCloudStorageFileNameHelper.assertSafeObjectName("file.txt");
            GoogleCloudStorageFileNameHelper.assertSafeObjectName("a/b/c.txt");
            GoogleCloudStorageFileNameHelper.assertSafeObjectName("a/./b.txt");
            // .. only matters as a whole path segment
            GoogleCloudStorageFileNameHelper.assertSafeObjectName("..hidden");
            GoogleCloudStorageFileNameHelper.assertSafeObjectName("a..b/c..");
        }).doesNotThrowAnyException();
    }

    @Test
    void objectNamesWithAParentSegmentAreNotSafe() {
        for (String objectName : new String[] { "..", "../x", "a/../b.txt", "a/..", "a\\..\\b.txt" }) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertSafeObjectName(objectName))
                    .withMessageContaining(objectName)
                    .withMessageContaining("'..' path segment");
        }
    }

    @Test
    void absoluteObjectNamesAreNotSafe() {
        for (String objectName : new String[] { "/etc/passwd", "\\x", "\\\\server\\share\\x", "C:\\x", "C:x", "c:/x" }) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertSafeObjectName(objectName))
                    .withMessageContaining(objectName)
                    .withMessageContaining("absolute path");
        }
    }

    @Test
    void relativePathInsideTheWorkingDirectoryIsAccepted() {
        assertThatCode(() -> {
            GoogleCloudStorageFileNameHelper.assertWithinWorkingDirectory("file.txt", "file.txt");
            GoogleCloudStorageFileNameHelper.assertWithinWorkingDirectory("a/../b.txt", "b.txt");
            GoogleCloudStorageFileNameHelper.assertWithinWorkingDirectory("/abs/dir/file.txt", "file.txt");
        }).doesNotThrowAnyException();
    }

    @Test
    void relativePathClimbingOutOfTheWorkingDirectoryIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertWithinWorkingDirectory("../file.txt",
                        "./file.txt"))
                .withMessageContaining("./file.txt")
                .withMessageContaining("working directory");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertWithinWorkingDirectory("a/../../file.txt",
                        "file.txt"));
    }

    @Test
    void symbolicLinkResolvingOutsideDirectoryIsRejected(@TempDir Path parent) throws IOException {
        Path downloadDir = Files.createDirectory(parent.resolve("downloads"));
        Path outsideDir = Files.createDirectory(parent.resolve("outside"));
        Path linkedPath = Files.createSymbolicLink(downloadDir.resolve("linked"), outsideDir);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(
                        downloadDir.toString(), linkedPath.resolve("file.txt").toString(), "linked/file.txt"))
                .withMessageContaining("linked/file.txt")
                .withMessageContaining(downloadDir.toString());
    }

    @Test
    void parentSegmentAfterSymbolicLinkIsResolvedByFilesystem(@TempDir Path parent) throws IOException {
        Path downloadDir = Files.createDirectory(parent.resolve("downloads"));
        Path outsideDir = Files.createDirectories(parent.resolve("outside/child"));
        Path linkedPath = Files.createSymbolicLink(downloadDir.resolve("linked"), outsideDir);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> GoogleCloudStorageFileNameHelper.assertWithinDirectory(
                        downloadDir.toString(), linkedPath.resolve("../file.txt").toString(), "linked/../file.txt"))
                .withMessageContaining("linked/../file.txt")
                .withMessageContaining(downloadDir.toString());
    }
}
