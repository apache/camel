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

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;

/**
 * Standardized {@link Column} definitions for camel-jbang table output.
 *
 * <p>
 * The same logical column (NAME, DESCRIPTION, ...) was previously hand-rolled in every command with differing widths
 * and overflow behaviour. These factories give a single source of truth so tables render consistently, and so the last
 * (rightmost) free-text column can be sized to fill the terminal width.
 *
 * <p>
 * Each factory returns a pre-configured {@link Column}; the caller chains {@code .with(getter)} (which returns the
 * opaque {@code ColumnData}), so all configuration must happen before {@code with}.
 */
public final class CamelTableColumns {

    /**
     * Maximum width for a NAME column. A name longer than this is the rare case; short names render at their content
     * width regardless, so this only acts as a ceiling.
     */
    public static final int NAME_MAX = 60;

    /** Minimum width for the last free-text column on narrow terminals. */
    public static final int LAST_MIN = 20;

    private CamelTableColumns() {
    }

    /** Standard NAME column: left aligned, capped at {@link #NAME_MAX}. */
    public static Column name() {
        return new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(NAME_MAX);
    }

    /** Standard SINCE column: right aligned. */
    public static Column since() {
        return new Column().header("SINCE").dataAlign(HorizontalAlign.RIGHT);
    }

    /**
     * The rightmost free-text column. Grows up to {@code width} and truncates with an ellipsis so each row stays on a
     * single line (use this for flat list/status tables; detail views may keep {@code NEWLINE} wrapping).
     *
     * @param header the column header
     * @param width  the maximum width, typically from {@link #lastColumnWidth(int, int, int...)}
     */
    public static Column lastText(String header, int width) {
        return new Column().header(header).dataAlign(HorizontalAlign.LEFT)
                .maxWidth(width, OverflowBehaviour.ELLIPSIS_RIGHT);
    }

    /**
     * Actual rendered width of a structured column: the longest of the header and any cell value, capped at
     * {@code maxWidth}. Pass {@link Integer#MAX_VALUE} for an unbounded column.
     *
     * @param header   the column header (may be {@code null})
     * @param maxWidth the column's maximum width (ceiling)
     * @param rows     the rows being rendered
     * @param getter   accessor returning the cell value for a row
     */
    public static <T> int measure(String header, int maxWidth, Collection<T> rows, Function<T, String> getter) {
        int width = header != null ? header.length() : 0;
        for (T row : rows) {
            String value = getter.apply(row);
            if (value != null) {
                width = Math.max(width, value.length());
            }
        }
        return Math.min(width, maxWidth);
    }

    /**
     * Width for the last column so the table reaches the terminal edge: the terminal width minus the (actual) widths of
     * the other columns and the border overhead, floored at {@link #LAST_MIN}.
     *
     * @param terminalWidth  total terminal width in columns
     * @param borderOverhead overhead from borders/padding (see {@link TerminalWidthHelper#noBorderOverhead(int)})
     * @param otherWidths    the measured widths of every other (visible) column
     */
    public static int lastColumnWidth(int terminalWidth, int borderOverhead, int... otherWidths) {
        int others = IntStream.of(otherWidths).sum();
        return TerminalWidthHelper.fillWidth(terminalWidth, others, borderOverhead, LAST_MIN);
    }
}
