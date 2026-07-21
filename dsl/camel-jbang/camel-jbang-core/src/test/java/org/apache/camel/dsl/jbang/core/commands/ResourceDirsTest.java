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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceDirsTest {

    @Test
    void validateShouldRejectAbsolutePath() {
        assertThatThrownBy(() -> Run.validateResourceDir("/some/absolute/path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only accepts relative paths");
    }

    @Test
    void validateShouldRejectNonDirectory(@TempDir Path tempDir) throws Exception {
        // create a regular file inside tempDir, then use a relative path to it
        Path file = tempDir.resolve("notadir.txt");
        Files.writeString(file, "hello");

        // use relative path from cwd to the file
        Path relative = Path.of("").toAbsolutePath().relativize(file);
        assertThatThrownBy(() -> Run.validateResourceDir(relative.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void validateShouldAcceptRelativeDir(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("soap");
        Files.createDirectories(dir);

        // use relative path from cwd to the dir
        Path relative = Path.of("").toAbsolutePath().relativize(dir);
        Path result = Run.validateResourceDir(relative.toString());
        assertThat(result).isNotNull();
    }

    @Test
    void validateShouldAcceptDotDotPaths(@TempDir Path tempDir) throws Exception {
        // create project/child and shared dirs, use ../shared from child's perspective
        Path project = tempDir.resolve("project/child");
        Path shared = tempDir.resolve("shared");
        Files.createDirectories(project);
        Files.createDirectories(shared);

        // use relative path from cwd — this will contain .. segments
        Path relative = Path.of("").toAbsolutePath().relativize(shared);
        Path result = Run.validateResourceDir(relative.toString());
        assertThat(result).isNotNull();
    }

    @Test
    void walkShouldFindFilesRecursively(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("soap");
        Path subDir = tempDir.resolve("soap/schemas");
        Files.createDirectories(subDir);
        Files.writeString(dir.resolve("file.xsd"), "<xsd/>");
        Files.writeString(subDir.resolve("common.xsd"), "<xsd/>");

        List<Path> files = Run.walkResourceDir(dir);
        assertThat(files).hasSize(2);
        assertThat(files).extracting(Path::getFileName).extracting(Path::toString)
                .containsExactlyInAnyOrder("file.xsd", "common.xsd");
    }

    @Test
    void walkShouldEnforceMaxFileLimit(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("big");
        Files.createDirectories(dir);

        for (int i = 0; i < Run.MAX_RESOURCE_DIR_FILES + 1; i++) {
            Files.writeString(dir.resolve("file" + i + ".txt"), "content");
        }

        assertThatThrownBy(() -> Run.walkResourceDir(dir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the limit");
    }

    @Test
    void parentPathShouldMapToLastComponent(@TempDir Path tempDir) throws Exception {
        // simulate ../shared/schemas structure where only the last dir name is used as target
        Path shared = tempDir.resolve("shared/schemas");
        Files.createDirectories(shared);
        Files.writeString(shared.resolve("common.xsd"), "<xsd/>");

        Path dirPath = shared;
        Path baseName = dirPath.getFileName();
        assertThat(baseName.toString()).isEqualTo("schemas");

        // verify target path uses only the last component
        Path srcResources = tempDir.resolve("export/src/main/resources");
        Files.createDirectories(srcResources);

        for (Path source : Run.walkResourceDir(dirPath)) {
            Path relativePath = dirPath.relativize(source);
            Path target = srcResources.resolve(baseName).resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        }

        // ../shared/schemas/common.xsd -> src/main/resources/schemas/common.xsd
        assertThat(srcResources.resolve("schemas/common.xsd")).exists();
    }

    @Test
    void nestedParentPathShouldMapToLastComponent() {
        // ../../mydata/foo -> last component is "foo"
        Path path = Path.of("../../mydata/foo").normalize();
        assertThat(path.getFileName().toString()).isEqualTo("foo");
    }

    @Test
    void deepSubdirectoryShouldPreserveStructure(@TempDir Path tempDir) throws Exception {
        // create a deep structure: soap/types/common/base.xsd
        Path dir = tempDir.resolve("soap");
        Path deep = tempDir.resolve("soap/types/common");
        Files.createDirectories(deep);
        Files.writeString(dir.resolve("service.wsdl"), "<wsdl/>");
        Files.writeString(deep.resolve("base.xsd"), "<xsd/>");

        Path baseName = dir.getFileName();
        Path srcResources = tempDir.resolve("export/src/main/resources");
        Files.createDirectories(srcResources);

        for (Path source : Run.walkResourceDir(dir)) {
            Path relativePath = dir.relativize(source);
            Path target = srcResources.resolve(baseName).resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        }

        assertThat(srcResources.resolve("soap/service.wsdl")).exists();
        assertThat(srcResources.resolve("soap/types/common/base.xsd")).exists();
    }
}
