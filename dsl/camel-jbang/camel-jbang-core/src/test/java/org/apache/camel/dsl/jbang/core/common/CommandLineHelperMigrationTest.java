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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the one-time migration of the legacy user configuration file ({@value CommandLineHelper#LEGACY_USER_CONFIG})
 * to the current name ({@value CommandLineHelper#USER_CONFIG}). The migration must be idempotent, must preserve the
 * current file on conflicts, and must be a no-op when nothing is present.
 */
@Isolated
class CommandLineHelperMigrationTest {

    private String originalHome;

    @AfterEach
    void tearDown() {
        if (originalHome != null) {
            CommandLineHelper.useHomeDir(originalHome);
        }
    }

    private void useHome(Path dir) {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(dir.toString());
    }

    @Test
    void renamesLegacyHomeFileToNewName(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path legacy = tempDir.resolve(CommandLineHelper.LEGACY_USER_CONFIG);
        Path current = tempDir.resolve(CommandLineHelper.USER_CONFIG);
        Files.writeString(legacy, "camel.tui.theme=light\n");

        CommandLineHelper.migrateLegacyUserConfig();

        assertFalse(Files.exists(legacy), "legacy file should have been renamed away");
        assertTrue(Files.exists(current), "new file should exist after migration");
        assertEquals("camel.tui.theme=light\n", Files.readString(current), "content must be preserved verbatim");
    }

    @Test
    void mergesLegacyIntoCurrentWhenCurrentIsNewerOrSameAge(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path legacy = tempDir.resolve(CommandLineHelper.LEGACY_USER_CONFIG);
        Path current = tempDir.resolve(CommandLineHelper.USER_CONFIG);
        Files.writeString(legacy, "camel.tui.theme=light\nold.key=legacy\n");
        Files.writeString(current, "camel.tui.theme=dark\n");

        CommandLineHelper.migrateLegacyUserConfig();

        assertFalse(Files.exists(legacy), "legacy file should be removed after merge");
        String content = Files.readString(current);
        assertTrue(content.contains("camel.tui.theme=dark"), "current file must win on conflicting keys");
        assertTrue(content.contains("old.key=legacy"), "legacy-only keys must be merged into the current file");
    }

    @Test
    void leavesLegacyAloneWhenItIsNewer(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path legacy = tempDir.resolve(CommandLineHelper.LEGACY_USER_CONFIG);
        Path current = tempDir.resolve(CommandLineHelper.USER_CONFIG);
        Files.writeString(current, "camel.tui.theme=dark\n");
        Files.setLastModifiedTime(current, FileTime.fromMillis(1000));
        Files.writeString(legacy, "camel.tui.theme=light\nold.key=legacy\n");
        Files.setLastModifiedTime(legacy, FileTime.fromMillis(5000));

        CommandLineHelper.migrateLegacyUserConfig();

        assertTrue(Files.exists(legacy), "legacy file must remain when it is newer");
        assertTrue(Files.exists(current), "current file must remain when it is older");
        assertEquals("camel.tui.theme=light\nold.key=legacy\n", Files.readString(legacy));
        assertEquals("camel.tui.theme=dark\n", Files.readString(current));
    }

    @Test
    void printsDiagnosticMessages(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path legacy = tempDir.resolve(CommandLineHelper.LEGACY_USER_CONFIG);
        Files.writeString(legacy, "camel.tui.theme=light\n");
        CapturingPrinter printer = new CapturingPrinter();

        CommandLineHelper.migrateLegacyUserConfig(printer);

        assertTrue(printer.lines.stream().anyMatch(line -> line.contains("Migrated legacy settings")));
    }

    @Test
    void noOpWhenNeitherFilePresent(@TempDir Path tempDir) {
        useHome(tempDir);

        assertDoesNotThrow(() -> CommandLineHelper.migrateLegacyUserConfig());
        assertFalse(Files.exists(tempDir.resolve(CommandLineHelper.USER_CONFIG)),
                "no new file should be created when there is nothing to migrate");
    }

    @Test
    void renamesLegacyLocalFileToNewName(@TempDir Path tempDir) throws Exception {
        // the historical local file lived in the working directory under the visible (no leading dot) name
        useHome(tempDir);
        Path legacy = Path.of(CommandLineHelper.LEGACY_LOCAL_USER_CONFIG);
        Path current = Path.of(CommandLineHelper.LOCAL_USER_CONFIG);
        Files.deleteIfExists(legacy);
        Files.deleteIfExists(current);
        Files.writeString(legacy, "camel.tui.theme=light\n");
        try {
            CommandLineHelper.migrateLegacyUserConfig();

            assertFalse(Files.exists(legacy), "legacy local file should have been renamed away");
            assertTrue(Files.exists(current), "new local file should exist after migration");
            assertEquals("camel.tui.theme=light\n", Files.readString(current), "content must be preserved verbatim");
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(current);
        }
    }

    @Test
    void isIdempotentOnRepeatedRuns(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path legacy = tempDir.resolve(CommandLineHelper.LEGACY_USER_CONFIG);
        Path current = tempDir.resolve(CommandLineHelper.USER_CONFIG);
        Files.writeString(legacy, "camel.tui.theme=light\n");

        CommandLineHelper.migrateLegacyUserConfig();
        CommandLineHelper.migrateLegacyUserConfig();

        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(current));
        assertEquals("camel.tui.theme=light\n", Files.readString(current));
    }

    private static final class CapturingPrinter implements Printer {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void println() {
            lines.add("");
        }

        @Override
        public void println(String line) {
            lines.add(line);
        }

        @Override
        public void print(String output) {
            lines.add(output);
        }

        @Override
        public void printf(String format, Object... args) {
            lines.add(String.format(format, args));
        }
    }
}
