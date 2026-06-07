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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

class SpansTab implements MonitorTab {

    private final MonitorContext ctx;
    private final AtomicReference<List<SpanEntry>> spans;

    private final TableState traceListState = new TableState();
    private final ScrollbarState waterfallScrollState = new ScrollbarState();

    private boolean waterfallView;
    private String selectedTraceId;
    private int waterfallScroll;
    private int waterfallSelected;

    boolean spanRefreshRequested;

    SpansTab(MonitorContext ctx, AtomicReference<List<SpanEntry>> spans) {
        this.ctx = ctx;
        this.spans = spans;
    }

    @Override
    public void onTabSelected() {
        spanRefreshRequested = true;
    }

    @Override
    public void onIntegrationChanged() {
        spanRefreshRequested = true;
        waterfallView = false;
        selectedTraceId = null;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (waterfallView) {
            return handleWaterfallKeys(ke);
        }
        if (ke.isConfirm()) {
            Integer sel = traceListState.selected();
            List<TraceSummary> summaries = buildTraceSummaries();
            if (sel != null && sel >= 0 && sel < summaries.size()) {
                selectedTraceId = summaries.get(sel).traceId;
                waterfallView = true;
                waterfallScroll = 0;
                waterfallSelected = 0;
            }
            return true;
        }
        if (ke.isChar('r') || ke.isChar('R')) {
            spanRefreshRequested = true;
            return true;
        }
        return false;
    }

