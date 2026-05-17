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

import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.widget.Widget;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.sparkline.Sparkline;

/**
 * A mirrored sparkline widget that displays two time-series datasets as vertical bars growing in opposite directions
 * from a shared centre axis.
 * <p>
 * The top series renders as bars growing <em>upward</em> from the centre; the bottom series renders as bars growing
 * <em>downward</em> from the centre. Sub-pixel resolution is achieved using Unicode block characters (▁▂▃▄▅▆▇█), giving
 * smooth visual gradation within a single character row. This layout matches the style of macOS Activity Monitor's
 * network and disk activity graphs.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * MirroredSparkline chart = MirroredSparkline.builder()
 *         .topData(inRates)
 *         .bottomData(outRates)
 *         .topStyle(Style.EMPTY.fg(Color.GREEN))
 *         .bottomStyle(Style.EMPTY.fg(Color.BLUE))
 *         .xLabels("-60s", "-45s", "-30s", "-15s", "now")
 *         .block(Block.builder().borderType(BorderType.ROUNDED)
 *                 .title(Title.from("In / Out  msg/s")).build())
 *         .build();
 * }</pre>
 *
 * <h2>Differences from {@link Sparkline}</h2>
 * <table>
 * <caption>Feature comparison between Sparkline and MirroredSparkline</caption>
 * <tr>
 * <th></th>
 * <th>{@code Sparkline}</th>
 * <th>{@code MirroredSparkline}</th>
 * </tr>
 * <tr>
 * <td>Series</td>
 * <td>1</td>
 * <td>2 (top + bottom)</td>
 * </tr>
 * <tr>
 * <td>Growth direction</td>
 * <td>always upward from bottom row</td>
 * <td>top grows up, bottom grows down, from a shared centre separator row</td>
 * </tr>
 * <tr>
 * <td>Height</td>
 * <td>1 row (fixed at bottom of area)</td>
 * <td>fills the full area height</td>
 * </tr>
 * <tr>
 * <td>Y-axis labels</td>
 * <td>none</td>
 * <td>optional: max / 0 / max at top, centre, and bottom rows</td>
 * </tr>
 * <tr>
 * <td>X-axis labels</td>
 * <td>none</td>
 * <td>optional label row rendered below the chart body</td>
 * </tr>
 * <tr>
 * <td>BarSet</td>
 * <td>yes ({@link Sparkline.BarSet})</td>
 * <td>yes (reuses {@link Sparkline.BarSet})</td>
 * </tr>
 * </table>
 *
 * <p>
 * This class is intended for contribution to the TamboUI project as a first-class widget under
 * {@code dev.tamboui.widgets.sparkline}. The package and license header would change accordingly upon contribution.
 */
public final class MirroredSparkline implements Widget {

    private static final int Y_LABEL_WIDTH = 4;
    private static final Style DIM = Style.EMPTY.dim();
    private static final String CENTRE_SEPARATOR = "─";

    private final long[] topData;
    private final long[] bottomData;
    private final Style topStyle;
    private final Style bottomStyle;
    private final Long max;
    private final Block block;
    private final Sparkline.BarSet barSet;
    private final boolean showYAxis;
    private final String[] xLabels;

    private MirroredSparkline(Builder builder) {
        this.topData = builder.topData;
        this.bottomData = builder.bottomData;
        this.topStyle = builder.topStyle;
        this.bottomStyle = builder.bottomStyle;
        this.max = builder.max;
        this.block = builder.block;
        this.barSet = builder.barSet;
        this.showYAxis = builder.showYAxis;
        this.xLabels = builder.xLabels;
    }

