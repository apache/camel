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
import java.util.List;

/**
 * The question the AI panel is opened with to fix a problem of the Source editor (Shift+F8): the file, the line, the
 * problem and the source around the line, and what to do with them - fix only that, with the edit tool, and validate.
 * It is put in the input of the panel, for the user to send with Enter or change first.
 */
final class AiFixPrompt {

    /** Lines of context before the line and after its step, as in a diff. */
    static final int CONTEXT_LINES = 2;
    /** The most lines of the step that are quoted after the line itself, so a big block does not flood the question. */
    static final int MAX_STEP_LINES = 8;

    private AiFixPrompt() {
    }

    /**
     * @param directory the project directory the AI's file tools are relative to; null when not known
     * @param file      the file of the problem
     * @param line      the line of the problem, 1-based
     */
    static String of(Path directory, Path file, int line, String problem, String lineText) {
        return "Fix the problem on line " + line + " of " + nameOf(directory, file) + ": " + problem + "\n"
               + source(file, line, lineText) + "\n"
               + "Change only what this problem is about, with camel_edit_file, then check the file with"
               + " camel_validate_source.";
    }

    /**
     * The question for a line that fails at runtime (Shift+F8 on a line with failures in the live run data). The cause
     * may lie elsewhere than on the line (such as a direct: endpoint no route consumes), so the AI is asked to find it
     * first, and dev mode reloads the fix once it is saved.
     *
     * @param failure how many exchanges failed on the line, and the exception of the last one when known
     */
    static String ofFailure(Path directory, Path file, int line, String failure, String lineText) {
        return "Exchanges fail at runtime on line " + line + " of " + nameOf(directory, file) + ": " + failure + "\n"
               + source(file, line, lineText) + "\n"
               + "Find the cause from the integration's errors and log, then fix it with camel_edit_file (the fix may"
               + " belong on another line or in another route) and check the file with camel_validate_source."
               + " Dev mode reloads the file when it is saved.";
    }

    /**
     * The question for an ERROR of the log whose source line is not known (Shift+F8 in the Log tab): explain it, and
     * fix it when the cause is in the project's routes.
     *
     * @param error the ERROR line, the exception and its causes
     */
    static String ofLogError(String error) {
        return "Explain this ERROR from the log of the running integration:\n" + error + "\n"
               + "If the cause is in the project's routes or configuration, fix it with camel_edit_file and check the"
               + " file with camel_validate_source. Dev mode reloads the file when it is saved.";
    }

    /**
     * The source around the line, read from the file: the whole step the line starts (the lines below it that are
     * indented deeper, such as the {@code uri} and {@code parameters} of a YAML {@code - to:}), with a few lines of
     * context, the line marked with {@code >}. The line alone when the file cannot be read or has changed.
     */
    private static String source(Path file, int line, String lineText) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            lines = List.of();
        }
        String excerpt = excerpt(lines, line - 1);
        if (excerpt == null) {
            return "The line is: " + lineText.strip();
        }
        return "The source around it (> marks line " + line + "):\n```\n" + excerpt + "```";
    }

    /**
     * The lines from a little before the row to a little after the end of its step, numbered, the row marked with
     * {@code >}; null when the row is not in the lines.
     *
     * @param row the row of the line, 0-based
     */
    static String excerpt(List<String> lines, int row) {
        if (row < 0 || row >= lines.size() || lines.get(row).isBlank()) {
            return null;
        }
        int end = stepEnd(lines, row);
        int from = Math.max(0, row - CONTEXT_LINES);
        int to = Math.min(lines.size() - 1, end + CONTEXT_LINES);
        int width = String.valueOf(to + 1).length();
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            String number = String.format("%" + width + "d", i + 1);
            sb.append(i == row ? "> " : "  ").append(number).append(" | ").append(lines.get(i).stripTrailing())
                    .append('\n');
        }
        return sb.toString();
    }

    /** The last row of the step the row starts: the rows below it that are indented deeper, blank rows inside them. */
    private static int stepEnd(List<String> lines, int row) {
        int indent = indentOf(lines.get(row));
        int end = row;
        for (int i = row + 1; i < lines.size() && i - row <= MAX_STEP_LINES; i++) {
            String text = lines.get(i);
            if (text.isBlank()) {
                continue;
            }
            if (indentOf(text) <= indent) {
                break;
            }
            end = i;
        }
        return end;
    }

    private static int indentOf(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** The name of the file relative to the project directory, when it is inside it. */
    private static String nameOf(Path directory, Path file) {
        String name = file.toString();
        if (directory != null) {
            Path dir = directory.toAbsolutePath().normalize();
            Path abs = file.toAbsolutePath().normalize();
            if (abs.startsWith(dir)) {
                name = dir.relativize(abs).toString();
            }
        }
        return name;
    }
}