    private boolean handleWaterfallKeys(KeyEvent ke) {
        if (ke.isUp()) {
            if (waterfallSelected > 0) {
                waterfallSelected--;
            }
            return true;
        }
        if (ke.isDown()) {
            List<WaterfallNode> nodes = buildWaterfallNodes(selectedTraceId);
            if (waterfallSelected < nodes.size() - 1) {
                waterfallSelected++;
            }
            return true;
        }
        if (ke.isChar('r') || ke.isChar('R')) {
            spanRefreshRequested = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (waterfallView) {
            waterfallView = false;
            selectedTraceId = null;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (!waterfallView) {
            traceListState.selectPrevious();
        }
    }

    @Override
    public void navigateDown() {
        if (!waterfallView) {
            traceListState.selectNext(buildTraceSummaries().size());
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            MonitorContext.renderNoSelection(frame, area);
            return;
        }

        List<SpanEntry> currentSpans = spans.get();
        if (currentSpans.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  No OTel spans captured. Use --observe flag when running.",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" OTel Spans ").build())
                            .build(),
                    area);
            return;
        }

        if (waterfallView && selectedTraceId != null) {
            renderWaterfallView(frame, area);
        } else {
            renderTraceList(frame, area);
        }
    }

    private void renderTraceList(Frame frame, Rect area) {
        List<TraceSummary> summaries = buildTraceSummaries();

        List<Row> rows = new ArrayList<>();
        for (TraceSummary ts : summaries) {
            Style statusStyle;
            if (ts.hasError) {
                statusStyle = Style.EMPTY.fg(Color.LIGHT_RED);
            } else {
                statusStyle = Style.EMPTY.fg(Color.GREEN);
            }

            rows.add(Row.from(
                    Cell.from(shortId(ts.traceId)),
                    Cell.from(String.valueOf(ts.spanCount)),
                    Cell.from(ts.rootName != null ? ts.rootName : "?"),
                    Cell.from(Span.styled(ts.hasError ? "ERROR" : "OK", statusStyle)),
                    Cell.from(ts.totalDurationMs + "ms"),
                    Cell.from(String.valueOf(ts.spanCount))));
        }

        String title = String.format(" OTel Traces — %d traces, %d spans ", summaries.size(), spans.get().size());
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("TRACE-ID", Style.EMPTY.fg(Color.YELLOW).bold())),
                        Cell.from(Span.styled("SPANS", Style.EMPTY.fg(Color.YELLOW).bold())),
                        Cell.from(Span.styled("ROOT", Style.EMPTY.fg(Color.YELLOW).bold())),
                        Cell.from(Span.styled("STATUS", Style.EMPTY.fg(Color.YELLOW).bold())),
                        Cell.from(Span.styled("DURATION", Style.EMPTY.fg(Color.YELLOW).bold())),
                        Cell.from(Span.styled("DEPTH", Style.EMPTY.fg(Color.YELLOW).bold()))))
                .widths(
                        Constraint.length(10),
                        Constraint.length(7),
                        Constraint.fill(),
                        Constraint.length(8),
                        Constraint.length(12),
                        Constraint.length(7))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();
        frame.renderStatefulWidget(table, area, traceListState);
    }

    private void renderWaterfallView(Frame frame, Rect area) {
        List<WaterfallNode> nodes = buildWaterfallNodes(selectedTraceId);
        if (nodes.isEmpty()) {
            waterfallView = false;
            return;
        }

        long traceStart = Long.MAX_VALUE;
        long traceEnd = 0;
        long minDuration = Long.MAX_VALUE;
        long maxDuration = 0;
        for (WaterfallNode n : nodes) {
            traceStart = Math.min(traceStart, n.span.startEpochNanos());
            traceEnd = Math.max(traceEnd, n.span.endEpochNanos());
            if (n.span.durationMs() > 0) {
                minDuration = Math.min(minDuration, n.span.durationMs());
                maxDuration = Math.max(maxDuration, n.span.durationMs());
            }
        }
        if (minDuration == Long.MAX_VALUE) {
            minDuration = 0;
        }
        long traceDuration = (traceEnd - traceStart) / 1_000_000;

        // Split: waterfall top, detail bottom
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(6))
                .split(area);

        renderWaterfall(frame, chunks.get(0), nodes, traceStart, traceDuration, minDuration, maxDuration);
        renderSpanDetail(frame, chunks.get(1), nodes);
    }

    private void renderWaterfall(
            Frame frame, Rect area, List<WaterfallNode> nodes,
            long traceStart, long traceDuration, long minDuration, long maxDuration) {

        String title = String.format(" Trace %s — %d spans, %dms ",
                shortId(selectedTraceId), nodes.size(), traceDuration);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(title)
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (inner.height() < 1 || inner.width() < 20) {
            return;
        }

        int visibleLines = inner.height();
        int maxScroll = Math.max(0, nodes.size() - visibleLines);
        waterfallScroll = Math.min(waterfallScroll, maxScroll);

        // Auto-scroll to keep selection visible
        if (waterfallSelected < waterfallScroll) {
            waterfallScroll = waterfallSelected;
        } else if (waterfallSelected >= waterfallScroll + visibleLines) {
            waterfallScroll = waterfallSelected - visibleLines + 1;
        }

        int labelWidth = 0;
        for (WaterfallNode n : nodes) {
            int indent = n.depth * 2;
            labelWidth = Math.max(labelWidth, indent + n.span.name().length());
        }
        labelWidth = Math.min(labelWidth + 2, inner.width() / 3);

        int barMaxWidth = Math.max(10, inner.width() - labelWidth - 12);

        int end = Math.min(waterfallScroll + visibleLines, nodes.size());
        List<Line> lines = new ArrayList<>();
        for (int i = waterfallScroll; i < end; i++) {
            WaterfallNode n = nodes.get(i);
            boolean selected = i == waterfallSelected;
            lines.add(renderWaterfallLine(n, labelWidth, barMaxWidth,
                    traceStart, traceDuration, minDuration, maxDuration, selected));
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), hChunks.get(0));

        if (nodes.size() > visibleLines) {
            waterfallScrollState
                    .contentLength(nodes.size())
                    .viewportContentLength(visibleLines)
                    .position(waterfallScroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), waterfallScrollState);
        }
    }

    private static Line renderWaterfallLine(
            WaterfallNode node, int labelWidth, int maxBarWidth,
            long traceStart, long traceDuration, long minDuration, long maxDuration,
            boolean selected) {
        String indicator = selected ? "▸ " : "  ";
        String indent = "  ".repeat(node.depth);
        String label = indent + node.span.name();
        if (label.length() > labelWidth) {
            label = label.substring(0, labelWidth - 1) + "…";
        } else {
            label = String.format("%-" + labelWidth + "s", label);
        }

        // Calculate bar offset and width relative to trace timeline
        long spanStart = node.span.startEpochNanos() - traceStart;
        long spanDuration = node.span.endEpochNanos() - node.span.startEpochNanos();

        double offsetRatio = traceDuration > 0 ? (double) (spanStart / 1_000_000) / traceDuration : 0;
        double widthRatio = traceDuration > 0 ? (double) (spanDuration / 1_000_000) / traceDuration : 0;

        int barOffset = (int) Math.round(offsetRatio * maxBarWidth);
        int barWidth = Math.max(1, (int) Math.round(widthRatio * maxBarWidth));
        barOffset = Math.min(barOffset, maxBarWidth - 1);
        barWidth = Math.min(barWidth, maxBarWidth - barOffset);

        String gap = " ".repeat(barOffset);
        String bar = "█".repeat(barWidth);

        String durationStr = node.span.durationMs() + "ms";
        int pad = Math.max(1, 8 - durationStr.length());

        Style labelStyle = selected ? Style.EMPTY.fg(Color.CYAN).bold() : Style.EMPTY.fg(Color.CYAN);
        Style bandStyle = node.span.isError()
                ? Style.EMPTY.fg(Color.LIGHT_RED)
                : TuiHelper.colorForDuration(node.span.durationMs(), minDuration, maxDuration);

        return Line.from(
                Span.styled(indicator, Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(label, labelStyle),
                Span.raw(gap),
                Span.styled(bar, bandStyle),
                Span.raw(" ".repeat(pad)),
                Span.styled(durationStr, node.span.isError()
                        ? Style.EMPTY.fg(Color.LIGHT_RED).bold()
                        : Style.EMPTY.fg(Color.WHITE).bold()));
    }

    private void renderSpanDetail(Frame frame, Rect area, List<WaterfallNode> nodes) {
        if (waterfallSelected < 0 || waterfallSelected >= nodes.size()) {
            return;
        }
        SpanEntry span = nodes.get(waterfallSelected).span;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  Span: %s  Parent: %s  Kind: %s  Status: %s  Duration: %dms",
                span.spanId(), span.parentSpanId() != null ? span.parentSpanId() : "-",
                span.kind(), span.status(), span.durationMs()));
        if (span.attributes() != null && !span.attributes().isEmpty()) {
            sb.append("  Attrs: ");
            span.attributes().forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
        }

        Style titleStyle = span.isError() ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.CYAN);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(Line.from(Span.styled(sb.toString(), Style.EMPTY.dim()))))
                        .block(Block.builder().borderType(BorderType.ROUNDED)
                                .title(dev.tamboui.widgets.block.Title.from(
                                        Line.from(Span.styled(" " + span.name() + " ", titleStyle))))
                                .build())
                        .build(),
                area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (waterfallView) {
            spans.add(Span.styled(" ↑↓ ", Style.EMPTY.fg(Color.BLACK).onWhite()));
            spans.add(Span.styled(" navigate ", Style.EMPTY));
            spans.add(Span.styled(" Esc ", Style.EMPTY.fg(Color.BLACK).onWhite()));
            spans.add(Span.styled(" back ", Style.EMPTY));
            spans.add(Span.styled(" r ", Style.EMPTY.fg(Color.BLACK).onWhite()));
            spans.add(Span.styled(" refresh ", Style.EMPTY));
        } else {
            spans.add(Span.styled(" ↑↓ ", Style.EMPTY.fg(Color.BLACK).onWhite()));
            spans.add(Span.styled(" navigate ", Style.EMPTY));
            spans.add(Span.styled(" Enter ", Style.EMPTY.fg(Color.BLACK).onWhite()));
            spans.add(Span.styled(" waterfall ", Style.EMPTY));
            spans.add(Span.styled(" r ", Style.EMPTY.fg(Color.BLACK).onWhite()));
            spans.add(Span.styled(" refresh ", Style.EMPTY));
        }
    }

    private List<TraceSummary> buildTraceSummaries() {
        List<SpanEntry> currentSpans = spans.get();
        Map<String, TraceSummary> byTrace = new LinkedHashMap<>();

        for (SpanEntry span : currentSpans) {
            TraceSummary ts = byTrace.computeIfAbsent(span.traceId(), k -> new TraceSummary(k));
            ts.spanCount++;
            if (span.isRoot()) {
                ts.rootName = span.name();
                ts.totalDurationMs = span.durationMs();
            }
            if (span.isError()) {
                ts.hasError = true;
            }
            ts.maxDepth = Math.max(ts.maxDepth, 0);
        }

        List<TraceSummary> result = new ArrayList<>(byTrace.values());
        // Fill duration from root span; if no root found, sum up
        for (TraceSummary ts : result) {
            if (ts.totalDurationMs == 0) {
                ts.totalDurationMs = currentSpans.stream()
                        .filter(s -> s.traceId().equals(ts.traceId))
                        .mapToLong(SpanEntry::durationMs)
                        .max().orElse(0);
            }
        }
        // Newest first (highest startEpochNanos)
        result.sort(Comparator.comparingLong(ts -> {
            return -currentSpans.stream()
                    .filter(s -> s.traceId().equals(ts.traceId))
                    .mapToLong(SpanEntry::startEpochNanos)
                    .max().orElse(0);
        }));

        return result;
    }

    private List<WaterfallNode> buildWaterfallNodes(String traceId) {
        List<SpanEntry> traceSpans = spans.get().stream()
                .filter(s -> traceId.equals(s.traceId()))
                .sorted(Comparator.comparingLong(SpanEntry::startEpochNanos))
                .toList();

        if (traceSpans.isEmpty()) {
            return List.of();
        }

        // Build parent-child tree
        Map<String, List<SpanEntry>> childrenMap = new LinkedHashMap<>();
        SpanEntry root = null;
        for (SpanEntry span : traceSpans) {
            if (span.isRoot()) {
                root = span;
            }
            String parentId = span.parentSpanId();
            if (parentId != null && !parentId.isEmpty()) {
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(span);
            }
        }

        if (root == null && !traceSpans.isEmpty()) {
            root = traceSpans.get(0);
        }

        List<WaterfallNode> result = new ArrayList<>();
        addToWaterfall(result, root, childrenMap, 0);
        return result;
    }

    private void addToWaterfall(
            List<WaterfallNode> result, SpanEntry span,
            Map<String, List<SpanEntry>> childrenMap, int depth) {
        result.add(new WaterfallNode(span, depth));
        List<SpanEntry> children = childrenMap.get(span.spanId());
        if (children != null) {
            for (SpanEntry child : children) {
                addToWaterfall(result, child, childrenMap, depth + 1);
            }
        }
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    static class TraceSummary {
        final String traceId;
        String rootName;
        int spanCount;
        long totalDurationMs;
        boolean hasError;
        int maxDepth;

        TraceSummary(String traceId) {
            this.traceId = traceId;
        }
    }

    record WaterfallNode(SpanEntry span, int depth) {
    }
}
