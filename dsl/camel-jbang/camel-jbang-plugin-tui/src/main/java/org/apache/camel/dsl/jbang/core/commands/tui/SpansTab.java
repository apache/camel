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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class SpansTab extends AbstractTab {

    private static final int MOUSE_SCROLL_LINES = 3;
    private static final String[] SORT_COLUMNS = { "trace-id", "route", "from", "spans", "routes", "status", "duration" };

    private final AtomicReference<List<SpanEntry>> spans;

    private final TableState traceListState = new TableState();
    private final ScrollbarState waterfallScrollState = new ScrollbarState();
    private final ScrollbarState tableScrollState = new ScrollbarState();
    private Rect lastTableArea;

    private boolean waterfallView;
    private String selectedTraceId;
    private String selectedListTraceId;
    private int waterfallScroll;
    private int waterfallSelected;
    private boolean showProcessors = true;
    private boolean camelOnly;
    private String sortColumn = "trace-id";
    private int sortIndex;
    private boolean sortReversed;
    private boolean filterInputActive;
    private TextInputState filterInputState = new TextInputState("");
    private String filterTerm;

    boolean spanRefreshRequested;

    SpansTab(MonitorContext ctx, AtomicReference<List<SpanEntry>> spans) {
        super(ctx);
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
        if (filterInputActive) {
            return handleFilterInput(ke);
        }
        if (ke.isConfirm()) {
            Integer sel = traceListState.selected();
            List<TraceSummary> summaries = buildFilteredTraceSummaries();
            if (sel != null && sel >= 0 && sel < summaries.size()) {
                selectedTraceId = summaries.get(sel).traceId;
                waterfallView = true;
                waterfallScroll = 0;
                waterfallSelected = 0;
            }
            return true;
        }
        if (ke.isKey(KeyCode.F5)) {
            spanRefreshRequested = true;
            return true;
        }
        if (ke.isChar('/')) {
            filterInputActive = true;
            filterInputState = new TextInputState(filterTerm != null ? filterTerm : "");
            return true;
        }
        if (ke.isChar('s')) {
            sortIndex = (sortIndex + 1) % SORT_COLUMNS.length;
            sortColumn = SORT_COLUMNS[sortIndex];
            sortReversed = false;
            return true;
        }
        if (ke.isChar('S')) {
            sortReversed = !sortReversed;
            return true;
        }
        return false;
    }

    private boolean handleFilterInput(KeyEvent ke) {
        if (ke.isKey(KeyCode.ESCAPE)) {
            filterInputActive = false;
            return true;
        }
        if (ke.isConfirm()) {
            String text = filterInputState.text().trim();
            filterTerm = text.isEmpty() ? null : text;
            filterInputActive = false;
            selectedListTraceId = null;
            traceListState.select(0);
            return true;
        }
        FormHelper.handleTextInput(ke, filterInputState);
        return true;
    }

    boolean isFilterInputActive() {
        return filterInputActive;
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
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            waterfallSelected = Math.max(0, waterfallSelected - 20);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            List<WaterfallNode> nodes = buildWaterfallNodes(selectedTraceId);
            waterfallSelected = Math.min(nodes.size() - 1, waterfallSelected + 20);
            return true;
        }
        if (ke.isChar('p')) {
            showProcessors = !showProcessors;
            waterfallSelected = 0;
            waterfallScroll = 0;
            return true;
        }
        if (ke.isChar('c')) {
            camelOnly = !camelOnly;
            waterfallSelected = 0;
            waterfallScroll = 0;
            return true;
        }
        if (ke.isKey(KeyCode.F5)) {
            spanRefreshRequested = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (!waterfallView) {
            List<TraceSummary> summaries = buildFilteredTraceSummaries();
            if (handleTableClick(me, lastTableArea, traceListState, summaries.size())) {
                syncSelectedListTraceId();
                return true;
            }
        }
        if (waterfallView) {
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                if (waterfallSelected > 0) {
                    waterfallSelected = Math.max(0, waterfallSelected - MOUSE_SCROLL_LINES);
                }
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                List<WaterfallNode> nodes = buildWaterfallNodes(selectedTraceId);
                waterfallSelected = Math.min(nodes.size() - 1, waterfallSelected + MOUSE_SCROLL_LINES);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (filterInputActive) {
            filterInputActive = false;
            return true;
        }
        if (waterfallView) {
            waterfallView = false;
            selectedTraceId = null;
            return true;
        }
        if (filterTerm != null) {
            filterTerm = null;
            selectedListTraceId = null;
            traceListState.select(0);
            return true;
        }
        return false;
    }

    @Override
    public boolean setFilter(String filter) {
        filterTerm = (filter != null && !filter.isEmpty()) ? filter : null;
        filterInputActive = false;
        selectedListTraceId = null;
        traceListState.select(0);
        return true;
    }

    @Override
    public boolean setInputValue(String field, String value) {
        if ("filter".equals(field)) {
            return setFilter(value);
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (!waterfallView) {
            traceListState.selectPrevious();
            syncSelectedListTraceId();
        }
    }

    @Override
    public void navigateDown() {
        if (!waterfallView) {
            traceListState.selectNext(buildFilteredTraceSummaries().size());
            syncSelectedListTraceId();
        }
    }

    private void syncSelectedListTraceId() {
        Integer sel = traceListState.selected();
        if (sel != null) {
            List<TraceSummary> summaries = buildFilteredTraceSummaries();
            if (sel >= 0 && sel < summaries.size()) {
                selectedListTraceId = summaries.get(sel).traceId;
            }
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<SpanEntry> currentSpans = spans.get();
        if (currentSpans.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  No OTel spans captured. Use --observe flag when running.",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
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

    private List<TraceSummary> buildFilteredTraceSummaries() {
        List<TraceSummary> all = buildTraceSummaries();
        if (filterTerm == null) {
            return all;
        }
        String filter = filterTerm.toLowerCase();
        List<TraceSummary> filtered = new ArrayList<>();
        for (TraceSummary ts : all) {
            if (ts.searchText.contains(filter)) {
                filtered.add(ts);
            }
        }
        return filtered;
    }

    private void renderTraceList(Frame frame, Rect area) {
        List<TraceSummary> allSummaries = buildTraceSummaries();
        List<TraceSummary> summaries = filterTerm != null ? buildFilteredTraceSummaries() : allSummaries;

        // Restore selection by traceId (survives data refresh/eviction)
        if (!summaries.isEmpty()) {
            if (selectedListTraceId != null) {
                int idx = -1;
                for (int i = 0; i < summaries.size(); i++) {
                    if (summaries.get(i).traceId.equals(selectedListTraceId)) {
                        idx = i;
                        break;
                    }
                }
                if (idx >= 0) {
                    traceListState.select(idx);
                } else {
                    traceListState.select(0);
                    selectedListTraceId = summaries.get(0).traceId;
                }
            } else {
                traceListState.select(0);
                selectedListTraceId = summaries.get(0).traceId;
            }
        }

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
                    Cell.from(ts.rootRouteId != null ? ts.rootRouteId : ""),
                    Cell.from(ts.rootName != null ? ts.rootName : "?"),
                    rightCell(String.valueOf(ts.spanCount), 5),
                    rightCell(String.valueOf(ts.routeCount), 5),
                    Cell.from(ts.remoteComponents.isEmpty() ? "-" : ts.remoteComponents),
                    Cell.from(Span.styled(ts.hasError ? "ERROR" : "OK", statusStyle)),
                    Cell.from(ts.totalDurationMs + "ms"),
                    rightCell(String.valueOf(ts.maxDepth), 5)));
        }

        String title;
        if (filterTerm != null) {
            title = String.format(" OTel Traces — %d/%d traces [%s] ",
                    summaries.size(), allSummaries.size(), filterTerm);
        } else {
            title = String.format(" OTel Traces — %d traces, %d spans ", allSummaries.size(), spans.get().size());
        }
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("TRACE-ID", "trace-id"), sortStyle("trace-id"))),
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled(sortLabel("FROM", "from"), sortStyle("from"))),
                        Cell.from(Span.styled(sortLabel("SPANS", "spans"), sortStyle("spans"))),
                        Cell.from(Span.styled(sortLabel("ROUTES", "routes"), sortStyle("routes"))),
                        Cell.from(Span.styled("REMOTE", Style.EMPTY.fg(Color.YELLOW).bold())),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("DURATION", "duration"), sortStyle("duration"))),
                        Cell.from(Span.styled("DEPTH", Style.EMPTY.fg(Color.YELLOW).bold()))))
                .widths(
                        Constraint.length(10),
                        Constraint.length(20),
                        Constraint.length(20),
                        Constraint.length(7),
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(8),
                        Constraint.length(12),
                        Constraint.length(7))
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, traceListState);
        renderTableScrollbar(frame, lastTableArea, traceListState, tableScrollState, summaries.size());
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
                .constraints(Constraint.fill(), Constraint.length(10))
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
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
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
            labelWidth = Math.max(labelWidth, indent + spanLabel(n.span).length());
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
        String label = indent + spanLabel(node.span);
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

        boolean error = node.span.isError();
        boolean camelSpan = node.span.isCamelSpan();
        Style labelStyle;
        Style bandStyle;
        if (error) {
            labelStyle = selected ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY.fg(Color.LIGHT_RED);
            bandStyle = Style.EMPTY.fg(Color.LIGHT_RED);
        } else if (!camelSpan) {
            labelStyle = selected ? Style.EMPTY.fg(Color.LIGHT_MAGENTA).bold() : Style.EMPTY.fg(Color.LIGHT_MAGENTA);
            bandStyle = Style.EMPTY.fg(Color.LIGHT_MAGENTA);
        } else {
            labelStyle = selected ? Style.EMPTY.fg(Color.CYAN).bold() : Style.EMPTY.fg(Color.CYAN);
            bandStyle = TuiHelper.colorForDuration(node.span.durationMs(), minDuration, maxDuration);
        }

        String errorTag = error ? " ERR" : "";

        return Line.from(
                Span.styled(indicator, Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(label, labelStyle),
                Span.raw(gap),
                Span.styled(bar, bandStyle),
                Span.styled(errorTag, Style.EMPTY.fg(Color.LIGHT_RED).bold()),
                Span.raw(" ".repeat(pad)),
                Span.styled(durationStr, error
                        ? Style.EMPTY.fg(Color.LIGHT_RED).bold()
                        : Style.EMPTY.fg(Color.WHITE).bold()));
    }

    private void renderSpanDetail(Frame frame, Rect area, List<WaterfallNode> nodes) {
        if (waterfallSelected < 0 || waterfallSelected >= nodes.size()) {
            return;
        }
        SpanEntry span = nodes.get(waterfallSelected).span;

        List<Line> lines = new ArrayList<>();

        // Row 1: span identity
        Style statusStyle = span.isError() ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY.fg(Color.GREEN);
        lines.add(Line.from(
                Span.styled(" Span:   ", Style.EMPTY.dim()),
                Span.styled(span.spanId(), Style.EMPTY.fg(Color.WHITE).bold()),
                Span.styled("  Parent: ", Style.EMPTY.dim()),
                Span.raw(span.parentSpanId() != null ? span.parentSpanId() : "-"),
                Span.styled("  Kind: ", Style.EMPTY.dim()),
                Span.raw(span.kind() != null ? span.kind() : "")));

        // Row 2: status and duration
        lines.add(Line.from(
                Span.styled(" Status: ", Style.EMPTY.dim()),
                Span.styled(span.isError() ? "ERROR" : "OK", statusStyle),
                Span.styled("  Duration: ", Style.EMPTY.dim()),
                Span.raw(span.durationMs() + "ms")));

        // Row 3: route, processor context, and scope (for 3rd-party spans)
        if (span.routeId() != null || span.processorId() != null || !span.isCamelSpan()) {
            List<Span> ctx = new ArrayList<>();
            if (span.routeId() != null) {
                ctx.add(Span.styled(" Route:  ", Style.EMPTY.dim()));
                ctx.add(Span.styled(span.routeId(), Style.EMPTY.fg(Color.YELLOW)));
            }
            if (span.processorId() != null) {
                ctx.add(Span.styled("  Processor: ", Style.EMPTY.dim()));
                ctx.add(Span.styled(span.processorId(), Style.EMPTY.fg(Color.YELLOW)));
            }
            if (!span.isCamelSpan()) {
                ctx.add(Span.styled("  Source: ", Style.EMPTY.dim()));
                ctx.add(Span.styled(span.scopeName(), Style.EMPTY.fg(Color.LIGHT_MAGENTA)));
            }
            lines.add(Line.from(ctx));
        }

        // Attributes as individual key: value lines
        if (span.attributes() != null && !span.attributes().isEmpty()) {
            lines.add(Line.from(Span.raw("")));
            for (Map.Entry<String, Object> entry : span.attributes().entrySet()) {
                lines.add(Line.from(
                        Span.styled(" " + entry.getKey() + ": ", Style.EMPTY.dim()),
                        Span.raw(String.valueOf(entry.getValue()))));
            }
        }

        Style titleStyle = span.isError() ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.CYAN);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(dev.tamboui.widgets.block.Title.from(
                                        Line.from(Span.styled(" " + spanLabel(span) + " ", titleStyle))))
                                .build())
                        .build(),
                area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (waterfallView) {
            hint(spans, "Esc", "back");
            hint(spans, "F5", "refresh");
            hint(spans, "c", camelOnly ? "camel-only [on]" : "camel-only [off]");
            hint(spans, "p", showProcessors ? "processors [on]" : "processors [off]");
            hint(spans, "↑↓", "navigate");
            hintLast(spans, "PgUp/Dn", "page");
        } else if (filterInputActive) {
            spans.add(Span.styled(" /", Style.EMPTY.fg(Color.YELLOW).bold()));
            spans.add(Span.raw(filterInputState.text() + "█  "));
            hint(spans, "Enter", "filter");
            hintLast(spans, "Esc", "cancel");
        } else {
            hint(spans, "Esc", filterTerm != null ? "clear" : "back");
            hint(spans, "F5", "refresh");
            hint(spans, "Enter", "waterfall");
            if (filterTerm != null) {
                spans.add(Span.styled("  /", Style.EMPTY.fg(Color.YELLOW).bold()));
                spans.add(Span.raw("\"" + filterTerm + "\"  "));
            } else {
                hint(spans, "/", "filter");
            }
            hintLast(spans, "↑↓", "navigate");
        }
    }

    private List<TraceSummary> buildTraceSummaries() {
        List<SpanEntry> currentSpans = spans.get();
        Map<String, TraceSummary> byTrace = new LinkedHashMap<>();

        for (SpanEntry span : currentSpans) {
            TraceSummary ts = byTrace.computeIfAbsent(span.traceId(), k -> new TraceSummary(k));
            if (span.isRoot()) {
                ts.rootRouteId = span.routeId();
                ts.rootName = compactUri(span);
            }
            if (span.isError()) {
                ts.hasError = true;
            }
        }

        List<TraceSummary> result = new ArrayList<>(byTrace.values());
        for (TraceSummary ts : result) {
            List<SpanEntry> traceSpans = currentSpans.stream()
                    .filter(s -> s.traceId().equals(ts.traceId))
                    .toList();
            // Fallback root: use the earliest span
            if (ts.rootName == null && !traceSpans.isEmpty()) {
                SpanEntry earliest = traceSpans.stream()
                        .min(Comparator.comparingLong(SpanEntry::startEpochNanos))
                        .orElse(null);
                if (earliest != null) {
                    ts.rootName = compactUri(earliest);
                    if (ts.rootRouteId == null) {
                        ts.rootRouteId = earliest.routeId();
                    }
                }
            }
            // Compute trace envelope duration (same as waterfall view)
            long traceStart = Long.MAX_VALUE;
            long traceEnd = 0;
            Set<String> routes = new HashSet<>();
            Set<String> exchangeIds = new HashSet<>();
            Set<String> remoteSchemes = new LinkedHashSet<>();
            for (SpanEntry s : traceSpans) {
                traceStart = Math.min(traceStart, s.startEpochNanos());
                traceEnd = Math.max(traceEnd, s.endEpochNanos());
                if (s.routeId() != null) {
                    routes.add(s.routeId());
                }
                if (s.attributes() != null) {
                    Object eid = s.attributes().get("exchangeId");
                    if (eid != null) {
                        exchangeIds.add(eid.toString());
                    }
                    Object scheme = s.attributes().get("url.scheme");
                    if (scheme != null && isRemoteScheme(scheme.toString())) {
                        remoteSchemes.add(scheme.toString());
                    }
                }
            }
            ts.totalDurationMs = traceStart < Long.MAX_VALUE ? (traceEnd - traceStart) / 1_000_000 : 0;
            ts.routeCount = routes.size();
            ts.remoteComponents = remoteSchemes.isEmpty() ? "" : String.join(",", remoteSchemes);
            // Build search text for filtering (traceId, exchangeIds, routes, remote components)
            StringBuilder sb = new StringBuilder();
            sb.append(ts.traceId).append(' ');
            exchangeIds.forEach(e -> sb.append(e).append(' '));
            routes.forEach(r -> sb.append(r).append(' '));
            if (!ts.remoteComponents.isEmpty()) {
                sb.append(ts.remoteComponents);
            }
            ts.searchText = sb.toString().toLowerCase();
            ts.spanCount = traceSpans.size();
            ts.maxDepth = computeMaxDepth(traceSpans);
        }
        result.sort((a, b) -> {
            int cmp = sortTrace(a, b, currentSpans);
            return sortReversed ? -cmp : cmp;
        });

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

        Set<String> included = new HashSet<>();
        Map<String, Integer> spanIdToDepth = new LinkedHashMap<>();
        List<WaterfallNode> result = new ArrayList<>();
        addToWaterfall(result, root, childrenMap, 0, included, spanIdToDepth);

        // Add orphan spans whose parent isn't reachable from the root.
        // Try to nest them under their parent if the parent is in the result
        // or was collapsed (spanIdToDepth tracks both shown and collapsed spans).
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SpanEntry span : traceSpans) {
                if (included.contains(span.spanId())) {
                    continue;
                }
                int depth = 0;
                if (span.parentSpanId() != null && spanIdToDepth.containsKey(span.parentSpanId())) {
                    depth = spanIdToDepth.get(span.parentSpanId()) + 1;
                }
                result.add(new WaterfallNode(span, depth));
                included.add(span.spanId());
                spanIdToDepth.put(span.spanId(), depth);
                changed = true;
            }
        }
        return result;
    }

    private void addToWaterfall(
            List<WaterfallNode> result, SpanEntry span,
            Map<String, List<SpanEntry>> childrenMap, int depth,
            Set<String> included, Map<String, Integer> spanIdToDepth) {
        if (!included.add(span.spanId())) {
            return;
        }
        // Record depth for every visited span (even collapsed ones)
        // so orphans can find their parent's depth
        spanIdToDepth.put(span.spanId(), depth);

        List<SpanEntry> children = childrenMap.get(span.spanId());
        // Collapse internal producer+consumer pairs:
        // When an EVENT_SENT span has a single EVENT_RECEIVED child with the same name,
        // skip the producer and show only the consumer (the route execution).
        // Never collapse if the skipped span has an error — the error would be hidden.
        if (isEventSent(span) && !span.isError() && children != null && children.size() == 1
                && isEventReceived(children.get(0))
                && span.name().equals(children.get(0).name())) {
            addToWaterfall(result, children.get(0), childrenMap, depth, included, spanIdToDepth);
            return;
        }
        // Hide 3rd-party agent spans when camelOnly is on — promote children to same depth
        if (camelOnly && !span.isError() && !span.isCamelSpan()) {
            if (children != null) {
                for (SpanEntry child : children) {
                    addToWaterfall(result, child, childrenMap, depth, included, spanIdToDepth);
                }
            }
            return;
        }
        // Hide processor spans when toggle is off — promote children to same depth
        // Keep error processors visible so errors aren't hidden
        if (!showProcessors && !span.isError() && isEventProcess(span)) {
            if (children != null) {
                for (SpanEntry child : children) {
                    addToWaterfall(result, child, childrenMap, depth, included, spanIdToDepth);
                }
            }
            return;
        }
        result.add(new WaterfallNode(span, depth));
        if (children != null) {
            for (SpanEntry child : children) {
                addToWaterfall(result, child, childrenMap, depth + 1, included, spanIdToDepth);
            }
        }
    }

    private static boolean isEventSent(SpanEntry span) {
        return span.attributes() != null && "EVENT_SENT".equals(span.attributes().get("op"));
    }

    private static boolean isEventReceived(SpanEntry span) {
        return span.attributes() != null && "EVENT_RECEIVED".equals(span.attributes().get("op"));
    }

    private static boolean isEventProcess(SpanEntry span) {
        return span.attributes() != null && "EVENT_PROCESS".equals(span.attributes().get("op"));
    }

    private static String spanLabel(SpanEntry span) {
        String name = span.name();
        Map<String, Object> attrs = span.attributes();
        if (attrs != null) {
            // Use camel.uri for endpoint spans (direct, stub, timer, etc.)
            Object uri = attrs.get("camel.uri");
            if (uri != null) {
                String label = uri.toString();
                if (span.routeId() != null) {
                    label += " (" + span.routeId() + ")";
                }
                return label;
            }
        }
        // Processor spans: use processorId with routeId context
        if (span.processorId() != null) {
            String label = span.processorId();
            if (span.routeId() != null) {
                label += " (" + span.routeId() + ")";
            }
            return label;
        }
        return name;
    }

    private int sortTrace(TraceSummary a, TraceSummary b, List<SpanEntry> currentSpans) {
        return switch (sortColumn) {
            case "route" -> compareNullable(a.rootRouteId, b.rootRouteId);
            case "from" -> compareNullable(a.rootName, b.rootName);
            case "duration" -> Long.compare(b.totalDurationMs, a.totalDurationMs);
            case "spans" -> Integer.compare(b.spanCount, a.spanCount);
            case "routes" -> Integer.compare(b.routeCount, a.routeCount);
            case "status" -> {
                int as = a.hasError ? 1 : 0;
                int bs = b.hasError ? 1 : 0;
                yield Integer.compare(bs, as);
            }
            default -> {
                // newest first
                long at = currentSpans.stream()
                        .filter(s -> s.traceId().equals(a.traceId))
                        .mapToLong(SpanEntry::startEpochNanos).max().orElse(0);
                long bt = currentSpans.stream()
                        .filter(s -> s.traceId().equals(b.traceId))
                        .mapToLong(SpanEntry::startEpochNanos).max().orElse(0);
                yield Long.compare(bt, at);
            }
        };
    }

    private String compactUri(SpanEntry span) {
        if (span.attributes() != null) {
            Object uri = span.attributes().get("camel.uri");
            if (uri != null) {
                String s = uri.toString();
                // strip scheme:// prefix to just scheme:path
                s = s.replace("://", ":");
                // strip query parameters
                int q = s.indexOf('?');
                if (q > 0) {
                    s = s.substring(0, q);
                }
                return s;
            }
        }
        return span.name();
    }

    private static boolean isRemoteScheme(String scheme) {
        return scheme != null
                && !"direct".equals(scheme) && !"seda".equals(scheme)
                && !"mock".equals(scheme) && !"log".equals(scheme)
                && !"bean".equals(scheme) && !"class".equals(scheme);
    }

    private static int compareNullable(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareToIgnoreCase(b);
    }

    private String sortLabel(String label, String column) {
        return sortLabel(label, column, sortColumn, sortReversed);
    }

    private Style sortStyle(String column) {
        return Style.EMPTY.fg(Color.YELLOW).bold();
    }

    private static int computeMaxDepth(List<SpanEntry> traceSpans) {
        Map<String, String> parentMap = new HashMap<>();
        for (SpanEntry s : traceSpans) {
            if (s.parentSpanId() != null && !s.parentSpanId().isEmpty()) {
                parentMap.put(s.spanId(), s.parentSpanId());
            }
        }
        int maxDepth = 0;
        for (SpanEntry s : traceSpans) {
            int depth = 0;
            String cur = s.spanId();
            while (parentMap.containsKey(cur)) {
                depth++;
                cur = parentMap.get(cur);
            }
            maxDepth = Math.max(maxDepth, depth);
        }
        return maxDepth + 1;
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    @Override
    public String getHelpText() {
        return """
                # OTel Spans

                The Spans tab shows OpenTelemetry traces captured from the running
                integration. Use `--observe` for lightweight Camel-only tracing, or
                `--open-telemetry-agent` for full auto-instrumentation (HTTP clients,
                JDBC, Kafka clients, etc.) via the OpenTelemetry Java Agent.

                ## Trace List

                The main view shows a table of traces with:

                - **TRACE-ID** — Short 8-character trace identifier
                - **ROUTE** — The root route that started the trace
                - **FROM** — The entry endpoint URI (e.g., timer:orders)
                - **SPANS** — Total number of spans in the trace
                - **ROUTES** — Number of distinct routes touched
                - **REMOTE** — External components used (kafka, http, sql, etc.)
                - **STATUS** — OK or ERROR
                - **DURATION** — Total trace duration (wall-clock envelope)
                - **DEPTH** — Maximum nesting depth of the span tree

                ## Waterfall View

                Press **Enter** on a trace to see the Jaeger-style waterfall showing
                the span tree with proportional duration bars. Each span shows its
                endpoint URI, processor ID, route context in parentheses, and duration.

                Indentation shows the parent-child relationship between spans. Duration
                bars are proportional to the trace envelope so you can visually spot
                where time is spent. Colors indicate relative duration: green (fast),
                yellow (medium), red (slow).

                ### Span Colors

                - **Cyan** — Camel spans (route execution, processors, endpoints)
                - **Magenta** — 3rd-party spans from the OTel Java Agent
                  (HTTP clients, JDBC, Kafka clients, gRPC, etc.)
                - **Red** — Error spans (regardless of source)

                The 3rd-party spans are only visible when using `--open-telemetry-agent`.
                The detail panel shows the **Source** field for agent-instrumented spans
                (e.g., `io.opentelemetry.jdk-http-client`).

                Processor spans (setBody, log, etc.) are shown by default. Press **p**
                to toggle them off for a cleaner view focused on endpoint-to-endpoint
                flow. Error spans are always shown regardless of the toggle.

                Press **Esc** to return to the trace list.

                Example waterfall for an order-processing integration:

                ```
                 Trace 4bb73039 — 15 spans, 4ms
                ▸ timer://orders (order-generator)       █████████████████████████████████  2ms
                    setBody1 (order-generator)            █  0ms
                    direct://process-order (process)      ████████████████  1ms
                      direct://validate-order (validate)  █  0ms
                        log2 (validate-order)             █  0ms
                      log1 (process-order)                           █  0ms
                      kafka://orders (order-dispatcher)                 ████████████████  1ms
                        log3 (order-dispatcher)                        █  0ms
                        multicast1 (order-dispatcher)                  █  0ms
                          kafka://fulfillment (fulfill)                           █  0ms
                            log4 (fulfillment)                                   █  0ms
                            kafka://warehouse (fulfill)                          █  0ms
                          kafka://notifications (notif)                          █  0ms
                            log5 (notification)                                  █  0ms
                            kafka://email-outbox (notif)                         █  0ms
                ```

                ## Keyboard Shortcuts

                | Key | Action |
                |-----|--------|
                | Enter | Drill into trace waterfall |
                | Esc | Back to list / clear filter |
                | / | Open filter input (matches trace ID, exchange ID, route, component) |
                | s | Cycle sort column (trace-id, route, from, spans, routes, status, duration) |
                | S | Reverse sort direction |
                | p | Toggle processor spans in waterfall |
                | c | Toggle camel-only (hide 3rd-party agent spans) |
                | F5 | Refresh span data |

                ## Filtering

                Press `/` to open the filter input. Type a search term and press Enter.
                Matches against trace ID, exchange ID, route names, and remote component
                names. For example, type `kafka` to find traces that use Kafka, or paste
                an exchange ID from a log line to find its trace.
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<TraceSummary> summaries = buildTraceSummaries();
        JsonObject result = new JsonObject();
        result.put("tab", "Spans");
        JsonArray rows = new JsonArray();
        for (TraceSummary ts : summaries) {
            JsonObject row = new JsonObject();
            row.put("traceId", ts.traceId);
            row.put("route", ts.rootRouteId);
            row.put("from", ts.rootName);
            row.put("spans", ts.spanCount);
            row.put("routes", ts.routeCount);
            row.put("remote", ts.remoteComponents);
            row.put("status", ts.hasError ? "ERROR" : "OK");
            row.put("duration", ts.totalDurationMs);
            row.put("depth", ts.maxDepth);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", summaries.size());
        Integer sel = traceListState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    static class TraceSummary {
        final String traceId;
        String rootRouteId;
        String rootName;
        int spanCount;
        long totalDurationMs;
        boolean hasError;
        int maxDepth;
        int routeCount;
        String remoteComponents;
        String searchText;

        TraceSummary(String traceId) {
            this.traceId = traceId;
        }
    }

    record WaterfallNode(SpanEntry span, int depth) {
    }
}
