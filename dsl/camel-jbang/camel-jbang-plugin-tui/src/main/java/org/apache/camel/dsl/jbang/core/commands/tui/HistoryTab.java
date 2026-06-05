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
import java.util.HashMap;
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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
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

    private boolean showDescription;

    private boolean showWaterfall;
    private int waterfallScroll;
    private final ScrollbarState waterfallScrollState = new ScrollbarState();

    private final DiagramSupport diagram = new DiagramSupport();

    volatile List<HistoryEntry> historyEntries = Collections.emptyList();
    private final TableState historyTableState = new TableState();
    private boolean showHistoryProperties;
    private boolean showHistoryVariables;
    private boolean showHistoryHeaders = true;
    private boolean showHistoryBody = true;
    private boolean historyWordWrap = true;
    private int historyDetailScroll;
    private int historyDetailHScroll;
    volatile boolean historyRefreshRequested;

    HistoryTab(MonitorContext ctx,
               AtomicReference<List<TraceEntry>> traces,
               Map<String, Long> traceFilePositions) {
        this.ctx = ctx;
        this.traces = traces;
        this.traceFilePositions = traceFilePositions;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (diagram.isShowDiagram() && diagram.isHistoryMode() && diagram.hasHistoryData()) {
            if (diagram.isHistoryTopologyMode()) {
                if (ke.isUp()) {
                    diagram.selectNodeUp();
                    diagram.scrollToSelectedNode();
                    return true;
                }
                if (ke.isDown()) {
                    diagram.selectNodeDown();
                    diagram.scrollToSelectedNode();
                    return true;
                }
                if (ke.isLeft()) {
                    diagram.selectNodeLeft();
                    diagram.scrollToSelectedNode();
                    return true;
                }
                if (ke.isRight()) {
                    diagram.selectNodeRight();
                    diagram.scrollToSelectedNode();
                    return true;
                }
                if (ke.isConfirm()) {
                    diagram.historyEnterDrillDown();
                    return true;
                }
            } else {
                if (ke.isUp()) {
                    diagram.historyNavigateUp();
                    return true;
                }
                if (ke.isDown()) {
                    diagram.historyNavigateDown();
                    return true;
                }
                if (ke.isLeft()) {
                    diagram.scrollLeft();
                    return true;
                }
                if (ke.isRight()) {
                    diagram.scrollRight();
                    return true;
                }
                if (ke.isChar('t')) {
                    diagram.historyReturnToTopology();
                    return true;
                }
            }
            if (ke.isHome()) {
                diagram.scrollHome();
                return true;
            }
            if (ke.isCharIgnoreCase('n')) {
                showDescription = !showDescription;
                diagram.setShowDescription(showDescription);
                diagram.endLoad();
                loadDiagramForCurrentView();
                return true;
            }
        }
        if (diagram.handleScrollKeys(ke)) {
            return true;
        }
        if (ke.isCharIgnoreCase('d')) {
            diagram.toggleDiagram(this::loadDiagramForCurrentView);
            return true;
        }

        boolean tracerActive = !traces.get().isEmpty();

        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            if (tracerActive && traceDetailView) {
                if (showWaterfall) {
                    for (int i = 0; i < 10; i++) {
                        traceStepTableState.selectPrevious();
                    }
                } else {
                    traceDetailScroll = Math.max(0, traceDetailScroll - 5);
                }
            } else {
                if (showWaterfall) {
                    for (int i = 0; i < 10; i++) {
                        historyTableState.selectPrevious();
                    }
                } else {
                    historyDetailScroll = Math.max(0, historyDetailScroll - 5);
                }
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            if (tracerActive && traceDetailView) {
                if (showWaterfall) {
                    List<TraceEntry> steps = getTraceSteps(traceSelectedExchangeId);
                    for (int i = 0; i < 10; i++) {
                        traceStepTableState.selectNext(steps.size());
                    }
                } else {
                    traceDetailScroll += 5;
                }
            } else {
                if (showWaterfall) {
                    for (int i = 0; i < 10; i++) {
                        historyTableState.selectNext(historyEntries.size());
                    }
                } else {
                    historyDetailScroll += 5;
                }
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

        if (ke.isCharIgnoreCase('n')) {
            showDescription = !showDescription;
            return true;
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
            if (ke.isCharIgnoreCase('g')) {
                showWaterfall = !showWaterfall;
                waterfallScroll = 0;
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
            if (ke.isCharIgnoreCase('g')) {
                showWaterfall = !showWaterfall;
                waterfallScroll = 0;
                return true;
            }
            if (ke.isKey(KeyCode.F5)) {
                historyEntries = Collections.emptyList();
                historyDetailScroll = 0;
                historyDetailHScroll = 0;
                historyRefreshRequested = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public void onIntegrationChanged() {
        historyEntries = Collections.emptyList();
        historyRefreshRequested = true;
        historyDetailScroll = 0;
        historyDetailHScroll = 0;
        traceFilePositions.clear();
        traces.set(Collections.emptyList());
        traceDetailView = false;
        traceSelectedExchangeId = null;
        traceDetailScroll = 0;
        traceDetailHScroll = 0;
        showWaterfall = false;
        waterfallScroll = 0;
        diagram.reset();
    }

    @Override
    public boolean handleEscape() {
        if (diagram.isShowDiagram() && diagram.isHistoryMode() && !diagram.isHistoryTopologyMode()) {
            diagram.historyGoBack();
            return true;
        }
        if (diagram.handleEscape()) {
            return true;
        }
        if (traceDetailView) {
            traceDetailView = false;
            traceSelectedExchangeId = null;
            traceDetailScroll = 0;
            showWaterfall = false;
            waterfallScroll = 0;
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

        if (diagram.isShowDiagram() && diagram.isHistoryMode() && diagram.hasHistoryData()) {
            if (diagram.isHistoryTopologyMode()) {
                Line title = Line.from(Span.styled(
                        String.format(" History Topology — step %d/%d ",
                                diagram.getHistoryStepIndex() + 1, diagram.getHistoryStepCount()),
                        Style.EMPTY.fg(Color.WHITE)));
                diagram.renderHistoryTopologyDiagram(frame, area, title);
            } else {
                String routeId = diagram.getHistoryDrillDownRouteId();
                Line title = buildHistoryBreadcrumbTitle();
                diagram.renderHistoryRouteDiagram(frame, area, title, routeId);
            }
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
        if (diagram.isShowDiagram()) {
            if (diagram.isHistoryMode() && diagram.hasHistoryData()) {
                if (diagram.isHistoryTopologyMode()) {
                    hint(spans, "Esc", "close");
                    hint(spans, "↑↓←→", "navigate");
                    hint(spans, "Enter", "drill-down");
                    hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
                } else {
                    hint(spans, "Esc", "back");
                    hint(spans, "↑↓", "step through path");
                    hint(spans, "←→", "h-scroll");
                    hint(spans, "t", "topology");
                    hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
                }
                return;
            }
            diagram.renderFooterHints(spans);
            return;
        }
        boolean tracerActive = !traces.get().isEmpty();
        if (tracerActive && traceDetailView) {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "navigate");
            hint(spans, "PgUp/PgDn", "scroll");
            if (!showWaterfall && !traceWordWrap) {
                hint(spans, "←→", "h-scroll");
            }
            hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
            hint(spans, "g", "waterfall" + (showWaterfall ? " [on]" : ""));
            hint(spans, "d", "diagram");
            if (!showWaterfall) {
                hint(spans, "p", "properties" + (showTraceProperties ? " [on]" : " [off]"));
                hint(spans, "v", "variables" + (showTraceVariables ? " [on]" : " [off]"));
                hint(spans, "h", "headers" + (showTraceHeaders ? " [on]" : " [off]"));
                hint(spans, "b", "body" + (showTraceBody ? " [on]" : " [off]"));
            }
            hintLast(spans, "w", "wrap" + (traceWordWrap ? " [on]" : " [off]"));
        } else if (tracerActive) {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
            hint(spans, "d", "diagram");
            hint(spans, "Enter", "details");
            hintLast(spans, "F5", "refresh");
        } else {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "navigate");
            hint(spans, "PgUp/PgDn", "scroll");
            if (!showWaterfall && !historyWordWrap) {
                hint(spans, "←→", "h-scroll");
            }
            hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
            hint(spans, "g", "waterfall" + (showWaterfall ? " [on]" : ""));
            hint(spans, "d", "diagram");
            if (!showWaterfall) {
                hint(spans, "p", "properties" + (showHistoryProperties ? " [on]" : " [off]"));
                hint(spans, "v", "variables" + (showHistoryVariables ? " [on]" : " [off]"));
                hint(spans, "h", "headers" + (showHistoryHeaders ? " [on]" : " [off]"));
                hint(spans, "b", "body" + (showHistoryBody ? " [on]" : " [off]"));
                hint(spans, "w", "wrap" + (historyWordWrap ? " [on]" : " [off]"));
            }
            hintLast(spans, "F5", "refresh");
        }
    }

    private Line buildHistoryBreadcrumbTitle() {
        Style nameStyle = Style.EMPTY.fg(Color.YELLOW).bold();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" History [", Style.EMPTY.fg(Color.WHITE)));
        var stack = diagram.getHistoryNavigationStack();
        if (stack.isEmpty()) {
            spans.add(Span.styled(diagram.getHistoryDrillDownRouteId(), nameStyle));
        } else {
            for (var it = stack.descendingIterator(); it.hasNext();) {
                spans.add(Span.styled(it.next(), nameStyle));
                spans.add(Span.styled(" → ", Style.EMPTY.fg(Color.GRAY)));
            }
            spans.add(Span.styled(diagram.getHistoryDrillDownRouteId(), nameStyle));
        }
        spans.add(Span.styled(String.format("] — step %d/%d ",
                diagram.getHistoryStepIndex() + 1, diagram.getHistoryStepCount()),
                Style.EMPTY.fg(Color.WHITE)));
        return Line.from(spans);
    }

    // ---- Diagram loading ----

    private void loadDiagramForCurrentView() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!diagram.beginLoad()) {
            return;
        }

        String[] messageHistory;
        boolean failed;
        int initialStep = -1;

        boolean tracerActive = !traces.get().isEmpty();
        if (tracerActive) {
            String exchangeId;
            if (traceDetailView && traceSelectedExchangeId != null) {
                exchangeId = traceSelectedExchangeId;
            } else {
                Integer sel = traceTableState.selected();
                if (sel != null && sel >= 0 && sel < traceSortedExchangeIds.size()) {
                    exchangeId = traceSortedExchangeIds.get(sel);
                } else {
                    diagram.endLoad();
                    return;
                }
            }
            List<TraceEntry> steps = getTraceSteps(exchangeId);
            if (steps.isEmpty()) {
                diagram.endLoad();
                return;
            }
            messageHistory = new String[steps.size()];
            for (int i = 0; i < steps.size(); i++) {
                TraceEntry e = steps.get(i);
                messageHistory[i] = (e.routeId != null ? e.routeId : "") + "[" + (e.nodeId != null ? e.nodeId : "") + "]";
            }
            TraceEntry lastStep = steps.get(steps.size() - 1);
            failed = lastStep.failed;
            if (traceDetailView) {
                Integer sel = traceStepTableState.selected();
                if (sel != null && sel >= 0 && sel < steps.size()) {
                    initialStep = sel;
                }
            }
        } else {
            List<HistoryEntry> entries = historyEntries;
            if (entries.isEmpty()) {
                diagram.endLoad();
                return;
            }
            messageHistory = new String[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                HistoryEntry e = entries.get(i);
                messageHistory[i] = (e.routeId != null ? e.routeId : "") + "[" + (e.nodeId != null ? e.nodeId : "") + "]";
            }
            HistoryEntry lastEntry = entries.get(entries.size() - 1);
            for (HistoryEntry e : entries) {
                if (e.last) {
                    lastEntry = e;
                    break;
                }
            }
            failed = lastEntry.failed;
            Integer sel = historyTableState.selected();
            if (sel != null && sel >= 0 && sel < entries.size()) {
                initialStep = sel;
            }
        }

        String pid = ctx.selectedPid;

        diagram.setLoadingPlaceholder();

        boolean isFailed = failed;
        int step = initialStep;
        ctx.runner.scheduler().execute(() -> {
            try {
                diagram.loadHighlightedNativeDiagramInBackground(ctx, pid, messageHistory, isFailed, step);
            } finally {
                diagram.endLoad();
            }
        });
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
                            s.routeId != null ? truncate(s.routeId, 25) : "",
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
                .constraints(Constraint.length(10), Constraint.length(1), Constraint.fill())
                .split(area);

        Map<String, String> descMap = showDescription ? getRouteDescriptions() : Collections.emptyMap();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            TraceEntry entry = steps.get(i);
            String desc = showDescription ? descMap.get(entry.routeId) : null;
            rows.add(buildStepRow(i + 1,
                    entry.direction, entry.first, entry.last, entry.failed,
                    entry.timestamp, entry.routeId, entry.nodeId, entry.processor, desc, entry.elapsed));
        }

        String stepTitle = String.format(" Trace [%s] — %d steps ", truncate(traceSelectedExchangeId, 30), steps.size());
        frame.renderStatefulWidget(
                buildStepTable(rows, stepTitle, showDescription), chunks.get(0), traceStepTableState);

        if (showWaterfall) {
            Integer sel = traceStepTableState.selected();
            renderWaterfall(frame, chunks.get(2), steps.stream().map(WaterfallStep::fromTrace).toList(),
                    sel != null ? sel : -1);
        } else {
            renderTraceStepDetail(frame, chunks.get(2), steps);
        }
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

    record WaterfallStep(String nodeId, String processor, String direction, boolean first, boolean last,
            int nodeLevel, long elapsed) {

        static WaterfallStep fromTrace(TraceEntry e) {
            return new WaterfallStep(e.nodeId, e.processor, e.direction, e.first, e.last, e.nodeLevel, e.elapsed);
        }

        static WaterfallStep fromHistory(HistoryEntry e) {
            return new WaterfallStep(e.nodeId, e.processor, e.direction, e.first, e.last, e.nodeLevel, e.elapsed);
        }

        WaterfallStep withElapsed(long newElapsed) {
            return new WaterfallStep(nodeId, processor, direction, first, last, nodeLevel, newElapsed);
        }

        String label() {
            if (nodeId != null && !nodeId.isEmpty()) {
                return nodeId;
            }
            if (processor != null) {
                return processor.stripLeading();
            }
            return "";
        }
    }

    private void renderWaterfall(Frame frame, Rect area, List<WaterfallStep> allSteps, int selectedIndex) {
        // Copy the elapsed from matching last entries onto first entries
        // (first entries have elapsed=0, the total is on the last entry)
        List<WaterfallStep> forward = new ArrayList<>();
        // Map original allSteps index to forward index for selection highlight
        int selectedForwardIndex = -1;
        for (int idx = 0; idx < allSteps.size(); idx++) {
            WaterfallStep e = allSteps.get(idx);
            if ("<--".equals(e.direction)) {
                continue;
            }
            if (idx == selectedIndex) {
                selectedForwardIndex = forward.size();
            }
            if (e.first) {
                long totalElapsed = e.elapsed;
                for (WaterfallStep other : allSteps) {
                    if (other.last && nodeIdEquals(e.nodeId, other.nodeId)) {
                        totalElapsed = other.elapsed;
                        break;
                    }
                }
                forward.add(totalElapsed != e.elapsed ? e.withElapsed(totalElapsed) : e);
            } else {
                forward.add(e);
            }
        }

        if (forward.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  No steps to display.", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Waterfall ").build())
                            .build(),
                    area);
            return;
        }

        long maxElapsed = 0;
        long minDuration = Long.MAX_VALUE;
        long maxDuration = 0;
        for (WaterfallStep e : forward) {
            maxElapsed = Math.max(maxElapsed, e.elapsed);
            if (!e.first) {
                minDuration = Math.min(minDuration, e.elapsed);
                maxDuration = Math.max(maxDuration, e.elapsed);
            }
        }
        if (minDuration == Long.MAX_VALUE) {
            minDuration = 0;
        }

        String title = String.format(" Waterfall — %d steps ", forward.size());
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(title)
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (inner.height() < 1 || inner.width() < 10) {
            return;
        }

        int visibleLines = inner.height();
        int maxScroll = Math.max(0, forward.size() - visibleLines);
        waterfallScroll = Math.min(waterfallScroll, maxScroll);

        int labelWidth = 0;
        for (WaterfallStep e : forward) {
            int indent = e.nodeLevel * 2;
            labelWidth = Math.max(labelWidth, indent + e.label().length());
        }
        labelWidth = Math.min(labelWidth + 2, inner.width() / 3);

        int barMaxWidth = Math.max(10, inner.width() - labelWidth - 12);

        // Auto-scroll to keep selected step visible
        if (selectedForwardIndex >= 0) {
            if (selectedForwardIndex < waterfallScroll) {
                waterfallScroll = selectedForwardIndex;
            } else if (selectedForwardIndex >= waterfallScroll + visibleLines) {
                waterfallScroll = selectedForwardIndex - visibleLines + 1;
            }
        }

        int end = Math.min(waterfallScroll + visibleLines, forward.size());
        List<Line> lines = new ArrayList<>();
        for (int i = waterfallScroll; i < end; i++) {
            lines.add(renderWaterfallStep(forward.get(i), labelWidth, barMaxWidth,
                    maxElapsed, minDuration, maxDuration, i == selectedForwardIndex));
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), hChunks.get(0));

        if (forward.size() > visibleLines) {
            waterfallScrollState
                    .contentLength(forward.size())
                    .viewportContentLength(visibleLines)
                    .position(waterfallScroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), waterfallScrollState);
        }
    }

    private static Line renderWaterfallStep(
            WaterfallStep entry, int labelWidth, int maxBarWidth,
            long maxElapsed, long minDuration, long maxDuration, boolean selected) {
        String indicator = selected ? ">> " : "   ";
        String indent = "  ".repeat(entry.nodeLevel);
        String label = indent + entry.label();
        if (label.length() > labelWidth) {
            label = label.substring(0, labelWidth - 1) + "…";
        } else {
            label = String.format("%-" + labelWidth + "s", label);
        }

        boolean isRoute = entry.first;
        Style bandStyle = isRoute ? Style.EMPTY.dim() : TuiHelper.colorForDuration(entry.elapsed, minDuration, maxDuration);

        double ratio = maxElapsed > 0 ? (double) entry.elapsed / maxElapsed : 0;
        int barWidth = Math.max(1, (int) Math.round(ratio * maxBarWidth));
        String bar = "█".repeat(barWidth);

        String durationStr = entry.elapsed + "ms";
        int pad = Math.max(1, 8 - durationStr.length());

        Style labelStyle = selected ? Style.EMPTY.fg(Color.CYAN).bold() : Style.EMPTY.fg(Color.CYAN);

        return Line.from(
                Span.styled(indicator, Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(label, labelStyle),
                Span.styled(bar, bandStyle),
                Span.raw(" ".repeat(pad)),
                Span.styled(durationStr, isRoute ? Style.EMPTY.dim() : Style.EMPTY.fg(Color.WHITE).bold()));
    }

    private static boolean nodeIdEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
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
                .constraints(Constraint.length(10), Constraint.length(1), Constraint.fill())
                .split(area);

        Map<String, String> descMap = showDescription ? getRouteDescriptions() : Collections.emptyMap();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            HistoryEntry entry = current.get(i);
            String desc = showDescription ? descMap.get(entry.routeId) : null;
            rows.add(buildStepRow(i + 1,
                    entry.direction, entry.first, entry.last, entry.failed,
                    entry.timestamp, entry.routeId, entry.nodeId, entry.processor, desc, entry.elapsed));
        }

        Title historyTitle = buildHistoryTitle(current);
        frame.renderStatefulWidget(
                buildStepTable(rows, historyTitle, showDescription), chunks.get(0), historyTableState);

        if (showWaterfall) {
            Integer sel = historyTableState.selected();
            renderWaterfall(frame, chunks.get(2), current.stream().map(WaterfallStep::fromHistory).toList(),
                    sel != null ? sel : -1);
        } else {
            renderHistoryDetail(frame, chunks.get(2), current);
        }
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

    private Map<String, String> getRouteDescriptions() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (RouteInfo ri : info.routes) {
            if (ri.routeId != null && ri.description != null && !ri.description.isEmpty()) {
                map.put(ri.routeId, ri.description);
            }
        }
        return map;
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
            int stepNumber,
            String direction, boolean first, boolean last, boolean failed,
            String timestamp, String routeId, String nodeId, String processor,
            String description, long elapsed) {
        Style dirStyle;
        if (first || last || !direction.isBlank()) {
            dirStyle = failed ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.GREEN);
        } else {
            dirStyle = failed ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY;
        }
        String elapsedStr = elapsed >= 0 ? elapsed + "ms" : "";
        String display = description != null ? description : (processor != null ? processor : "");
        return Row.from(
                rightCell(String.valueOf(stepNumber), 3),
                Cell.from(Span.styled(direction, dirStyle)),
                Cell.from(timestamp != null ? truncate(timestamp, 12) : ""),
                Cell.from(Span.styled(routeId != null ? truncate(routeId, 25) : "", Style.EMPTY.fg(Color.CYAN))),
                Cell.from(nodeId != null ? truncate(nodeId, 15) : ""),
                Cell.from(display),
                rightCell(elapsedStr, 10));
    }

    private static Table buildStepTable(List<Row> rows, Object title, boolean descriptionMode) {
        Row header = Row.from(
                rightCell("#", 3, Style.EMPTY.bold()),
                Cell.from(Span.styled("", Style.EMPTY.bold())),
                Cell.from(Span.styled("TIME", Style.EMPTY.bold())),
                Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                Cell.from(Span.styled("ID", Style.EMPTY.bold())),
                Cell.from(Span.styled(descriptionMode ? "DESCRIPTION" : "PROCESSOR", Style.EMPTY.bold())),
                rightCell("ELAPSED", 10, Style.EMPTY.bold()));
        Block block = title instanceof Title t
                ? Block.builder().borderType(BorderType.ROUNDED).title(t).build()
                : Block.builder().borderType(BorderType.ROUNDED).title(title.toString()).build();
        return Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(3),
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
        spans.add(Span.raw(" History of last completed — " + entries.size() + " steps ("));
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
            // word-wrap breaks at word boundaries which can produce more lines
            // than char-based math; add padding so last section is always reachable
            contentHeight += visibleHeight;
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

    @Override
    public String getHelpText() {
        return """
                # Inspect

                The Inspect tab shows a history of recently processed exchanges
                (messages). This is one of the most powerful debugging tools — it
                lets you see exactly what happened to each message as it traveled
                through the integration, including every step it passed through.

                Camel uses a BacklogTracer to record exchange details. The most
                recent exchanges are kept in memory for inspection.

                ## Exchange List

                - **ID** — Unique exchange identifier (e.g., `ID-myhost-1234-5`). Every message passing through Camel gets a unique ID
                - **STATUS** — Whether the exchange completed successfully (`done`) or failed (`fail`). Failed exchanges also appear on the Errors tab
                - **ROUTE** — The route that processed this exchange
                - **AGO** — How long ago this exchange was processed (e.g., `2s`, `1m`, `5m`)
                - **ELAPSED** — Total processing time from when the exchange entered the route until it completed. Long elapsed times may indicate slow downstream services

                ## Example Screen

                ```
                 ID                   STATUS  ROUTE          AGO  ELAPSED
                 ID-myhost-1234-10    done    timer-to-log   1s   0ms
                 ID-myhost-1234-9     done    seda-consumer  2s   0ms
                 ID-myhost-1234-8     done    timer-to-seda  2s   1ms
                 ID-myhost-1234-7     fail    kafka-route    5s   5023ms
                ```

                The last exchange (`kafka-route`) failed after 5 seconds — likely a
                connection timeout to the Kafka broker.

                ## Detail View

                Press `Enter` on an exchange to see its full journey:

                **Message History** — A step-by-step trace of every node the exchange
                visited. This shows the exact path the message took through the route,
                including which branch was taken in a `choice` and how long each step
                took:

                ```
                      RouteId        NodeId     Processor            Elapsed
                 *->  timer-to-log   timer1     from[timer:hello]    0ms
                      timer-to-log   setBody1   setBody[simple]      0ms
                      timer-to-log   choice1    choice               0ms
                      timer-to-log   when1      when[simple]         0ms
                 --->  timer-to-log   to1       to[kafka:orders]     2ms
                      timer-to-log   log1       log[HIGH: ${body}]   0ms
                 <-*  timer-to-log   timer1     from[timer:hello]    3ms
                ```

                This tells you the message entered via the timer, went through setBody,
                reached a choice node, matched the `when` condition, and was logged.
                The elapsed time for each step helps identify bottlenecks.

                ## Direction Arrows

                The first column shows direction arrows that indicate the type
                of each step:

                - `*-->` — First step of a route consuming from a **remote** endpoint (e.g., Kafka, HTTP)
                - `*-> ` — First step of a route consuming from a **local** endpoint (e.g., timer, direct)
                - `<--*` — Last step of a route with a **remote** consumer endpoint
                - `<-* ` — Last step of a route with a **local** consumer endpoint
                - `--->` — A step that sends to a **remote** endpoint (e.g., `to[kafka:orders]`)
                - `~-->` — First step or send to a **stub** endpoint (running with `--stub` mode)
                - `<--~` — Last step of a route with a **stub** consumer endpoint
                - _(blank)_ — A regular processing step (log, setBody, choice, etc.)

                **Exchange Content** — Toggle these sections to inspect the message:

                - `h` — **Headers**: Key-value pairs carried with the message (e.g., `Content-Type`, `CamelFileName`, custom headers)
                - `b` — **Body**: The actual message content (text, JSON, XML, etc.)
                - `p` — **Properties**: Exchange-level metadata (not forwarded to endpoints, used for internal routing)
                - `v` — **Variables**: Exchange variables set during processing

                ## Use Cases

                - **Debugging routing logic**: Check which branch a `choice` or `filter` took
                - **Verifying transformations**: Compare body before and after a `transform` or `marshal` step
                - **Finding bottlenecks**: Look for steps with high elapsed times
                - **Understanding failures**: See exactly where in the route a failure occurred

                ## Route Diagram

                Press `d` to open the route diagram for the selected exchange.
                The diagram shows the route structure as a visual flowchart with
                box-drawing characters, highlighting the path the exchange took
                through the route in green (or red for failed exchanges).

                **Progressive Path Highlighting** — Use `Up/Down` to step through
                the exchange's journey node by node. As you navigate forward, each
                visited node lights up progressively in green, creating a visual
                replay of the message's path. Stepping backward removes the
                highlight from the last node. The currently selected node is
                shown with a dark background.

                **Multi-route exchanges** — When an exchange spans multiple routes
                (e.g., via `direct` or `seda` endpoints), all involved routes are
                shown stacked vertically. The diagram auto-scrolls to keep the
                current step visible.

                **Route Structure Preview** — A compact tree view appears in the
                bottom-right corner showing the full route hierarchy. The currently
                selected node is highlighted, helping you maintain orientation in
                large routes. This is the same minimap available on the Routes and
                Diagram tabs.

                Press `Esc` to close the diagram and return to the exchange list.

                ## Keys

                - `Up/Down` — select exchange (or step through path in diagram)
                - `Enter` — view exchange details
                - `d` — toggle route diagram
                - `n` — toggle description mode
                - `g` — toggle waterfall view
                - `h` — toggle headers
                - `b` — toggle body
                - `p` — toggle properties
                - `v` — toggle variables
                - `w` — toggle word wrap
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `Left/Right` — horizontal scroll (diagram or detail)
                - `PgUp/PgDn` — page scroll
                - `F5` — refresh data
                - `Esc` — back to list / close diagram
                """;
    }

    void selectTraceExchange(String exchangeId) {
        if (exchangeId != null && traceSortedExchangeIds.contains(exchangeId)) {
            traceSelectedExchangeId = exchangeId;
            traceDetailView = true;
            traceStepTableState.select(0);
            traceDetailScroll = 0;
        }
    }

    @Override
    public JsonObject getTableDataAsJson() {
        JsonObject result = new JsonObject();
        boolean tracerActive = !traces.get().isEmpty();
        if (tracerActive) {
            if (traceDetailView && traceSelectedExchangeId != null) {
                result.put("tab", "Trace Steps");
                List<TraceEntry> steps = getTraceSteps(traceSelectedExchangeId);
                JsonArray rows = new JsonArray();
                for (TraceEntry t : steps) {
                    JsonObject row = new JsonObject();
                    row.put("exchangeId", t.exchangeId);
                    row.put("routeId", t.routeId);
                    row.put("nodeId", t.nodeId);
                    row.put("processor", t.processor);
                    row.put("elapsed", t.elapsed);
                    row.put("timestamp", t.timestamp);
                    row.put("direction", t.direction);
                    row.put("status", t.status);
                    row.put("failed", t.failed);
                    row.put("first", t.first);
                    row.put("last", t.last);
                    if (t.body != null) {
                        row.put("body", t.body);
                    }
                    if (t.bodyType != null) {
                        row.put("bodyType", t.bodyType);
                    }
                    if (t.exception != null) {
                        row.put("exception", t.exception);
                    }
                    if (t.nodeLabel != null) {
                        row.put("nodeLabel", t.nodeLabel);
                    }
                    if (t.nodeShortName != null) {
                        row.put("nodeShortName", t.nodeShortName);
                    }
                    if (t.location != null) {
                        row.put("location", t.location);
                    }
                    if (t.threadName != null) {
                        row.put("threadName", t.threadName);
                    }
                    row.put("nodeLevel", t.nodeLevel);
                    if (t.headers != null && !t.headers.isEmpty()) {
                        row.put("headers", new JsonObject(t.headers));
                    }
                    if (t.headerTypes != null && !t.headerTypes.isEmpty()) {
                        row.put("headerTypes", new JsonObject(t.headerTypes));
                    }
                    if (t.exchangeProperties != null && !t.exchangeProperties.isEmpty()) {
                        row.put("exchangeProperties", new JsonObject(t.exchangeProperties));
                    }
                    if (t.exchangeVariables != null && !t.exchangeVariables.isEmpty()) {
                        row.put("exchangeVariables", new JsonObject(t.exchangeVariables));
                    }
                    rows.add(row);
                }
                result.put("rows", rows);
                result.put("totalRows", steps.size());
                result.put("exchangeId", traceSelectedExchangeId);
                Integer sel = traceStepTableState.selected();
                result.put("selectedIndex", sel != null ? sel : -1);
            } else {
                result.put("tab", "Traces");
                List<String> exchangeIds = getTraceExchangeIds();
                JsonArray rows = new JsonArray();
                for (String eid : exchangeIds) {
                    JsonObject row = new JsonObject();
                    row.put("exchangeId", eid);
                    rows.add(row);
                }
                result.put("rows", rows);
                result.put("totalRows", exchangeIds.size());
                Integer sel = traceTableState.selected();
                result.put("selectedIndex", sel != null ? sel : -1);
            }
        } else {
            result.put("tab", "History");
            List<HistoryEntry> entries = historyEntries;
            JsonArray rows = new JsonArray();
            for (int i = 0; i < entries.size(); i++) {
                HistoryEntry h = entries.get(i);
                JsonObject row = new JsonObject();
                row.put("step", i + 1);
                row.put("exchangeId", h.exchangeId);
                row.put("routeId", h.routeId);
                row.put("nodeId", h.nodeId);
                row.put("processor", h.processor);
                row.put("elapsed", h.elapsed);
                row.put("timestamp", h.timestamp);
                row.put("direction", h.direction);
                row.put("first", h.first);
                row.put("last", h.last);
                row.put("failed", h.failed);
                if (h.body != null) {
                    row.put("body", h.body);
                }
                if (h.bodyType != null) {
                    row.put("bodyType", h.bodyType);
                }
                if (h.exception != null) {
                    row.put("exception", h.exception);
                }
                if (h.nodeLabel != null) {
                    row.put("nodeLabel", h.nodeLabel);
                }
                if (h.nodeShortName != null) {
                    row.put("nodeShortName", h.nodeShortName);
                }
                if (h.location != null) {
                    row.put("location", h.location);
                }
                if (h.threadName != null) {
                    row.put("threadName", h.threadName);
                }
                row.put("nodeLevel", h.nodeLevel);
                if (h.fromRouteId != null) {
                    row.put("fromRouteId", h.fromRouteId);
                }
                if (h.headers != null && !h.headers.isEmpty()) {
                    row.put("headers", new JsonObject(h.headers));
                }
                if (h.headerTypes != null && !h.headerTypes.isEmpty()) {
                    row.put("headerTypes", new JsonObject(h.headerTypes));
                }
                if (h.exchangeProperties != null && !h.exchangeProperties.isEmpty()) {
                    row.put("exchangeProperties", new JsonObject(h.exchangeProperties));
                }
                if (h.exchangeVariables != null && !h.exchangeVariables.isEmpty()) {
                    row.put("exchangeVariables", new JsonObject(h.exchangeVariables));
                }
                rows.add(row);
            }
            result.put("rows", rows);
            result.put("totalRows", entries.size());
            Integer sel = historyTableState.selected();
            result.put("selectedIndex", sel != null ? sel : -1);
        }
        return result;
    }
}
