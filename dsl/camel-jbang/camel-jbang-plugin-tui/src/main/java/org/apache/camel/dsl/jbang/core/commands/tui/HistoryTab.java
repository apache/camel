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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.layout.Alignment;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
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

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class HistoryTab extends AbstractTab {

    private static final String[] TRACE_SORT_COLUMNS = { "time", "route", "elapsed", "exchange" };
    private static final int MOUSE_SCROLL_LINES = 1;

    private static final Comparator<TraceEntry> UID_COMPARATOR = (a, b) -> {
        String ua = a.uid != null ? a.uid : "";
        String ub = b.uid != null ? b.uid : "";
        try {
            return Long.compare(Long.parseLong(ua), Long.parseLong(ub));
        } catch (NumberFormatException e) {
            return ua.compareTo(ub);
        }
    };

    private final AtomicReference<List<TraceEntry>> traces;
    private final Map<String, Long> traceFilePositions;

    private final TableState traceTableState = new TableState();
    private Rect lastTraceTableArea;
    private Rect lastHistoryTableArea;
    private Rect lastTraceStepArea;
    private final TableState traceStepTableState = new TableState();
    private final ScrollbarState traceDetailScrollState = new ScrollbarState();
    private final ScrollbarState historyDetailScrollState = new ScrollbarState();
    private final ScrollbarState tableScrollState = new ScrollbarState();
    private final ScrollbarState traceStepScrollState = new ScrollbarState();
    private final ScrollbarState historyTableScrollState = new ScrollbarState();

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

    private static final int INFO_NARROW = 0;
    private static final int INFO_WIDE = 1;
    private static final int INFO_FULL = 2;

    private List<TraceEntry> diagramTraceSteps = Collections.emptyList();
    private List<HistoryEntry> diagramHistorySteps = Collections.emptyList();
    private int infoPanelSize = INFO_NARROW;

    private int traceTopPanelHeight = 12;
    private int traceDetailTopHeight = 10;
    private int historyTopPanelHeight = 10;
    private final DragSplit vSplit = new DragSplit();
    private final DragSplit detailSplit = new DragSplit();

    volatile List<HistoryEntry> historyEntries = Collections.emptyList();
    private volatile int historyVisibleCount;
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
        super(ctx);
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
            boolean isTraceMode = !diagramTraceSteps.isEmpty();
            if (ke.isChar('b')) {
                if (isTraceMode) {
                    showTraceBody = !showTraceBody;
                } else {
                    showHistoryBody = !showHistoryBody;
                }
                return true;
            }
            if (ke.isChar('h')) {
                if (isTraceMode) {
                    showTraceHeaders = !showTraceHeaders;
                } else {
                    showHistoryHeaders = !showHistoryHeaders;
                }
                return true;
            }
            if (ke.isChar('p')) {
                if (isTraceMode) {
                    showTraceProperties = !showTraceProperties;
                } else {
                    showHistoryProperties = !showHistoryProperties;
                }
                return true;
            }
            if (ke.isChar('v')) {
                if (isTraceMode) {
                    showTraceVariables = !showTraceVariables;
                } else {
                    showHistoryVariables = !showHistoryVariables;
                }
                return true;
            }
            if (ke.isChar('w')) {
                if (isTraceMode) {
                    traceWordWrap = !traceWordWrap;
                } else {
                    historyWordWrap = !historyWordWrap;
                }
                return true;
            }
            if (ke.isChar('i')) {
                infoPanelSize = (infoPanelSize + 1) % 3;
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
                    List<TraceEntry> steps = getTraceStepsDepthFirst(traceSelectedExchangeId);
                    for (int i = 0; i < 10; i++) {
                        traceStepTableState.selectNext(steps.size());
                    }
                } else {
                    traceDetailScroll += 5;
                }
            } else {
                if (showWaterfall) {
                    for (int i = 0; i < 10; i++) {
                        historyTableState.selectNext(historyVisibleCount);
                    }
                } else {
                    historyDetailScroll += 5;
                }
            }
            return true;
        }
        if (ke.isHome()) {
            if (tracerActive && traceDetailView) {
                traceStepTableState.selectFirst();
                traceDetailScroll = 0;
            } else if (tracerActive) {
                traceTableState.selectFirst();
            } else {
                historyTableState.selectFirst();
                historyDetailScroll = 0;
            }
            return true;
        }
        if (ke.isEnd()) {
            if (tracerActive && traceDetailView) {
                List<TraceEntry> steps = getTraceStepsDepthFirst(traceSelectedExchangeId);
                traceStepTableState.selectLast(steps.size());
                traceDetailScroll = 0;
            } else if (tracerActive) {
                traceTableState.selectLast(traceSortedExchangeIds.size());
            } else {
                historyTableState.selectLast(historyVisibleCount);
                historyDetailScroll = 0;
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
        if (diagram.isShowDiagram() && diagram.isHistoryMode()) {
            if (!diagram.isHistoryTopologyMode()) {
                diagram.historyGoBack();
            }
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
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (!diagram.isShowDiagram()) {
            if (detailSplit.handleMouse(me, me.y())) {
                if (detailSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                    boolean tracerActive = !traces.get().isEmpty();
                    int detailAreaY = area.y() + (tracerActive ? traceTopPanelHeight : historyTopPanelHeight);
                    int detailAreaHeight = area.height() - (tracerActive ? traceTopPanelHeight : historyTopPanelHeight);
                    int newHeight = me.y() - detailAreaY;
                    traceDetailTopHeight = Math.max(3, Math.min(newHeight, detailAreaHeight - 5));
                }
                return true;
            }
            if (vSplit.handleMouse(me, me.y())) {
                if (vSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                    int newHeight = me.y() - area.y();
                    newHeight = Math.max(3, Math.min(newHeight, area.height() - 5));
                    boolean tracerActive = !traces.get().isEmpty();
                    if (tracerActive) {
                        traceTopPanelHeight = newHeight;
                    } else {
                        historyTopPanelHeight = newHeight;
                    }
                }
                return true;
            }
        }

        if (diagram.isShowDiagram()) {
            if (diagram.handleMouseScroll(me)) {
                return true;
            }
            if (me.isClick()) {
                if (diagram.isHistoryMode() && diagram.isHistoryTopologyMode()) {
                    int clicked = diagram.handleNodeClick(me);
                    if (clicked >= 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean tracerActive = !traces.get().isEmpty();
        if (tracerActive && !traceDetailView) {
            if (handleTableClick(me, lastTraceTableArea, traceTableState, traceSortedExchangeIds.size())) {
                return true;
            }
        }
        if (tracerActive && traceDetailView) {
            List<TraceEntry> steps = getTraceStepsDepthFirst(traceSelectedExchangeId);
            if (handleTableClick(me, lastTraceStepArea, traceStepTableState, steps.size())) {
                traceDetailScroll = 0;
                return true;
            }
        }
        if (!tracerActive) {
            if (handleTableClick(me, lastHistoryTableArea, historyTableState, historyVisibleCount)) {
                historyDetailScroll = 0;
                return true;
            }
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            if (tracerActive && traceDetailView) {
                if (isInArea(me, lastTraceTableArea)) {
                    traceTableState.selectPrevious();
                } else if (isInArea(me, lastTraceStepArea) || showWaterfall) {
                    traceStepTableState.selectPrevious();
                    traceDetailScroll = 0;
                } else {
                    traceDetailScroll = Math.max(0, traceDetailScroll - MOUSE_SCROLL_LINES);
                }
            } else if (tracerActive) {
                if (isInArea(me, lastTraceTableArea)) {
                    traceTableState.selectPrevious();
                } else {
                    historyDetailScroll = Math.max(0, historyDetailScroll - MOUSE_SCROLL_LINES);
                }
            } else {
                if (isInArea(me, lastHistoryTableArea) || showWaterfall) {
                    historyTableState.selectPrevious();
                    historyDetailScroll = 0;
                } else {
                    historyDetailScroll = Math.max(0, historyDetailScroll - MOUSE_SCROLL_LINES);
                }
            }
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            if (tracerActive && traceDetailView) {
                if (isInArea(me, lastTraceTableArea)) {
                    traceTableState.selectNext(traceSortedExchangeIds.size());
                } else if (isInArea(me, lastTraceStepArea) || showWaterfall) {
                    List<TraceEntry> steps = getTraceStepsDepthFirst(traceSelectedExchangeId);
                    traceStepTableState.selectNext(steps.size());
                    traceDetailScroll = 0;
                } else {
                    traceDetailScroll += MOUSE_SCROLL_LINES;
                }
            } else if (tracerActive) {
                if (isInArea(me, lastTraceTableArea)) {
                    traceTableState.selectNext(traceSortedExchangeIds.size());
                } else {
                    historyDetailScroll += MOUSE_SCROLL_LINES;
                }
            } else {
                if (isInArea(me, lastHistoryTableArea) || showWaterfall) {
                    historyTableState.selectNext(historyVisibleCount);
                    historyDetailScroll = 0;
                } else {
                    historyDetailScroll += MOUSE_SCROLL_LINES;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isInArea(MouseEvent me, Rect area) {
        return area != null && me.y() >= area.y() && me.y() < area.y() + area.height();
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
                List<TraceEntry> steps = getTraceStepsDepthFirst(traceSelectedExchangeId);
                traceStepTableState.selectNext(steps.size());
                traceDetailScroll = 0;
            } else {
                List<String> exchangeIds = getTraceExchangeIds();
                traceTableState.selectNext(exchangeIds.size());
            }
        } else {
            historyTableState.selectNext(historyVisibleCount);
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
            if (infoPanelSize == INFO_FULL) {
                renderDiagramInfoPanel(frame, area);
            } else {
                Rect diagramArea = area;
                Rect infoArea = null;
                if (area.width() > 70) {
                    int panelWidth = infoPanelSize == INFO_WIDE ? area.width() / 2 : 35;
                    List<Rect> hParts = Layout.horizontal()
                            .constraints(Constraint.length(panelWidth), Constraint.fill())
                            .split(area);
                    infoArea = hParts.get(0);
                    diagramArea = hParts.get(1);
                }
                boolean hideOverlays = infoPanelSize == INFO_WIDE;
                if (diagram.isHistoryTopologyMode()) {
                    Line title = Line.from(Span.styled(
                            String.format(" History Topology — step %d/%d ",
                                    diagram.getHistoryStepIndex() + 1, diagram.getHistoryStepCount()),
                            Style.EMPTY.fg(Theme.baseFg())));
                    diagram.renderHistoryTopologyDiagram(frame, diagramArea, title);
                } else {
                    String routeId = diagram.getHistoryDrillDownRouteId();
                    Line title = buildHistoryBreadcrumbTitle();
                    diagram.renderHistoryRouteDiagram(frame, diagramArea, title, routeId, hideOverlays);
                }
                if (infoArea != null) {
                    renderDiagramInfoPanel(frame, infoArea);
                }
            }
            return;
        }

        boolean tracerActive = !traces.get().isEmpty();
        if (!tracerActive) {
            renderHistory(frame, area);
            return;
        }

        traceTopPanelHeight = Math.max(3, Math.min(traceTopPanelHeight, area.height() - 5));
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(traceTopPanelHeight), Constraint.fill())
                .split(area);

        renderTraceExchangeList(frame, chunks.get(0));
        vSplit.setBorderPos(chunks.get(1).y());

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
                boolean isTraceMode = !diagramTraceSteps.isEmpty();
                boolean sb = isTraceMode ? showTraceBody : showHistoryBody;
                boolean sh = isTraceMode ? showTraceHeaders : showHistoryHeaders;
                boolean sp = isTraceMode ? showTraceProperties : showHistoryProperties;
                boolean sv = isTraceMode ? showTraceVariables : showHistoryVariables;
                boolean sw = isTraceMode ? traceWordWrap : historyWordWrap;
                String infoLabel = switch (infoPanelSize) {
                    case INFO_WIDE -> "info [wide]";
                    case INFO_FULL -> "info [full]";
                    default -> "info [narrow]";
                };
                if (diagram.isHistoryTopologyMode()) {
                    hint(spans, "d", "close");
                    hint(spans, TuiIcons.HINT_NAV, "navigate");
                    hint(spans, "Enter", "drill-down");
                    hint(spans, "i", infoLabel);
                    hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
                    hintShowBhpv(spans, sb, sh, sp, sv);
                    hintLast(spans, "w", "wrap" + (sw ? " [on]" : " [off]"));
                } else {
                    hint(spans, "d", "close");
                    hint(spans, "Esc", "back");
                    hint(spans, TuiIcons.HINT_SCROLL, "step through path");
                    hint(spans, TuiIcons.HINT_H, "h-scroll");
                    hint(spans, "t", "topology");
                    hint(spans, "i", infoLabel);
                    hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
                    hintShowBhpv(spans, sb, sh, sp, sv);
                    hintLast(spans, "w", "wrap" + (sw ? " [on]" : " [off]"));
                }
                return;
            }
            diagram.renderFooterHints(spans);
            return;
        }
        boolean tracerActive = !traces.get().isEmpty();
        if (tracerActive && traceDetailView) {
            hint(spans, "Esc", "back");
            hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            hint(spans, "PgUp/PgDn", "scroll");
            if (!showWaterfall && !traceWordWrap) {
                hint(spans, TuiIcons.HINT_H, "h-scroll");
            }
            hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
            hint(spans, "g", "waterfall" + (showWaterfall ? " [on]" : ""));
            hint(spans, "d", "diagram");
            if (!showWaterfall) {
                hintShowBhpv(spans, showTraceBody, showTraceHeaders, showTraceProperties, showTraceVariables);
            }
            hintLast(spans, "w", "wrap" + (traceWordWrap ? " [on]" : " [off]"));
        } else if (tracerActive) {
            hint(spans, "Esc", "back");
            hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            hint(spans, "s", "sort");
            hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
            hint(spans, "d", "diagram");
            hint(spans, "Enter", "details");
            hintLast(spans, "F5", "refresh");
        } else {
            hint(spans, "Esc", "back");
            hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            hint(spans, "PgUp/PgDn", "scroll");
            if (!showWaterfall && !historyWordWrap) {
                hint(spans, TuiIcons.HINT_H, "h-scroll");
            }
            hint(spans, "n", "description" + (showDescription ? " [on]" : ""));
            hint(spans, "g", "waterfall" + (showWaterfall ? " [on]" : ""));
            hint(spans, "d", "diagram");
            if (!showWaterfall) {
                hintShowBhpv(spans, showHistoryBody, showHistoryHeaders, showHistoryProperties, showHistoryVariables);
                hint(spans, "w", "wrap" + (historyWordWrap ? " [on]" : " [off]"));
            }
            hintLast(spans, "F5", "refresh");
        }
    }

    private Line buildHistoryBreadcrumbTitle() {
        Style nameStyle = Theme.label().bold();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" History [", Style.EMPTY.fg(Theme.baseFg())));
        var stack = diagram.getHistoryNavigationStack();
        if (stack.isEmpty()) {
            spans.add(Span.styled(diagram.getHistoryDrillDownRouteId(), nameStyle));
        } else {
            for (var it = stack.descendingIterator(); it.hasNext();) {
                spans.add(Span.styled(it.next(), nameStyle));
                spans.add(Span.styled(" → ", Theme.muted()));
            }
            spans.add(Span.styled(diagram.getHistoryDrillDownRouteId(), nameStyle));
        }
        spans.add(Span.styled(String.format("] — step %d/%d ",
                diagram.getHistoryStepIndex() + 1, diagram.getHistoryStepCount()),
                Style.EMPTY.fg(Theme.baseFg())));
        return Line.from(spans);
    }

    private void renderDiagramInfoPanel(Frame frame, Rect area) {
        int stepIdx = diagram.getHistoryStepIndex();
        List<Line> lines = new ArrayList<>();

        String exchangeId = null;
        String routeId = null;
        String nodeId = null;
        String processor = null;
        String direction = null;
        String timestamp = null;
        String threadName = null;
        long elapsed = -1;
        boolean failed = false;
        String body = null;
        String bodyType = null;
        String exception = null;
        Map<String, Object> headers = null;
        Map<String, Object> properties = null;
        Map<String, Object> variables = null;
        Map<String, Object> prevHeaders = null;
        Map<String, Object> prevProperties = null;
        Map<String, Object> prevVariables = null;

        if (!diagramTraceSteps.isEmpty() && stepIdx >= 0 && stepIdx < diagramTraceSteps.size()) {
            TraceEntry e = diagramTraceSteps.get(stepIdx);
            exchangeId = e.exchangeId;
            routeId = e.routeId;
            nodeId = e.nodeId;
            processor = e.processor;
            direction = e.direction;
            timestamp = e.timestamp;
            threadName = e.threadName;
            elapsed = e.elapsed;
            failed = e.failed;
            body = e.body;
            bodyType = e.bodyType;
            exception = e.exception;
            headers = e.headers;
            properties = e.exchangeProperties;
            variables = e.exchangeVariables;
            if (stepIdx > 0) {
                TraceEntry p = diagramTraceSteps.get(stepIdx - 1);
                prevHeaders = p.headers;
                prevProperties = p.exchangeProperties;
                prevVariables = p.exchangeVariables;
            }
        } else if (!diagramHistorySteps.isEmpty() && stepIdx >= 0 && stepIdx < diagramHistorySteps.size()) {
            HistoryEntry e = diagramHistorySteps.get(stepIdx);
            exchangeId = e.exchangeId;
            routeId = e.routeId;
            nodeId = e.nodeId;
            processor = e.processor;
            direction = e.direction;
            timestamp = e.timestamp;
            threadName = e.threadName;
            elapsed = e.elapsed;
            failed = e.failed;
            body = e.body;
            bodyType = e.bodyType;
            exception = e.exception;
            headers = e.headers;
            properties = e.exchangeProperties;
            variables = e.exchangeVariables;
            if (stepIdx > 0) {
                HistoryEntry p = diagramHistorySteps.get(stepIdx - 1);
                prevHeaders = p.headers;
                prevProperties = p.exchangeProperties;
                prevVariables = p.exchangeVariables;
            }
        }

        if (exchangeId == null) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("No step selected", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Info ").build())
                            .build(),
                    area);
            return;
        }

        List<Span> stepSpans = new ArrayList<>();
        stepSpans.add(Span.styled(" Step:     ", Theme.muted()));
        stepSpans.add(Span.raw(String.format("%d/%d", stepIdx + 1, diagram.getHistoryStepCount())));
        if (direction != null && !direction.isBlank()) {
            Style dirStyle = failed ? Theme.error() : Theme.success();
            stepSpans.add(Span.raw(" "));
            stepSpans.add(Span.styled(direction, dirStyle));
        }
        lines.add(Line.from(stepSpans));
        lines.add(Line.from(
                Span.styled(" Exchange: ", Theme.muted()),
                Span.raw(exchangeId)));
        lines.add(Line.from(
                Span.styled(" Route:    ", Theme.muted()),
                Span.styled(routeId != null ? routeId : "", Style.EMPTY.fg(Theme.accent()))));
        lines.add(Line.from(
                Span.styled(" Node:     ", Theme.muted()),
                Span.raw(nodeId != null ? nodeId : "")));
        if (processor != null) {
            lines.add(Line.from(
                    Span.styled(" Proc:     ", Theme.muted()),
                    Span.raw(processor.strip())));
        }
        if (elapsed >= 0) {
            lines.add(Line.from(
                    Span.styled(" Elapsed:  ", Theme.muted()),
                    Span.raw(elapsed + "ms")));
        }
        if (timestamp != null) {
            lines.add(Line.from(
                    Span.styled(" Time:     ", Theme.muted()),
                    Span.raw(timestamp)));
        }
        if (threadName != null) {
            lines.add(Line.from(
                    Span.styled(" Thread:   ", Theme.muted()),
                    Span.raw(threadName)));
        }
        if (failed) {
            lines.add(Line.from(
                    Span.styled(" Status:   ", Theme.muted()),
                    Span.styled("Failed", Theme.error().bold())));
        }

        // Compute BHPV change indicators
        String changes = "";
        if (!diagramTraceSteps.isEmpty() && stepIdx > 0 && stepIdx < diagramTraceSteps.size()) {
            changes = computeTraceChanges(diagramTraceSteps.get(stepIdx - 1), diagramTraceSteps.get(stepIdx));
        } else if (!diagramHistorySteps.isEmpty() && stepIdx > 0 && stepIdx < diagramHistorySteps.size()) {
            changes = computeHistoryChanges(diagramHistorySteps.get(stepIdx - 1), diagramHistorySteps.get(stepIdx));
        }
        boolean bodyChanged = changes.length() > 0 && changes.charAt(0) == 'B';
        boolean headersChanged = changes.length() > 1 && changes.charAt(1) == 'H';
        boolean propsChanged = changes.length() > 2 && changes.charAt(2) == 'P';
        boolean varsChanged = changes.length() > 3 && changes.charAt(3) == 'V';

        List<Span> changeSpans = new ArrayList<>();
        changeSpans.add(Span.styled(" Changed:  ", Theme.muted()));
        if (!changes.isBlank()) {
            changeSpans.addAll(buildChangeSpans(changes));
        }
        lines.add(Line.from(changeSpans));

        boolean isTraceMode = !diagramTraceSteps.isEmpty();
        boolean showBody = isTraceMode ? showTraceBody : showHistoryBody;
        boolean showHeaders = isTraceMode ? showTraceHeaders : showHistoryHeaders;
        boolean showProps = isTraceMode ? showTraceProperties : showHistoryProperties;
        boolean showVars = isTraceMode ? showTraceVariables : showHistoryVariables;

        if (exception != null && !exception.isBlank()) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" Exception", Theme.error().bold())));
            lines.add(Line.from(Span.raw(" " + exception)));
        }

        if (showBody && body != null) {
            Style headerStyle = bodyChanged ? Theme.change().bold() : Theme.muted();
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled(" Body", headerStyle),
                    bodyType != null ? Span.styled(" (" + bodyType + ")", Style.EMPTY.dim()) : Span.raw("")));
            for (String line : body.split("\n")) {
                lines.add(Line.from(Span.raw(" " + line)));
            }
        }

        if (showHeaders && headers != null && !headers.isEmpty()) {
            Style sectionStyle = headersChanged ? Theme.change().bold() : Theme.muted();
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" Headers", sectionStyle)));
            addInfoKvLines(lines, headers, headersChanged, prevHeaders);
        }

        if (showProps && properties != null && !properties.isEmpty()) {
            Style sectionStyle = propsChanged ? Theme.change().bold() : Theme.muted();
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" Properties", sectionStyle)));
            addInfoKvLines(lines, properties, propsChanged, prevProperties);
        }

        if (showVars && variables != null && !variables.isEmpty()) {
            Style sectionStyle = varsChanged ? Theme.change().bold() : Theme.muted();
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" Variables", sectionStyle)));
            addInfoKvLines(lines, variables, varsChanged, prevVariables);
        }

        boolean wordWrap = !diagramTraceSteps.isEmpty() ? traceWordWrap : historyWordWrap;
        Paragraph.Builder pb = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Info ").build());
        if (wordWrap) {
            pb.overflow(Overflow.WRAP_WORD);
        }
        frame.renderWidget(pb.build(), area);
    }

    private static void addInfoKvLines(
            List<Line> lines, Map<String, Object> map,
            boolean sectionChanged, Map<String, Object> prevMap) {
        for (var entry : map.entrySet()) {
            String val = entry.getValue() != null ? entry.getValue().toString() : "null";
            boolean keyChanged = sectionChanged && prevMap != null
                    && (!prevMap.containsKey(entry.getKey())
                            || !Objects.equals(prevMap.get(entry.getKey()), entry.getValue()));
            Style keyStyle = keyChanged ? Theme.change() : Theme.muted();
            Style valStyle = keyChanged ? Theme.change() : Style.EMPTY;
            lines.add(Line.from(
                    Span.styled(" " + entry.getKey(), keyStyle),
                    Span.raw(" = "),
                    Span.styled(val, valStyle)));
        }
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
            List<TraceEntry> steps = getTraceStepsDepthFirst(exchangeId);
            if (steps.isEmpty()) {
                diagram.endLoad();
                return;
            }
            diagramTraceSteps = steps;
            diagramHistorySteps = Collections.emptyList();
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
            List<HistoryEntry> entries = reorderHistoryDepthFirst(historyEntries);
            if (entries.isEmpty()) {
                diagram.endLoad();
                return;
            }
            diagramHistorySteps = entries;
            diagramTraceSteps = Collections.emptyList();
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

        // Compute child exchange IDs that will be inlined into a parent's depth-first view
        Set<String> childExchangeIds = computeAllChildExchangeIds(exchangeIds);

        record ExchangeSummary(String exchangeId, String timestamp, long epochMs, String routeId,
                String status, long elapsed, int steps) {
        }
        List<ExchangeSummary> summaries = new ArrayList<>();
        for (String exchangeId : exchangeIds) {
            if (childExchangeIds.contains(exchangeId)) {
                continue;
            }
            List<TraceEntry> depthFirstSteps = getTraceStepsDepthFirst(exchangeId);
            TraceEntry first = null;
            TraceEntry lastEntry = null;
            TraceEntry latestEntry = null;
            int stepCount = depthFirstSteps.size();
            for (TraceEntry e : depthFirstSteps) {
                if (first == null) {
                    first = e;
                }
                latestEntry = e;
                if (e.last && exchangeId.equals(e.exchangeId)) {
                    lastEntry = e;
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
                case "Done" -> Theme.success();
                case "Failed" -> Theme.error();
                case "Processing" -> Theme.warning();
                default -> Style.EMPTY;
            };
            rows.add(Row.from(
                    Cell.from(s.timestamp != null ? TuiHelper.truncate(s.timestamp, 12) : ""),
                    Cell.from(Span.styled(
                            s.routeId != null ? TuiHelper.truncate(s.routeId, 25) : "",
                            Style.EMPTY.fg(Theme.accent()))),
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

        String traceTitle = String.format(" Traces [%d] ", summaries.size());

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
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(traceTitle).build())
                .build();

        lastTraceTableArea = area;
        int traceVisibleRows = Math.max(0, area.height() - 3);
        traceTableState.scrollToSelected(traceVisibleRows, rows);
        frame.renderStatefulWidget(table, area, traceTableState);
        renderTableScrollbar(frame, lastTraceTableArea, traceTableState, tableScrollState,
                traceSortedExchangeIds.size());
    }

    private void renderTraceExchangeDetail(Frame frame, Rect area) {
        List<TraceEntry> steps = getTraceStepsDepthFirst(traceSelectedExchangeId);

        traceDetailTopHeight = Math.max(3, Math.min(traceDetailTopHeight, area.height() - 5));
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(traceDetailTopHeight), Constraint.fill())
                .split(area);

        Map<String, String> descMap = showDescription ? getRouteDescriptions() : Collections.emptyMap();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            TraceEntry entry = steps.get(i);
            TraceEntry prev = i > 0 ? steps.get(i - 1) : null;
            String desc = showDescription ? descMap.get(entry.routeId) : null;
            String changes = computeTraceChanges(prev, entry);
            rows.add(buildStepRow(i + 1, entry.inlineDepth,
                    entry.direction, entry.first, entry.last, entry.failed,
                    entry.timestamp, entry.routeId, entry.nodeId, entry.processor, desc, entry.elapsed, changes));
        }

        String stepTitle
                = String.format(" Trace [%s] — %d steps ", TuiHelper.truncate(traceSelectedExchangeId, 30), steps.size());
        lastTraceStepArea = chunks.get(0);
        detailSplit.setBorderPos(chunks.get(1).y());
        int stepVisibleRows = Math.max(0, chunks.get(0).height() - 3);
        traceStepTableState.scrollToSelected(stepVisibleRows, rows);
        frame.renderStatefulWidget(
                buildStepTable(rows, stepTitle, showDescription), chunks.get(0), traceStepTableState);
        renderTableScrollbar(frame, lastTraceStepArea, traceStepTableState, traceStepScrollState,
                steps.size());

        if (showWaterfall) {
            Integer sel = traceStepTableState.selected();
            renderWaterfall(frame, chunks.get(1), steps.stream().map(WaterfallStep::fromTrace).toList(),
                    sel != null ? sel : -1);
        } else {
            renderTraceStepDetail(frame, chunks.get(1), steps);
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
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).build())
                            .build(),
                    area);
            return;
        }

        TraceEntry entry = steps.get(sel);
        TraceEntry prev = sel > 0 ? steps.get(sel - 1) : null;
        String changes = computeTraceChanges(prev, entry);
        boolean bodyChanged = changes.length() > 0 && changes.charAt(0) == 'B';
        boolean headersChanged = changes.length() > 1 && changes.charAt(1) == 'H';
        boolean propsChanged = changes.length() > 2 && changes.charAt(2) == 'P';
        boolean varsChanged = changes.length() > 3 && changes.charAt(3) == 'V';
        List<Line> lines = new ArrayList<>();

        addExchangeInfoLines(lines, entry.exchangeId, entry.routeId, entry.nodeId, entry.nodeLabel,
                entry.location, entry.elapsed, entry.threadName, entry.failed);
        if (showTraceProperties) {
            addKvLines(lines, " Exchange Properties:", entry.exchangeProperties, entry.exchangePropertyTypes,
                    propsChanged, prev != null ? prev.exchangeProperties : null);
        }
        if (showTraceVariables) {
            addKvLines(lines, " Exchange Variables:", entry.exchangeVariables, entry.exchangeVariableTypes,
                    varsChanged, prev != null ? prev.exchangeVariables : null);
        }
        if (showTraceHeaders) {
            addKvLines(lines, " Headers:", entry.headers, entry.headerTypes,
                    headersChanged, prev != null ? prev.headers : null);
        }
        if (showTraceBody) {
            addBodyLines(lines, entry.body, entry.bodyType, bodyChanged);
        }
        addExceptionLines(lines, entry.exception);

        int[] scroll = { traceDetailScroll };
        int[] hScroll = { traceDetailHScroll };
        renderDetailPanel(frame, area, lines, traceWordWrap, hScroll, scroll, traceDetailScrollState, " Detail ");
        traceDetailScroll = scroll[0];
        traceDetailHScroll = hScroll[0];
    }

    record WaterfallStep(String nodeId, String processor, String direction, boolean first, boolean last,
            int nodeLevel, long elapsed, int inlineDepth) {

        static WaterfallStep fromTrace(TraceEntry e) {
            return new WaterfallStep(
                    e.nodeId, e.processor, e.direction, e.first, e.last, e.nodeLevel, e.elapsed,
                    e.inlineDepth);
        }

        static WaterfallStep fromHistory(HistoryEntry e) {
            return new WaterfallStep(
                    e.nodeId, e.processor, e.direction, e.first, e.last, e.nodeLevel, e.elapsed,
                    e.inlineDepth);
        }

        WaterfallStep withElapsed(long newElapsed) {
            return new WaterfallStep(nodeId, processor, direction, first, last, nodeLevel, newElapsed, inlineDepth);
        }

        String label() {
            String prefix = inlineDepth > 0 ? "  ".repeat(inlineDepth) : "";
            if (nodeId != null && !nodeId.isEmpty()) {
                return prefix + nodeId;
            }
            if (processor != null) {
                return prefix + processor.stripLeading();
            }
            return prefix;
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
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
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
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
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

        Style labelStyle = selected ? Style.EMPTY.fg(Theme.accent()).bold() : Style.EMPTY.fg(Theme.accent());

        return Line.from(
                Span.styled(indicator, Theme.label().bold()),
                Span.styled(label, labelStyle),
                Span.styled(bar, bandStyle),
                Span.raw(" ".repeat(pad)),
                Span.styled(durationStr, isRoute ? Style.EMPTY.dim() : Style.EMPTY.fg(Theme.baseFg()).bold()));
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

        List<HistoryEntry> current = reorderHistoryDepthFirst(historyEntries);
        historyVisibleCount = current.size();

        historyTopPanelHeight = Math.max(3, Math.min(historyTopPanelHeight, area.height() - 5));
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(historyTopPanelHeight), Constraint.fill())
                .split(area);

        Map<String, String> descMap = showDescription ? getRouteDescriptions() : Collections.emptyMap();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            HistoryEntry entry = current.get(i);
            HistoryEntry prev = i > 0 ? current.get(i - 1) : null;
            String desc = showDescription ? descMap.get(entry.routeId) : null;
            String changes = computeHistoryChanges(prev, entry);
            rows.add(buildStepRow(i + 1, entry.inlineDepth,
                    entry.direction, entry.first, entry.last, entry.failed,
                    entry.timestamp, entry.routeId, entry.nodeId, entry.processor, desc, entry.elapsed, changes));
        }

        Title historyTitle = buildHistoryTitle(current);
        lastHistoryTableArea = chunks.get(0);
        vSplit.setBorderPos(chunks.get(1).y());
        int histVisibleRows = Math.max(0, chunks.get(0).height() - 3);
        historyTableState.scrollToSelected(histVisibleRows, rows);
        frame.renderStatefulWidget(
                buildStepTable(rows, historyTitle, showDescription), chunks.get(0), historyTableState);
        renderTableScrollbar(frame, lastHistoryTableArea, historyTableState, historyTableScrollState,
                current.size());

        if (showWaterfall) {
            Integer sel = historyTableState.selected();
            renderWaterfall(frame, chunks.get(1), current.stream().map(WaterfallStep::fromHistory).toList(),
                    sel != null ? sel : -1);
        } else {
            renderHistoryDetail(frame, chunks.get(1), current);
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
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        HistoryEntry entry = current.get(sel);
        HistoryEntry prev = sel > 0 ? current.get(sel - 1) : null;
        String changes = computeHistoryChanges(prev, entry);
        boolean bodyChanged = changes.length() > 0 && changes.charAt(0) == 'B';
        boolean headersChanged = changes.length() > 1 && changes.charAt(1) == 'H';
        boolean propsChanged = changes.length() > 2 && changes.charAt(2) == 'P';
        boolean varsChanged = changes.length() > 3 && changes.charAt(3) == 'V';
        List<Line> lines = new ArrayList<>();

        addExchangeInfoLines(lines, entry.exchangeId, entry.routeId, entry.nodeId, entry.nodeLabel,
                entry.location, entry.elapsed, entry.threadName, entry.failed);
        if (showHistoryProperties) {
            addKvLines(lines, " Exchange Properties:", entry.exchangeProperties, entry.exchangePropertyTypes,
                    propsChanged, prev != null ? prev.exchangeProperties : null);
        }
        if (showHistoryVariables) {
            addKvLines(lines, " Exchange Variables:", entry.exchangeVariables, entry.exchangeVariableTypes,
                    varsChanged, prev != null ? prev.exchangeVariables : null);
        }
        if (showHistoryHeaders) {
            addKvLines(lines, " Headers:", entry.headers, entry.headerTypes,
                    headersChanged, prev != null ? prev.headers : null);
        }
        if (showHistoryBody) {
            addBodyLines(lines, entry.body, entry.bodyType, bodyChanged);
        }
        addExceptionLines(lines, entry.exception);

        int[] scroll = { historyDetailScroll };
        int[] hScroll = { historyDetailHScroll };
        renderDetailPanel(frame, area, lines, historyWordWrap, hScroll, scroll, historyDetailScrollState, " Detail ");
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

    private List<TraceEntry> getTraceStepsDepthFirst(String exchangeId) {
        List<TraceEntry> allTraces = traces.get();
        if (allTraces.isEmpty() || exchangeId == null) {
            return Collections.emptyList();
        }

        // Group by exchangeId, each sorted by uid
        Map<String, List<TraceEntry>> byExchange = new LinkedHashMap<>();
        for (TraceEntry e : allTraces) {
            if (e.exchangeId != null) {
                byExchange.computeIfAbsent(e.exchangeId, k -> new ArrayList<>()).add(e);
            }
        }
        byExchange.values().forEach(list -> list.sort(UID_COMPARATOR));

        // Build from-endpoint → exchangeIds index (consumer endpoint of each exchange)
        Map<String, List<String>> fromIndex = new LinkedHashMap<>();
        for (var entry : byExchange.entrySet()) {
            List<TraceEntry> steps = entry.getValue();
            if (!steps.isEmpty() && steps.get(0).first) {
                String ep = extractFromEndpoint(steps.get(0));
                if (ep != null) {
                    fromIndex.computeIfAbsent(ep, k -> new ArrayList<>()).add(entry.getKey());
                }
            }
        }

        // Build multicast/splitter child index: routeId → [child exchangeIds]
        // A child exchange has no first=true entry and its first step is a to[...] node
        Map<String, List<String>> branchChildren = new LinkedHashMap<>();
        for (var entry : byExchange.entrySet()) {
            List<TraceEntry> steps = entry.getValue();
            if (!steps.isEmpty() && !steps.get(0).first) {
                TraceEntry firstStep = steps.get(0);
                if (firstStep.routeId != null && extractToEndpoint(firstStep) != null) {
                    branchChildren.computeIfAbsent(firstStep.routeId, k -> new ArrayList<>()).add(entry.getKey());
                }
            }
        }

        // Walk depth-first
        List<TraceEntry> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        inlineExchange(exchangeId, byExchange, fromIndex, branchChildren, visited, result, 0);
        return result;
    }

    private void inlineExchange(
            String exchangeId, Map<String, List<TraceEntry>> byExchange,
            Map<String, List<String>> fromIndex, Map<String, List<String>> branchChildren,
            Set<String> visited, List<TraceEntry> result, int depth) {
        if (!visited.add(exchangeId)) {
            return;
        }
        List<TraceEntry> steps = byExchange.get(exchangeId);
        if (steps == null) {
            return;
        }
        for (TraceEntry step : steps) {
            step.inlineDepth = depth;
            result.add(step);

            // After a multicast/splitter step, inline branch child exchanges
            if (isMulticastNode(step)) {
                List<String> children = branchChildren.get(step.routeId);
                if (children != null) {
                    for (String childId : children) {
                        if (!visited.contains(childId)) {
                            inlineExchange(childId, byExchange, fromIndex, branchChildren, visited, result, depth + 1);
                        }
                    }
                }
            }

            // After a to[X] step, inline consumer exchanges matching endpoint X
            String targetEndpoint = extractToEndpoint(step);
            if (targetEndpoint != null) {
                List<String> children = fromIndex.get(targetEndpoint);
                if (children != null) {
                    for (String childId : children) {
                        if (!visited.contains(childId)) {
                            inlineExchange(childId, byExchange, fromIndex, branchChildren, visited, result, depth + 1);
                        }
                    }
                }
            }
        }
    }

    private List<HistoryEntry> reorderHistoryDepthFirst(List<HistoryEntry> entries) {
        if (entries.isEmpty()) {
            return entries;
        }

        Map<String, List<HistoryEntry>> byExchange = new LinkedHashMap<>();
        for (HistoryEntry e : entries) {
            if (e.exchangeId != null) {
                byExchange.computeIfAbsent(e.exchangeId, k -> new ArrayList<>()).add(e);
            }
        }

        Map<String, List<String>> fromIndex = new LinkedHashMap<>();
        for (var entry : byExchange.entrySet()) {
            List<HistoryEntry> steps = entry.getValue();
            if (!steps.isEmpty() && steps.get(0).first) {
                String ep = extractFromEndpoint(steps.get(0).nodeLabel);
                if (ep != null) {
                    fromIndex.computeIfAbsent(ep, k -> new ArrayList<>()).add(entry.getKey());
                }
            }
        }

        Map<String, List<String>> branchChildren = new LinkedHashMap<>();
        for (var entry : byExchange.entrySet()) {
            List<HistoryEntry> steps = entry.getValue();
            if (!steps.isEmpty() && !steps.get(0).first) {
                HistoryEntry firstStep = steps.get(0);
                if (firstStep.routeId != null && extractToEndpoint(firstStep.nodeLabel) != null) {
                    branchChildren.computeIfAbsent(firstStep.routeId, k -> new ArrayList<>()).add(entry.getKey());
                }
            }
        }

        String rootExchangeId = entries.get(0).exchangeId;
        if (rootExchangeId == null) {
            return entries;
        }

        List<HistoryEntry> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        inlineHistoryExchange(rootExchangeId, byExchange, fromIndex, branchChildren, visited, result, 0);
        return result;
    }

    private void inlineHistoryExchange(
            String exchangeId, Map<String, List<HistoryEntry>> byExchange,
            Map<String, List<String>> fromIndex, Map<String, List<String>> branchChildren,
            Set<String> visited, List<HistoryEntry> result, int depth) {
        if (!visited.add(exchangeId)) {
            return;
        }
        List<HistoryEntry> steps = byExchange.get(exchangeId);
        if (steps == null) {
            return;
        }
        for (HistoryEntry step : steps) {
            step.inlineDepth = depth;
            result.add(step);

            if (isMulticastNode(step.nodeShortName)) {
                List<String> children = branchChildren.get(step.routeId);
                if (children != null) {
                    for (String childId : children) {
                        if (!visited.contains(childId)) {
                            inlineHistoryExchange(childId, byExchange, fromIndex, branchChildren, visited, result,
                                    depth + 1);
                        }
                    }
                }
            }

            String targetEndpoint = extractToEndpoint(step.nodeLabel);
            if (targetEndpoint != null) {
                List<String> children = fromIndex.get(targetEndpoint);
                if (children != null) {
                    for (String childId : children) {
                        if (!visited.contains(childId)) {
                            inlineHistoryExchange(childId, byExchange, fromIndex, branchChildren, visited, result,
                                    depth + 1);
                        }
                    }
                }
            }
        }
    }

    private static boolean isMulticastNode(TraceEntry entry) {
        return isMulticastNode(entry.nodeShortName);
    }

    private static boolean isMulticastNode(String nodeShortName) {
        if (nodeShortName != null) {
            return switch (nodeShortName) {
                case "multicast", "recipientList", "split", "routingSlip", "enrich", "pollEnrich",
                        "wireTap", "dynamicRouter" ->
                    true;
                default -> false;
            };
        }
        return false;
    }

    private static String extractFromEndpoint(TraceEntry entry) {
        return extractFromEndpoint(entry.nodeLabel);
    }

    private static String extractFromEndpoint(String nodeLabel) {
        if (nodeLabel != null) {
            return normalizeEndpoint(nodeLabel);
        }
        return null;
    }

    private static String extractToEndpoint(TraceEntry entry) {
        return extractToEndpoint(entry.nodeLabel);
    }

    private static String extractToEndpoint(String nodeLabel) {
        if (nodeLabel != null && nodeLabel.startsWith("to[")) {
            int end = nodeLabel.indexOf(']');
            if (end > 3) {
                return normalizeEndpoint(nodeLabel.substring(3, end));
            }
        }
        return null;
    }

    private static String normalizeEndpoint(String endpoint) {
        int q = endpoint.indexOf('?');
        if (q > 0) {
            endpoint = endpoint.substring(0, q);
        }
        return endpoint.replace("://", ":");
    }

    private Set<String> computeAllChildExchangeIds(List<String> exchangeIds) {
        Set<String> allChildren = new LinkedHashSet<>();
        for (String exchangeId : exchangeIds) {
            if (allChildren.contains(exchangeId)) {
                continue;
            }
            List<TraceEntry> depthFirst = getTraceStepsDepthFirst(exchangeId);
            for (TraceEntry e : depthFirst) {
                if (!exchangeId.equals(e.exchangeId)) {
                    allChildren.add(e.exchangeId);
                }
            }
        }
        return allChildren;
    }

    private static void hintShowBhpv(List<Span> spans, boolean body, boolean headers, boolean props, boolean vars) {
        spans.add(Span.styled(" show ", Theme.hintKey()));
        spans.add(Span.raw(" "));
        spans.add(Span.styled(body ? "B" : "b", body ? Style.EMPTY.fg(Theme.baseFg()).bold() : Style.EMPTY.dim()));
        spans.add(Span.styled(headers ? "H" : "h", headers ? Style.EMPTY.fg(Theme.baseFg()).bold() : Style.EMPTY.dim()));
        spans.add(Span.styled(props ? "P" : "p", props ? Style.EMPTY.fg(Theme.baseFg()).bold() : Style.EMPTY.dim()));
        spans.add(Span.styled(vars ? "V" : "v", vars ? Style.EMPTY.fg(Theme.baseFg()).bold() : Style.EMPTY.dim()));
        spans.add(Span.raw("  "));
    }

    private String traceSortLabel(String label, String column) {
        return sortLabel(label, column, traceSort, traceSortReversed);
    }

    private Style traceSortStyle(String column) {
        return sortStyle(column, traceSort);
    }

    private static Row buildStepRow(
            int stepNumber, int inlineDepth,
            String direction, boolean first, boolean last, boolean failed,
            String timestamp, String routeId, String nodeId, String processor,
            String description, long elapsed, String changes) {
        Style dirStyle;
        if (first || last || !direction.isBlank()) {
            dirStyle = failed ? Theme.error() : Theme.success();
        } else {
            dirStyle = failed ? Theme.error() : Style.EMPTY;
        }
        String elapsedStr = elapsed >= 0 ? elapsed + "ms" : "";
        String display = description != null ? description : (processor != null ? processor : "");
        String indent = inlineDepth > 0 ? "  ".repeat(inlineDepth) : "";
        List<Span> changeSpans = buildChangeSpans(changes);
        return Row.from(
                rightCell(String.valueOf(stepNumber), 3),
                Cell.from(Span.styled(direction, dirStyle)),
                Cell.from(timestamp != null ? TuiHelper.truncate(timestamp, 12) : ""),
                Cell.from(Span.styled(routeId != null ? TuiHelper.truncate(routeId, 25) : "", Style.EMPTY.fg(Theme.accent()))),
                Cell.from(indent + (nodeId != null ? TuiHelper.truncate(nodeId, 25) : "")),
                Cell.from(indent + display),
                Cell.from(Line.from(changeSpans)),
                rightCell(elapsedStr, 10));
    }

    private static List<Span> buildChangeSpans(String changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of(Span.raw(""));
        }
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < changes.length(); i++) {
            char c = changes.charAt(i);
            if (c == ' ') {
                spans.add(Span.styled(String.valueOf(c), Style.EMPTY.dim()));
            } else {
                spans.add(Span.styled(String.valueOf(c), Theme.change()));
            }
        }
        return spans;
    }

    static String computeTraceChanges(TraceEntry prev, TraceEntry curr) {
        if (prev == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(!Objects.equals(prev.body, curr.body) ? 'B' : ' ');
        sb.append(!Objects.equals(prev.headers, curr.headers) ? 'H' : ' ');
        sb.append(!Objects.equals(prev.exchangeProperties, curr.exchangeProperties) ? 'P' : ' ');
        sb.append(!Objects.equals(prev.exchangeVariables, curr.exchangeVariables) ? 'V' : ' ');
        return sb.toString().isBlank() ? "" : sb.toString();
    }

    static String computeHistoryChanges(HistoryEntry prev, HistoryEntry curr) {
        if (prev == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(!Objects.equals(prev.body, curr.body) ? 'B' : ' ');
        sb.append(!Objects.equals(prev.headers, curr.headers) ? 'H' : ' ');
        sb.append(!Objects.equals(prev.exchangeProperties, curr.exchangeProperties) ? 'P' : ' ');
        sb.append(!Objects.equals(prev.exchangeVariables, curr.exchangeVariables) ? 'V' : ' ');
        return sb.toString().isBlank() ? "" : sb.toString();
    }

    private static Table buildStepTable(List<Row> rows, Object title, boolean descriptionMode) {
        Row header = Row.from(
                rightCell("#", 3, Style.EMPTY.bold()),
                Cell.from(Span.styled("", Style.EMPTY.bold())),
                Cell.from(Span.styled("TIME", Style.EMPTY.bold())),
                Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                Cell.from(Span.styled("ID", Style.EMPTY.bold())),
                Cell.from(Span.styled(descriptionMode ? "DESCRIPTION" : "PROCESSOR", Style.EMPTY.bold())),
                Cell.from(Span.styled("BHPV", Style.EMPTY.bold())),
                rightCell("ELAPSED", 10, Style.EMPTY.bold()));
        Block block = title instanceof Title t
                ? Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(t).build()
                : Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title.toString()).build();
        return Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(3),
                        Constraint.length(4),
                        Constraint.length(12),
                        Constraint.length(25),
                        Constraint.length(25),
                        Constraint.fill(),
                        Constraint.length(4),
                        Constraint.length(11))
                .highlightStyle(Theme.selectionBg())
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
        spans.add(Span.styled("status:" + (failed ? "failed" : "ok"),
                failed ? Theme.error().bold() : Theme.success().bold()));
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
                Span.styled(" Exchange: ", Theme.muted()),
                Span.raw(exchangeId != null ? exchangeId : "")));
        if (nodeId != null) {
            lines.add(Line.from(
                    Span.styled(" Route:    ", Theme.muted()),
                    Span.raw(String.format("%-25s", routeId != null ? routeId : "")),
                    Span.styled("  Node: ", Theme.muted()),
                    Span.raw(nodeId),
                    Span.raw(nodeLabel != null ? " (" + nodeLabel + ")" : "")));
        } else {
            lines.add(Line.from(
                    Span.styled(" Route:    ", Theme.muted()),
                    Span.raw(routeId != null ? routeId : "")));
        }
        lines.add(Line.from(
                Span.styled(" Location: ", Theme.muted()),
                Span.raw(location != null ? location : "")));
        if (threadName != null) {
            lines.add(Line.from(
                    Span.styled(" Elapsed:  ", Theme.muted()),
                    Span.raw(elapsed >= 0 ? elapsed + "ms" : ""),
                    Span.styled("  Thread: ", Theme.muted()),
                    Span.raw(threadName)));
        } else {
            lines.add(Line.from(
                    Span.styled(" Elapsed:  ", Theme.muted()),
                    Span.raw(elapsed >= 0 ? elapsed + "ms" : "")));
        }
        if (failed) {
            lines.add(Line.from(
                    Span.styled(" Status:   ", Theme.muted()),
                    Span.styled("Failed", Theme.error().bold())));
        }
        lines.add(Line.from(Span.raw("")));
    }

    static void addKvLines(
            List<Line> lines, String section,
            Map<String, Object> map, Map<String, String> types,
            boolean changed, Map<String, Object> prevMap) {
        if (map == null || map.isEmpty()) {
            return;
        }
        Style headerStyle = changed ? Theme.change().bold() : Theme.muted();
        lines.add(Line.from(Span.styled(section, headerStyle)));
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
            boolean keyChanged = changed && prevMap != null
                    && (!prevMap.containsKey(entry.getKey())
                            || !Objects.equals(prevMap.get(entry.getKey()), entry.getValue()));
            Style keyStyle = keyChanged ? Theme.change() : Theme.muted();
            Style valStyle = keyChanged ? Theme.change() : Style.EMPTY;
            lines.add(Line.from(
                    Span.styled("   " + typeLabel, Style.EMPTY.dim()),
                    Span.styled(entry.getKey(), keyStyle),
                    Span.raw(" = "),
                    Span.styled(val, valStyle)));
        }
        lines.add(Line.from(Span.raw("")));
    }

    static void addBodyLines(List<Line> lines, String body, String bodyType, boolean changed) {
        Style headerStyle = changed ? Theme.change().bold() : Theme.muted();
        if (body != null) {
            if (bodyType != null) {
                lines.add(Line.from(
                        Span.styled(" Body: ", headerStyle),
                        Span.styled("(" + bodyType + ")", Style.EMPTY.dim())));
            } else {
                lines.add(Line.from(Span.styled(" Body:", headerStyle)));
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
            lines.add(Line.from(Span.styled(" Body is null", headerStyle)));
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
        lines.add(Line.from(Span.styled(" Exception:", Theme.error().bold())));
        for (String l : exception.split("\n", -1)) {
            lines.add(Line.from(Span.raw("   " + TuiHelper.fixControlChars(l))));
        }
        lines.add(Line.from(Span.raw("")));
    }

    static void renderDetailPanel(
            Frame frame, Rect area, List<Line> lines,
            boolean wordWrap, int[] hScroll, int[] scroll, ScrollbarState scrollState) {
        renderDetailPanel(frame, area, lines, wordWrap, hScroll, scroll, scrollState, null);
    }

    static void renderDetailPanel(
            Frame frame, Rect area, List<Line> lines,
            boolean wordWrap, int[] hScroll, int[] scroll, ScrollbarState scrollState, String title) {
        Block.Builder bb = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL);
        if (title != null) {
            bb.title(title);
        }
        Block block = bb.build();
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
                List<TraceEntry> steps = getTraceStepsDepthFirst(traceSelectedExchangeId);
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
        List<String> items = reorderHistoryDepthFirst(historyEntries).stream()
                .map(h -> h.exchangeId != null ? h.exchangeId : "")
                .toList();
        Integer sel = historyTableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "History");
    }

    @Override
    public String description() {
        return "Message history trace showing route path, headers, body, and timing";
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
                      RouteId        NodeId     Processor            BHPV  Elapsed
                 *->  timer-to-log   timer1     from[timer:hello]          0ms
                      timer-to-log   setBody1   setBody[simple]      B     0ms
                      timer-to-log   choice1    choice                     0ms
                      timer-to-log   when1      when[simple]               0ms
                 --->  timer-to-log   to1       to[kafka:orders]      H    2ms
                      timer-to-log   log1       log[HIGH: ${body}]         0ms
                 <-*  timer-to-log   timer1     from[timer:hello]          3ms
                ```

                This tells you the message entered via the timer, went through setBody,
                reached a choice node, matched the `when` condition, and was logged.
                The elapsed time for each step helps identify bottlenecks.

                **Change Indicators (BHPV)** — The BHPV column shows at a glance
                which parts of the exchange were modified at each step compared to
                the previous step:

                - `B` — Body changed
                - `H` — Headers changed
                - `P` — Exchange properties changed
                - `V` — Exchange variables changed

                Steps with no changes leave the column blank, so mutations stand
                out visually.

                **Depth-first ordering** — When an exchange spans multiple routes
                via async EIPs (multicast, splitter, recipientList), child exchange
                steps are inlined under the parent step that triggered them, indented
                with 2 spaces per depth level. This keeps the logical flow readable
                instead of interleaving concurrent branches.

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

                **Info Panel** — An info panel on the left side of the diagram shows
                trace metadata for the current step: exchange ID, route, node,
                processor, elapsed time, thread, and direction. It also shows body,
                headers, properties, and variables respecting the same `b/h/p/v`
                toggles as the table view. Press `i` to cycle the panel size:
                narrow (35 chars), wide (half screen), or full (entire area).
                In wide mode, the minimap and tree preview are hidden to give more
                space. Word wrap (`w`) is also supported.

                Press `d` to close the diagram and return to the table.
                Press `Esc` to navigate back one route in drill-down mode.

                ## Keys

                - `Up/Down` — select exchange (or step through path in diagram)
                - `Enter` — view exchange details
                - `d` — toggle route diagram (open and close)
                - `Esc` — back to list / back one route in diagram drill-down
                - `i` — cycle info panel size (narrow / wide / full) in diagram
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

    String toggleDisplaySection(String section, Boolean enabled) {
        boolean tracerActive = !traces.get().isEmpty();
        boolean newValue;
        switch (section) {
            case "headers" -> {
                if (tracerActive) {
                    showTraceHeaders = enabled != null ? enabled : !showTraceHeaders;
                    newValue = showTraceHeaders;
                } else {
                    showHistoryHeaders = enabled != null ? enabled : !showHistoryHeaders;
                    newValue = showHistoryHeaders;
                }
            }
            case "properties" -> {
                if (tracerActive) {
                    showTraceProperties = enabled != null ? enabled : !showTraceProperties;
                    newValue = showTraceProperties;
                } else {
                    showHistoryProperties = enabled != null ? enabled : !showHistoryProperties;
                    newValue = showHistoryProperties;
                }
            }
            case "variables" -> {
                if (tracerActive) {
                    showTraceVariables = enabled != null ? enabled : !showTraceVariables;
                    newValue = showTraceVariables;
                } else {
                    showHistoryVariables = enabled != null ? enabled : !showHistoryVariables;
                    newValue = showHistoryVariables;
                }
            }
            case "body" -> {
                if (tracerActive) {
                    showTraceBody = enabled != null ? enabled : !showTraceBody;
                    newValue = showTraceBody;
                } else {
                    showHistoryBody = enabled != null ? enabled : !showHistoryBody;
                    newValue = showHistoryBody;
                }
            }
            case "wrap" -> {
                if (tracerActive) {
                    traceWordWrap = enabled != null ? enabled : !traceWordWrap;
                    newValue = traceWordWrap;
                } else {
                    historyWordWrap = enabled != null ? enabled : !historyWordWrap;
                    newValue = historyWordWrap;
                }
            }
            default -> {
                return null;
            }
        }
        return section + "=" + (newValue ? "on" : "off");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        JsonObject result = new JsonObject();
        boolean tracerActive = !traces.get().isEmpty();
        if (tracerActive) {
            if (traceDetailView && traceSelectedExchangeId != null) {
                result.put("tab", "Trace Steps");
                List<TraceEntry> steps = getTraceStepsDepthFirst(traceSelectedExchangeId);
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
            List<HistoryEntry> entries = reorderHistoryDepthFirst(historyEntries);
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
