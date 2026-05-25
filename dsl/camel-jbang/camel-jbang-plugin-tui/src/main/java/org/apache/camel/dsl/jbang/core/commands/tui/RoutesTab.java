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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.image.Image;
import dev.tamboui.image.ImageData;
import dev.tamboui.image.ImageScaling;
import dev.tamboui.image.capability.TerminalImageCapabilities;
import dev.tamboui.image.protocol.ImageProtocol;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
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
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.diagram.RouteDiagramAsciiRenderer;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramRenderer;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class RoutesTab implements MonitorTab {

    private static final String[] ROUTE_SORT_COLUMNS = { "name", "group", "from", "status", "total", "failed" };
    private static final String[] ROUTE_TOP_SORT_COLUMNS = { "mean", "max", "min", "last", "delta" };

    private final MonitorContext ctx;

    // Route sort state
    private String routeSort = "name";
    private int routeSortIndex;
    private boolean routeSortReversed;
    private boolean routeTopMode;
    private String routeTopSort = "mean";
    private int routeTopSortIndex;
    private boolean routeTopSortReversed;

    // Table states
    private final TableState routeTableState = new TableState();
    private final TableState processorTableState = new TableState();
    private final TableState routeHeaderTableState = new TableState();

    // Diagram state
    private boolean showDiagram;
    private boolean diagramTextMode;
    private boolean diagramAllRoutes;
    private boolean diagramMetrics = true;
    private List<RouteDiagramAsciiRenderer.CounterPos> diagramCounterPositions = Collections.emptyList();
    private Set<Integer> diagramRouteTitleRows = Collections.emptySet();
    private List<String> diagramLines = Collections.emptyList();
    private int diagramScroll;
    private int diagramScrollX;
    private final ScrollbarState diagramVScrollState = new ScrollbarState();
    private final ScrollbarState diagramHScrollState = new ScrollbarState();
    private String diagramRouteId;
    private ImageData diagramImageData;
    private ImageData diagramFullImageData;
    private ImageProtocol diagramProtocol;
    private int diagramCropX = -1;
    private int diagramCropY = -1;
    private int diagramCropW = -1;
    private int diagramCropH = -1;
    private final AtomicBoolean diagramLoading = new AtomicBoolean(false);

    // Source viewer state
    private boolean showSource;
    private List<String> sourceLines = Collections.emptyList();
    private String sourceTitle;
    private int sourceScroll;
    private int sourceScrollX;
    private final ScrollbarState sourceVScrollState = new ScrollbarState();
    private final ScrollbarState sourceHScrollState = new ScrollbarState();
    private final AtomicBoolean sourceLoading = new AtomicBoolean(false);

    RoutesTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    boolean isTopMode() {
        return routeTopMode;
    }

    boolean isShowDiagram() {
        return showDiagram;
    }

    boolean isDiagramTextMode() {
        return diagramTextMode;
    }

    boolean isDiagramMetrics() {
        return diagramMetrics;
    }

    boolean isDiagramAllRoutes() {
        return diagramAllRoutes;
    }

    boolean isShowSource() {
        return showSource;
    }

    ImageData getDiagramFullImageData() {
        return diagramFullImageData;
    }

    boolean hasImageDiagram() {
        return diagramFullImageData != null;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        // Source view scrolling
        if (showSource) {
            if (ke.isChar('c') || ke.isCancel()) {
                showSource = false;
                return true;
            }
            if (ke.isUp()) {
                sourceScroll = Math.max(0, sourceScroll - 1);
            } else if (ke.isDown()) {
                sourceScroll++;
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                sourceScroll = Math.max(0, sourceScroll - 20);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                sourceScroll += 20;
            } else if (ke.isLeft()) {
                sourceScrollX = Math.max(0, sourceScrollX - 1);
            } else if (ke.isRight()) {
                sourceScrollX++;
            } else if (ke.isHome()) {
                sourceScroll = 0;
                sourceScrollX = 0;
            } else if (ke.isEnd()) {
                sourceScroll = Integer.MAX_VALUE;
            } else {
                return false;
            }
            return true;
        }

        // Diagram scrolling and controls
        if (showDiagram) {
            if (ke.isUp()) {
                diagramScroll = Math.max(0, diagramScroll - 1);
                return true;
            }
            if (ke.isDown()) {
                diagramScroll++;
                return true;
            }
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                diagramScroll = Math.max(0, diagramScroll - 20);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                diagramScroll += 20;
                return true;
            }
            if (ke.isLeft()) {
                diagramScrollX = Math.max(0, diagramScrollX - 1);
                return true;
            }
            if (ke.isRight()) {
                diagramScrollX++;
                return true;
            }
            if (ke.isHome()) {
                diagramScroll = 0;
                diagramScrollX = 0;
                return true;
            }
            if (ke.isEnd()) {
                diagramScroll = Integer.MAX_VALUE;
                return true;
            }
            if (ke.isCharIgnoreCase('m')) {
                diagramMetrics = !diagramMetrics;
                diagramLoading.set(false);
                loadDiagramForSelectedRoute();
                return true;
            }
            if (!diagramTextMode && ke.isKey(KeyCode.F5)) {
                diagramLoading.set(false);
                loadDiagramForSelectedRoute();
                return true;
            }
        }

        // Sort
        if (ke.isChar('s')) {
            if (routeTopMode) {
                routeTopSortIndex = (routeTopSortIndex + 1) % ROUTE_TOP_SORT_COLUMNS.length;
                routeTopSort = ROUTE_TOP_SORT_COLUMNS[routeTopSortIndex];
                routeTopSortReversed = false;
            } else {
                routeSortIndex = (routeSortIndex + 1) % ROUTE_SORT_COLUMNS.length;
                routeSort = ROUTE_SORT_COLUMNS[routeSortIndex];
                routeSortReversed = false;
            }
            return true;
        }
        if (ke.isChar('S')) {
            if (routeTopMode) {
                routeTopSortReversed = !routeTopSortReversed;
            } else {
                routeSortReversed = !routeSortReversed;
            }
            return true;
        }

        // Toggle top mode (only when not in source or diagram view)
        if (!showSource && !showDiagram && ke.isCharIgnoreCase('t')) {
            routeTopMode = !routeTopMode;
            return true;
        }

        // Toggle all routes diagram flag
        if (!showSource && !showDiagram && ke.isCharIgnoreCase('a')) {
            diagramAllRoutes = !diagramAllRoutes;
            return true;
        }

        // Image diagram toggle
        if (ke.isChar('d')) {
            if (showDiagram) {
                showDiagram = false;
                diagramImageData = null;
                diagramFullImageData = null;
            } else {
                diagramTextMode = false;
                loadDiagramForSelectedRoute();
            }
            return true;
        }
        // Text diagram toggle
        if (ke.isChar('D')) {
            if (showDiagram) {
                showDiagram = false;
                diagramImageData = null;
                diagramFullImageData = null;
            } else {
                diagramTextMode = true;
                loadDiagramForSelectedRoute();
            }
            return true;
        }

        // Source viewer toggle
        if (ke.isChar('c')) {
            if (showSource) {
                showSource = false;
            } else {
                loadSourceForSelectedRoute();
            }
            return true;
        }

        // Route start/stop
        if (!showSource && !showDiagram && ke.isChar('p')) {
            toggleRouteStartStop();
            return true;
        }

        // Route suspend/resume
        if (!showSource && !showDiagram && ke.isChar('P') && selectedRouteSupportsSuspension()) {
            toggleRouteSuspendResume();
            return true;
        }

        return false;
    }

    @Override
    public boolean handleEscape() {
        if (showSource) {
            showSource = false;
            return true;
        }
        if (showDiagram) {
            showDiagram = false;
            diagramImageData = null;
            diagramFullImageData = null;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        routeTableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        routeTableState.selectNext(info != null ? info.routes.size() : 0);
    }

    @Override
    public void onIntegrationChanged() {
        showSource = false;
        sourceLines = Collections.emptyList();
        sourceTitle = null;
        sourceScroll = 0;
        sourceScrollX = 0;
        showDiagram = false;
        diagramImageData = null;
        diagramFullImageData = null;
        diagramLines = Collections.emptyList();
        diagramScroll = 0;
        diagramScrollX = 0;
        routeTableState.select(0);
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Fullscreen source view
        if (showSource) {
            List<Rect> fullChunks = Layout.vertical()
                    .constraints(Constraint.length(4), Constraint.fill())
                    .split(area);
            renderRouteHeader(frame, fullChunks.get(0), info);
            renderSource(frame, fullChunks.get(1));
            return;
        }

        // Fullscreen diagram mode
        if (showDiagram && (diagramTextMode ? !diagramLines.isEmpty() : diagramFullImageData != null)) {
            if (diagramAllRoutes) {
                renderDiagram(frame, area);
            } else {
                // Split: route info header (4 rows) + diagram (fill)
                List<Rect> fullChunks = Layout.vertical()
                        .constraints(Constraint.length(4), Constraint.fill())
                        .split(area);
                renderRouteHeader(frame, fullChunks.get(0), info);
                renderDiagram(frame, fullChunks.get(1));
            }
            return;
        }

        // Sort routes
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);

        // Split: routes table (top half) + processors table (bottom half)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(45), Constraint.percentage(55))
                .split(area);

        // Routes table
        Table routeTable;
        if (routeTopMode) {
            sortedRoutes.sort(this::sortRouteTop);

            List<Row> routeRows = new ArrayList<>();
            for (RouteInfo route : sortedRoutes) {
                Style failStyle = route.failed > 0
                        ? Style.EMPTY.fg(Color.LIGHT_RED).bold()
                        : Style.EMPTY;

                routeRows.add(Row.from(
                        Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                        Cell.from(route.from != null ? route.from : ""),
                        rightCell(route.total > 0 ? String.valueOf(route.meanTime) : "", 6, topTimeStyle(route.meanTime)),
                        rightCell(route.total > 0 ? String.valueOf(route.maxTime) : "", 6, topTimeStyle(route.maxTime)),
                        rightCell(route.total > 0 ? String.valueOf(route.minTime) : "", 6),
                        rightCell(route.total > 0 ? String.valueOf(route.lastTime) : "", 6),
                        rightCell(route.deltaTime != 0 ? String.valueOf(route.deltaTime) : "", 6,
                                topDeltaStyle(route.deltaTime)),
                        rightCell(String.valueOf(route.total), 8),
                        rightCell(String.valueOf(route.failed), 6, failStyle),
                        rightCell(String.valueOf(route.inflight), 8),
                        rightCell(route.throughput != null ? route.throughput : "", 8),
                        rightCell(formatLoad(route.load01, route.load05, route.load15), 12)));
            }

            routeTable = Table.builder()
                    .rows(routeRows)
                    .header(Row.from(
                            Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                            Cell.from(Span.styled("FROM", Style.EMPTY.bold())),
                            rightCell(routeTopSortLabel("MEAN", "mean"), 6, routeTopSortStyle("mean")),
                            rightCell(routeTopSortLabel("MAX", "max"), 6, routeTopSortStyle("max")),
                            rightCell(routeTopSortLabel("MIN", "min"), 6, routeTopSortStyle("min")),
                            rightCell(routeTopSortLabel("LAST", "last"), 6, routeTopSortStyle("last")),
                            rightCell(routeTopSortLabel("DELTA", "delta"), 6, routeTopSortStyle("delta")),
                            rightCell("TOTAL", 8, Style.EMPTY.bold()),
                            rightCell("FAIL", 6, Style.EMPTY.bold()),
                            rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                            rightCell("MSG/S", 8, Style.EMPTY.bold()),
                            rightCell("LOAD", 12, Style.EMPTY.bold())))
                    .widths(
                            Constraint.length(12),
                            Constraint.fill(),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(8),
                            Constraint.length(12))
                    .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                    .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                    .block(Block.builder().borderType(BorderType.ROUNDED)
                            .title(" Top Routes sort:" + routeTopSort + " ").build())
                    .build();
        } else {
            List<Row> routeRows = new ArrayList<>();
            for (RouteInfo route : sortedRoutes) {
                Style stateStyle = "Started".equals(route.state)
                        ? Style.EMPTY.fg(Color.GREEN)
                        : Style.EMPTY.fg(Color.LIGHT_RED);

                Style failStyle = route.failed > 0
                        ? Style.EMPTY.fg(Color.LIGHT_RED).bold()
                        : Style.EMPTY;

                String sinceLastRoute = formatSinceLastRoute(route);

                routeRows.add(Row.from(
                        Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                        Cell.from(Span.styled(route.group != null ? route.group : "", Style.EMPTY.dim())),
                        Cell.from(route.from != null ? route.from : ""),
                        Cell.from(Span.styled(route.state != null ? route.state : "", stateStyle)),
                        Cell.from(route.uptime != null ? route.uptime : ""),
                        rightCell(route.coverage != null ? route.coverage : "", 6),
                        rightCell(route.throughput != null ? route.throughput : "", 8),
                        rightCell(String.valueOf(route.total), 8),
                        rightCell(String.valueOf(route.failed), 6, failStyle),
                        rightCell(String.valueOf(route.inflight), 8),
                        rightCell(route.total > 0
                                ? route.minTime + "/" + route.maxTime + "/" + route.meanTime
                                : "", 14),
                        Cell.from(sinceLastRoute)));
            }

            routeTable = Table.builder()
                    .rows(routeRows)
                    .header(Row.from(
                            Cell.from(Span.styled(routeSortLabel("ROUTE", "name"), routeSortStyle("name"))),
                            Cell.from(Span.styled(routeSortLabel("GROUP", "group"), routeSortStyle("group"))),
                            Cell.from(Span.styled(routeSortLabel("FROM", "from"), routeSortStyle("from"))),
                            Cell.from(Span.styled(routeSortLabel("STATUS", "status"), routeSortStyle("status"))),
                            Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                            rightCell("COVER", 6, Style.EMPTY.bold()),
                            rightCell("MSG/S", 8, Style.EMPTY.bold()),
                            rightCell(routeSortLabel("TOTAL", "total"), 8, routeSortStyle("total")),
                            rightCell(routeSortLabel("FAIL", "failed"), 6, routeSortStyle("failed")),
                            rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                            rightCell("MIN/MAX/MEAN", 14, Style.EMPTY.bold()),
                            Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold()))))
                    .widths(
                            Constraint.length(12),
                            Constraint.length(14),
                            Constraint.fill(),
                            Constraint.length(10),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(14),
                            Constraint.length(12))
                    .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                    .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                    .block(Block.builder().borderType(BorderType.ROUNDED)
                            .title(" Routes sort:" + routeSort + " ").build())
                    .build();
        }

        frame.renderStatefulWidget(routeTable, chunks.get(0), routeTableState);

        // Bottom panel: diagram or processors
        if (showDiagram && (diagramImageData != null || !diagramLines.isEmpty())) {
            renderDiagram(frame, chunks.get(1));
        } else {
            Integer selectedRoute = routeTableState.selected();
            if (selectedRoute != null && selectedRoute >= 0 && selectedRoute < sortedRoutes.size()) {
                RouteInfo route = sortedRoutes.get(selectedRoute);
                renderProcessors(frame, chunks.get(1), route);
            } else if (!sortedRoutes.isEmpty()) {
                renderProcessors(frame, chunks.get(1), sortedRoutes.get(0));
            } else {
                frame.renderWidget(
                        Paragraph.builder()
                                .text(Text.from(Line.from(Span.styled("No routes", Style.EMPTY.dim()))))
                                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Processors ").build())
                                .build(),
                        chunks.get(1));
            }
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (showSource) {
            hint(spans, "c/Esc", "close");
            hint(spans, "↑↓←→", "scroll");
            hint(spans, "PgUp/PgDn", "page");
            hintLast(spans, "Home/End", "top/bottom");
        } else if (showDiagram) {
            String closeKey = diagramTextMode ? "D" : "d";
            hint(spans, closeKey + "/Esc", "close");
            hint(spans, "↑↓←→", "scroll");
            hint(spans, "PgUp/PgDn", "page");
            hint(spans, "Home/End", "top/bottom");
            if (diagramMetrics && !diagramTextMode) {
                hint(spans, "m", "metrics [on]");
                hintLast(spans, "F5", "refresh counters");
            } else {
                hintLast(spans, "m", "metrics" + (diagramMetrics ? " [on]" : " [off]"));
            }
        } else {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "navigate");
            hint(spans, "s", "sort");
            hint(spans, "t", routeTopMode ? "top [on]" : "top [off]");
            if (!routeTopMode) {
                hint(spans, "c", "source");
                hint(spans, "d", "diagram");
                hint(spans, "D", "text diagram");
                hint(spans, "a", diagramAllRoutes ? "all [on]" : "all [off]");
                String routeState = selectedRouteState();
                boolean supSus = selectedRouteSupportsSuspension();
                if ("Started".equals(routeState)) {
                    hint(spans, "p", "stop");
                    if (supSus) {
                        hint(spans, "P", "suspend");
                    }
                } else if ("Suspended".equals(routeState)) {
                    hint(spans, "p", "start");
                    if (supSus) {
                        hint(spans, "P", "resume");
                    }
                } else if (routeState != null) {
                    hint(spans, "p", "start");
                }
            }
            hint(spans, "1-9", "tabs");
        }
    }

    /**
     * Refreshes the text diagram when metrics auto-update is active. Called from tick handler.
     */
    void refreshDiagramIfNeeded() {
        if (showDiagram && diagramTextMode && diagramMetrics) {
            loadDiagramForSelectedRoute();
        }
    }

    // ---- Rendering helpers ----

    private void renderProcessors(Frame frame, Rect area, RouteInfo route) {
        Table table;

        if (routeTopMode) {
            List<Row> rows = new ArrayList<>();

            List<ProcessorInfo> sorted = new ArrayList<>(route.processors);
            sorted.sort(this::sortProcessorTop);

            long maxValue = sorted.stream().mapToLong(this::procChartValue).max().orElse(1);
            if (maxValue <= 0) {
                maxValue = 1;
            }

            for (ProcessorInfo proc : sorted) {
                Style nameStyle = proc.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.CYAN);
                long chartVal = procChartValue(proc);
                String bar;
                if (chartVal > 0) {
                    bar = buildBar(chartVal, maxValue, 20);
                } else if (proc.total > 0) {
                    bar = "█";
                } else {
                    bar = "";
                }
                Style barStyle = topTimeStyle(chartVal);
                if (barStyle == Style.EMPTY) {
                    barStyle = Style.EMPTY.fg(Color.CYAN);
                }

                rows.add(Row.from(
                        Cell.from("   " + (proc.processor != null ? proc.processor : "")),
                        Cell.from(Span.styled(proc.id != null ? proc.id : "", nameStyle)),
                        Cell.from(Span.styled(bar, barStyle)),
                        rightCell(proc.total > 0 ? String.valueOf(proc.meanTime) : "", 6, topTimeStyle(proc.meanTime)),
                        rightCell(proc.total > 0 ? String.valueOf(proc.maxTime) : "", 6, topTimeStyle(proc.maxTime)),
                        rightCell(proc.total > 0 ? String.valueOf(proc.minTime) : "", 6),
                        rightCell(proc.total > 0 ? String.valueOf(proc.lastTime) : "", 6),
                        rightCell(proc.deltaTime != 0 ? String.valueOf(proc.deltaTime) : "", 6,
                                topDeltaStyle(proc.deltaTime)),
                        rightCell(String.valueOf(proc.total), 8),
                        rightCell(String.valueOf(proc.failed), 6,
                                proc.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY),
                        rightCell(String.valueOf(proc.inflight), 8)));
            }

            table = Table.builder()
                    .rows(rows)
                    .header(Row.from(
                            Cell.from(Span.styled("   TYPE", Style.EMPTY.bold())),
                            Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                            Cell.from(""),
                            rightCell(routeTopSortLabel("MEAN", "mean"), 6, routeTopSortStyle("mean")),
                            rightCell(routeTopSortLabel("MAX", "max"), 6, routeTopSortStyle("max")),
                            rightCell(routeTopSortLabel("MIN", "min"), 6, routeTopSortStyle("min")),
                            rightCell(routeTopSortLabel("LAST", "last"), 6, routeTopSortStyle("last")),
                            rightCell(routeTopSortLabel("DELTA", "delta"), 6, routeTopSortStyle("delta")),
                            rightCell("TOTAL", 8, Style.EMPTY.bold()),
                            rightCell("FAIL", 6, Style.EMPTY.bold()),
                            rightCell("INFLIGHT", 8, Style.EMPTY.bold())))
                    .widths(
                            Constraint.length(20),
                            Constraint.length(14),
                            Constraint.fill(),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8))
                    .block(Block.builder().borderType(BorderType.ROUNDED)
                            .title(" Top Processors [" + route.routeId + "] sort:" + routeTopSort + " ").build())
                    .build();
        } else {
            List<Row> rows = new ArrayList<>();

            // Synthetic top row representing the route itself
            Style routeStyle = route.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.CYAN);
            rows.add(Row.from(
                    Cell.from("   route"),
                    Cell.from(Span.styled(route.from != null ? route.from : route.routeId, routeStyle)),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    rightCell(String.valueOf(route.total), 8),
                    rightCell(String.valueOf(route.failed), 6,
                            route.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY),
                    rightCell(String.valueOf(route.inflight), 8),
                    rightCell(route.total > 0
                            ? route.minTime + "/" + route.maxTime + "/" + route.meanTime
                            : "", 14),
                    Cell.from("")));

            for (ProcessorInfo proc : route.processors) {
                String indent = "  ".repeat(proc.level);
                Style nameStyle = proc.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY.fg(Color.CYAN);

                rows.add(Row.from(
                        Cell.from("   " + (proc.processor != null ? proc.processor : "")),
                        Cell.from(Span.styled(indent + (proc.id != null ? proc.id : ""), nameStyle)),
                        Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                        rightCell(String.valueOf(proc.total), 8),
                        rightCell(String.valueOf(proc.failed), 6,
                                proc.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY),
                        rightCell(String.valueOf(proc.inflight), 8),
                        rightCell(proc.total > 0
                                ? proc.minTime + "/" + proc.maxTime + "/" + proc.meanTime
                                : "", 14),
                        Cell.from("")));
            }

            table = Table.builder()
                    .rows(rows)
                    .header(Row.from(
                            Cell.from(Span.styled("   TYPE", Style.EMPTY.bold())),
                            Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                            Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                            rightCell("TOTAL", 8, Style.EMPTY.bold()),
                            rightCell("FAIL", 6, Style.EMPTY.bold()),
                            rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                            rightCell("MIN/MAX/MEAN", 14, Style.EMPTY.bold()),
                            Cell.from("")))
                    .widths(
                            Constraint.length(20),
                            Constraint.fill(),
                            Constraint.length(10),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(14),
                            Constraint.length(12))
                    .block(Block.builder().borderType(BorderType.ROUNDED)
                            .title(" Processors [" + route.routeId + "] ").build())
                    .build();
        }

        frame.renderStatefulWidget(table, area, processorTableState);
    }

    private void renderRouteHeader(Frame frame, Rect area, IntegrationInfo info) {
        RouteInfo route = null;
        if (diagramRouteId != null) {
            for (RouteInfo r : info.routes) {
                if (diagramRouteId.equals(r.routeId)) {
                    route = r;
                    break;
                }
            }
        }

        List<Row> rows = new ArrayList<>();
        if (route != null) {
            Style stateStyle = "Started".equals(route.state)
                    ? Style.EMPTY.fg(Color.GREEN)
                    : Style.EMPTY.fg(Color.LIGHT_RED);
            Style failStyle = route.failed > 0
                    ? Style.EMPTY.fg(Color.LIGHT_RED).bold()
                    : Style.EMPTY;
            rows.add(Row.from(
                    Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(route.from != null ? route.from : ""),
                    Cell.from(Span.styled(route.state != null ? route.state : "", stateStyle)),
                    Cell.from(route.uptime != null ? route.uptime : ""),
                    rightCell(route.throughput != null ? route.throughput : "", 8),
                    rightCell(String.valueOf(route.total), 8),
                    rightCell(String.valueOf(route.failed), 6, failStyle),
                    rightCell(String.valueOf(route.inflight), 8)));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("FROM", Style.EMPTY.bold())),
                        Cell.from(Span.styled("STATUS", Style.EMPTY.bold())),
                        Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                        rightCell("MSG/S", 8, Style.EMPTY.bold()),
                        rightCell("TOTAL", 8, Style.EMPTY.bold()),
                        rightCell("FAIL", 6, Style.EMPTY.bold()),
                        rightCell("INFLIGHT", 8, Style.EMPTY.bold())))
                .widths(
                        Constraint.length(12),
                        Constraint.fill(),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Route ").build())
                .build();

        frame.renderStatefulWidget(table, area, routeHeaderTableState);
    }

    private void renderSource(Frame frame, Rect area) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Source [" + (sourceTitle != null ? sourceTitle : "") + "] ")
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (sourceLines.isEmpty()) {
            return;
        }

        int visibleLines = inner.height();
        int maxScroll = Math.max(0, sourceLines.size() - visibleLines);
        sourceScroll = Math.min(sourceScroll, maxScroll);

        int maxLineWidth = sourceLines.stream().mapToInt(String::length).max().orElse(0);
        int maxHScroll = Math.max(0, maxLineWidth - inner.width());
        sourceScrollX = Math.min(sourceScrollX, maxHScroll);

        int end = Math.min(sourceScroll + visibleLines, sourceLines.size());
        List<Line> visible = new ArrayList<>();
        for (int i = sourceScroll; i < end; i++) {
            String raw = sourceLines.get(i);
            visible.add(TuiHelper.ansiToLine(raw, sourceScrollX));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(visible)).build(), inner);

        // Vertical scrollbar
        if (sourceLines.size() > visibleLines) {
            sourceVScrollState.contentLength(sourceLines.size()).viewportContentLength(visibleLines).position(sourceScroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), inner, sourceVScrollState);
        }
        // Horizontal scrollbar
        if (maxHScroll > 0) {
            sourceHScrollState.contentLength(maxLineWidth).viewportContentLength(inner.width()).position(sourceScrollX);
            frame.renderStatefulWidget(Scrollbar.horizontal(), inner, sourceHScrollState);
        }
    }

    private void renderDiagram(Frame frame, Rect area) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(diagramTextMode ? "" : " Diagram [" + diagramRouteId + "] ")
                .build();

        if (diagramFullImageData != null) {
            renderImageDiagram(frame, area, block);
            return;
        }

        // Compute max width for horizontal scrolling
        int maxWidth = 0;
        for (String line : diagramLines) {
            maxWidth = Math.max(maxWidth, CharWidth.of(line));
        }

        Rect inner = block.inner(area);
        // Reserve 1 col for vertical scrollbar, 1 row for horizontal scrollbar
        int visibleLines = Math.max(1, inner.height() - 1);
        int visibleCols = Math.max(1, inner.width() - 1);

        int maxVScroll = Math.max(0, diagramLines.size() - visibleLines);
        int maxHScroll = Math.max(0, maxWidth - visibleCols);
        diagramScroll = Math.min(diagramScroll, maxVScroll);
        diagramScrollX = Math.min(diagramScrollX, maxHScroll);

        // Build visible lines with horizontal offset applied
        List<Line> lines = new ArrayList<>();
        int end = Math.min(diagramScroll + visibleLines, diagramLines.size());
        for (int i = diagramScroll; i < end; i++) {
            String line = diagramLines.get(i);
            if (diagramScrollX > 0) {
                line = diagramScrollX < line.length() ? line.substring(diagramScrollX) : "";
            }
            lines.add(styleDiagramLine(line, i, diagramScrollX));
        }

        // Layout: outer block wraps everything, inner splits content + scrollbars
        frame.renderWidget(block, area);

        // Vertical layout inside the block: [content row (fill), horizontal scrollbar (1 row)]
        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        // Horizontal layout for content row: [text (fill), vertical scrollbar (1 col)]
        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        // Render diagram text
        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .build();
        frame.renderWidget(paragraph, hChunks.get(0));

        // Render vertical scrollbar
        diagramVScrollState.contentLength(diagramLines.size());
        diagramVScrollState.viewportContentLength(visibleLines);
        diagramVScrollState.position(diagramScroll);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), diagramVScrollState);

        // Render horizontal scrollbar
        if (maxWidth > visibleCols) {
            diagramHScrollState.contentLength(maxWidth);
            diagramHScrollState.viewportContentLength(visibleCols);
            diagramHScrollState.position(diagramScrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), diagramHScrollState);
        }
    }

    private void renderImageDiagram(Frame frame, Rect area, Block block) {
        int imgW = diagramFullImageData.width();
        int imgH = diagramFullImageData.height();

        Rect inner = block.inner(area);
        // Convert cell area to pixel viewport using protocol resolution
        int pxPerCol = diagramProtocol.resolution().widthMultiplier();
        int pxPerRow = diagramProtocol.resolution().heightMultiplier();
        // Reserve 1 col for vertical scrollbar, 1 row for horizontal scrollbar
        int viewCols = Math.max(1, inner.width() - 1);
        int viewRows = Math.max(1, inner.height() - 1);
        int viewW = viewCols * pxPerCol;
        int viewH = viewRows * pxPerRow;

        // Scroll units are in cells; convert to pixels for clamping
        int maxScrollY = Math.max(0, (imgH - viewH + pxPerRow - 1) / pxPerRow);
        int maxScrollX = Math.max(0, (imgW - viewW + pxPerCol - 1) / pxPerCol);
        diagramScroll = Math.min(diagramScroll, maxScrollY);
        diagramScrollX = Math.min(diagramScrollX, maxScrollX);

        int cropX = Math.min(diagramScrollX * pxPerCol, imgW);
        int cropY = Math.min(diagramScroll * pxPerRow, imgH);
        int cropW = Math.min(viewW, imgW - cropX);
        int cropH = Math.min(viewH, imgH - cropY);

        if (cropW > 0 && cropH > 0) {
            if (cropX != diagramCropX || cropY != diagramCropY
                    || cropW != diagramCropW || cropH != diagramCropH) {
                diagramImageData = diagramFullImageData.crop(cropX, cropY, cropW, cropH);
                diagramCropX = cropX;
                diagramCropY = cropY;
                diagramCropW = cropW;
                diagramCropH = cropH;
            }
        } else if (diagramImageData != diagramFullImageData) {
            diagramImageData = diagramFullImageData;
        }

        // Render the outer block border
        frame.renderWidget(block, area);

        // Vertical layout inside the block: [image+vscrollbar (fill), hscrollbar (1 row)]
        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        // Horizontal layout: [image (fill), vertical scrollbar (1 col)]
        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        // Render cropped image
        Image img = Image.builder()
                .data(diagramImageData)
                .protocol(diagramProtocol)
                .scaling(ImageScaling.FIT)
                .build();
        frame.renderWidget(img, hChunks.get(0));

        // Render vertical scrollbar
        int totalRows = (imgH + pxPerRow - 1) / pxPerRow;
        diagramVScrollState.contentLength(totalRows);
        diagramVScrollState.viewportContentLength(viewRows);
        diagramVScrollState.position(diagramScroll);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), diagramVScrollState);

        // Render horizontal scrollbar
        if (imgW > viewW) {
            int totalCols = (imgW + pxPerCol - 1) / pxPerCol;
            diagramHScrollState.contentLength(totalCols);
            diagramHScrollState.viewportContentLength(viewCols);
            diagramHScrollState.position(diagramScrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), diagramHScrollState);
        }
    }

    // ---- Diagram styling ----

    private Line styleDiagramLine(String text, int row, int scrollX) {
        if (diagramRouteTitleRows.contains(row)) {
            return Line.from(Span.styled(text, Style.EMPTY.fg(Color.WHITE).bold()));
        }

        // Build counter color ranges for this row
        List<int[]> counterRanges = new ArrayList<>();
        for (RouteDiagramAsciiRenderer.CounterPos cp : diagramCounterPositions) {
            if (cp.row() == row) {
                int start = cp.col() - scrollX;
                int end = start + cp.length();
                if (end > 0 && start < text.length()) {
                    start = Math.max(0, start);
                    end = Math.min(end, text.length());
                    int colorFlag = cp.type() == RouteDiagramAsciiRenderer.CounterType.OK ? 1 : 2;
                    counterRanges.add(new int[] { start, end, colorFlag });
                }
            }
        }

        List<Span> spans = new ArrayList<>();
        int idx = 0;
        while (idx < text.length()) {
            int open = text.indexOf('[', idx);
            if (open < 0) {
                addStyledSegment(spans, text, idx, text.length(), counterRanges, Color.WHITE);
                break;
            }
            int close = text.indexOf(']', open);
            if (close < 0) {
                addStyledSegment(spans, text, idx, text.length(), counterRanges, Color.WHITE);
                break;
            }
            if (open > idx) {
                addStyledSegment(spans, text, idx, open, counterRanges, Color.GRAY);
            }
            String tag = text.substring(open + 1, close);
            Color tagColor = getDiagramNodeColor(tag);
            spans.add(Span.styled("[" + tag + "]", Style.EMPTY.fg(tagColor).bold()));

            int afterTag = close + 1;
            int nextOpen = text.indexOf('[', afterTag);
            int labelEnd = nextOpen >= 0 ? nextOpen : text.length();
            if (afterTag < labelEnd) {
                addStyledSegment(spans, text, afterTag, labelEnd, counterRanges, Color.WHITE);
            }
            idx = labelEnd;
        }
        return Line.from(spans);
    }

    private void addStyledSegment(
            List<Span> spans, String text, int from, int to, List<int[]> counterRanges, Color defaultColor) {
        int pos = from;
        while (pos < to) {
            int[] cr = findNextCounterRange(counterRanges, pos, to);
            if (cr != null) {
                if (pos < cr[0]) {
                    spans.add(Span.styled(text.substring(pos, cr[0]), Style.EMPTY.fg(defaultColor)));
                }
                int counterEnd = Math.min(cr[1], to);
                Color counterColor = cr[2] == 1 ? Color.GREEN : Color.LIGHT_RED;
                spans.add(Span.styled(text.substring(cr[0], counterEnd), Style.EMPTY.fg(counterColor).bold()));
                pos = counterEnd;
            } else {
                spans.add(Span.styled(text.substring(pos, to), Style.EMPTY.fg(defaultColor)));
                pos = to;
            }
        }
    }

    private static int[] findNextCounterRange(List<int[]> ranges, int pos, int limit) {
        int[] best = null;
        for (int[] range : ranges) {
            if (range[1] > pos && range[0] < limit) {
                int start = Math.max(range[0], pos);
                if (best == null || start < best[0]) {
                    best = new int[] { start, range[1], range[2] };
                }
            }
        }
        return best;
    }

    private Color getDiagramNodeColor(String type) {
        if (type == null) {
            return Color.GRAY;
        }
        return switch (type) {
            case "from" -> Color.GREEN;
            case "to", "toD", "wireTap", "enrich", "pollEnrich" -> Color.CYAN;
            case "choice", "when", "otherwise" -> Color.YELLOW;
            case "marshal", "unmarshal", "transform", "setBody", "setHeader", "setProperty",
                    "convertBodyTo", "removeHeader", "removeHeaders", "removeProperty", "removeProperties" ->
                Color.CYAN;
            case "bean", "process", "log", "script", "delay" -> Color.MAGENTA;
            case "filter", "split", "aggregate", "multicast", "recipientList",
                    "routingSlip", "dynamicRouter", "loadBalance",
                    "circuitBreaker", "saga", "doTry", "doCatch", "doFinally",
                    "onException", "onCompletion", "intercept",
                    "loop", "resequence", "throttle", "kamelet", "pipeline", "threads" ->
                Color.rgb(0x89, 0x57, 0xE5);
            default -> Color.GRAY;
        };
    }

    // ---- Sorting ----

    private int sortRoute(RouteInfo a, RouteInfo b) {
        int result = switch (routeSort) {
            case "total" -> Long.compare(b.total, a.total);
            case "failed" -> Long.compare(b.failed, a.failed);
            case "name" -> {
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
            case "status" -> {
                String sa = a.state != null ? a.state : "";
                String sb2 = b.state != null ? b.state : "";
                yield sa.compareToIgnoreCase(sb2);
            }
            case "group" -> {
                String ga = a.group != null ? a.group : "";
                String gb = b.group != null ? b.group : "";
                yield ga.compareToIgnoreCase(gb);
            }
            case "from" -> {
                String fa = a.from != null ? a.from : "";
                String fb = b.from != null ? b.from : "";
                yield fa.compareToIgnoreCase(fb);
            }
            default -> 0;
        };
        return routeSortReversed ? -result : result;
    }

    private int sortRouteTop(RouteInfo a, RouteInfo b) {
        int result = switch (routeTopSort) {
            case "mean" -> Long.compare(b.meanTime, a.meanTime);
            case "max" -> Long.compare(b.maxTime, a.maxTime);
            case "min" -> Long.compare(b.minTime, a.minTime);
            case "last" -> Long.compare(b.lastTime, a.lastTime);
            case "delta" -> Long.compare(b.deltaTime, a.deltaTime);
            default -> 0;
        };
        return routeTopSortReversed ? -result : result;
    }

    private int sortProcessorTop(ProcessorInfo a, ProcessorInfo b) {
        int result = switch (routeTopSort) {
            case "mean" -> Long.compare(b.meanTime, a.meanTime);
            case "max" -> Long.compare(b.maxTime, a.maxTime);
            case "min" -> Long.compare(b.minTime, a.minTime);
            case "last" -> Long.compare(b.lastTime, a.lastTime);
            case "delta" -> Long.compare(b.deltaTime, a.deltaTime);
            default -> 0;
        };
        return routeTopSortReversed ? -result : result;
    }

    private long procChartValue(ProcessorInfo proc) {
        return switch (routeTopSort) {
            case "mean" -> proc.meanTime;
            case "max" -> proc.maxTime;
            case "min" -> proc.minTime;
            case "last" -> proc.lastTime;
            case "delta" -> Math.abs(proc.deltaTime);
            default -> proc.meanTime;
        };
    }

    private String routeSortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, routeSort, routeSortReversed);
    }

    private Style routeSortStyle(String column) {
        return MonitorContext.sortStyle(column, routeSort);
    }

    private String routeTopSortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, routeTopSort, routeTopSortReversed);
    }

    private Style routeTopSortStyle(String column) {
        return MonitorContext.sortStyle(column, routeTopSort);
    }

    // ---- Route actions ----

    private String selectedRouteState() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return null;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo route = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        return route.state;
    }

    private boolean selectedRouteSupportsSuspension() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return false;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo route = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        return route.supportsSuspension;
    }

    private void toggleRouteStartStop() {
        if (ctx.selectedPid == null) {
            return;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo route = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        // Started -> stop; Stopped or Suspended -> start
        String command = "Started".equals(route.state) ? "stop" : "start";
        sendRouteCommand(ctx.selectedPid, route.routeId, command);
    }

    private void toggleRouteSuspendResume() {
        if (ctx.selectedPid == null) {
            return;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo route = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        // Started -> suspend; Suspended -> resume; Stopped -> start
        String command = switch (route.state != null ? route.state : "") {
            case "Started" -> "suspend";
            case "Suspended" -> "resume";
            default -> "start";
        };
        sendRouteCommand(ctx.selectedPid, route.routeId, command);
    }

    void sendRouteCommand(String pid, String routeId, String command) {
        JsonObject root = new JsonObject();
        root.put("action", "route");
        root.put("id", routeId);
        root.put("command", command);
        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);
    }

    // ---- Async loading ----

    private void loadDiagramForSelectedRoute() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!diagramLoading.compareAndSet(false, true)) {
            return;
        }

        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            diagramLoading.set(false);
            return;
        }

        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);

        Integer sel = routeTableState.selected();
        RouteInfo selectedRoute;
        if (sel != null && sel >= 0 && sel < sortedRoutes.size()) {
            selectedRoute = sortedRoutes.get(sel);
        } else {
            selectedRoute = sortedRoutes.get(0);
        }

        // Capture state needed by the background thread
        String pid = ctx.selectedPid;
        boolean textMode = diagramTextMode;
        boolean showMetrics = diagramMetrics;
        String routeId = diagramAllRoutes ? null : selectedRoute.routeId;

        boolean initialLoad = !showDiagram;
        if (initialLoad) {
            diagramRouteId = routeId != null ? routeId : "all";
            diagramLines = List.of("(Loading diagram...)");
            diagramImageData = null;
            diagramFullImageData = null;
            showDiagram = true;
            diagramScroll = 0;
            diagramScrollX = 0;
        }

        ctx.runner.scheduler().execute(() -> {
            try {
                loadDiagramInBackground(pid, textMode, routeId, showMetrics);
            } finally {
                diagramLoading.set(false);
            }
        });
    }

    private void loadDiagramInBackground(String pid, boolean textMode, String routeId, boolean metrics) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-structure");
        root.put("filter", "*");
        root.put("brief", false);
        root.put("metric", true);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            applyDiagramResult(routeId, List.of("(No response from integration)"), null, null, null);
            return;
        }

        JsonArray arr = (JsonArray) jo.get("routes");
        if (arr == null) {
            applyDiagramResult(routeId, List.of("(No routes in response)"), null, null, null);
            return;
        }

        List<RouteDiagramLayoutEngine.RouteInfo> diagramRoutes = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            String rid = objToString(o.get("routeId"));
            if (routeId != null && !routeId.equals(rid)) {
                continue;
            }
            RouteDiagramLayoutEngine.RouteInfo route = new RouteDiagramLayoutEngine.RouteInfo();
            route.routeId = rid;
            List<JsonObject> lines = o.getCollection("code");
            if (lines != null) {
                for (JsonObject line : lines) {
                    RouteDiagramLayoutEngine.NodeInfo node = new RouteDiagramLayoutEngine.NodeInfo();
                    node.type = objToString(line.get("type"));
                    node.code = Jsoner.unescape(objToString(line.get("code")));
                    Integer level = line.getInteger("level");
                    node.level = level != null ? level : 0;
                    JsonObject stats = (JsonObject) line.get("statistics");
                    if (stats != null) {
                        RouteDiagramLayoutEngine.StatInfo stat = new RouteDiagramLayoutEngine.StatInfo();
                        stat.exchangesTotal = stats.getLongOrDefault("exchangesTotal", 0);
                        stat.exchangesFailed = stats.getLongOrDefault("exchangesFailed", 0);
                        node.stat = stat;
                    }
                    route.nodes.add(node);
                }
            }
            diagramRoutes.add(route);
        }

        if (textMode) {
            RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                    RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE,
                    RouteDiagramLayoutEngine.NodeLabelMode.CODE);

            List<String> result = new ArrayList<>();
            List<RouteDiagramAsciiRenderer.CounterPos> positions = new ArrayList<>();
            Set<Integer> titleRows = new HashSet<>();

            for (RouteDiagramLayoutEngine.RouteInfo r : diagramRoutes) {
                // Add separator between routes
                if (!result.isEmpty()) {
                    result.add("");
                    result.add("");
                }

                int titleRow = result.size();

                RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, RouteDiagramLayoutEngine.PADDING);
                RouteDiagramAsciiRenderer asciiRenderer = new RouteDiagramAsciiRenderer(
                        RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE, true, metrics);
                String ascii = asciiRenderer.renderDiagram(List.of(lr), lr.maxY + RouteDiagramLayoutEngine.V_GAP);
                List<RouteDiagramAsciiRenderer.CounterPos> origPositions = asciiRenderer.getCounterPositions();

                // Strip empty lines and remap counter positions
                String[] rawLines = ascii.split("\n", -1);
                int[] rowMapping = new int[rawLines.length];
                int baseRow = result.size();
                int newRow = baseRow;
                for (int i = 0; i < rawLines.length; i++) {
                    if (!rawLines[i].isEmpty()) {
                        rowMapping[i] = newRow++;
                        result.add(rawLines[i]);
                    } else {
                        rowMapping[i] = -1;
                    }
                }
                for (RouteDiagramAsciiRenderer.CounterPos cp : origPositions) {
                    if (cp.row() >= 0 && cp.row() < rowMapping.length && rowMapping[cp.row()] >= 0) {
                        positions.add(new RouteDiagramAsciiRenderer.CounterPos(
                                rowMapping[cp.row()], cp.col(), cp.length(), cp.type()));
                    }
                }
                titleRows.add(titleRow);
            }

            applyDiagramResult(routeId, result, null, null, null, positions, titleRows);
        } else {
            TerminalImageCapabilities caps = TerminalImageCapabilities.detect();
            if (caps.supportsNativeImages()) {
                RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
                List<RouteDiagramLayoutEngine.LayoutRoute> layoutRoutes = new ArrayList<>();
                int totalHeight = 0;
                for (RouteDiagramLayoutEngine.RouteInfo r : diagramRoutes) {
                    RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, totalHeight);
                    layoutRoutes.add(lr);
                    totalHeight = lr.maxY;
                }
                RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                        engine.getNodeWidth(), RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE * RouteDiagramLayoutEngine.SCALE,
                        metrics);
                RouteDiagramRenderer.DiagramColors colors = RouteDiagramRenderer.DiagramColors.parse("transparent");
                java.awt.image.BufferedImage image = renderer.renderDiagram(layoutRoutes, totalHeight, colors);
                ImageData fullImage = ImageData.fromBufferedImage(image);
                ImageData resized = fullImage.resize(fullImage.width() / 2, fullImage.height() / 2);
                ImageProtocol protocol = caps.bestProtocol();
                applyDiagramResult(routeId, Collections.emptyList(), resized, resized, protocol);
            } else {
                applyDiagramResult(routeId, List.of(
                        "(Terminal does not support image rendering)",
                        "(Press Shift+D for text diagram)"), null, null, null);
            }
        }
    }

    private void applyDiagramResult(
            String routeId, List<String> lines, ImageData imageData, ImageData fullImageData, ImageProtocol protocol) {
        applyDiagramResult(routeId, lines, imageData, fullImageData, protocol, Collections.emptyList(),
                Collections.emptySet());
    }

    private void applyDiagramResult(
            String routeId, List<String> lines, ImageData imageData, ImageData fullImageData, ImageProtocol protocol,
            List<RouteDiagramAsciiRenderer.CounterPos> positions, Set<Integer> titleRows) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            boolean wasShowing = showDiagram;
            diagramRouteId = routeId != null ? routeId : "all";
            diagramLines = lines;
            diagramCounterPositions = positions;
            diagramRouteTitleRows = titleRows;
            diagramImageData = imageData;
            diagramFullImageData = fullImageData;
            diagramProtocol = protocol;
            if (!wasShowing) {
                diagramScroll = 0;
                diagramScrollX = 0;
                diagramCropX = -1;
                diagramCropY = -1;
                diagramCropW = -1;
                diagramCropH = -1;
            }
            // Only restore showDiagram if user hasn't cancelled via Esc while loading
            if (wasShowing) {
                showDiagram = true;
            }
        });
    }

    private void loadSourceForSelectedRoute() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!sourceLoading.compareAndSet(false, true)) {
            return;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            sourceLoading.set(false);
            return;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo selectedRoute = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);

        sourceLines = List.of("(Loading source...)");
        sourceTitle = selectedRoute.routeId;
        sourceScroll = 0;
        sourceScrollX = 0;
        showSource = true;

        String pid = ctx.selectedPid;
        String routeId = selectedRoute.routeId;
        ctx.runner.scheduler().execute(() -> {
            try {
                loadSourceInBackground(pid, routeId);
            } finally {
                sourceLoading.set(false);
            }
        });
    }

    private void loadSourceInBackground(String pid, String routeId) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "source");
        root.put("filter", routeId);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            applySourceResult(routeId, null, List.of("(No response from integration)"));
            return;
        }

        JsonArray routes = (JsonArray) jo.get("routes");
        if (routes == null || routes.isEmpty()) {
            applySourceResult(routeId, null, List.of("(No source available for route: " + routeId + ")"));
            return;
        }

        JsonObject routeObj = (JsonObject) routes.get(0);
        String sourceLocation = objToString(routeObj.get("source"));
        List<JsonObject> codeLines = routeObj.getCollection("code");
        if (codeLines == null || codeLines.isEmpty()) {
            applySourceResult(routeId, sourceLocation, List.of("(No source code available)"));
            return;
        }

        List<String> lines = new ArrayList<>();
        int maxLineNum = 0;
        for (JsonObject codeLine : codeLines) {
            Integer lineNum = codeLine.getInteger("line");
            if (lineNum != null && lineNum > maxLineNum) {
                maxLineNum = lineNum;
            }
        }
        int lineNumWidth = String.valueOf(maxLineNum).length();
        int matchLine = -1;
        int idx = 0;
        for (JsonObject codeLine : codeLines) {
            Integer lineNum = codeLine.getInteger("line");
            String code = Jsoner.unescape(objToString(codeLine.get("code")));
            Boolean match = codeLine.getBoolean("match");
            String prefix = lineNum != null
                    ? String.format("%" + lineNumWidth + "d  ", lineNum)
                    : String.format("%" + lineNumWidth + "s  ", "");
            lines.add(prefix + code);
            if (Boolean.TRUE.equals(match) && matchLine < 0) {
                matchLine = idx;
            }
            idx++;
        }

        int scrollTo = matchLine > 0 ? Math.max(0, matchLine - 2) : 0;
        applySourceResult(routeId, sourceLocation, lines, scrollTo);
    }

    private void applySourceResult(String routeId, String location, List<String> lines) {
        applySourceResult(routeId, location, lines, 0);
    }

    private void applySourceResult(String routeId, String location, List<String> lines, int scrollTo) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            if (!showSource) {
                return; // user cancelled via Esc while loading
            }
            sourceTitle = location != null ? routeId + "  " + location : routeId;
            sourceLines = lines;
            sourceScroll = scrollTo;
        });
    }

    private static String objToString(Object o) {
        return o != null ? o.toString() : "";
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return null;
        }
        List<RouteInfo> sorted = new ArrayList<>(info.routes);
        sorted.sort(this::sortRoute);
        List<String> items = sorted.stream().map(r -> r.routeId != null ? r.routeId : "").toList();
        Integer sel = routeTableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Routes");
    }
}
