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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.TimeUtils;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class MemoryTab implements MonitorTab {

    // Unicode block characters for gauge bar
    private static final String GAUGE_FILLED = "█";
    private static final String GAUGE_EMPTY = "░";

    private final MonitorContext ctx;
    private final Map<String, LinkedList<Long>> heapMemHistory;

    MemoryTab(MonitorContext ctx, Map<String, LinkedList<Long>> heapMemHistory) {
        this.ctx = ctx;
        this.heapMemHistory = heapMemHistory;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        return false;
    }

    @Override
    public boolean handleEscape() {
        return false;
    }

    @Override
    public void navigateUp() {
    }

    @Override
    public void navigateDown() {
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Layout: stats panel + chart row (14 + 1 for axis)
        int statsHeight = 10;
        if (info.oldGenUsed > 0) {
            statsHeight += 2;
        }
        if (info.metaspaceUsed > 0) {
            statsHeight += 2;
        }
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(statsHeight), Constraint.length(15))
                .split(area);

        renderStats(frame, chunks.get(0), info);

        // Limit chart width: use ~2/3 of area, leave right side empty
        int chartWidth = Math.max(40, area.width() * 2 / 3);
        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.length(chartWidth), Constraint.fill())
                .split(chunks.get(1));
        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.length(14), Constraint.length(1))
                .split(hChunks.get(0));

        renderSparkline(frame, vChunks.get(0), info);
        renderTimeAxis(frame, vChunks.get(1), info);
    }

    private void renderStats(Frame frame, Rect area, IntegrationInfo info) {
        List<Line> lines = new ArrayList<>();

        // Heap memory with gauge bar
        if (info.heapMemUsed > 0) {
            long pct = info.heapMemMax > 0 ? info.heapMemUsed * 100 / info.heapMemMax : 0;
            String gauge = buildGaugeBar(pct, 30);
            Color gaugeColor = pct >= 80 ? Color.LIGHT_RED : pct >= 60 ? Color.YELLOW : Color.GREEN;

            lines.add(Line.from(
                    Span.styled("  Heap Memory", Style.EMPTY.fg(Color.CYAN).bold())));
            lines.add(Line.from(
                    Span.styled("  used:      ", Style.EMPTY.dim()),
                    Span.styled(String.format("%-10s", formatBytes(info.heapMemUsed)), Style.EMPTY.fg(Color.WHITE).bold()),
                    Span.styled(gauge, Style.EMPTY.fg(gaugeColor)),
                    Span.styled(String.format("  %d%%", pct), Style.EMPTY.fg(gaugeColor).bold())));
            lines.add(Line.from(
                    Span.styled("  committed: ", Style.EMPTY.dim()),
                    Span.raw(formatBytes(info.heapMemCommitted)),
                    Span.styled("    max: ", Style.EMPTY.dim()),
                    Span.raw(formatBytes(info.heapMemMax))));
        }

        // Old Gen pool
        if (info.oldGenUsed > 0) {
            long oldPct = info.oldGenMax > 0 ? info.oldGenUsed * 100 / info.oldGenMax : 0;
            String oldGauge = buildGaugeBar(oldPct, 30);
            Color oldColor = oldPct >= 80 ? Color.LIGHT_RED : oldPct >= 60 ? Color.YELLOW : Color.GREEN;

            lines.add(Line.from(
                    Span.styled("  Old Gen:   ", Style.EMPTY.dim()),
                    Span.styled(String.format("%-10s", formatBytes(info.oldGenUsed)), Style.EMPTY.fg(Color.WHITE).bold()),
                    Span.styled(oldGauge, Style.EMPTY.fg(oldColor)),
                    Span.styled(String.format("  %d%%", oldPct), Style.EMPTY.fg(oldColor).bold())));
            lines.add(Line.from(
                    Span.styled("  committed: ", Style.EMPTY.dim()),
                    Span.raw(formatBytes(info.oldGenCommitted)),
                    Span.styled("    max: ", Style.EMPTY.dim()),
                    Span.raw(formatBytes(info.oldGenMax))));
        }

        // Non-heap memory + Metaspace
        if (info.nonHeapMemUsed > 0) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  Non-Heap Memory", Style.EMPTY.fg(Color.CYAN).bold())));
            lines.add(Line.from(
                    Span.styled("  used:      ", Style.EMPTY.dim()),
                    Span.styled(String.format("%-10s", formatBytes(info.nonHeapMemUsed)), Style.EMPTY.fg(Color.WHITE).bold()),
                    Span.styled("  committed: ", Style.EMPTY.dim()),
                    Span.raw(formatBytes(info.nonHeapMemCommitted))));
        }
        if (info.metaspaceUsed > 0) {
            lines.add(Line.from(
                    Span.styled("  Metaspace: ", Style.EMPTY.dim()),
                    Span.styled(String.format("%-10s", formatBytes(info.metaspaceUsed)), Style.EMPTY.fg(Color.WHITE).bold()),
                    Span.styled("  committed: ", Style.EMPTY.dim()),
                    Span.raw(formatBytes(info.metaspaceCommitted)),
                    info.metaspaceMax > 0
                            ? Span.styled("  max: " + formatBytes(info.metaspaceMax), Style.EMPTY.dim())
                            : Span.raw("")));
        }

        // GC and class loading on the same line
        List<Span> gcSpans = new ArrayList<>();
        gcSpans.add(Span.styled("  GC: ", Style.EMPTY.dim()));
        gcSpans.add(Span.raw(info.gcCollectionCount + " collections"));
        if (info.gcCollectionTime > 0) {
            gcSpans.add(Span.styled("  time: ", Style.EMPTY.dim()));
            gcSpans.add(Span.raw(TimeUtils.printDuration(info.gcCollectionTime, true)));
        }
        if (info.loadedClassCount > 0) {
            gcSpans.add(Span.styled("    Classes: ", Style.EMPTY.dim()));
            gcSpans.add(Span.raw(String.valueOf(info.loadedClassCount)));
        }
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(gcSpans));

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Memory ").build())
                .build();

        frame.renderWidget(paragraph, area);
    }

    private static final String[] BAR_EIGHTHS = { " ", "▁", "▂", "▃", "▄", "▅", "▆", "▇", "█" };

    private void renderSparkline(Frame frame, Rect area, IntegrationInfo info) {
        String pid = info.pid;
        LinkedList<Long> hist = heapMemHistory.get(pid);

        if (hist == null || hist.isEmpty()) {
            Paragraph p = Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled("  Collecting heap data...", Style.EMPTY.dim()))))
                    .block(Block.builder().borderType(BorderType.ROUNDED).title(" Heap Usage ").build())
                    .build();
            frame.renderWidget(p, area);
            return;
        }

        // Use committed as the scale ceiling
        long observedMax = hist.stream().mapToLong(Long::longValue).max().orElse(1);
        long ceiling = info.heapMemCommitted > 0 ? info.heapMemCommitted : observedMax;
        if (observedMax > ceiling) {
            ceiling = observedMax;
        }

        long pct = ceiling > 0 ? info.heapMemUsed * 100 / ceiling : 0;
        Color barColor = pct >= 80 ? Color.LIGHT_RED : pct >= 60 ? Color.YELLOW : Color.GREEN;

        String title = String.format(" Heap Usage (%s / %s committed) ", formatBytes(info.heapMemUsed), formatBytes(ceiling));

        // Render the block border first
        Block block = Block.builder().borderType(BorderType.ROUNDED).title(title).build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);

        if (inner.isEmpty() || ceiling <= 0) {
            return;
        }

        int chartW = inner.width();
        int chartH = inner.height();

        // Build data array right-aligned: latest data on the right
        long[] data = new long[chartW];
        int startIdx = Math.max(0, hist.size() - chartW);
        int dataOffset = Math.max(0, chartW - hist.size());
        for (int i = startIdx; i < hist.size(); i++) {
            data[dataOffset + (i - startIdx)] = hist.get(i);
        }

        // Render multi-row bar chart into the buffer
        Buffer buf = frame.buffer();
        Style barStyle = Style.EMPTY.fg(barColor);

        for (int col = 0; col < chartW; col++) {
            double ratio = (double) data[col] / ceiling;
            // Total eighths this column fills (chartH rows * 8 eighths per row)
            double fillEighths = ratio * chartH * 8.0;
            int totalEighths = (int) Math.round(fillEighths);

            // Render from bottom to top
            for (int row = 0; row < chartH; row++) {
                int y = inner.y() + chartH - 1 - row;
                int x = inner.x() + col;
                int rowEighths = Math.min(8, Math.max(0, totalEighths - row * 8));
                if (rowEighths > 0) {
                    buf.setString(x, y, BAR_EIGHTHS[rowEighths], barStyle);
                }
            }
        }
    }

    private void renderTimeAxis(Frame frame, Rect area, IntegrationInfo info) {
        LinkedList<Long> hist = heapMemHistory.get(info.pid);
        int points = hist != null ? hist.size() : 0;
        long totalSeconds = points * 5L;

        String timeLabel;
        if (totalSeconds < 60) {
            timeLabel = totalSeconds + "s ago";
        } else {
            timeLabel = (totalSeconds / 60) + "m ago";
        }

        int w = area.width();
        if (w < 10) {
            return;
        }

        // Pad to fill the width: "Xm ago" on the left, "now" on the right
        String left = " " + timeLabel;
        String right = "now ";
        int gap = Math.max(1, w - left.length() - right.length());

        Line line = Line.from(
                Span.styled(left, Style.EMPTY.dim()),
                Span.raw(" ".repeat(gap)),
                Span.styled(right, Style.EMPTY.dim()));

        frame.renderWidget(Paragraph.builder().text(Text.from(line)).build(), area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "1-9", "tabs");
    }

    private static String buildGaugeBar(long pct, int width) {
        int filled = (int) (pct * width / 100);
        int empty = width - filled;
        return GAUGE_FILLED.repeat(Math.max(0, filled)) + GAUGE_EMPTY.repeat(Math.max(0, empty));
    }
}
