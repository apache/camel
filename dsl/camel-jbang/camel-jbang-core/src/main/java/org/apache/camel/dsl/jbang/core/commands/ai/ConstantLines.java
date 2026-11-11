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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The lines to write for a constant that holds a simple function, such as {@code constant: "unknown order
 * ${header.id}"}: a constant is used as written, and the validator says to use simple instead. The lines with simple:
 * in place of constant: end the message, so a model can copy them, and the write tool makes the fix when the same
 * content comes a third time ({@link RefusalFixes}, CAMEL-26758). In r31 of the local-model benchmark,
 * contracts-rest-code-first step 1 sent the same file 13 times: the refusal of its jsonpath with [] gave the line to
 * write, the refusal of its constant with ${header.id} gave none, so the write tool could not fix the file
 * (CAMEL-26825).
 */
final class ConstantLines {

    /**
     * The validator's message for it: "Line 58: constant: ${header.id} is not evaluated, a constant is used as ...".
     */
    private static final Pattern CONSTANT_WITH_SIMPLE = Pattern.compile(
            "^Line (\\d+): (?:\\S*/)?constant: \\$\\{.* is not evaluated, a constant is used as written");
    /** The constant key, with its value or without: constant: "x", constant:, - constant: "x". */
    private static final Pattern CONSTANT_KEY = Pattern.compile("^(- +)?constant:(\\s|$)");

    /** How many lines are shown at most; for a longer value only the line of the key is given. */
    private static final int MAX_LINES = 12;

    private ConstantLines() {
    }

    /** The messages, with the lines to write after each message of a constant that holds a simple function. */
    static List<String> withLines(List<String> messages, String content) {
        if (messages.isEmpty() || content == null) {
            return messages;
        }
        String[] lines = content.split("\n", -1);
        List<String> answer = new ArrayList<>(messages.size());
        for (String message : messages) {
            Matcher m = CONSTANT_WITH_SIMPLE.matcher(message);
            String written = m.find() ? linesToWrite(lines, Integer.parseInt(m.group(1)) - 1) : null;
            answer.add(written != null ? message + "." + written : message);
        }
        return answer;
    }

    /**
     * " Write lines 57-58 as:" and the lines of the constant with simple: in place of constant:; null when the line is
     * not of a constant.
     */
    static String linesToWrite(String[] lines, int index) {
        int key = keyLine(lines, index);
        if (key < 0) {
            return null;
        }
        String line = lines[key];
        int at = indent(line);
        Matcher k = CONSTANT_KEY.matcher(line.substring(at));
        if (!k.find()) {
            return null;
        }
        // the column of the key: past the dash of a list item
        int column = at + (k.group(1) != null ? k.group(1).length() : 0);
        int end = blockEnd(lines, key, column);
        if (end - key > MAX_LINES) {
            end = key + 1;
        }
        StringBuilder sb
                = new StringBuilder(end == key + 1 ? " Write line " + (key + 1) : " Write lines " + (key + 1) + "-" + end)
                        .append(" as:\n")
                        .append(line, 0, column).append("simple").append(line.substring(column + "constant".length()));
        for (int i = key + 1; i < end; i++) {
            sb.append('\n').append(lines[i]);
        }
        return sb.toString();
    }

    /**
     * The line of the constant key: the line of the message, or for a constant whose value is on the lines below it
     * (constant: and then expression: ...), the key above that the value belongs to; -1 when there is none.
     */
    private static int keyLine(String[] lines, int index) {
        if (index < 0 || index >= lines.length || lines[index].isBlank()) {
            return -1;
        }
        if (CONSTANT_KEY.matcher(lines[index].stripLeading()).find()) {
            return index;
        }
        int column = indent(lines[index]);
        for (int i = index - 1; i >= 0; i--) {
            String l = lines[i];
            if (!l.isBlank() && indent(l) < column) {
                String key = l.strip();
                return CONSTANT_KEY.matcher(key).find() && key.endsWith("constant:") ? i : -1;
            }
        }
        return -1;
    }

    /**
     * The end (exclusive) of the value of the key at the column: the lines below it that are more indented, without the
     * blank lines at its end.
     */
    private static int blockEnd(String[] lines, int start, int column) {
        int end = start + 1;
        while (end < lines.length && (lines[end].isBlank() || indent(lines[end]) > column)) {
            end++;
        }
        while (end > start + 1 && lines[end - 1].isBlank()) {
            end--;
        }
        return end;
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }
}
