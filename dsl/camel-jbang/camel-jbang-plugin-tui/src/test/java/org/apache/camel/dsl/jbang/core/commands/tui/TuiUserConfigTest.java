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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the per-key local/global routing in {@link TuiUserConfig}. These tests encode the two Codex adversarial
 * review findings as regression guards:
 * <ol>
 * <li>A local config that only carries non-TUI keys must not shadow global TUI preferences.</li>
 * <li>Writing a new TUI key must go to the global file, not into the project-local config.</li>
 * </ol>
 */
@Isolated
class TuiUserConfigTest {

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
    void readFallsBackToGlobalWhenKeyAbsentFromLocal(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path globalFile = tempDir.resolve(CommandLineHelper.USER_CONFIG);
        Files.writeString(globalFile, "camel.tui.theme=light\n");

        Path localFile = Path.of(CommandLineHelper.LOCAL_USER_CONFIG);
        try {
            Files.writeString(localFile, "camel.jbang.runtime=spring-boot\n");

            assertEquals("light", TuiUserConfig.read("camel.tui.theme"),
                    "global theme must not be shadowed by an unrelated local config file");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    @Test
    void readPrefersLocalWhenKeyExistsLocally(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path globalFile = tempDir.resolve(CommandLineHelper.USER_CONFIG);
        Files.writeString(globalFile, "camel.tui.startTab=Overview\n");

        Path localFile = Path.of(CommandLineHelper.LOCAL_USER_CONFIG);
        try {
            Files.writeString(localFile, "camel.tui.startTab=Health\n");

            assertEquals("Health", TuiUserConfig.read("camel.tui.startTab"),
                    "a local override must win over the global value");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    @Test
    void writeRoutesToGlobalForNewKey(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path localFile = Path.of(CommandLineHelper.LOCAL_USER_CONFIG);
        try {
            Files.writeString(localFile, "camel.jbang.runtime=spring-boot\n");

            TuiUserConfig.write("camel.tui.theme", "dark");

            String localContent = Files.readString(localFile);
            assertFalse(localContent.contains("camel.tui.theme"),
                    "writing a new TUI key must NOT dirty the project-local config");
            Path globalFile = tempDir.resolve(CommandLineHelper.USER_CONFIG);
            assertTrue(Files.exists(globalFile), "the global file must be created");
            String globalContent = Files.readString(globalFile);
            assertTrue(globalContent.contains("camel.tui.theme=dark"),
                    "the new key must land in the global file");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    @Test
    void writeUpdatesLocalForExistingLocalKey(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path localFile = Path.of(CommandLineHelper.LOCAL_USER_CONFIG);
        try {
            Files.writeString(localFile, "camel.tui.startTab=Health\n");

            TuiUserConfig.write("camel.tui.startTab", "Trace");

            String localContent = Files.readString(localFile);
            assertTrue(localContent.contains("camel.tui.startTab=Trace"),
                    "an existing local override must be updated in the local file");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    @Test
    void readReturnsNullWhenKeyAbsentFromBothFiles(@TempDir Path tempDir) {
        useHome(tempDir);
        assertNull(TuiUserConfig.read("camel.tui.theme"));
    }

    @Test
    void writeBlankRemovesKey(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path globalFile = tempDir.resolve(CommandLineHelper.USER_CONFIG);
        Files.writeString(globalFile, "camel.tui.theme=dark\n");

        TuiUserConfig.write("camel.tui.theme", "   ");

        String content = Files.readString(globalFile);
        assertFalse(content.contains("camel.tui.theme"), "a blank value must remove the key");
    }
}
