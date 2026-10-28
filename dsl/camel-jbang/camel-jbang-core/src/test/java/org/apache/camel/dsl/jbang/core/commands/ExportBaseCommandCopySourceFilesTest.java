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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExportBaseCommand#copySourceFiles} focusing on the multi-directory {@code --local-kamelet-dir}
 * handling (CAMEL-26371).
 *
 * <p>
 * Each test constructs a minimal settings file and calls {@code copySourceFiles} directly, bypassing the full Maven
 * export pipeline so the tests run fast and without network access.
 */
class ExportBaseCommandCopySourceFilesTest {

    /** Thin subclass that exposes {@code copySourceFiles} for testing. */
    private static class TestableExport extends ExportCamelMain {
        TestableExport() {
            super(new CamelJBangMain());
            // copySourceFiles calls mavenResolver.repos() and PluginHelper; initialise
            // both so the tests don't need a Maven resolver or plugin infrastructure.
            mavenResolver = new MavenResolverMixin();
            skipPlugins = true;
        }

        void runCopySourceFiles(
                Path settings, Path profile, Path srcJavaDirRoot, Path srcJavaDir,
                Path srcResourcesDir, Path srcCamelResourcesDir, Path srcKameletsResourcesDir)
                throws Exception {
            copySourceFiles(settings, profile, srcJavaDirRoot, srcJavaDir,
                    srcResourcesDir, srcCamelResourcesDir, srcKameletsResourcesDir, null);
        }
    }

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("camel-export-copy-test");
    }

    @AfterEach
    void tearDown() {
        FileUtil.removeDir(tempDir.toFile());
    }

    /**
     * Regression test for CAMEL-26371: when two directories are given in LOCAL_KAMELET_DIR (each prefixed with "file:"
     * as written by Run.createMainInstance), the kamelet that lives in the second directory must be copied to the
     * kamelets resource directory.
     */
    @Test
    void shouldCopyKameletFromSecondLocalKameletDir() throws Exception {
        // Two local-kamelet directories: the kamelet lives only in the second one
        Path dir1 = Files.createTempDirectory(tempDir, "dir1");
        Path dir2 = Files.createTempDirectory(tempDir, "dir2");

        // Place the kamelet file in dir2 only
        String kameletName = "my-source";
        Path kameletFile = dir2.resolve(kameletName + ".kamelet.yaml");
        Files.writeString(kameletFile,
                "apiVersion: camel.apache.org/v1\nkind: Kamelet\nmetadata:\n  name: " + kameletName + "\n");

        // Build a settings file that mirrors what Run.createMainInstance() writes:
        // LOCAL_KAMELET_DIR = "file:<dir1>,file:<dir2>" and "kamelet=<name>"
        Path settings = tempDir.resolve(".camel-jbang-run.properties");
        String localKameletDirValue = "file:" + dir1.toAbsolutePath() + ",file:" + dir2.toAbsolutePath();
        Files.writeString(settings,
                "camel.jbang.localKameletDir=" + localKameletDirValue + "\n"
                                    + "kamelet=" + kameletName + "\n");

        // Create the output directory tree
        Path srcRoot = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path srcResources = Files.createDirectories(tempDir.resolve("src/main/resources"));
        Path srcCamel = Files.createDirectories(srcResources.resolve("camel"));
        Path srcKamelets = Files.createDirectories(srcResources.resolve("kamelets"));

        // A profile file is required by copySourceFiles (to skip copying it); use a dummy path
        Path profile = tempDir.resolve("application.properties");
        Files.writeString(profile, "");

        TestableExport cmd = new TestableExport();
        cmd.runCopySourceFiles(settings, profile, srcRoot, srcRoot, srcResources, srcCamel, srcKamelets);

        // The kamelet file must have been copied into the kamelets resource directory
        assertThat(srcKamelets.resolve(kameletName + ".kamelet.yaml"))
                .as("kamelet from second dir should be copied to kamelets resource directory")
                .isRegularFile();
    }

    /**
     * Sanity check: a single directory (no comma) still works correctly.
     */
    @Test
    void shouldCopyKameletFromSingleLocalKameletDir() throws Exception {
        Path dir1 = Files.createTempDirectory(tempDir, "dir1");

        String kameletName = "my-sink";
        Path kameletFile = dir1.resolve(kameletName + ".kamelet.yaml");
        Files.writeString(kameletFile,
                "apiVersion: camel.apache.org/v1\nkind: Kamelet\nmetadata:\n  name: " + kameletName + "\n");

        Path settings = tempDir.resolve(".camel-jbang-run.properties");
        Files.writeString(settings,
                "camel.jbang.localKameletDir=file:" + dir1.toAbsolutePath() + "\n"
                                    + "kamelet=" + kameletName + "\n");

        Path srcRoot = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path srcResources = Files.createDirectories(tempDir.resolve("src/main/resources"));
        Path srcCamel = Files.createDirectories(srcResources.resolve("camel"));
        Path srcKamelets = Files.createDirectories(srcResources.resolve("kamelets"));
        Path profile = tempDir.resolve("application.properties");
        Files.writeString(profile, "");

        TestableExport cmd = new TestableExport();
        cmd.runCopySourceFiles(settings, profile, srcRoot, srcRoot, srcResources, srcCamel, srcKamelets);

        assertThat(srcKamelets.resolve(kameletName + ".kamelet.yaml"))
                .as("kamelet from single dir should be copied to kamelets resource directory")
                .isRegularFile();
    }
}
