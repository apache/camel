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

import java.util.ArrayList;
import java.util.List;

/**
 * YAML-structure-aware block operations for the TUI source editor.
 */
final class YamlBlockEditor {

    record BlockRange(int startRow, int endRow) {
        boolean isEmpty() {
            return startRow < 0 || endRow < startRow;
        }
    }

    record EditResult(List<String> lines, int cursorRow, int cursorCol) {
    }

    private YamlBlockEditor() {
    }

    static List<String> toLines(String text) {
        if (text.isEmpty()) {
            return new ArrayList<>(List.of(""));
        }
        return new ArrayList<>(List.of(text.split("\n", -1)));
    }

    static String fromLines(List<String> lines) {
        return String.join("\n", lines);
    }

    static BlockRange findBlock(List<String> lines, int row, boolean yamlListBlocks) {
        if (lines.isEmpty() || row < 0 || row >= lines.size()) {
            return new BlockRange(row, row);
        }
        if (!yamlListBlocks) {
            return new BlockRange(row, row);
        }
        int startRow = findBlockStart(lines, row);
        int blockIndent = leadingSpaces(lines.get(startRow));
        int endRow = findBlockEnd(lines, startRow, blockIndent);
        return new BlockRange(startRow, endRow);
    }

    static EditResult deleteBlock(List<String> lines, int row, boolean yamlListBlocks) {
        BlockRange block = findBlock(lines, row, yamlListBlocks);
        if (block.isEmpty()) {
            return new EditResult(lines, row, 0);
        }
        List<String> answer = new ArrayList<>(lines);
        answer.subList(block.startRow(), block.endRow() + 1).clear();
        if (answer.isEmpty()) {
            answer.add("");
        }
        int cursorRow = Math.min(block.startRow(), answer.size() - 1);
        return new EditResult(answer, cursorRow, leadingSpaces(answer.get(cursorRow)));
    }

    static EditResult duplicateBlock(List<String> lines, int row, boolean yamlListBlocks) {
        BlockRange block = findBlock(lines, row, yamlListBlocks);
        if (block.isEmpty()) {
            return new EditResult(lines, row, 0);
        }
        List<String> copy = new ArrayList<>(lines.subList(block.startRow(), block.endRow() + 1));
        List<String> answer = new ArrayList<>(lines);
        answer.addAll(block.endRow() + 1, copy);
        return new EditResult(answer, block.endRow() + 1, leadingSpaces(copy.get(0)));
    }

    static EditResult moveBlockUp(List<String> lines, int row, boolean yamlListBlocks) {
        BlockRange block = findBlock(lines, row, yamlListBlocks);
        if (block.isEmpty() || block.startRow() == 0) {
            return null;
        }
        BlockRange previous = findPreviousSibling(lines, block, yamlListBlocks);
        if (previous == null || previous.isEmpty()) {
            return null;
        }
        EditResult swapped = swapBlocks(lines, previous, block);
        List<String> answer = swapped.lines();
        int cursorRow = previous.startRow();
        int cursorCol = answer.isEmpty() ? 0 : YamlBlockEditor.leadingSpaces(answer.get(cursorRow));
        return new EditResult(answer, cursorRow, cursorCol);
    }

    static EditResult moveBlockDown(List<String> lines, int row, boolean yamlListBlocks) {
        BlockRange block = findBlock(lines, row, yamlListBlocks);
        if (block.isEmpty() || block.endRow() >= lines.size() - 1) {
            return null;
        }
        BlockRange next = findNextSibling(lines, block, yamlListBlocks);
        if (next == null || next.isEmpty()) {
            return null;
        }
        return swapBlocks(lines, block, next);
    }

