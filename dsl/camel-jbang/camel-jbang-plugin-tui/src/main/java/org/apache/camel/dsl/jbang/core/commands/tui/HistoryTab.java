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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.layout.Alignment;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class HistoryTab implements MonitorTab {

    private static final String[] TRACE_SORT_COLUMNS = { "time", "route", "elapsed", "exchange" };

    private final MonitorContext ctx;
    private final AtomicReference<List<TraceEntry>> traces;
    private final Map<String, Long> traceFilePositions;

    private final TableState traceTableState = new TableState();
    private final TableState traceStepTableState = new TableState();
    private final ScrollbarState traceDetailScrollState = new ScrollbarState();
    private final ScrollbarState historyDetailScrollState = new ScrollbarState();

    private String traceSort = "time";
    private int traceSortIndex;
    private boolean traceSortReversed;
    private boolean traceDetailView;
    private volatile List<String> traceSortedExchangeIds = Collections.emptyList();
    private String traceSelectedExchangeId;
    private boolean showTraceProperties;
    private boolean showTraceVariables;
    private boolean showTraceHeaders = true;
    private boolean showTraceBody = true;
    private boolean traceWordWrap = true;
    private int traceDetailScroll;
    private int traceDetailHScroll;

    volatile List<HistoryEntry> historyEntries = Collections.emptyList();
    private final TableState historyTableState = new TableState();
    private boolean showHistoryProperties;
    private boolean showHistoryVariables;
    private boolean showHistoryHeaders = true;
    private boolean showHistoryBody = true;
    private boolean historyWordWrap = true;
    private int historyDetailScroll;
    private int historyDetailHScroll;

    HistoryTab(MonitorContext ctx,
               AtomicReference<List<TraceEntry>> traces,
               Map<String, Long> traceFilePositions) {
        this.ctx = ctx;
        this.traces = traces;
        this.traceFilePositions = traceFilePositions;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        boolean tracerActive = !traces.get().isEmpty();

        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            if (tracerActive && traceDetailView) {
                traceDetailScroll = Math.max(0, traceDetailScroll - 5);
            } else {
                historyDetailScroll = Math.max(0, historyDetailScroll - 5);
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            if (tracerActive && traceDetailView) {
                traceDetailScroll += 5;
            } else {
                historyDetailScroll += 5;
            }
            return true;
        }
        if (ke.isLeft()) {
            if (tracerActive && traceDetailView && !traceWordWrap) {
                traceDetailHScroll = Math.max(0, traceDetailHScroll - 4);
                return true;
            } else if (!historyWordWrap) {
                historyDetailHScroll = Math.max(0, historyDetailHScroll - 4);
                return true;
            }
        }
        if (ke.isRight()) {
            if (tracerActive && traceDetailView && !traceWordWrap) {
                traceDetailHScroll += 4;
                return true;
            } else if (!historyWordWrap) {
                historyDetailHScroll += 4;
                return true;
            }
        }

        if (tracerActive && traceDetailView) {
            if (ke.isCharIgnoreCase('p')) {
                showTraceProperties = !showTraceProperties;
                return true;
            }
            if (ke.isCharIgnoreCase('v')) {
                showTraceVariables = !showTraceVariables;
                return true;
            }
            if (ke.isCharIgnoreCase('h')) {
                showTraceHeaders = !showTraceHeaders;
                return true;
            }
            if (ke.isCharIgnoreCase('b')) {
                showTraceBody = !showTraceBody;
                return true;
            }
            if (ke.isCharIgnoreCase('w')) {
                traceWordWrap = !traceWordWrap;
                traceDetailScroll = 0;
                traceDetailHScroll = 0;
                return true;
            }
        } else if (tracerActive) {
            if (ke.isChar('s')) {
                traceSortIndex = (traceSortIndex + 1) % TRACE_SORT_COLUMNS.length;
                traceSort = TRACE_SORT_COLUMNS[traceSortIndex];
                traceSortReversed = false;
                return true;
            }
            if (ke.isChar('S')) {
                traceSortReversed = !traceSortReversed;
                return true;
            }
            if (ke.isConfirm()) {
                Integer sel = traceTableState.selected();
                if (sel != null && sel >= 0 && sel < traceSortedExchangeIds.size()) {
                    traceSelectedExchangeId = traceSortedExchangeIds.get(sel);
                    traceDetailView = true;
                    traceStepTableState.select(0);
                    traceDetailScroll = 0;
                }
                return true;
            }
            if (ke.isKey(KeyCode.F5)) {
                if (ctx.selectedPid != null) {
                    traceFilePositions.clear();
                    traces.set(Collections.emptyList());
                }
                return true;
            }
        } else {
            if (ke.isCharIgnoreCase('p')) {
                showHistoryProperties = !showHistoryProperties;
                return true;
            }
            if (ke.isCharIgnoreCase('v')) {
                showHistoryVariables = !showHistoryVariables;
                return true;
            }
            if (ke.isCharIgnoreCase('h')) {
                showHistoryHeaders = !showHistoryHeaders;
                return true;
            }
            if (ke.isCharIgnoreCase('b')) {
                showHistoryBody = !showHistoryBody;
                return true;
            }
            if (ke.isCharIgnoreCase('w')) {
                historyWordWrap = !historyWordWrap;
                historyDetailScroll = 0;
                historyDetailHScroll = 0;
                return true;
            }
            if (ke.isKey(KeyCode.F5)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (traceDetailView) {
            traceDetailView = false;
            traceSelectedExchangeId = null;
            traceDetailScroll = 0;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (!traces.get().isEmpty()) {
            if (traceDetailView) {
                traceStepTableState.selectPrevious();
                traceDetailScroll = 0;
            } else {
                traceTableState.selectPrevious();
            }
        } else {
            historyTableState.selectPrevious();
            historyDetailScroll = 0;
        }
    }

    @Override
    public void navigateDown() {
        if (!traces.get().isEmpty()) {
            if (traceDetailView) {
                List<TraceEntry> steps = getTraceSteps(traceSelectedExchangeId);
                traceStepTableState.selectNext(steps.size());
                traceDetailScroll = 0;
            } else {
                List<String> exchangeIds = getTraceExchangeIds();
                traceTableState.selectNext(exchangeIds.size());
            }
        } else {
            historyTableState.selectNext(historyEntries.size());
            historyDetailScroll = 0;
        }
    }

    @Override
    public void onTabSelected() {
        if (!historyEntries.isEmpty()) {
            historyTableState.select(0);
        }
        traceDetailView = false;
        traceSelectedExchangeId = null;
        if (!traces.get().isEmpty()) {
            traceTableState.select(0);
        }
    }

    boolean isTraceDetailView() {
        return traceDetailView;
    }

    boolean isTracerActive() {
        return !traces.get().isEmpty();
    }

    boolean needsF5Refresh() {
        return true;
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        boolean tracerActive = !traces.get().isEmpty();
        if (!tracerActive) {
            renderHistory(frame, area);
            return;
        }

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(12), Constraint.fill())
                .split(area);

        renderTraceExchangeList(frame, chunks.get(0));

        if (traceDetailView && traceSelectedExchangeId != null) {
            renderTraceExchangeDetail(frame, chunks.get(1));
        } else {
            renderHistoryDetail(frame, chunks.get(1), historyEntries);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        boolean tracerActive = !traces.get().isEmpty();
        if (tracerActive && traceDetailView) {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "navigate");
            hint(spans, "PgUp/PgDn", "scroll detail");
            if (!traceWordWrap) {
                hint(spans, "←→", "h-scroll");
            }
            hint(spans, "p", "properties" + (showTraceProperties ? " [on]" : " [off]"));
            hint(spans, "v", "variables" + (showTraceVariables ? " [on]" : " [off]"));
            hint(spans, "h", "headers" + (showTraceHeaders ? " [on]" : " [off]"));
            hint(spans, "b", "body" + (showTraceBody ? " [on]" : " [off]"));
            hintLast(spans, "w", "wrap" + (traceWordWrap ? " [on]" : " [off]"));
        } else if (tracerActive) {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "Enter", "details");
            hintLast(spans, "F5", "refresh");
        } else {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "navigate");
            hint(spans, "PgUp/PgDn", "scroll detail");
            if (!historyWordWrap) {
                hint(spans, "←→", "h-scroll");
            }
            hint(spans, "p", "properties" + (showHistoryProperties ? " [on]" : " [off]"));
            hint(spans, "v", "variables" + (showHistoryVariables ? " [on]" : " [off]"));
            hint(spans, "h", "headers" + (showHistoryHeaders ? " [on]" : " [off]"));
            hint(spans, "b", "body" + (showHistoryBody ? " [on]" : " [off]"));
            hint(spans, "w", "wrap" + (historyWordWrap ? " [on]" : " [off]"));
            hintLast(spans, "F5", "refresh");
        }
    }

    // ---- Trace rendering ----

    private void renderTraceExchangeList(Frame frame, Rect area) {
        List<String> exchangeIds = getTraceExchangeIds();

        List<TraceEntry> current = traces.get();
        record ExchangeSummary(String exchangeId, String timestamp, long epochMs, String routeId,
                String status, long elapsed, int steps) {
        }
        List<ExchangeSummary> summaries = new ArrayList<>();
        for (String exchangeId : exchangeIds) {
            TraceEntry first = null;
            TraceEntry lastEntry = null;
            TraceEntry latestEntry = null;
            int stepCount = 0;
            for (TraceEntry e : current) {
                if (exchangeId.equals(e.exchangeId)) {
                    if (first == null) {
                        first = e;
                    }
                    latestEntry = e;
                    if (e.last) {
                        lastEntry = e;
                    }
                    stepCount++;
                }
            }
            if (first != null) {
                TraceEntry doneEntry = lastEntry != null ? lastEntry : latestEntry;
                String status = doneEntry.status != null ? doneEntry.status : "Processing";
                long elapsed = doneEntry.elapsed;
                summaries.add(new ExchangeSummary(
                        exchangeId, first.timestamp, first.epochMs,
                        first.routeId, status, elapsed, stepCount));
            }
        }

        summaries.sort((a, b) -> {
            int result = switch (traceSort) {
                case "time" -> Long.compare(b.epochMs, a.epochMs);
                case "route" -> {
                    String ra = a.routeId != null ? a.routeId : "";
                    String rb = b.routeId != null ? b.routeId : "";
                    yield ra.compareToIgnoreCase(rb);
                }
                case "elapsed" -> Long.compare(b.elapsed, a.elapsed);
                case "exchange" -> a.exchangeId.compareTo(b.exchangeId);
                default -> 0;
            };
            return traceSortReversed ? -result : result;
        });

        traceSortedExchangeIds = summaries.stream().map(ExchangeSummary::exchangeId).toList();

        List<Row> rows = new ArrayList<>();
        for (ExchangeSummary s : summaries) {
            Style statusStyle = switch (s.status) {
                case "Done" -> Style.EMPTY.fg(Color.GREEN);
                case "Failed" -> Style.EMPTY.fg(Color.LIGHT_RED);
                case "Processing" -> Style.EMPTY.fg(Color.YELLOW);
                default -> Style.EMPTY;
            };
            rows.add(Row.from(
                    Cell.from(s.timestamp != null ? truncate(s.timestamp, 12) : ""),
                    Cell.from(Span.styled(
                            s.routeId != null ? truncate(s.routeId, 15) : "",
                            Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(s.status, statusStyle)),
                    rightCell(s.elapsed + "ms", 10),
                    rightCell(String.valueOf(s.steps), 6),
                    Cell.from(s.exchangeId)));
        }

        Row header = Row.from(
                Cell.from(Span.styled(traceSortLabel("TIME", "time"), traceSortStyle("time"))),
                Cell.from(Span.styled(traceSortLabel("ROUTE", "route"), traceSortStyle("route"))),
                Cell.from(Span.styled("STATUS", Style.EMPTY.bold())),
                rightCell(traceSortLabel("ELAPSED", "elapsed"), 10, traceSortStyle("elapsed")),
                rightCell("STEPS", 6, Style.EMPTY.bold()),
                Cell.from(Span.styled(traceSortLabel("EXCHANGE", "exchange"), traceSortStyle("exchange"))));

        String traceTitle = String.format(" Traces [%d] sort:%s ", summaries.size(), traceSort);

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(12),
                        Constraint.length(15),
                        Constraint.length(12),
                        Constraint.length(10),
                        Constraint.length(6),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(traceTitle).build())
                .build();

        frame.renderStatefulWidget(table, area, traceTableState);
    }

    private void renderTraceExchangeDetail(Frame frame, Rect area) {
        List<TraceEntry> steps = getTraceSteps(traceSelectedExchangeId);

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(10), Constraint.fill())
                .split(area);

        List<Row> rows = new ArrayList<>();
        for (TraceEntry entry : steps) {
            rows.add(buildStepRow(
                    entry.direction, entry.first, entry.last, entry.failed,
                    entry.timestamp, entry.routeId, entry.nodeId, entry.processor, entry.elapsed));
        }

        String stepTitle = String.format(" Trace [%s] ", truncate(traceSelectedExchangeId, 30));
        frame.renderStatefulWidget(
                buildStepTable(rows, stepTitle), chunks.get(0), traceStepTableState);

        renderTraceStepDetail(frame, chunks.get(1), steps);
    }

    private void renderTraceStepDetail(Frame frame, Rect area, List<TraceEntry> steps) {
        Integer sel = traceStepTableState.selected();

        if (sel == null || sel < 0 || sel >= steps.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a trace step to view details",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).build())
                            .build(),
                    area);
            return;
        }

        TraceEntry entry = steps.get(sel);
        List<Line> lines = new ArrayList<>();

        addExchangeInfoLines(lines, entry.exchangeId, entry.routeId, entry.nodeId, entry.nodeLabel,
                entry.location, entry.elapsed, entry.threadName, entry.failed);
        if (showTraceProperties) {
            addKvLines(lines, " Exchange Properties:", entry.exchangeProperties, entry.exchangePropertyTypes);
        }
        if (showTraceVariables) {
            addKvLines(lines, " Exchange Variables:", entry.exchangeVariables, entry.exchangeVariableTypes);
        }
        if (showTraceHeaders) {
            addKvLines(lines, " Headers:", entry.headers, entry.headerTypes);
        }
        if (showTraceBody) {
            addBodyLines(lines, entry.body, entry.bodyType);
        }
        addExceptionLines(lines, entry.exception);

        int[] scroll = { traceDetailScroll };
        int[] hScroll = { traceDetailHScroll };
        renderDetailPanel(frame, area, lines, traceWordWrap, hScroll, scroll, traceDetailScrollState);
        traceDetailScroll = scroll[0];
        traceDetailHScroll = hScroll[0];
    }

    // ---- History (Last) rendering ----

    private void renderHistory(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<HistoryEntry> current = historyEntries;

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(10), Constraint.fill())
                .split(area);

        List<Row> rows = new ArrayList<>();
        for (HistoryEntry entry : current) {
            rows.add(buildStepRow(
                    entry.direction, entry.first, entry.last, entry.failed,
                    entry.timestamp, entry.routeId, entry.nodeId, entry.processor, entry.elapsed));
        }

        Title historyTitle = buildHistoryTitle(current);
        frame.renderStatefulWidget(
                buildStepTable(rows, historyTitle), chunks.get(0), historyTableState);

        renderHistoryDetail(frame, chunks.get(1), current);
    }

    private void renderHistoryDetail(Frame frame, Rect area, List<HistoryEntry> current) {
        Integer sel = historyTableState.selected();

        if (sel == null || sel < 0 || sel >= current.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a history entry to view details",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        HistoryEntry entry = current.get(sel);
        List<Line> lines = new ArrayList<>();

        addExchangeInfoLines(lines, entry.exchangeId, entry.routeId, entry.nodeId, entry.nodeLabel,
                entry.location, entry.elapsed, entry.threadName, entry.failed);
        if (showHistoryProperties) {
            addKvLines(lines, " Exchange Properties:", entry.exchangeProperties, entry.exchangePropertyTypes);
        }
        if (showHistoryVariables) {
            addKvLines(lines, " Exchange Variables:", entry.exchangeVariables, entry.exchangeVariableTypes);
        }
        if (showHistoryHeaders) {
            addKvLines(lines, " Headers:", entry.headers, entry.headerTypes);
        }
        if (showHistoryBody) {
            addBodyLines(lines, entry.body, entry.bodyType);
        }
        addExceptionLines(lines, entry.exception);

        int[] scroll = { historyDetailScroll };
        int[] hScroll = { historyDetailHScroll };
        renderDetailPanel(frame, area, lines, historyWordWrap, hScroll, scroll, historyDetailScrollState);
        historyDetailScroll = scroll[0];
        historyDetailHScroll = hScroll[0];
    }

    // ---- Shared helpers ----

    private List<String> getTraceExchangeIds() {
        List<TraceEntry> current = traces.get();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (TraceEntry e : current) {
            if (e.exchangeId != null) {
                seen.add(e.exchangeId);
            }
        }
        return new ArrayList<>(seen);
    }

    private List<TraceEntry> getTraceSteps(String exchangeId) {
        List<TraceEntry> current = traces.get();
        List<TraceEntry> steps = new ArrayList<>();
        for (TraceEntry e : current) {
            if (exchangeId != null && exchangeId.equals(e.exchangeId)) {
                steps.add(e);
            }
        }
        steps.sort((a, b) -> {
            String ua = a.uid != null ? a.uid : "";
            String ub = b.uid != null ? b.uid : "";
            try {
                return Long.compare(Long.parseLong(ua), Long.parseLong(ub));
            } catch (NumberFormatException e) {
                return ua.compareTo(ub);
            }
        });
        return steps;
    }

    private String traceSortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, traceSort, traceSortReversed);
    }

    private Style traceSortStyle(String column) {
        return MonitorContext.sortStyle(column, traceSort);
    }

    private static Row buildStepRow(
            String direction, boolean first, boolean last, boolean failed,
            String timestamp, String routeId, String nodeId, String processor, long elapsed) {
        Style dirStyle;
        if (first) {
            dirStyle = Style.EMPTY.fg(Color.GREEN);
        } else if (last) {
            dirStyle = failed ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.GREEN);
        } else {
            dirStyle = failed ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY;
        }
        String elapsedStr = elapsed >= 0 ? elapsed + "ms" : "";
        return Row.from(
                Cell.from(Span.styled(direction, dirStyle)),
                Cell.from(timestamp != null ? truncate(timestamp, 12) : ""),
                Cell.from(Span.styled(routeId != null ? truncate(routeId, 15) : "", Style.EMPTY.fg(Color.CYAN))),
                Cell.from(nodeId != null ? truncate(nodeId, 15) : ""),
                Cell.from(processor != null ? processor : ""),
                rightCell(elapsedStr, 10));
    }

    private static Table buildStepTable(List<Row> rows, Object title) {
        Row header = Row.from(
                Cell.from(Span.styled("", Style.EMPTY.bold())),
                Cell.from(Span.styled("TIME", Style.EMPTY.bold())),
                Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                Cell.from(Span.styled("ID", Style.EMPTY.bold())),
                Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                rightCell("ELAPSED", 10, Style.EMPTY.bold()));
        Block block = title instanceof Title t
                ? Block.builder().borderType(BorderType.ROUNDED).title(t).build()
                : Block.builder().borderType(BorderType.ROUNDED).title(title.toString()).build();
        return Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(4),
                        Constraint.length(12),
                        Constraint.length(15),
                        Constraint.length(15),
                        Constraint.fill(),
                        Constraint.length(10))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(block)
                .build();
    }

    private static Title buildHistoryTitle(List<HistoryEntry> entries) {
        if (entries.isEmpty()) {
            return Title.from(" History of last completed ");
        }
        HistoryEntry first = entries.get(0);
        HistoryEntry last = null;
        for (HistoryEntry e : entries) {
            if (e.last) {
                last = e;
                break;
            }
        }
        if (last == null) {
            last = entries.get(entries.size() - 1);
        }

        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" History of last completed ("));
        boolean failed = last.failed;
        spans.add(Span.styled("status:" + (failed ? "failed" : "success"),
                failed ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY.fg(Color.GREEN).bold()));
        if (last.elapsed >= 0) {
            spans.add(Span.raw(" elapsed:" + TimeUtils.printDuration(last.elapsed, true)));
        }
        if (first.epochMs > 0) {
            String ago = TimeUtils.printSince(first.epochMs);
            spans.add(Span.raw(" ago:" + ago));
        }
        spans.add(Span.raw(") "));
        return new Title(Line.from(spans), Alignment.LEFT, Overflow.CLIP);
    }

    static void addExchangeInfoLines(
            List<Line> lines, String exchangeId, String routeId,
            String nodeId, String nodeLabel, String location, long elapsed, String threadName, boolean failed) {
        lines.add(Line.from(
                Span.styled(" Exchange: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(exchangeId != null ? exchangeId : "")));
        lines.add(Line.from(
                Span.styled(" Route:    ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(routeId != null ? routeId : ""),
                Span.styled("  Node: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(nodeId != null ? nodeId : ""),
                Span.raw(nodeLabel != null ? " (" + nodeLabel + ")" : "")));
        lines.add(Line.from(
                Span.styled(" Location: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(location != null ? location : "")));
        lines.add(Line.from(
                Span.styled(" Elapsed:  ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(elapsed >= 0 ? elapsed + "ms" : ""),
                Span.styled("  Thread: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(threadName != null ? threadName : "")));
        if (failed) {
            lines.add(Line.from(
                    Span.styled(" Status:   ", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled("Failed", Style.EMPTY.fg(Color.LIGHT_RED).bold())));
        }
        lines.add(Line.from(Span.raw("")));
    }

    static void addKvLines(
            List<Line> lines, String section,
            Map<String, Object> map, Map<String, String> types) {
        if (map == null || map.isEmpty()) {
            return;
        }
        lines.add(Line.from(Span.styled(section, Style.EMPTY.fg(Color.GREEN).bold())));
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String type = types != null ? types.get(entry.getKey()) : null;
            String typeLabel;
            if (type != null) {
                String t = "(" + type + ")";
                t = TuiHelper.truncateStart(t, 20);
                typeLabel = String.format("%-20s ", t);
            } else {
                typeLabel = String.format("%-21s", "");
            }
            String val = entry.getValue() != null ? entry.getValue().toString() : "null";
            try {
                val = Jsoner.unescape(val);
            } catch (Exception e) {
                // ignore
            }
            val = stripControlChars(val);
            lines.add(Line.from(
                    Span.styled("   " + typeLabel, Style.EMPTY.dim()),
                    Span.styled(entry.getKey(), Style.EMPTY.fg(Color.CYAN)),
                    Span.raw(" = "),
                    Span.raw(val)));
        }
        lines.add(Line.from(Span.raw("")));
    }

    static void addBodyLines(List<Line> lines, String body, String bodyType) {
        if (body != null) {
            if (bodyType != null) {
                lines.add(Line.from(
                        Span.styled(" Body: ", Style.EMPTY.fg(Color.GREEN).bold()),
                        Span.styled("(" + bodyType + ")", Style.EMPTY.dim())));
            } else {
                lines.add(Line.from(Span.styled(" Body:", Style.EMPTY.fg(Color.GREEN).bold())));
            }
            try {
                body = Jsoner.unescape(body);
            } catch (Exception e) {
                // ignore
            }
            String[] bodyParts = body.split("\n");
            for (String bl : bodyParts) {
                lines.add(Line.from(Span.raw("   " + stripControlChars(bl))));
            }
        } else {
            lines.add(Line.from(Span.styled(" Body is null", Style.EMPTY.fg(Color.GREEN).bold())));
        }
        lines.add(Line.from(Span.raw("")));
    }

    static String stripControlChars(String s) {
        if (s == null) {
            return s;
        }
        boolean needsStrip = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 0x20 || (ch >= 0x7F && ch <= 0x9F)) {
                needsStrip = true;
                break;
            }
        }
        if (!needsStrip) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\t') {
                sb.append("    ");
            } else if (ch < 0x20 || (ch >= 0x7F && ch <= 0x9F)) {
                // skip C0 and C1 control chars
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    static void addExceptionLines(List<Line> lines, String exception) {
        if (exception == null) {
            return;
        }
        lines.add(Line.from(Span.styled(" Exception:", Style.EMPTY.fg(Color.LIGHT_RED).bold())));
        for (String l : exception.split("\n", -1)) {
            lines.add(Line.from(Span.raw("   " + TuiHelper.fixControlChars(l))));
        }
        lines.add(Line.from(Span.raw("")));
    }

    static void renderDetailPanel(
            Frame frame, Rect area, List<Line> lines,
            boolean wordWrap, int[] hScroll, int[] scroll, ScrollbarState scrollState) {
        Block block = Block.builder().borderType(BorderType.ROUNDED).build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        int visibleHeight = Math.max(1, inner.height());
        int visibleWidth = Math.max(1, inner.width() - 1);
        int contentHeight;
        if (wordWrap) {
            contentHeight = 0;
            for (Line l : lines) {
                int w = l.width();
                contentHeight += Math.max(1, (w + visibleWidth - 1) / visibleWidth);
            }
        } else {
            contentHeight = lines.size();
        }
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        if (scroll[0] > maxScroll) {
            scroll[0] = maxScroll;
        }

        if (!wordWrap) {
            int maxLineWidth = lines.stream().mapToInt(Line::width).max().orElse(0);
            hScroll[0] = Math.min(hScroll[0], Math.max(0, maxLineWidth - visibleWidth));
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Line> visibleLines = (!wordWrap && hScroll[0] > 0) ? applyHSkip(lines, hScroll[0]) : lines;
        Paragraph detail = Paragraph.builder()
                .text(Text.from(visibleLines))
                .overflow(wordWrap ? Overflow.WRAP_WORD : Overflow.CLIP)
                .scroll(scroll[0])
                .build();
        frame.renderWidget(detail, hChunks.get(0));

        if (contentHeight > visibleHeight) {
            scrollState.contentLength(contentHeight);
            scrollState.viewportContentLength(visibleHeight);
            scrollState.position(scroll[0]);
            frame.renderStatefulWidget(
                    Scrollbar.builder().build(),
                    hChunks.get(1), scrollState);
        }
    }

    private static List<Line> applyHSkip(List<Line> lines, int hSkip) {
        List<Line> result = new ArrayList<>(lines.size());
        for (Line line : lines) {
            result.add(hSkipLine(line, hSkip));
        }
        return result;
    }

    private static Line hSkipLine(Line line, int hSkip) {
        List<Span> result = new ArrayList<>();
        int skip = hSkip;
        for (Span span : line.spans()) {
            if (skip <= 0) {
                result.add(span);
                continue;
            }
            String text = span.content();
            int spanWidth = CharWidth.of(text);
            if (spanWidth <= skip) {
                skip -= spanWidth;
            } else {
                int i = 0;
                int consumed = 0;
                while (i < text.length() && consumed < skip) {
                    int cp = text.codePointAt(i);
                    consumed += CharWidth.of(cp);
                    i += Character.charCount(cp);
                }
                skip = 0;
                String remaining = text.substring(i);
                if (!remaining.isEmpty()) {
                    result.add(Span.styled(remaining, span.style()));
                }
            }
        }
        return Line.from(result);
    }

    @Override
    public SelectionContext getSelectionContext() {
        boolean tracerActive = !traces.get().isEmpty();
        if (tracerActive) {
            if (traceDetailView) {
                List<TraceEntry> steps = getTraceSteps(traceSelectedExchangeId);
                if (steps.isEmpty()) {
                    return null;
                }
                List<String> items = steps.stream()
                        .map(s -> s.nodeId != null ? s.nodeId : "")
                        .toList();
                Integer sel = traceStepTableState.selected();
                return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Trace Steps");
            }
            List<String> exchangeIds = getTraceExchangeIds();
            if (exchangeIds.isEmpty()) {
                return null;
            }
            Integer sel = traceTableState.selected();
            return new SelectionContext("table", exchangeIds, sel != null ? sel : -1, exchangeIds.size(), "Traces");
        }
        if (historyEntries.isEmpty()) {
            return null;
        }
        List<String> items = historyEntries.stream()
                .map(h -> h.exchangeId != null ? h.exchangeId : "")
                .toList();
        Integer sel = historyTableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "History");
    }
}
