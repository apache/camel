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
 * <p>
 * The same for a second EIP in a step (log: below - setBody:, at the column of setBody:): the line to write is the line
 * of the second EIP as its own - item. In r32 of the local-model benchmark, the refusal said only "start it as its own
 * item, - setBody:", and four step runs sent the same content 5, 14, 14 and 20 times: with no line to write, the write
 * tool could not make the fix when the same content came a third time ({@link RefusalFixes}, CAMEL-26874).
 */
final class IndentLines {

    /** The validator's message for it: "Line 13: must have at most 1 properties (steps: lines up with split:, ...". */
    private static final Pattern OPTION_AT_EIP = Pattern.compile(
            "^Line (\\d+): must have at most 1 properties \\((\\w+): lines up with (\\w+):, so it is read as a second key");
    /**
     * The validator's message for a second EIP, when it is the only key to move: "Line 44: must have at most 1
     * properties (log: is another EIP: start it as its own item, - log:)". With several keys the hint has a part for
     * each, joined with "; ", and gets no lines.
     */
    private static final Pattern ANOTHER_EIP = Pattern.compile(
            "^Line (\\d+): must have at most 1 properties \\((\\w+): is another EIP: start it as its own item, - \\2:\\)$");

    /** How many lines are shown at most; a longer block is said in words after them. */
    private static final int MAX_LINES = 12;

    private IndentLines() {
    }

    /**
     * The messages, with the lines to write after each message of an option at the column of its EIP, and the line to
     * write after each message of a second EIP in a step.
     */
    static List<String> withLines(List<String> messages, String content) {
        if (messages.isEmpty() || content == null) {
            return messages;
        }
        String[] lines = content.split("\n", -1);
        List<String> answer = new ArrayList<>(messages.size());
        for (String message : messages) {
            String written = null;
            Matcher m = OPTION_AT_EIP.matcher(message);
            if (m.find()) {
                written = linesToWrite(lines, Integer.parseInt(m.group(1)) - 1, m.group(2));
            } else if ((m = ANOTHER_EIP.matcher(message)).find()) {
                written = itemToWrite(lines, Integer.parseInt(m.group(1)) - 1, m.group(2));
            }
            answer.add(written != null ? message + written : message);
        }
        return answer;
    }

    /** " Write lines 15-17 as:" and the lines two spaces further right; null when the key is not found. */
    static String linesToWrite(String[] lines, int itemIndex, String key) {
        int j = keyLine(lines, itemIndex, key);
        if (j < 0) {
            return null;
        }
        int end = blockEnd(lines, j);
        StringBuilder sb = new StringBuilder(" Write lines ").append(j + 1).append('-').append(end).append(" as:");
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

    /**
     * " Write line 46 as:" and the line of the key as a - item of its own: the dash in the column of the dash of the
     * item, in place of the two spaces before the key. The key keeps its column, so the lines under it stay as they
     * are. Null when the key is not found.
     */
    static String itemToWrite(String[] lines, int itemIndex, String key) {
        int j = keyLine(lines, itemIndex, key);
        if (j < 0) {
            return null;
        }
        int dash = indent(lines[itemIndex]);
        return " Write line " + (j + 1) + " as:\n" + lines[j].substring(0, dash) + "- " + lines[j].substring(dash + 2);
    }

    /**
     * The index of the line of the key below the - item at itemIndex, in the column of the keys of the item (two more
     * than its dash); -1 when the line is not a - item or the key is not found in it.
     */
    private static int keyLine(String[] lines, int itemIndex, String key) {
        if (itemIndex < 0 || itemIndex >= lines.length) {
            return -1;
        }
        String item = lines[itemIndex];
        int dash = indent(item);
        if (!item.startsWith("- ", dash)) {
            return -1;
        }
        int keyColumn = dash + 2;
        for (int j = itemIndex + 1; j < lines.length; j++) {
            String l = lines[j];
            if (l.isBlank()) {
                continue;
            }
            int i = indent(l);
            if (i < keyColumn) {
                return -1; // past the item
            }
            if (i == keyColumn && l.startsWith(key + ":", i)) {
                return j;
            }
        }
        return -1;
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
