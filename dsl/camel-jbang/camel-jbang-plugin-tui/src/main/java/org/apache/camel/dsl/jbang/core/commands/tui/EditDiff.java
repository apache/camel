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

    static final DiffEntry SEPARATOR = new DiffEntry('~', "───", -1);

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
