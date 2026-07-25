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
import org.jline.reader.LineReader;
import org.jline.shell.Shell;
import org.jline.shell.ShellBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TuiShellHistorySupportTest {

    private String originalHome;

    @AfterEach
    void tearDown() {
        if (originalHome != null) {
            CommandLineHelper.useHomeDir(originalHome);
        }
    }

    @Test
    void zeroDisablesShellHistory(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        TuiSettings settings = new TuiSettings();
        settings.setShellHistory("0");

        ShellBuilder builder = newShellBuilder();
        TuiShellHistorySupport.configure(builder, settings);
        try (var shell = builder.build()) {
            LineReader reader = shell.reader();
            assertThat(reader.getVariable(LineReader.DISABLE_HISTORY)).isEqualTo(true);
        }
    }

    @Test
    void positiveLimitUsesCamelHistoryFile(@TempDir Path tempDir) throws Exception {
        useHome(tempDir);
        TuiSettings settings = new TuiSettings();
        settings.setShellHistory("50");

        ShellBuilder builder = newShellBuilder();
        TuiShellHistorySupport.configure(builder, settings);
        try (var shell = builder.build()) {
            LineReader reader = shell.reader();
            assertThat(reader.getVariable(LineReader.HISTORY_SIZE)).isEqualTo(50);
            assertThat(reader.getVariable(LineReader.HISTORY_FILE_SIZE)).isEqualTo(50);
            assertThat(Path.of(String.valueOf(reader.getVariable(LineReader.HISTORY_FILE))))
                    .isEqualTo(TuiHistoryFiles.shellHistoryFile());
            assertThat(Files.isDirectory(tempDir.resolve(CommandLineHelper.CAMEL_DIR))).isTrue();
        }
    }

    private static ShellBuilder newShellBuilder() throws Exception {
        Terminal terminal = TerminalBuilder.builder().dumb(true).build();
        return Shell.builder().terminal(terminal).prompt("camel> ");
    }

    private void useHome(Path dir) {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(dir.toString());
    }
}