    /**
     * Creates a new builder.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        if (area.isEmpty()) {
            return;
        }

        Rect inner = area;
        if (block != null) {
            block.render(area, buffer);
            inner = block.inner(area);
        }

        if (inner.isEmpty()) {
            return;
        }

        int innerH = inner.height();
        int innerW = inner.width();

        boolean hasXAxis = xLabels != null && xLabels.length > 0;
        // Reserve one row at the bottom for x-axis labels when configured
        int chartBodyRows = hasXAxis ? Math.max(2, innerH - 1) : innerH;
        int halfH = Math.max(1, (chartBodyRows - 1) / 2);
        int centerRow = halfH;

        int yLabelW = showYAxis ? Y_LABEL_WIDTH : 0;
        int chartW = Math.max(1, innerW - yLabelW);

        int dataLen = Math.max(topData.length, bottomData.length);
        int ticks = Math.min(dataLen, chartW);

        long effectiveMax = computeMax();

        // --- Bar rows ---
        for (int r = 0; r < chartBodyRows; r++) {
            int y = inner.y() + r;

            if (showYAxis) {
                String label;
                if (r == 0) {
                    label = effectiveMax > 9999 ? "999+" : String.format("%4d", effectiveMax);
                } else if (r == centerRow) {
                    label = "   0";
                } else if (r == chartBodyRows - 1) {
                    label = effectiveMax > 9999 ? "999+" : String.format("%4d", effectiveMax);
                } else {
                    label = "    ";
                }
                buffer.setString(inner.x(), y, label, DIM);
            }

            for (int t = 0; t < ticks; t++) {
                int x = inner.x() + yLabelW + t;
                int dataIdx = dataLen - ticks + t;
                long topVal = dataIdx >= 0 && dataIdx < topData.length ? topData[dataIdx] : 0;
                long botVal = dataIdx >= 0 && dataIdx < bottomData.length ? bottomData[dataIdx] : 0;

                String ch;
                Style style;

                if (r < centerRow) {
                    // Top series: bars grow upward from the centre
                    int rowOffset = centerRow - 1 - r; // 0 at the row nearest the centre
                    long barPx = topVal * halfH * 8 / effectiveMax;
                    long threshold = (long) rowOffset * 8;
                    if (barPx >= threshold + 8) {
                        ch = barSet.full();
                    } else if (barPx > threshold) {
                        ch = barSet.symbolForLevel((double) (barPx - threshold) / 8.0);
                    } else {
                        ch = barSet.empty();
                    }
                    style = topStyle;
                } else if (r == centerRow) {
                    ch = CENTRE_SEPARATOR;
                    style = DIM;
                } else {
                    // Bottom series: bars grow downward from the centre
                    int rowOffset = r - centerRow - 1; // 0 at the row nearest the centre
                    long barPx = botVal * halfH * 8 / effectiveMax;
                    long threshold = (long) rowOffset * 8;
                    if (barPx >= threshold + 8) {
                        ch = barSet.full();
                    } else if (barPx > threshold) {
                        ch = barSet.symbolForLevel((double) (barPx - threshold) / 8.0);
                    } else {
                        ch = barSet.empty();
                    }
                    style = bottomStyle;
                }

                buffer.setString(x, y, ch, style);
            }
        }

        // --- X-axis label row ---
        if (hasXAxis) {
            int xAxisY = inner.y() + chartBodyRows;
            char[] xChars = new char[chartW];
            for (int i = 0; i < chartW; i++) {
                xChars[i] = ' ';
            }
            // Distribute labels evenly across the tick range
            for (int li = 0; li < xLabels.length; li++) {
                String lbl = xLabels[li];
                double fraction = xLabels.length > 1 ? (double) li / (xLabels.length - 1) : 0;
                int col = (int) Math.round(fraction * (ticks - 1));
                // Right-align the last label so it doesn't run past the right edge
                int start = li == xLabels.length - 1
                        ? Math.max(0, col - lbl.length() + 1)
                        : col;
                for (int k = 0; k < lbl.length() && start + k < chartW; k++) {
                    xChars[start + k] = lbl.charAt(k);
                }
            }
            if (showYAxis) {
                buffer.setString(inner.x(), xAxisY, " ".repeat(yLabelW), DIM);
            }
            buffer.setString(inner.x() + yLabelW, xAxisY, new String(xChars), DIM);
        }
    }

    private long computeMax() {
        if (max != null) {
            return Math.max(1, max);
        }
        long m = 1;
        for (long v : topData) {
            m = Math.max(m, v);
        }
        for (long v : bottomData) {
            m = Math.max(m, v);
        }
        return m;
    }

    /**
     * Builder for {@link MirroredSparkline}.
     */
    public static final class Builder {
        private long[] topData = new long[0];
        private long[] bottomData = new long[0];
        private Style topStyle = Style.EMPTY;
        private Style bottomStyle = Style.EMPTY;
        private Long max;
        private Block block;
        private Sparkline.BarSet barSet = Sparkline.BarSet.NINE_LEVELS;
        private boolean showYAxis = true;
        private String[] xLabels;

