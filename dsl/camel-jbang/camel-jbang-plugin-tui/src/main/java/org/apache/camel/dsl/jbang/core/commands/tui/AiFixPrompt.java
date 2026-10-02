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

/**
 * The question the AI panel is opened with to fix a problem of the Source editor (Shift+F8): the file, the line, the
 * problem and the text of the line, and what to do with them - fix only that, with the edit tool, and validate. It is
 * put in the input of the panel, for the user to send with Enter or change first.
 */
final class AiFixPrompt {

    private AiFixPrompt() {
    }

    /**
     * @param directory the project directory the AI's file tools are relative to; null when not known
     * @param file      the file of the problem
     * @param line      the line of the problem, 1-based
     */
    static String of(Path directory, Path file, int line, String problem, String lineText) {
        String name = file.toString();
        if (directory != null) {
            Path dir = directory.toAbsolutePath().normalize();
            Path abs = file.toAbsolutePath().normalize();
            if (abs.startsWith(dir)) {
                name = dir.relativize(abs).toString();
            }
        }
        return "Fix the problem on line " + line + " of " + name + ": " + problem + "\n"
               + "The line is: " + lineText.strip() + "\n"
               + "Change only what this problem is about, with camel_edit_file, then check the file with"
               + " camel_validate_source.";
    }
}
