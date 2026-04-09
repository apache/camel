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

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Helper for detecting the terminal width to adapt table and command output.
 *
 * <p>
 * Uses JLine to query the actual terminal size. Falls back to a default width when the terminal size cannot be
 * determined (e.g., when output is piped or redirected).
 */
public final class TerminalWidthHelper {

    private static final int DEFAULT_WIDTH = 120;
    private static final int MIN_WIDTH = 40;

    private TerminalWidthHelper() {
    }

    /**
     * Returns the current terminal width in columns.
     *
     * <p>
     * Attempts to detect the terminal width using JLine. Returns {@value #DEFAULT_WIDTH} if detection fails or if the
     * output is not connected to a terminal.
     */
    public static int getTerminalWidth() {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(true)
                .build()) {
            int width = terminal.getWidth();
            if (width > 0) {
                return Math.max(width, MIN_WIDTH);
            }
        } catch (Exception e) {
            // ignore
        }
        return DEFAULT_WIDTH;
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