        private Builder() {
        }

        /**
         * Sets the top series data (bars grow upward from centre).
         *
         * @param  data the data values
         * @return      this builder
         */
        public Builder topData(long... data) {
            this.topData = data != null ? data.clone() : new long[0];
            return this;
        }

        /**
         * Sets the top series data from a list (bars grow upward from centre).
         *
         * @param  data the data values
         * @return      this builder
         */
        public Builder topData(List<Long> data) {
            this.topData = data == null ? new long[0] : data.stream().mapToLong(Long::longValue).toArray();
            return this;
        }

        /**
         * Sets the bottom series data (bars grow downward from centre).
         *
         * @param  data the data values
         * @return      this builder
         */
        public Builder bottomData(long... data) {
            this.bottomData = data != null ? data.clone() : new long[0];
            return this;
        }

        /**
         * Sets the bottom series data from a list (bars grow downward from centre).
         *
         * @param  data the data values
         * @return      this builder
         */
        public Builder bottomData(List<Long> data) {
            this.bottomData = data == null ? new long[0] : data.stream().mapToLong(Long::longValue).toArray();
            return this;
        }

        /**
         * Sets the style for the top series bars.
         *
         * @param  style the style
         * @return       this builder
         */
        public Builder topStyle(Style style) {
            this.topStyle = style != null ? style : Style.EMPTY;
            return this;
        }

        /**
         * Sets the style for the bottom series bars.
         *
         * @param  style the style
         * @return       this builder
         */
        public Builder bottomStyle(Style style) {
            this.bottomStyle = style != null ? style : Style.EMPTY;
            return this;
        }

        /**
         * Sets an explicit maximum value for scaling both series. When not set the maximum value across both datasets
         * is used.
         *
         * @param  max the maximum value
         * @return     this builder
         */
        public Builder max(long max) {
            this.max = max;
            return this;
        }

        /**
         * Clears an explicit maximum, reverting to auto-scaling from the data.
         *
         * @return this builder
         */
        public Builder autoMax() {
            this.max = null;
            return this;
        }

        /**
         * Wraps the chart in a block (border + optional title).
         *
         * @param  block the block
         * @return       this builder
         */
        public Builder block(Block block) {
            this.block = block;
            return this;
        }

        /**
         * Sets the bar symbol set used for sub-pixel rendering.
         *
         * @param  barSet the bar set
         * @return        this builder
         */
        public Builder barSet(Sparkline.BarSet barSet) {
            this.barSet = barSet != null ? barSet : Sparkline.BarSet.NINE_LEVELS;
            return this;
        }

        /**
         * Controls whether a Y-axis label column is rendered on the left. Shows the shared maximum at the top and
         * bottom rows and {@code 0} at the centre row. Defaults to {@code true}.
         *
         * @param  show whether to show the y-axis labels
         * @return      this builder
         */
        public Builder showYAxis(boolean show) {
            this.showYAxis = show;
            return this;
        }

        /**
         * Sets the x-axis labels rendered as a single row below the chart body. Labels are distributed evenly across
         * the data range. The last label is right-aligned at its position so it does not overflow the right edge.
         * <p>
         * Example: {@code xLabels("-60s", "-45s", "-30s", "-15s", "now")}
         *
         * @param  labels the labels, distributed left-to-right
         * @return        this builder
         */
        public Builder xLabels(String... labels) {
            this.xLabels = labels != null ? labels.clone() : null;
            return this;
        }

        /**
         * Builds the widget.
         *
         * @return a new MirroredSparkline
         */
        public MirroredSparkline build() {
            return new MirroredSparkline(this);
        }
    }
}
