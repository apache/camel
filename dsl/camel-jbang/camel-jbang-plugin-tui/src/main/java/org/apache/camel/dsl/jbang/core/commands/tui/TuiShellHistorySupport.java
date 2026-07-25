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
import java.nio.file.Files;
import java.nio.file.Path;

import org.jline.reader.LineReader;
import org.jline.shell.ShellBuilder;

/**
 * Applies {@link TuiSettings} shell command-line history limits to a JLine {@link ShellBuilder}.
 */
final class TuiShellHistorySupport {

    private TuiShellHistorySupport() {
    }

    static void configure(ShellBuilder builder, TuiSettings settings) throws IOException {
        int limit = settings.getShellHistoryLimit();
        if (limit == 0) {
            builder.historyCommands(false);
            builder.variable(LineReader.DISABLE_HISTORY, true);
            return;
        }
        builder.historyCommands(true);
        Path historyFile = TuiHistoryFiles.shellHistoryFile();
        Files.createDirectories(historyFile.getParent());
        builder.historyFile(historyFile);
        builder.variable(LineReader.HISTORY_SIZE, limit);
        builder.variable(LineReader.HISTORY_FILE_SIZE, limit);
    }
}
