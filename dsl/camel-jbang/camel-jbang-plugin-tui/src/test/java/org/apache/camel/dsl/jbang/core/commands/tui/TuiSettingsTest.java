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
 * Verifies {@link TuiSettings} load/mutate/save semantics against real (isolated) disk I/O: the full round-trip of all
 * fields, blank-clears-the-key behavior, resilience to a missing file, and local-config precedence.
 */
@Isolated
class TuiSettingsTest {

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
    void roundTripPreservesAllFields(@TempDir Path tempDir) {
        useHome(tempDir);

        TuiSettings settings = TuiSettings.load();
        settings.setThemeId("light");
        settings.setStartTab("Health");
        settings.setDefaultFolder("/tmp/projects");
        settings.save();

        TuiSettings loaded = TuiSettings.load();
        assertEquals("light", loaded.getThemeId());
        assertEquals("Health", loaded.getStartTab());
        assertEquals("/tmp/projects", loaded.getDefaultFolder());
    }

    @Test
    void blankFieldRemovesItsKeyButKeepsOthers(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);

        TuiSettings settings = TuiSettings.load();
        settings.setThemeId("dark");
        settings.setDefaultFolder("/tmp/x");
        settings.save();

        TuiSettings second = TuiSettings.load();
        assertEquals("/tmp/x", second.getDefaultFolder());
        second.setDefaultFolder("   ");
        second.save();

        TuiSettings third = TuiSettings.load();
        assertNull(third.getDefaultFolder(), "a blank folder must clear its key");
        assertEquals("dark", third.getThemeId(), "unrelated keys must remain");

        String content = Files.readString(tempDir.resolve(CommandLineHelper.USER_CONFIG));
        assertFalse(content.contains("camel.tui.defaultFolder"), "the cleared key must not be written back");
    }

    @Test
    void missingFileYieldsNullFieldsWithoutThrowing(@TempDir Path tempDir) {
        useHome(tempDir);

        TuiSettings settings = TuiSettings.load();
        assertNull(settings.getThemeId());
        assertNull(settings.getStartTab());
        assertNull(settings.getDefaultFolder());
    }

    @Test
    void defaultFolderIsLoadedWhenPresentAlongsideLastFolder(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Path globalFile = tempDir.resolve(CommandLineHelper.USER_CONFIG);

        Files.writeString(globalFile,
                "camel.tui.defaultFolder=/projects/myapp\ncamel.tui.lastFolder=/tmp/other\n");

        TuiSettings loaded = TuiSettings.load();
        assertEquals("/projects/myapp", loaded.getDefaultFolder());

        Files.writeString(globalFile, "camel.tui.lastFolder=/tmp/other\n");

        TuiSettings withoutDefaultFolder = TuiSettings.load();
        assertNull(withoutDefaultFolder.getDefaultFolder());
    }

    @Test
    void localOverrideIsReadAndWrittenBackLocally(@TempDir Path tempDir) throws Exception {
        // A project has deliberately pinned startTab in the local file.
        // Editing it via the dialog should update the local file, not the global file.
        useHome(tempDir);
        Path localFile = Path.of(CommandLineHelper.LOCAL_USER_CONFIG);
        try {
            Files.writeString(localFile, "camel.tui.startTab=Health\n");
            assertTrue(CommandLineHelper.hasLocalUserConfig());

            TuiSettings loaded = TuiSettings.load();
            assertEquals("Health", loaded.getStartTab(), "load must read the local override");

            loaded.setStartTab("Trace");
            loaded.save();

            String localContent = Files.readString(localFile);
            assertTrue(localContent.contains("camel.tui.startTab=Trace"),
                    "save must write a locally-overridden key back to the local file");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }

    @Test
    void newKeyGoesToGlobalEvenWhenLocalFileExists(@TempDir Path tempDir) throws Exception {
        // A project has a local config for other CLI settings, but no camel.tui.defaultFolder.
        // Saving defaultFolder must NOT dirty the project-local file.
        useHome(tempDir);
        Path localFile = Path.of(CommandLineHelper.LOCAL_USER_CONFIG);
        try {
            Files.writeString(localFile, "camel.jbang.runtime=spring-boot\n");
            assertTrue(CommandLineHelper.hasLocalUserConfig());

            TuiSettings loaded = TuiSettings.load();
            loaded.setDefaultFolder("/tmp/example");
            loaded.save();

            String localContent = Files.readString(localFile);
            assertFalse(localContent.contains("camel.tui.defaultFolder"),
                    "personal TUI state must not be written to the project-local file");
            assertTrue(Files.exists(tempDir.resolve(CommandLineHelper.USER_CONFIG)),
                    "the global file must be created for the personal setting");
            String globalContent = Files.readString(tempDir.resolve(CommandLineHelper.USER_CONFIG));
            assertTrue(globalContent.contains("camel.tui.defaultFolder=/tmp/example"),
                    "personal TUI state must be written to the global file");
        } finally {
            Files.deleteIfExists(localFile);
        }
    }
}