    static List<String> toggleComment(List<String> lines, BlockRange block) {
        List<String> answer = new ArrayList<>(lines);
        boolean uncomment = true;
        for (int i = block.startRow(); i <= block.endRow() && i < answer.size(); i++) {
            String line = answer.get(i);
            if (line.isBlank()) {
                continue;
            }
            if (!isCommented(line)) {
                uncomment = false;
                break;
            }
        }
        for (int i = block.startRow(); i <= block.endRow() && i < answer.size(); i++) {
            answer.set(i, uncomment ? uncommentLine(answer.get(i)) : commentLine(answer.get(i)));
        }
        return answer;
    }

    private static EditResult swapBlocks(List<String> lines, BlockRange first, BlockRange second) {
        List<String> firstLines = new ArrayList<>(lines.subList(first.startRow(), first.endRow() + 1));
        List<String> secondLines = new ArrayList<>(lines.subList(second.startRow(), second.endRow() + 1));
        List<String> answer = new ArrayList<>();
        answer.addAll(lines.subList(0, first.startRow()));
        answer.addAll(secondLines);
        answer.addAll(lines.subList(first.endRow() + 1, second.startRow()));
        answer.addAll(firstLines);
        answer.addAll(lines.subList(second.endRow() + 1, lines.size()));
        return new EditResult(answer, second.startRow(), leadingSpaces(secondLines.get(0)));
    }

    private static BlockRange findPreviousSibling(List<String> lines, BlockRange block, boolean yamlListBlocks) {
        if (!yamlListBlocks) {
            if (block.startRow() == 0) {
                return null;
            }
            return new BlockRange(block.startRow() - 1, block.startRow() - 1);
        }
        int blockIndent = leadingSpaces(lines.get(block.startRow()));
        for (int i = block.startRow() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = leadingSpaces(line);
            if (indent == blockIndent && line.trim().startsWith("- ")) {
                return findBlock(lines, i, true);
            }
            if (indent < blockIndent) {
                break;
            }
        }
        return null;
    }

    private static BlockRange findNextSibling(List<String> lines, BlockRange block, boolean yamlListBlocks) {
        if (!yamlListBlocks) {
            if (block.endRow() >= lines.size() - 1) {
                return null;
            }
            return new BlockRange(block.endRow() + 1, block.endRow() + 1);
        }
        int blockIndent = leadingSpaces(lines.get(block.startRow()));
        for (int i = block.endRow() + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = leadingSpaces(line);
            if (indent == blockIndent && line.trim().startsWith("- ")) {
                return findBlock(lines, i, true);
            }
            if (indent < blockIndent) {
                break;
            }
        }
        return null;
    }

    private static int findBlockStart(List<String> lines, int row) {
        int cursorIndent = leadingSpaces(lines.get(row));
        for (int i = row; i >= 0; i--) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = leadingSpaces(line);
            if (line.trim().startsWith("- ") && indent <= cursorIndent) {
                return i;
            }
            if (indent < cursorIndent) {
                return Math.max(i, 0);
            }
        }
        return row;
    }

    private static int findBlockEnd(List<String> lines, int startRow, int blockIndent) {
        int endRow = startRow;
        for (int i = startRow + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                endRow = i;
                continue;
            }
            int indent = leadingSpaces(line);
            if (indent <= blockIndent && (line.trim().startsWith("- ") || indent < blockIndent)) {
                break;
            }
            endRow = i;
        }
        return endRow;
    }

    static int leadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static boolean isCommented(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("#");
    }

    private static String commentLine(String line) {
        if (line.isBlank() || isCommented(line)) {
            return line;
        }
        int indent = leadingSpaces(line);
        return line.substring(0, indent) + "# " + line.substring(indent);
    }

    private static String uncommentLine(String line) {
        if (line.isBlank()) {
            return line;
        }
        int indent = leadingSpaces(line);
        String rest = line.substring(indent);
        if (rest.startsWith("# ")) {
            return line.substring(0, indent) + rest.substring(2);
        }
        if (rest.startsWith("#")) {
            return line.substring(0, indent) + rest.substring(1);
        }
        return line;
    }
}
