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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFileOpsTest {

    @TempDir
    Path dir;

    @Test
    void validateNameRejectsEmptyDotsAndSeparators() {
        assertThat(SourceFileOps.validateName("route.yaml")).isNull();
        assertThat(SourceFileOps.validateName("")).isNotNull();
        assertThat(SourceFileOps.validateName("  ")).isNotNull();
        assertThat(SourceFileOps.validateName(".")).isNotNull();
        assertThat(SourceFileOps.validateName("..")).isNotNull();
        assertThat(SourceFileOps.validateName("a/b")).isNotNull();
        assertThat(SourceFileOps.validateName("a\\b")).isNotNull();
    }

    @Test
    void suggestDuplicatePreservesCompoundExtension() {
        assertThat(SourceFileOps.suggestDuplicateName("route.camel.yaml")).isEqualTo("route-copy.camel.yaml");
        assertThat(SourceFileOps.suggestDuplicateName("notes")).isEqualTo("notes-copy");
        assertThat(SourceFileOps.suggestDuplicateName(".hidden")).isEqualTo(".hidden-copy");
    }

    @Test
    void createFileCreatesEmptyFile() throws IOException {
        Path p = SourceFileOps.createFile(dir, "new.camel.yaml");
        assertThat(p).exists().hasFileName("new.camel.yaml");
        assertThat(Files.readString(p)).isEmpty();
    }

    @Test
    void createFileRejectsDuplicate() throws IOException {
        SourceFileOps.createFile(dir, "dup.yaml");
        assertThatThrownBy(() -> SourceFileOps.createFile(dir, "dup.yaml"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Already exists");
    }

    @Test
    void createFolderCreatesDirectory() throws IOException {
        Path p = SourceFileOps.createFolder(dir, "sub");
        assertThat(p).isDirectory();
    }

    @Test
    void renameMovesFile() throws IOException {
        Path src = Files.writeString(dir.resolve("old.yaml"), "hi", StandardCharsets.UTF_8);
        Path p = SourceFileOps.rename(src, "new.yaml");
        assertThat(src).doesNotExist();
        assertThat(p).exists().hasFileName("new.yaml");
        assertThat(Files.readString(p)).isEqualTo("hi");
    }

    @Test
    void renameRejectsExistingTarget() throws IOException {
        Path src = Files.writeString(dir.resolve("a.yaml"), "a", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("b.yaml"), "b", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SourceFileOps.rename(src, "b.yaml"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Already exists");
    }

    @Test
    void copyDuplicatesContent() throws IOException {
        Path src = Files.writeString(dir.resolve("a.yaml"), "body", StandardCharsets.UTF_8);
        Path p = SourceFileOps.copy(src, "a-copy.yaml");
        assertThat(src).exists();
        assertThat(p).exists();
        assertThat(Files.readString(p)).isEqualTo("body");
    }

    @Test
    void deleteRemovesFile() throws IOException {
        Path src = Files.writeString(dir.resolve("gone.yaml"), "x", StandardCharsets.UTF_8);
        SourceFileOps.delete(src);
        assertThat(src).doesNotExist();
    }

    @Test
    void deleteRemovesEmptyDirectory() throws IOException {
        Path sub = Files.createDirectory(dir.resolve("empty"));
        SourceFileOps.delete(sub);
        assertThat(sub).doesNotExist();
    }

    @Test
    void deleteRefusesNonEmptyDirectory() throws IOException {
        Path sub = Files.createDirectory(dir.resolve("full"));
        Files.writeString(sub.resolve("child.yaml"), "x", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SourceFileOps.delete(sub))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not empty");
        assertThat(sub).isDirectory();
    }

    @Test
    void createRejectsInvalidName() {
        assertThatThrownBy(() -> SourceFileOps.createFile(dir, "a/b.yaml"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateNameRejectsAbsoluteAndTraversalPaths() {
        assertThat(SourceFileOps.validateName("/usr/evil/xxx")).isNotNull();
        assertThat(SourceFileOps.validateName("../../etc/passwd")).isNotNull();
        assertThat(SourceFileOps.validateName("C:\\Windows\\evil")).isNotNull();
        assertThat(SourceFileOps.validateName("foo:bar")).isNotNull();
    }

    @Test
    void validateNameRejectsControlCharsAndOverlongNames() {
        assertThat(SourceFileOps.validateName("a\u0000b")).isNotNull();
        assertThat(SourceFileOps.validateName("a\tb")).isNotNull();
        assertThat(SourceFileOps.validateName("x".repeat(256))).isNotNull();
        assertThat(SourceFileOps.validateName("x".repeat(255))).isNull();
    }

    @Test
    void createFolderRejectsAbsolutePath() {
        assertThatThrownBy(() -> SourceFileOps.createFolder(dir, "/usr/evil/xxx"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dir.resolve("usr")).doesNotExist();
    }

    @Test
    void createFolderStaysInsideDir() throws IOException {
        Path p = SourceFileOps.createFolder(dir, "sub");
        assertThat(p.toAbsolutePath().normalize().getParent())
                .isEqualTo(dir.toAbsolutePath().normalize());
    }
}
