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
package org.apache.camel.dsl.jbang.core.common;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for detecting the terminal width to adapt table and command output.
 *
 * <p>
 * Uses the {@code COLUMNS} environment variable, {@code stty size} (POSIX) or {@code mode con} (Windows) to detect the
 * terminal width. Falls back to a default width when the terminal size cannot be determined (e.g., when output is piped
 * or redirected).
 *
 * <p>
 * Avoids using JLine's {@code TerminalBuilder} for width detection because it sends escape sequence queries (DA1, CPR)
 * to the terminal that can leak into the shell output when the terminal is closed before responses arrive.
 */
public final class TerminalWidthHelper {

    private static final int DEFAULT_WIDTH = 80;
    private static final int MIN_WIDTH = 40;

    private static final Pattern INTEGER = Pattern.compile("\\d+");

    private TerminalWidthHelper() {
    }

    /**
     * Returns the current terminal width in columns.
     *
     * <p>
     * Tries the {@code COLUMNS} environment variable first, then {@code stty size} on POSIX or {@code mode con} on
     * Windows. Returns {@value #DEFAULT_WIDTH} if detection fails or if the output is not connected to a terminal.
     */
    public static int getTerminalWidth() {
        // Try COLUMNS env var first (set by most shells)
        String cols = System.getenv("COLUMNS");
        if (cols != null) {
            try {
                int w = Integer.parseInt(cols.trim());
                if (w > 0) {
                    return Math.max(w, MIN_WIDTH);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        // Fall back to an OS native command that reads the terminal size without escape sequences
        int w = isWindows()
                ? readWidthFromCommand("cmd", "/c", "mode", "con")
                : readWidthFromCommand("stty", "size");
        if (w > 0) {
            return Math.max(w, MIN_WIDTH);
        }
        return DEFAULT_WIDTH;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static int readWidthFromCommand(String... command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return parseColumns(output);
        } catch (Exception e) {
            // ignore — command not available (e.g. stty/mode missing)
            return -1;
        }
    }

    /**
     * Parses the column count from the output of {@code stty size} ("rows cols") or Windows {@code mode con} (a
     * multi-line "Lines: N / Columns: N" block). Both place the column count as the <b>second</b> integer in the
     * output, so it is parsed positionally rather than by label to survive localized Windows output where the
     * {@code Columns:} label is translated.
     *
     * @param  output the raw command output
     * @return        the column count, or {@code -1} if it cannot be determined
     */
    static int parseColumns(String output) {
        if (output == null) {
            return -1;
        }
        Matcher m = INTEGER.matcher(output);
        Integer first = null;
        while (m.find()) {
            int value = Integer.parseInt(m.group());
            if (first == null) {
                first = value;
            } else {
                return value;
            }
        }
        return -1;
    }

    /**
     * Computes the available width for a flexible column (e.g., DESCRIPTION), given the total terminal width, the sum
     * of other fixed column widths, and the border overhead.
     *
     * @param  terminalWidth     total terminal width in columns
     * @param  fixedColumnsWidth sum of max widths of all other (non-flexible) columns
     * @param  borderOverhead    overhead from table borders and padding (use {@link #noBorderOverhead(int)} or
     *                           {@link #fancyBorderOverhead(int)})
     * @param  minFlexWidth      minimum width for the flexible column
     * @param  maxFlexWidth      maximum width for the flexible column (used when terminal is wide)
     * @return                   the computed width for the flexible column
     */
    public static int flexWidth(
            int terminalWidth, int fixedColumnsWidth, int borderOverhead,
            int minFlexWidth, int maxFlexWidth) {
        int available = terminalWidth - fixedColumnsWidth - borderOverhead;
        return Math.max(minFlexWidth, Math.min(maxFlexWidth, available));
    }

    /**
     * Computes the width for the last column so the table fills the terminal width. Unlike
     * {@link #flexWidth(int, int, int, int, int)} there is no upper cap: the column grows all the way to the terminal
     * edge.
     *
     * @param  terminalWidth     total terminal width in columns
     * @param  fixedColumnsWidth sum of the (actual) widths of all other columns
     * @param  borderOverhead    overhead from table borders and padding (use {@link #noBorderOverhead(int)} or
     *                           {@link #fancyBorderOverhead(int)})
     * @param  minWidth          minimum width for the last column (used when terminal is narrow)
     * @return                   the computed width for the last column
     */
    public static int fillWidth(
            int terminalWidth, int fixedColumnsWidth, int borderOverhead, int minWidth) {
        return Math.max(minWidth, terminalWidth - fixedColumnsWidth - borderOverhead);
    }

    /**
     * Scales a column width proportionally based on available terminal width. All columns with the given preferred
     * widths are scaled proportionally to fit within the terminal.
     *
     * @param  terminalWidth  total terminal width in columns
     * @param  borderOverhead overhead from table borders and padding
     * @param  preferred      the preferred (maximum) width for this column
     * @param  minWidth       the minimum width for this column
     * @param  allPreferred   the sum of all columns' preferred widths
     * @return                the scaled width for this column
     */
    public static int scaleWidth(
            int terminalWidth, int borderOverhead,
            int preferred, int minWidth, int allPreferred) {
        int available = terminalWidth - borderOverhead;
        if (available >= allPreferred) {
            return preferred;
        }
        int scaled = available * preferred / allPreferred;
        return Math.max(minWidth, Math.min(preferred, scaled));
    }

    /**
     * Border overhead for NO_BORDERS tables: 2 spaces between each column pair.
     */
    public static int noBorderOverhead(int columnCount) {
        return (columnCount - 1) * 2;
    }

    /**
     * Border overhead for FANCY_ASCII tables: | col1 | col2 | ... | colN |
     */
    public static int fancyBorderOverhead(int columnCount) {
        // 1 left border + 1 right border + (columnCount * 2 padding) + (columnCount - 1) separators
        return 2 + columnCount * 2 + (columnCount - 1);
    }
}
