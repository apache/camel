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

import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;

/**
 * On-disk history files for the embedded TUI shell and AI prompt, stored under the Camel CLI home directory.
 */
final class TuiHistoryFiles {

    private TuiHistoryFiles() {
    }

    static Path shellHistoryFile() {
        return historyFile("tui-shell.history");
    }

    static Path aiPromptHistoryFile() {
        return historyFile("tui-ai-prompt.history");
    }

    private static Path historyFile(String name) {
        return CommandLineHelper.getHomeDir().resolve(CommandLineHelper.CAMEL_DIR).resolve(name);
    }
}
