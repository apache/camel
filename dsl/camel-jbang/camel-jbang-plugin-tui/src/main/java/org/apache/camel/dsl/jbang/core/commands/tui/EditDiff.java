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
import java.util.Arrays;
import java.util.List;

/**
 * LCS-based diff utility for the TUI source editor. Classifies lines as unchanged, modified, or added for gutter
 * markers, and produces unified diff output for the F7 overlay.
 */
final class EditDiff {

    enum LineStatus {
        UNCHANGED,
        MODIFIED,
        ADDED
    }

    private EditDiff() {
    }

    static LineStatus[] diff(List<String> original, List<String> current) {
        int m = original.size();
        int n = current.size();
        LineStatus[] statuses = new LineStatus[n];
        Arrays.fill(statuses, LineStatus.UNCHANGED);

        if (m == 0) {
            Arrays.fill(statuses, LineStatus.ADDED);
            return statuses;
        }

        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (original.get(i).equals(current.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        // Build edit script: 0=equal, 1=delete, 2=insert
        List<int[]> ops = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            if (original.get(i).equals(current.get(j))) {
                ops.add(new int[] { 0, i++, j++ });
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                ops.add(new int[] { 1, i++, -1 });
            } else {
                ops.add(new int[] { 2, -1, j++ });
            }
        }
        while (i < m) {
            ops.add(new int[] { 1, i++, -1 });
        }
        while (j < n) {
            ops.add(new int[] { 2, -1, j++ });
        }

        for (int k = 0; k < ops.size(); k++) {
            int[] op = ops.get(k);
            if (op[0] == 2) {
                boolean afterDelete = k > 0 && ops.get(k - 1)[0] == 1;
                statuses[op[2]] = afterDelete ? LineStatus.MODIFIED : LineStatus.ADDED;
            }
        }
        return statuses;
    }

    record DiffEntry(char type, String text, int lineNum) {
    }

    /** Added and removed line counts of a diff, as "+3 -1". */
    static String summary(List<DiffEntry> entries) {
        long added = entries.stream().filter(e -> e.type() == '+').count();
        long removed = entries.stream().filter(e -> e.type() == '-').count();
        return "+" + added + " -" + removed;
    }

    /**
     * Renders unified diff entries the way the source editor's F7 overlay does (removed lines on red, added lines on
     * green, a line number gutter), starting at {@code scrollY}. Returns the scroll offset actually used, clamped so
     * the last entry stays visible.
     */
    static int render(
            dev.tamboui.terminal.Frame frame, dev.tamboui.layout.Rect inner, List<DiffEntry> entries,
            int scrollY) {
        if (entries.isEmpty()) {
            entries = List.of(new DiffEntry(' ', "(no changes)", 0));
        }
        int maxLineNum = entries.stream().mapToInt(DiffEntry::lineNum).max().orElse(1);
        int lineDigits = Math.max(2, String.valueOf(maxLineNum).length());
        int gutterWidth = lineDigits + 2;

        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, entries.size() - inner.height())));
        for (int r = 0; r < inner.height(); r++) {
            int idx = scrollY + r;
            if (idx >= entries.size()) {
                break;
            }
            int screenY = inner.top() + r;
            DiffEntry entry = entries.get(idx);
            dev.tamboui.style.Style lineStyle;
            dev.tamboui.style.Style gutterStyle;
            if (entry.type() == '-') {
                lineStyle = dev.tamboui.style.Style.EMPTY.fg(dev.tamboui.style.Color.WHITE)
                        .bg(dev.tamboui.style.Color.rgb(0x6E, 0x1B, 0x1B));
                gutterStyle = lineStyle;
            } else if (entry.type() == '+') {
                lineStyle = dev.tamboui.style.Style.EMPTY.fg(dev.tamboui.style.Color.WHITE)
                        .bg(dev.tamboui.style.Color.rgb(0x1B, 0x4D, 0x1B));
                gutterStyle = lineStyle;
            } else if (entry.type() == '~') {
                lineStyle = dev.tamboui.style.Style.EMPTY.dim();
                gutterStyle = dev.tamboui.style.Style.EMPTY.dim();
            } else {
                lineStyle = dev.tamboui.style.Style.EMPTY;
                gutterStyle = dev.tamboui.style.Style.EMPTY.dim();
            }

            // fill entire row with background for changed lines
            if (entry.type() == '-' || entry.type() == '+') {
                dev.tamboui.layout.Rect rowRect = new dev.tamboui.layout.Rect(inner.left(), screenY, inner.width(), 1);
                frame.buffer().setStyle(rowRect, lineStyle);
            }

            // line number from original file (for -) or current file (for + and context)
            String lineNum = entry.lineNum() > 0
                    ? String.format("%" + lineDigits + "d ", entry.lineNum())
                    : " ".repeat(lineDigits + 1);
            frame.buffer().setString(inner.left(), screenY, lineNum, gutterStyle);
            frame.buffer().set(inner.left() + gutterWidth - 1, screenY,
                    new dev.tamboui.buffer.Cell("│", gutterStyle));

            int textX = inner.left() + gutterWidth;
            int maxWidth = Math.max(0, inner.width() - gutterWidth);
            String prefix = entry.type() == ' ' ? "  " : entry.type() + " ";
            String text = prefix + entry.text();
            if (text.length() > maxWidth) {
                text = text.substring(0, maxWidth);
            }
            frame.buffer().setString(textX, screenY, text, lineStyle);
        }
        return scrollY;
    }

    static final DiffEntry SEPARATOR = new DiffEntry('~', "───", -1);

    /**
     * One change of a diff, anchored by context lines the way a unified diff hunk is, so it can be applied to a buffer
     * whose line numbers have shifted (the context is searched, not assumed). {@code body} holds the lines of the hunk
     * in order: context (' '), removed ('-') and added ('+').
     */
    record Hunk(List<String> before, List<DiffEntry> body, List<String> after) {

        /** The lines of the original text this hunk expects: context before, body context and removed lines, after. */
        List<String> originalLines() {
            List<String> lines = new ArrayList<>(before);
            for (DiffEntry e : body) {
                if (e.type() != '+') {
                    lines.add(e.text());
                }
            }
            lines.addAll(after);
            return lines;
        }

        int added() {
            return (int) body.stream().filter(e -> e.type() == '+').count();
        }

        int removed() {
            return (int) body.stream().filter(e -> e.type() == '-').count();
        }

        /**
         * Where the given lines contain this hunk's original lines, starting the search at {@code fromRow}; -1 if not.
         */
        int locate(List<String> lines, int fromRow) {
            List<String> pattern = originalLines();
            if (pattern.isEmpty()) {
                return Math.min(Math.max(0, fromRow), lines.size());
            }
            for (int start = Math.max(0, fromRow); start + pattern.size() <= lines.size(); start++) {
                boolean match = true;
                for (int i = 0; i < pattern.size(); i++) {
                    if (!lines.get(start + i).equals(pattern.get(i))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return start;
                }
            }
            return -1;
        }
    }

    /**
     * Splits the changes between {@code original} and {@code current} into hunks with {@code contextLines} lines of
     * context; changes closer to each other than twice the context share a hunk.
     */
    static List<Hunk> hunks(List<String> original, List<String> current, int contextLines) {
        List<DiffEntry> raw = rawDiff(original, current);
        List<Hunk> hunks = new ArrayList<>();
        int k = 0;
        while (k < raw.size()) {
            if (raw.get(k).type() == ' ') {
                k++;
                continue;
            }
            // start of a hunk: context before
            int bodyStart = k;
            List<String> before = new ArrayList<>();
            for (int c = Math.max(0, bodyStart - contextLines); c < bodyStart; c++) {
                before.add(raw.get(c).text());
            }
            // extend the body over changes separated by short equal runs
            int bodyEnd = k;
            int j = k;
            while (j < raw.size()) {
                if (raw.get(j).type() != ' ') {
                    bodyEnd = j + 1;
                    j++;
                    continue;
                }
                int run = 0;
                while (j + run < raw.size() && raw.get(j + run).type() == ' ') {
                    run++;
                }
                if (j + run < raw.size() && run <= 2 * contextLines) {
                    j += run;
                } else {
                    break;
                }
            }
            List<DiffEntry> body = new ArrayList<>(raw.subList(bodyStart, bodyEnd));
            List<String> after = new ArrayList<>();
            for (int c = bodyEnd; c < Math.min(raw.size(), bodyEnd + contextLines); c++) {
                after.add(raw.get(c).text());
            }
            hunks.add(new Hunk(before, body, after));
            k = bodyEnd;
        }
        return hunks;
    }

    private static List<DiffEntry> rawDiff(List<String> original, List<String> current) {
        int m = original.size();
        int n = current.size();
        List<DiffEntry> rawDiff = new ArrayList<>();
        if (m == 0 && n == 0) {
            return rawDiff;
        }
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (original.get(i).equals(current.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            if (original.get(i).equals(current.get(j))) {
                rawDiff.add(new DiffEntry(' ', original.get(i), j + 1));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                rawDiff.add(new DiffEntry('-', original.get(i), i + 1));
                i++;
            } else {
                rawDiff.add(new DiffEntry('+', current.get(j), j + 1));
                j++;
            }
        }
        while (i < m) {
            rawDiff.add(new DiffEntry('-', original.get(i), i + 1));
            i++;
        }
        while (j < n) {
            rawDiff.add(new DiffEntry('+', current.get(j), j + 1));
            j++;
        }
        return rawDiff;
    }

    static List<DiffEntry> unifiedDiff(List<String> original, List<String> current, int contextLines) {
        int m = original.size();
        int n = current.size();

        if (m == 0 && n == 0) {
            return List.of();
        }

        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (original.get(i).equals(current.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<DiffEntry> rawDiff = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            if (original.get(i).equals(current.get(j))) {
                rawDiff.add(new DiffEntry(' ', original.get(i), j + 1));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                rawDiff.add(new DiffEntry('-', original.get(i), i + 1));
                i++;
            } else {
                rawDiff.add(new DiffEntry('+', current.get(j), j + 1));
                j++;
            }
        }
        while (i < m) {
            rawDiff.add(new DiffEntry('-', original.get(i), i + 1));
            i++;
        }
        while (j < n) {
            rawDiff.add(new DiffEntry('+', current.get(j), j + 1));
            j++;
        }

        // Filter to changed hunks with context
        boolean[] visible = new boolean[rawDiff.size()];
        for (int k = 0; k < rawDiff.size(); k++) {
            if (rawDiff.get(k).type != ' ') {
                for (int c = Math.max(0, k - contextLines); c <= Math.min(rawDiff.size() - 1, k + contextLines); c++) {
                    visible[c] = true;
                }
            }
        }

        List<DiffEntry> result = new ArrayList<>();
        boolean inHunk = false;
        for (int k = 0; k < rawDiff.size(); k++) {
            if (visible[k]) {
                if (!inHunk && k > 0) {
                    result.add(SEPARATOR);
                }
                inHunk = true;
                result.add(rawDiff.get(k));
            } else {
                inHunk = false;
            }
        }
        return result;
    }
}
