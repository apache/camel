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
package org.apache.camel.dsl.jbang.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Release gate for the native {@code camel.exe} packaged into the launcher distribution. Runs during {@code verify}
 * when {@code -Dcamel.launcher.requireWindowsExe=true} is set on a Windows x64 host.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "camel.launcher.requireWindowsExe", matches = "true")
class CamelLauncherWindowsExeIT {

    private static final Path TARGET = Paths.get("target");
    private static final Path STAGED_EXE = TARGET.resolve("camel.exe");

    @Test
    void stagedCamelExeExists() {
        assertTrue(Files.isRegularFile(STAGED_EXE),
                "target/camel.exe must be present before packaging the launcher distribution");
    }

    @Test
    void binArchiveIncludesCamelExe() throws IOException {
        Path zip = findBinZip();
        assertNotNull(zip, "camel-launcher-*-bin.zip must be produced by the assembly plugin");

        try (ZipFile archive = new ZipFile(zip.toFile())) {
            // maven-assembly-plugin defaults includeBaseDirectory to true, so entries are
            // nested under camel-launcher-<version>/, not at the archive root.
            ZipEntry entry = archive.stream()
                    .filter(e -> e.getName().endsWith("/bin/camel.exe"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(entry, "bin/camel.exe must be included in " + zip.getFileName());
            assertTrue(entry.getSize() > 0, "bin/camel.exe must not be empty");
        }
    }

    private static Path findBinZip() throws IOException {
        try (Stream<Path> paths = Files.list(TARGET)) {
            return paths
                    .filter(p -> p.getFileName().toString().matches("camel-launcher-.*-bin\\.zip"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
