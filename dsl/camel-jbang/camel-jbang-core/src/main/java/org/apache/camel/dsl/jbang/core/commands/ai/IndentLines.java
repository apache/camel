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
 * The lines to write for an option of a step's EIP written at the column of the EIP (steps: next to - split:): the
 * validator says which way to indent them (CAMEL-25529), and a model that does not see it in its own lines writes the
 * same YAML again (sixteen times in a row in the local-model benchmark); the lines as they must be, it copies
 * (CAMEL-25556).
 */
final class IndentLines {

    /** The validator's message for it: "Line 13: must have at most 1 properties (steps: lines up with split:, ...". */
    private static final Pattern OPTION_AT_EIP = Pattern.compile(
            "^Line (\\d+): must have at most 1 properties \\((\\w+): lines up with (\\w+):, so it is read as a second key");

    /** How many lines are shown at most; a longer block is said in words after them. */
    private static final int MAX_LINES = 12;

    private IndentLines() {
    }

    /** The messages, with the lines to write after each message of an option at the column of its EIP. */
    static List<String> withLines(List<String> messages, String content) {
        if (messages.isEmpty() || content == null) {
            return messages;
        }
        String[] lines = content.split("\n", -1);
        List<String> answer = new ArrayList<>(messages.size());
        for (String message : messages) {
            Matcher m = OPTION_AT_EIP.matcher(message);
            String written = m.find() ? linesToWrite(lines, Integer.parseInt(m.group(1)) - 1, m.group(2)) : null;
            answer.add(written != null ? message + written : message);
        }
        return answer;
    }

    /** " Write lines 15-17 as:" and the lines two spaces further right; null when the key is not found. */
    static String linesToWrite(String[] lines, int itemIndex, String key) {
        if (itemIndex < 0 || itemIndex >= lines.length) {
            return null;
        }
        String item = lines[itemIndex];
        int dash = indent(item);
        if (!item.startsWith("- ", dash)) {
            return null;
        }
        int keyColumn = dash + 2;
        for (int j = itemIndex + 1; j < lines.length; j++) {
            String l = lines[j];
            if (l.isBlank()) {
                continue;
            }
            int i = indent(l);
            if (i < keyColumn) {
                return null; // past the item
            }
            if (i == keyColumn && l.startsWith(key + ":", i)) {
                int end = blockEnd(lines, j);
                StringBuilder sb = new StringBuilder(" Write lines ").append(j + 1).append('-').append(end)
                        .append(" as:");
                int shown = 0;
                for (int k = j; k < end; k++) {
                    if (shown == MAX_LINES) {
                        sb.append("\n(and the ").append(end - k).append(" lines after them, two spaces further right too)");
                        break;
                    }
                    sb.append('\n').append(lines[k].isBlank() ? "" : "  " + lines[k]);
                    shown++;
                }
                return sb.toString();
            }
        }
        return null;
    }

    /**
     * The end (exclusive) of the block of a key: the lines below it that are more indented, and the items of a list at
     * the key's own column (YAML allows them there), without the blank lines at its end.
     */
    private static int blockEnd(String[] lines, int start) {
        int col = indent(lines[start]);
        boolean keyWithoutValue = lines[start].strip().endsWith(":");
        int end = start + 1;
        while (end < lines.length) {
            String l = lines[end];
            if (l.isBlank() || indent(l) > col
                    || keyWithoutValue && indent(l) == col && l.startsWith("- ", col)) {
                end++;
            } else {
                break;
            }
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
