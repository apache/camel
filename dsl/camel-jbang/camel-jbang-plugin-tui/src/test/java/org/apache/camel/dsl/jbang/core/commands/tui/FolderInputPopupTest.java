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

/**
 * Verifies the pre-filled folder precedence of {@link FolderInputPopup#open()}. The documented contract is that the
 * most recently used folder wins, the configured default folder is used only when there is no remembered folder, and
 * {@code user.dir} is the final fallback.
 */
@Isolated
class FolderInputPopupTest {

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
    void lastFolderWinsOverDefaultFolder(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Files.writeString(tempDir.resolve(CommandLineHelper.USER_CONFIG),
                "camel.tui.defaultFolder=/opt/default\ncamel.tui.lastFolder=/home/user/recent\n");

        FolderInputPopup popup = new FolderInputPopup(null);
        popup.open();

        assertEquals("/home/user/recent", popup.getInputText(),
                "the most recently used folder must take precedence over the configured default");
    }

    @Test
    void defaultFolderUsedWhenNoLastFolder(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        Files.writeString(tempDir.resolve(CommandLineHelper.USER_CONFIG),
                "camel.tui.defaultFolder=/opt/default\n");

        FolderInputPopup popup = new FolderInputPopup(null);
        popup.open();

        assertEquals("/opt/default", popup.getInputText(),
                "the configured default folder must be used when there is no remembered folder");
    }

    @Test
    void fallsBackToUserDirWhenNothingConfigured(@TempDir Path tempDir) {
        useHome(tempDir);

        FolderInputPopup popup = new FolderInputPopup(null);
        popup.open();

        assertEquals(System.getProperty("user.dir"), popup.getInputText(),
                "user.dir must be the final fallback when neither a last folder nor a default is set");
    }
}
