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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.image.ImageData;
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
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
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

    // Diagram support (shared rendering/loading logic)
    private final DiagramSupport diagram = new DiagramSupport();
    private boolean diagramAllRoutes;
    private boolean diagramMetrics = true;
    private String diagramRouteId;

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
        return diagram.isShowDiagram();
    }

    boolean isDiagramTextMode() {
        return diagram.isDiagramTextMode();
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
        return diagram.getFullImageData();
    }

    boolean hasImageDiagram() {
        return diagram.getFullImageData() != null;
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

        // Diagram scrolling
        if (diagram.handleScrollKeys(ke)) {
            return true;
        }

        // Diagram-specific controls (only when diagram is showing)
        if (diagram.isShowDiagram()) {
            if (ke.isCharIgnoreCase('m')) {
                diagramMetrics = !diagramMetrics;
                diagram.endLoad();
                loadDiagramForSelectedRoute();
                return true;
            }
            if (!diagram.isDiagramTextMode() && ke.isKey(KeyCode.F5)) {
                diagram.endLoad();
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
        if (!showSource && !diagram.isShowDiagram() && ke.isCharIgnoreCase('t')) {
            routeTopMode = !routeTopMode;
            return true;
        }

        // Toggle all routes diagram flag
        if (!showSource && !diagram.isShowDiagram() && ke.isCharIgnoreCase('a')) {
            diagramAllRoutes = !diagramAllRoutes;
            return true;
        }

        // Image diagram toggle
        if (ke.isChar('d')) {
            diagram.toggleImageDiagram(this::loadDiagramForSelectedRoute);
            return true;
        }
        // Text diagram toggle
        if (ke.isChar('D')) {
            diagram.toggleTextDiagram(this::loadDiagramForSelectedRoute);
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
        if (!showSource && !diagram.isShowDiagram() && ke.isChar('p')) {
            toggleRouteStartStop();
            return true;
        }

        // Route suspend/resume
        if (!showSource && !diagram.isShowDiagram() && ke.isChar('P') && selectedRouteSupportsSuspension()) {
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
        return diagram.handleEscape();
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
        diagram.reset();
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
        if (diagram.isShowDiagram() && diagram.hasDiagramData()) {
            String title = diagram.isDiagramTextMode() ? "" : " Diagram [" + diagramRouteId + "] ";
            if (diagramAllRoutes) {
                diagram.renderDiagram(frame, area, title);
            } else {
                List<Rect> fullChunks = Layout.vertical()
                        .constraints(Constraint.length(4), Constraint.fill())
                        .split(area);
                renderRouteHeader(frame, fullChunks.get(0), info);
                diagram.renderDiagram(frame, fullChunks.get(1), title);
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
        if (diagram.isShowDiagram() && diagram.hasDiagramData()) {
            String title = diagram.isDiagramTextMode() ? "" : " Diagram [" + diagramRouteId + "] ";
            diagram.renderDiagram(frame, chunks.get(1), title);
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
        } else if (diagram.isShowDiagram()) {
            diagram.renderFooterHints(spans);
            if (diagramMetrics && !diagram.isDiagramTextMode()) {
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
        }
    }

    void refreshDiagramIfNeeded() {
        if (diagram.isShowDiagram() && diagram.isDiagramTextMode() && diagramMetrics) {
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

    String selectedRouteId() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return null;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo route = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        return route.routeId;
    }

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
        if (!diagram.beginLoad()) {
            return;
        }

        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            diagram.endLoad();
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

        String pid = ctx.selectedPid;
        boolean textMode = diagram.isDiagramTextMode();
        boolean showMetrics = diagramMetrics;
        String routeId = diagramAllRoutes ? null : selectedRoute.routeId;

        diagramRouteId = routeId != null ? routeId : "all";
        diagram.setLoadingPlaceholder();

        ctx.runner.scheduler().execute(() -> {
            try {
                diagram.loadRouteDiagramInBackground(ctx, pid, textMode, routeId, showMetrics);
            } finally {
                diagram.endLoad();
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
                return;
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

    @Override
    public String getHelpText() {
        return """
                # Routes

                Routes are the building blocks of a Camel integration. Each route defines
                a message flow: where messages come from, how they are processed, and where
                they are sent to. A typical integration has multiple routes working together.

                ## Route Table Columns

                - **ROUTE** — Unique route identifier (e.g., `timer-to-log`, `seda-consumer`)
                - **FROM** — The endpoint that triggers this route (e.g., `timer`, `kafka`, `file`). This is the source of messages
                - **STATUS** — Route state: `Started` (running), `Stopped` (not running), or `Suspended` (paused, can be resumed)
                - **COVER** — Node coverage: what percentage of route nodes have processed at least one message. Shows as `5/10` meaning 5 of 10 nodes were reached. Helps find dead code paths — nodes that are defined but never reached. 100% coverage means all branches in the route have been exercised
                - **MSG/S** — Current message throughput (messages per second) for this route
                - **TOTAL** — Total exchanges processed by this route since startup
                - **FAIL** — Exchanges that ended with an unhandled error in this route
                - **INFLIGHT** — Exchanges currently being processed by this route
                - **MIN** — Fastest exchange processing time in milliseconds. This is the time from when the exchange entered the route until it completed
                - **MEAN** — Average exchange processing time in milliseconds. A rising MEAN may indicate a downstream service getting slower
                - **MAX** — Slowest exchange processing time in milliseconds. A very high MAX compared to MEAN suggests occasional slow outliers
                - **SINCE-LAST** — Time since the last exchange was processed by this route

                ## Example Screen

                ```
                 ROUTE           FROM                  STATUS   COVER  MSG/S  TOTAL  FAIL  INFLIGHT  MIN  MEAN  MAX  SINCE-LAST
                 timer-to-log    timer://hello?p=2000  Started  5/5    0.50   142    0     0         0    0     2    1s
                 timer-to-seda   timer://pump?p=3000   Started  2/2    0.33   95     0     0         0    0     2    2s
                 seda-consumer   seda://queue          Started  1/1    0.33   95     0     0         0    0     0    2s
                ```

                ## Top Mode

                Press `t` to switch to **Top mode** — a performance-focused view that
                includes processor-level breakdown and load averages. This shows every
                processor (step) inside a route, not just the route totals.

                - **LOAD** — Three throughput averages over 1m/5m/15m windows, similar to Unix load average but measuring message throughput instead of CPU. Higher values mean more messages flowing through. The three windows help you see if traffic is increasing or decreasing

                ## Route Diagram

                Press `d` to see a visual flow chart of the selected route. The diagram
                shows every EIP node and how messages flow between them. Numbers on
                each node show how many exchanges passed through it.

                Scroll down to view the diagram example:

                ```
                    ┌──────────────────────┐
                    │ from[timer:hello?..] │
                    └──────────────────────┘
                                │
                                ▼ 29
                    ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
                    ╎  ┌────────────────────────┐  ╎
                    ╎  │        choice          │  ╎
                    ╎  └────────────────────────┘  ╎
                    ╎       │              │       ╎
                    ╎       ▼ 9            ▼ 20    ╎
                    ╎  ┌──────────┐  ┌──────────┐  ╎
                    ╎  │ log[HI]  │  │ log[LO]  │  ╎
                    ╎  └──────────┘  └──────────┘  ╎
                    ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
                ```

                The dotted border groups nodes that belong to the same EIP block
                (like `choice`, `split`, `multicast`). The numbers show how many
                exchanges took each branch — useful for verifying routing logic.

                ## Source View

                Press `s` to see the original route source code (YAML, XML, or Java).

                ## Keys

                - `Up/Down` — select route
                - `d` — show route diagram
                - `s` — show route source / cycle sort column (context-dependent)
                - `S` — reverse sort order
                - `t` — toggle Top mode
                - `Enter` — view detailed route info
                - `Esc` — back to route list
                """;
    }
}
