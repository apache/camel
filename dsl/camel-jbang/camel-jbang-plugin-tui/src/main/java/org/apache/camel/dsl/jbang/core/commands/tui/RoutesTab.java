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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class RoutesTab extends AbstractTab {

    private static final String[] ROUTE_SORT_COLUMNS = { "name", "from", "status", "total", "failed" };
    private static final String[] ROUTE_TOP_SORT_COLUMNS = { "mean", "max", "min", "last", "delta", "p50", "p95", "p99" };

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
    private final ScrollbarState routeTableScrollState = new ScrollbarState();
    private final TableState processorTableState = new TableState();
    private final ScrollbarState processorTableScrollState = new ScrollbarState();
    private Rect lastRouteTableArea;
    private int topPanelHeight = -1;
    private final DragSplit vSplit = new DragSplit();
    private int infoPanelWidth = 30;
    private final DragSplit hSplit = new DragSplit();

    // Diagram support (shared rendering/loading logic)
    private final DiagramSupport diagram = new DiagramSupport();
    private final SourceViewer sourceViewer = new SourceViewer();
    private boolean diagramMetrics = true;
    private boolean showDescription;
    private static final String[] EXTERNAL_LABELS = { " [off]", " [edges]", " [all]" };
    private int externalMode;
    private boolean topologyMode = true;
    private String drillDownRouteId;
    private final Deque<String> routeNavigationStack = new ArrayDeque<>();

    RoutesTab(MonitorContext ctx) {
        super(ctx);
    }

    boolean isShowDiagram() {
        return diagram.isShowDiagram();
    }

    boolean isDiagramMetrics() {
        return diagramMetrics;
    }

    boolean isShowSource() {
        return sourceViewer.isVisible();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        // Source view scrolling (takes priority when active)
        if (sourceViewer.handleKeyEvent(ke)) {
            return true;
        }

        // Source viewer toggle (drill-down mode)
        if (!topologyMode && diagram.isShowDiagram() && ke.isChar('c')) {
            loadSourceForSelectedNode();
            return true;
        }

        // Source viewer toggle (topology mode)
        if (topologyMode && diagram.isShowDiagram() && ke.isChar('c')) {
            loadSourceForSelectedTopologyRoute();
            return true;
        }

        // Topology node navigation
        if (diagram.isShowDiagram() && topologyMode && diagram.hasDiagramData()
                && !diagram.getNodeBoxes().isEmpty()) {
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
            if (ke.isHome()) {
                diagram.selectFirstNode();
                diagram.scrollToSelectedNode();
                return true;
            }
            if (ke.isEnd()) {
                diagram.selectLastNode();
                diagram.scrollToSelectedNode();
                return true;
            }
        }

        // EIP node navigation in route drill-down mode
        if (diagram.isShowDiagram() && !topologyMode && !diagram.getEipNodeBoxes().isEmpty()) {
            if (ke.isUp()) {
                diagram.selectEipNodeUp();
                diagram.scrollToSelectedEipNode();
                return true;
            }
            if (ke.isDown()) {
                diagram.selectEipNodeDown();
                diagram.scrollToSelectedEipNode();
                return true;
            }
            if (ke.isLeft()) {
                diagram.selectEipNodeLeft();
                diagram.scrollToSelectedEipNode();
                return true;
            }
            if (ke.isRight()) {
                diagram.selectEipNodeRight();
                diagram.scrollToSelectedEipNode();
                return true;
            }
            if (ke.isHome()) {
                diagram.selectFirstEipNode();
                diagram.scrollToSelectedEipNode();
                return true;
            }
            if (ke.isEnd()) {
                diagram.selectLastEipNode();
                diagram.scrollToSelectedEipNode();
                return true;
            }
        }

        // Jump back to topology from drill-down
        if (diagram.isShowDiagram() && !topologyMode && ke.isChar('t')) {
            routeNavigationStack.clear();
            diagram.setPendingSelectionRouteId(drillDownRouteId);
            drillDownRouteId = null;
            topologyMode = true;
            diagram.setTopologyMode(true);
            diagram.setSelectedEipNodeIndex(-1);
            diagram.resetScroll();
            if (diagram.hasNativeLayout()) {
                return true;
            }
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Diagram scrolling (PgUp/PgDn etc)
        if (diagram.handleScrollKeys(ke)) {
            return true;
        }

        // Toggle metrics (diagram mode)
        if (diagram.isShowDiagram() && ke.isCharIgnoreCase('m')) {
            diagramMetrics = !diagramMetrics;
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Cycle external systems: off → edges → all → off (topology mode only)
        if (diagram.isShowDiagram() && topologyMode && ke.isCharIgnoreCase('e')) {
            externalMode = (externalMode + 1) % 3;
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Toggle description (diagram mode)
        if (diagram.isShowDiagram() && ke.isCharIgnoreCase('n')) {
            diagram.setShowDescription(!diagram.isShowDescription());
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Jump to linked route from EIP node (Enter in route mode)
        if (diagram.isShowDiagram() && !topologyMode && ke.isConfirm() && !diagram.getEipNodeBoxes().isEmpty()) {
            String linkedRouteId = diagram.findLinkedRouteId(drillDownRouteId);
            if (linkedRouteId != null && diagram.getRouteLayout(linkedRouteId) != null) {
                if (linkedRouteId.equals(drillDownRouteId)) {
                    return true;
                }
                if (routeNavigationStack.contains(linkedRouteId)) {
                    while (!routeNavigationStack.isEmpty() && !linkedRouteId.equals(routeNavigationStack.peek())) {
                        routeNavigationStack.pop();
                    }
                    routeNavigationStack.pop();
                } else {
                    routeNavigationStack.push(drillDownRouteId);
                }
                drillDownRouteId = linkedRouteId;
                diagram.selectFromNode(linkedRouteId);
                diagram.resetScroll();
                return true;
            }
            return true;
        }

        // Drill down into route diagram (Enter from topology)
        if (diagram.isShowDiagram() && topologyMode && ke.isConfirm()) {
            String selectedRouteId = diagram.getSelectedRouteId();
            if (selectedRouteId != null) {
                IntegrationInfo info = ctx.findSelectedIntegration();
                if (info != null && info.routes.stream().anyMatch(r -> selectedRouteId.equals(r.routeId))) {
                    routeNavigationStack.clear();
                    drillDownRouteId = selectedRouteId;
                    topologyMode = false;
                    diagram.setTopologyMode(false);
                    diagram.selectFromNode(selectedRouteId);
                    diagram.resetScroll();
                    diagram.endLoad();
                    if (diagram.getRouteLayout(selectedRouteId) != null) {
                        return true;
                    }
                    reloadDiagram();
                }
            }
            return true;
        }

        // Sort (only when not in diagram)
        if (!diagram.isShowDiagram() && ke.isChar('s')) {
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
        if (!diagram.isShowDiagram() && ke.isChar('S')) {
            if (routeTopMode) {
                routeTopSortReversed = !routeTopSortReversed;
            } else {
                routeSortReversed = !routeSortReversed;
            }
            return true;
        }

        // Toggle top mode (only when not in source or diagram view)
        if (!sourceViewer.isVisible() && !diagram.isShowDiagram() && ke.isCharIgnoreCase('t')) {
            routeTopMode = !routeTopMode;
            return true;
        }

        // Toggle description in route table (only when not in diagram)
        if (!sourceViewer.isVisible() && !diagram.isShowDiagram() && ke.isCharIgnoreCase('n')) {
            showDescription = !showDescription;
            return true;
        }

        // Enter in table mode opens topology
        if (!diagram.isShowDiagram() && !sourceViewer.isVisible() && ke.isConfirm()) {
            openTopology();
            return true;
        }

        // d in table mode opens route diagram, in diagram mode closes it
        if (ke.isCharIgnoreCase('d')) {
            if (diagram.isShowDiagram()) {
                closeDiagram();
            } else {
                openRouteDiagram();
            }
            return true;
        }

        // Source viewer toggle (table mode — route source)
        if (!diagram.isShowDiagram() && ke.isChar('c')) {
            if (sourceViewer.isVisible()) {
                sourceViewer.hide();
            } else {
                loadSourceForSelectedRoute();
            }
            return true;
        }

        // Route start/stop
        if (!sourceViewer.isVisible() && !diagram.isShowDiagram() && ke.isChar('p')) {
            toggleRouteStartStop();
            return true;
        }

        // Route suspend/resume
        if (!sourceViewer.isVisible() && !diagram.isShowDiagram() && ke.isChar('P') && selectedRouteSupportsSuspension()) {
            toggleRouteSuspendResume();
            return true;
        }

        return false;
    }

    @Override
    public boolean handleEscape() {
        if (sourceViewer.isVisible()) {
            sourceViewer.hide();
            return true;
        }
        if (diagram.isShowDiagram()) {
            if (!topologyMode) {
                if (!routeNavigationStack.isEmpty()) {
                    drillDownRouteId = routeNavigationStack.pop();
                    diagram.selectFromNode(drillDownRouteId);
                    diagram.resetScroll();
                    return true;
                }
                // Go back to topology
                diagram.setPendingSelectionRouteId(drillDownRouteId);
                topologyMode = true;
                diagram.setTopologyMode(true);
                diagram.setSelectedEipNodeIndex(-1);
                diagram.resetScroll();
                if (diagram.hasNativeLayout()) {
                    return true;
                }
                diagram.endLoad();
                reloadDiagram();
                return true;
            }
            // Close diagram entirely from topology
            closeDiagram();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (!diagram.isShowDiagram() && vSplit.handleMouse(me, me.y())) {
            if (vSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                topPanelHeight = Math.max(3, Math.min(me.y() - area.y(), area.height() - 5));
            }
            return true;
        }
        if (diagram.isShowDiagram() && hSplit.handleMouse(me, me.x())) {
            if (hSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                infoPanelWidth = Math.max(10, Math.min(me.x() - area.x(), area.width() - 20));
            }
            return true;
        }
        if (!diagram.isShowDiagram()) {
            IntegrationInfo info = ctx.findSelectedIntegration();
            if (info != null) {
                if (handleTableClick(me, lastRouteTableArea, routeTableState, info.routes.size())) {
                    return true;
                }
            }
        }
        if (diagram.isShowDiagram()) {
            if (diagram.handleMouseScroll(me)) {
                return true;
            }
            if (me.isClick()) {
                if (topologyMode) {
                    int clicked = diagram.handleNodeClick(me);
                    if (clicked >= 0) {
                        return true;
                    }
                } else {
                    int clicked = diagram.handleEipNodeClick(me);
                    if (clicked >= 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (!diagram.isShowDiagram()) {
            routeTableState.selectPrevious();
        }
    }

    @Override
    public void navigateDown() {
        if (!diagram.isShowDiagram()) {
            IntegrationInfo info = ctx.findSelectedIntegration();
            routeTableState.selectNext(info != null ? info.routes.size() : 0);
        }
    }

    @Override
    public void onIntegrationChanged() {
        sourceViewer.reset();
        diagram.reset();
        topologyMode = true;
        drillDownRouteId = null;
        routeNavigationStack.clear();
        routeTableState.select(0);
    }

    void preloadDiagram() {
        if (ctx.selectedPid != null) {
            diagram.preload(ctx, ctx.selectedPid);
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Fullscreen source view (from table mode)
        if (sourceViewer.isVisible() && !diagram.isShowDiagram()) {
            sourceViewer.render(frame, area);
            return;
        }

        // Fullscreen source view (from drill-down mode)
        if (sourceViewer.isVisible() && diagram.isShowDiagram()) {
            sourceViewer.render(frame, area);
            return;
        }

        // Fullscreen diagram mode
        if (diagram.isShowDiagram() && diagram.hasDiagramData()) {
            if (topologyMode && diagram.hasNativeLayout()) {
                String selectedRouteId = diagram.getSelectedRouteId();
                Line title;
                if (info.name != null) {
                    title = Line.from(
                            Span.raw(" Topology ["),
                            Span.styled(info.name, Theme.label().bold()),
                            Span.raw("] "));
                } else {
                    title = Line.from(Span.raw(" Topology "));
                }
                if (selectedRouteId != null && area.width() > 60) {
                    infoPanelWidth = Math.max(10, Math.min(infoPanelWidth, area.width() - 20));
                    List<Rect> hChunks = Layout.horizontal()
                            .constraints(Constraint.length(infoPanelWidth), Constraint.fill())
                            .split(area);
                    hSplit.setBorderPos(hChunks.get(1).x());
                    renderInfoPanel(frame, hChunks.get(0), info, selectedRouteId);
                    diagram.renderNativeDiagram(frame, hChunks.get(1), title, diagramMetrics);
                } else {
                    diagram.renderNativeDiagram(frame, area, title, diagramMetrics);
                }
                return;
            } else if (!topologyMode && drillDownRouteId != null
                    && diagram.getRouteLayout(drillDownRouteId) != null) {
                Line title = buildBreadcrumbTitle();
                var routeLayout = diagram.getRouteLayout(drillDownRouteId);
                if (area.width() > 60) {
                    infoPanelWidth = Math.max(10, Math.min(infoPanelWidth, area.width() - 20));
                    List<Rect> hChunks = Layout.horizontal()
                            .constraints(Constraint.length(infoPanelWidth), Constraint.fill())
                            .split(area);
                    hSplit.setBorderPos(hChunks.get(1).x());
                    renderEipInfoPanel(frame, hChunks.get(0));
                    diagram.renderNativeRouteDiagram(
                            frame, hChunks.get(1), title, diagramMetrics, drillDownRouteId, routeLayout);
                } else {
                    diagram.renderNativeRouteDiagram(frame, area, title, diagramMetrics, drillDownRouteId, routeLayout);
                }
                return;
            }

            // Fallback: loading or no native layout yet
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(
                                    "Loading diagram...",
                                    Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Diagram ").build())
                            .build(),
                    area);
            return;
        }

        // Normal table view
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);

        if (topPanelHeight < 0) {
            topPanelHeight = area.height() * 45 / 100;
        }
        topPanelHeight = Math.max(3, Math.min(topPanelHeight, area.height() - 5));
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(topPanelHeight), Constraint.fill())
                .split(area);

        // Routes table
        Table routeTable;
        if (routeTopMode) {
            sortedRoutes.sort(this::sortRouteTop);

            List<Row> routeRows = new ArrayList<>();
            for (RouteInfo route : sortedRoutes) {
                Style failStyle = route.failed > 0
                        ? Theme.error().bold()
                        : Style.EMPTY;

                routeRows.add(Row.from(
                        Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                        Cell.from(routeFromLabel(route)),
                        rightCell(route.total > 0 ? String.valueOf(route.meanTime) : "", 6, topTimeStyle(route.meanTime)),
                        rightCell(route.total > 0 ? String.valueOf(route.maxTime) : "", 6, topTimeStyle(route.maxTime)),
                        rightCell(route.total > 0 ? String.valueOf(route.minTime) : "", 6),
                        rightCell(route.total > 0 ? String.valueOf(route.lastTime) : "", 6),
                        rightCell(route.deltaTime != 0 ? String.valueOf(route.deltaTime) : "", 6,
                                topDeltaStyle(route.deltaTime)),
                        rightCell(route.p50Time >= 0 ? String.valueOf(route.p50Time) : "", 6),
                        rightCell(route.p95Time >= 0 ? String.valueOf(route.p95Time) : "", 6),
                        rightCell(route.p99Time >= 0 ? String.valueOf(route.p99Time) : "", 6),
                        rightCell(String.valueOf(route.total), 8),
                        rightCell(String.valueOf(route.failed), 6, failStyle),
                        rightCell(String.valueOf(route.inflight), 8),
                        rightCell(route.throughput != null ? route.throughput : "", 8),
                        rightCell(formatLoad(route.load01, route.load05, route.load15), 12)));
            }

            IntegrationInfo selTop = ctx.findSelectedIntegration();
            if (selTop != null && selTop.exchangesTotal > 0) {
                Style ts = Theme.label();
                routeRows.add(Row.from(
                        Cell.from(Span.styled("TOTAL", ts)),
                        Cell.from(""),
                        rightCell(String.valueOf(selTop.meanTime), 6, ts),
                        rightCell(String.valueOf(selTop.maxTime), 6, ts),
                        rightCell(String.valueOf(selTop.minTime), 6, ts),
                        rightCell(String.valueOf(selTop.lastTime), 6, ts),
                        rightCell(selTop.deltaTime != 0 ? String.valueOf(selTop.deltaTime) : "", 6, ts),
                        rightCell(selTop.p50Time >= 0 ? String.valueOf(selTop.p50Time) : "", 6, ts),
                        rightCell(selTop.p95Time >= 0 ? String.valueOf(selTop.p95Time) : "", 6, ts),
                        rightCell(selTop.p99Time >= 0 ? String.valueOf(selTop.p99Time) : "", 6, ts),
                        rightCell(String.valueOf(selTop.exchangesTotal), 8, ts),
                        rightCell(String.valueOf(selTop.failed), 6,
                                selTop.failed > 0 ? Theme.error().bold() : ts),
                        rightCell(String.valueOf(selTop.inflight), 8, ts),
                        rightCell(selTop.throughput != null ? selTop.throughput : "", 8, ts),
                        rightCell(TuiHelper.formatLoad(
                                selTop.inflightLoad01, selTop.inflightLoad05, selTop.inflightLoad15), 12, ts)));
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
                            rightCell(routeTopSortLabel("P50", "p50"), 6, routeTopSortStyle("p50")),
                            rightCell(routeTopSortLabel("P95", "p95"), 6, routeTopSortStyle("p95")),
                            rightCell(routeTopSortLabel("P99", "p99"), 6, routeTopSortStyle("p99")),
                            rightCell("TOTAL", 8, Style.EMPTY.bold()),
                            rightCell("FAIL", 6, Style.EMPTY.bold()),
                            rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                            rightCell("MSG/S", 8, Style.EMPTY.bold()),
                            rightCell("LOAD", 12, Style.EMPTY.bold())))
                    .widths(
                            Constraint.length(24),
                            Constraint.fill(),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(8),
                            Constraint.length(13))
                    .highlightStyle(Theme.selectionBg())
                    .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                    .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                            .title(" Routes ").build())
                    .build();
        } else {
            boolean hasPercentiles = sortedRoutes.stream().anyMatch(r -> r.p50Time >= 0);

            List<Row> routeRows = new ArrayList<>();
            for (RouteInfo route : sortedRoutes) {
                Style stateStyle = "Started".equals(route.state)
                        ? Theme.success()
                        : Theme.error();

                Style failStyle = route.failed > 0
                        ? Theme.error().bold()
                        : Style.EMPTY;

                String sinceLastRoute = formatSinceLastRoute(route);

                String timingCol;
                if (hasPercentiles && route.p50Time >= 0) {
                    timingCol = route.p50Time + "/" + route.p95Time + "/" + route.p99Time;
                } else if (route.total > 0) {
                    timingCol = route.minTime + "/" + route.maxTime + "/" + route.meanTime;
                } else {
                    timingCol = "";
                }

                routeRows.add(Row.from(
                        Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                        Cell.from(routeFromLabel(route)),
                        Cell.from(Span.styled(route.state != null ? route.state : "", stateStyle)),
                        Cell.from(route.uptime != null ? route.uptime : ""),
                        rightCell(route.coverage != null ? route.coverage : "", 6),
                        rightCell(route.throughput != null ? route.throughput : "", 8),
                        rightCell(String.valueOf(route.total), 8),
                        rightCell(String.valueOf(route.failed), 6, failStyle),
                        rightCell(String.valueOf(route.inflight), 8),
                        rightCell(timingCol, 14),
                        Cell.from(sinceLastRoute)));
            }

            IntegrationInfo selDef = ctx.findSelectedIntegration();
            if (selDef != null && selDef.exchangesTotal > 0) {
                Style ts = Theme.label();
                String totalTimingCol;
                if (hasPercentiles && selDef.p50Time >= 0) {
                    totalTimingCol = selDef.p50Time + "/" + selDef.p95Time + "/" + selDef.p99Time;
                } else {
                    totalTimingCol = selDef.minTime + "/" + selDef.maxTime + "/" + selDef.meanTime;
                }
                routeRows.add(Row.from(
                        Cell.from(Span.styled("TOTAL", ts)),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        rightCell(selDef.throughput != null ? selDef.throughput : "", 8, ts),
                        rightCell(String.valueOf(selDef.exchangesTotal), 8, ts),
                        rightCell(String.valueOf(selDef.failed), 6,
                                selDef.failed > 0 ? Theme.error().bold() : ts),
                        rightCell(String.valueOf(selDef.inflight), 8, ts),
                        rightCell(totalTimingCol, 14, ts),
                        Cell.from("")));
            }

            String timingHeader = hasPercentiles ? "P50/P95/P99" : "MIN/MAX/MEAN";

            routeTable = Table.builder()
                    .rows(routeRows)
                    .header(Row.from(
                            Cell.from(Span.styled(routeSortLabel("ROUTE", "name"), routeSortStyle("name"))),
                            Cell.from(Span.styled(routeSortLabel("FROM", "from"), routeSortStyle("from"))),
                            Cell.from(Span.styled(routeSortLabel("STATUS", "status"), routeSortStyle("status"))),
                            Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                            rightCell("COVER", 6, Style.EMPTY.bold()),
                            rightCell("MSG/S", 8, Style.EMPTY.bold()),
                            rightCell(routeSortLabel("TOTAL", "total"), 8, routeSortStyle("total")),
                            rightCell(routeSortLabel("FAIL", "failed"), 6, routeSortStyle("failed")),
                            rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                            rightCell(timingHeader, 14, Style.EMPTY.bold()),
                            Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold()))))
                    .widths(
                            Constraint.length(24),
                            Constraint.fill(),
                            Constraint.length(10),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(14),
                            Constraint.length(13))
                    .highlightStyle(Theme.selectionBg())
                    .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                    .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                            .title(" Routes ").build())
                    .build();
        }

        lastRouteTableArea = chunks.get(0);
        vSplit.setBorderPos(chunks.get(1).y());
        frame.renderStatefulWidget(routeTable, chunks.get(0), routeTableState);
        renderTableScrollbar(frame, lastRouteTableArea, routeTableState, routeTableScrollState,
                info.routes.size());

        // Bottom panel: processors
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
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Processors ")
                                    .build())
                            .build(),
                    chunks.get(1));
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (sourceViewer.isVisible()) {
            sourceViewer.renderFooter(spans);
        } else if (diagram.isShowDiagram()) {
            if (!topologyMode && !diagram.getEipNodeBoxes().isEmpty()) {
                hint(spans, "Esc", "back");
                hint(spans, "t", "topology");
                hint(spans, TuiIcons.HINT_NAV, "navigate");
                hint(spans, "PgUp/PgDn", "page");
                hint(spans, "c", "source");
            } else if (!topologyMode) {
                hint(spans, "Esc", "back");
                hint(spans, "t", "topology");
                hint(spans, TuiIcons.HINT_NAV, "scroll");
                hint(spans, "PgUp/PgDn", "page");
            } else if (!diagram.getNodeBoxes().isEmpty()) {
                hint(spans, "Esc", "close");
                hint(spans, TuiIcons.HINT_NAV, "navigate");
                hint(spans, "Enter", "drill-down");
                hint(spans, "PgUp/PgDn", "page");
                hint(spans, "c", "source");
            } else {
                diagram.renderFooterHints(spans);
            }
            hint(spans, "m", "metrics" + (diagramMetrics ? " [on]" : " [off]"));
            if (topologyMode) {
                hint(spans, "e", "external" + EXTERNAL_LABELS[externalMode]);
            }
            hint(spans, "n", "description" + (diagram.isShowDescription() ? " [on]" : " [off]"));
        } else {
            hint(spans, "Esc", "back");
            hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            hint(spans, "Enter", "topology");
            hint(spans, "d", "diagram");
            hint(spans, "s", "sort");
            hint(spans, "n", "description" + (showDescription ? " [on]" : " [off]"));
            hint(spans, "t", routeTopMode ? "top [on]" : "top [off]");
            if (!routeTopMode) {
                hint(spans, "c", "source");
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
        if (diagram.isShowDiagram() && diagramMetrics) {
            reloadDiagram();
        }
    }

    private String routeFromLabel(RouteInfo route) {
        if (showDescription && route.description != null && !route.description.isBlank()) {
            return route.description;
        }
        return route.from != null ? route.from : "";
    }

    // ---- Diagram open/close ----

    private void openTopology() {
        topologyMode = true;
        drillDownRouteId = null;
        routeNavigationStack.clear();
        diagram.setTopologyMode(true);

        // Pre-select the currently highlighted route from the table
        String selectedId = selectedRouteId();
        if (selectedId != null) {
            diagram.setPendingSelectionRouteId(selectedId);
        }

        if (diagram.hasCachedData(ctx.selectedPid)) {
            diagram.showCached();
            diagram.applyPendingSelection();
        } else {
            loadDiagram(true);
        }
    }

    private void openRouteDiagram() {
        String selectedId = selectedRouteId();
        if (selectedId == null) {
            return;
        }
        topologyMode = false;
        drillDownRouteId = selectedId;
        routeNavigationStack.clear();
        diagram.setTopologyMode(false);
        diagram.selectFromNode(selectedId);

        if (diagram.hasCachedData(ctx.selectedPid)) {
            diagram.showCached();
        } else {
            loadDiagram(true);
        }
    }

    void closeDiagram() {
        topologyMode = true;
        drillDownRouteId = null;
        routeNavigationStack.clear();
        diagram.close();
    }

    // ---- Info panels (mirrored from DiagramTab) ----

    private void renderInfoPanel(Frame frame, Rect area, IntegrationInfo info, String routeId) {
        RouteInfo route = null;
        for (RouteInfo r : info.routes) {
            if (routeId.equals(r.routeId)) {
                route = r;
                break;
            }
        }

        List<Line> lines = new ArrayList<>();
        if (route != null) {
            lines.add(Line.from(
                    Span.styled(" Route: ", Theme.muted()),
                    Span.styled(route.routeId, Style.EMPTY.fg(Theme.baseFg()).bold())));
            lines.add(Line.from(
                    Span.styled(" From:  ", Theme.muted()),
                    Span.raw(route.from != null ? route.from : "")));
            String stateLabel = route.state != null ? route.state : "";
            Style stateStyle = "Started".equals(route.state) ? Theme.success() : Theme.error();
            lines.add(Line.from(
                    Span.styled(" State: ", Theme.muted()),
                    Span.styled(stateLabel, stateStyle)));

            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled(" Uptime:     ", Theme.muted()),
                    Span.raw(route.uptime != null ? route.uptime : "")));
            lines.add(Line.from(
                    Span.styled(" Throughput: ", Theme.muted()),
                    Span.raw(route.throughput != null ? route.throughput : "")));
            if (route.coverage != null) {
                lines.add(Line.from(
                        Span.styled(" Coverage:   ", Theme.muted()),
                        Span.raw(route.coverage)));
            }

            lines.add(Line.from(Span.raw("")));
            int w = numWidth(route.total, route.failed, route.inflight);
            lines.add(Line.from(
                    Span.styled(" Total:    ", Theme.muted()),
                    Span.raw(String.format("%" + w + "d", route.total))));
            Style failStyle = route.failed > 0 ? Theme.error().bold() : Style.EMPTY;
            lines.add(Line.from(
                    Span.styled(" Failed:   ", Theme.muted()),
                    Span.styled(String.format("%" + w + "d", route.failed), failStyle)));
            lines.add(Line.from(
                    Span.styled(" Inflight: ", Theme.muted()),
                    Span.raw(String.format("%" + w + "d", route.inflight))));

            lines.add(Line.from(Span.raw("")));
            if (route.total > 0) {
                int tw = numWidth(route.meanTime, route.maxTime, route.minTime);
                lines.add(Line.from(
                        Span.styled(" Mean: ", Theme.muted()),
                        Span.raw(String.format("%" + tw + "d ms", route.meanTime))));
                lines.add(Line.from(
                        Span.styled(" Max:  ", Theme.muted()),
                        Span.raw(String.format("%" + tw + "d ms", route.maxTime))));
                lines.add(Line.from(
                        Span.styled(" Min:  ", Theme.muted()),
                        Span.raw(String.format("%" + tw + "d ms", route.minTime))));
                if (route.p50Time >= 0) {
                    int pw = numWidth(route.p50Time, route.p95Time, route.p99Time);
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(" p50:  ", Theme.muted()),
                            Span.raw(String.format("%" + pw + "d ms", route.p50Time))));
                    lines.add(Line.from(
                            Span.styled(" p95:  ", Theme.muted()),
                            Span.raw(String.format("%" + pw + "d ms", route.p95Time))));
                    lines.add(Line.from(
                            Span.styled(" p99:  ", Theme.muted()),
                            Span.raw(String.format("%" + pw + "d ms", route.p99Time))));
                }
            }

            if (route.sinceLastCompleted != null || route.sinceLastFailed != null) {
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" Since last:", Theme.muted())));
                if (route.sinceLastCompleted != null) {
                    lines.add(Line.from(
                            Span.styled("   success: ", Theme.muted()),
                            Span.raw(route.sinceLastCompleted)));
                }
                if (route.sinceLastFailed != null) {
                    lines.add(Line.from(
                            Span.styled("   fail:    ", Theme.muted()),
                            Span.styled(route.sinceLastFailed,
                                    Theme.error())));
                }
            }

        } else {
            var topoNode = diagram.getSelectedTopologyNode();
            if (topoNode != null) {
                boolean isInbound = "external-in".equals(topoNode.nodeType);
                lines.add(Line.from(
                        Span.styled(isInbound ? " Inbound" : " Outbound",
                                Style.EMPTY.fg(Theme.accent()).bold())));
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" URI: ", Theme.muted()),
                        Span.raw(topoNode.from != null ? topoNode.from : "")));
                if (topoNode.description != null && !topoNode.description.isBlank()) {
                    lines.add(Line.from(
                            Span.styled(" Path: ", Theme.muted()),
                            Span.raw(topoNode.description)));
                }
                String connectedRoute = diagram.getConnectedRouteId(routeId);
                if (connectedRoute != null) {
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(isInbound ? " To route: " : " From route: ", Theme.muted()),
                            Span.styled(connectedRoute, Style.EMPTY.fg(Theme.baseFg()))));
                }
                if (topoNode.exchangesTotal > 0 || topoNode.exchangesFailed > 0) {
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(" Total:  ", Theme.muted()),
                            Span.raw(String.valueOf(topoNode.exchangesTotal))));
                    if (topoNode.exchangesFailed > 0) {
                        lines.add(Line.from(
                                Span.styled(" Failed: ", Theme.muted()),
                                Span.styled(String.valueOf(topoNode.exchangesFailed),
                                        Theme.error().bold())));
                    }
                }
            } else {
                lines.add(Line.from(
                        Span.styled(" " + routeId, Style.EMPTY.fg(Theme.accent()).bold())));
                lines.add(Line.from(
                        Span.styled(" (external endpoint)", Style.EMPTY.dim())));
            }
        }

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Info ").build())
                .build();
        frame.renderWidget(paragraph, area);
    }

    private void renderEipInfoPanel(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        var selected = diagram.getSelectedEipNodeBox();
        if (selected != null && selected.layoutNode() != null) {
            var ln = selected.layoutNode();

            String typeLabel = ln.type != null ? ln.type : "unknown";
            Color eipColor = org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors.getEipColor(typeLabel);
            lines.add(Line.from(
                    Span.styled(" [" + typeLabel + "]", Style.EMPTY.fg(eipColor).bold())));

            String label = String.join("", ln.wrappedLines);
            if (!label.isBlank()) {
                lines.add(Line.from(
                        Span.styled(" ", Style.EMPTY.dim()),
                        Span.raw(label)));
            }

            if (ln.id != null) {
                lines.add(Line.from(
                        Span.styled(" ID: ", Style.EMPTY.dim()),
                        Span.raw(ln.id)));
            }

            lines.add(Line.from(Span.raw("")));
            String linkedRoute = diagram.findLinkedRouteId(drillDownRouteId);
            if (linkedRoute != null && diagram.getRouteLayout(linkedRoute) != null) {
                lines.add(Line.from(
                        Span.styled(" ↵ ", Theme.label().bold()),
                        Span.styled(linkedRoute, Style.EMPTY.fg(Theme.baseFg()))));
            } else if (ln.treeNode != null && ln.treeNode.info.remote) {
                String arrow = "from".equals(ln.type) ? " external → " : " → external";
                lines.add(Line.from(
                        Span.styled(arrow, Theme.muted())));
            } else {
                lines.add(Line.from(Span.raw("")));
            }

            if (ln.treeNode != null && ln.treeNode.info.stat != null) {
                var stat = ln.treeNode.info.stat;
                lines.add(Line.from(Span.raw("")));
                int w = numWidth(stat.exchangesTotal, stat.exchangesFailed, stat.exchangesInflight);
                lines.add(Line.from(
                        Span.styled(" Total:    ", Style.EMPTY.dim()),
                        Span.raw(String.format("%" + w + "d", stat.exchangesTotal))));
                Style failStyle = stat.exchangesFailed > 0
                        ? Theme.error().bold() : Style.EMPTY;
                lines.add(Line.from(
                        Span.styled(" Failed:   ", Style.EMPTY.dim()),
                        Span.styled(String.format("%" + w + "d", stat.exchangesFailed), failStyle)));
                lines.add(Line.from(
                        Span.styled(" Inflight: ", Style.EMPTY.dim()),
                        Span.raw(String.format("%" + w + "d", stat.exchangesInflight))));

                if (stat.exchangesTotal > 0) {
                    lines.add(Line.from(Span.raw("")));
                    int tw = numWidth(stat.meanProcessingTime, stat.maxProcessingTime,
                            stat.minProcessingTime, stat.lastProcessingTime);
                    lines.add(Line.from(
                            Span.styled(" Mean: ", Style.EMPTY.dim()),
                            Span.raw(String.format("%" + tw + "d ms", stat.meanProcessingTime))));
                    lines.add(Line.from(
                            Span.styled(" Max:  ", Style.EMPTY.dim()),
                            Span.raw(String.format("%" + tw + "d ms", stat.maxProcessingTime))));
                    lines.add(Line.from(
                            Span.styled(" Min:  ", Style.EMPTY.dim()),
                            Span.raw(String.format("%" + tw + "d ms", stat.minProcessingTime))));
                    lines.add(Line.from(
                            Span.styled(" Last: ", Style.EMPTY.dim()),
                            Span.raw(String.format("%" + tw + "d ms", stat.lastProcessingTime))));
                    if (stat.p50ProcessingTime >= 0) {
                        int pw = numWidth(stat.p50ProcessingTime, stat.p95ProcessingTime,
                                stat.p99ProcessingTime);
                        lines.add(Line.from(Span.raw("")));
                        lines.add(Line.from(
                                Span.styled(" p50:  ", Style.EMPTY.dim()),
                                Span.raw(String.format("%" + pw + "d ms", stat.p50ProcessingTime))));
                        lines.add(Line.from(
                                Span.styled(" p95:  ", Style.EMPTY.dim()),
                                Span.raw(String.format("%" + pw + "d ms", stat.p95ProcessingTime))));
                        lines.add(Line.from(
                                Span.styled(" p99:  ", Style.EMPTY.dim()),
                                Span.raw(String.format("%" + pw + "d ms", stat.p99ProcessingTime))));
                    }

                    if (stat.lastCompletedExchangeTimestamp > 0 || stat.lastFailedExchangeTimestamp > 0) {
                        long now = System.currentTimeMillis();
                        lines.add(Line.from(Span.raw("")));
                        lines.add(Line.from(
                                Span.styled(" Since last:", Style.EMPTY.dim())));
                        if (stat.lastCompletedExchangeTimestamp > 0) {
                            long ago = now - stat.lastCompletedExchangeTimestamp;
                            lines.add(Line.from(
                                    Span.styled("   success: ", Style.EMPTY.dim()),
                                    Span.raw(TimeUtils.printDuration(ago, false))));
                        }
                        if (stat.lastFailedExchangeTimestamp > 0) {
                            long ago = now - stat.lastFailedExchangeTimestamp;
                            lines.add(Line.from(
                                    Span.styled("   fail:    ", Style.EMPTY.dim()),
                                    Span.styled(TimeUtils.printDuration(ago, false),
                                            Theme.error())));
                        }
                    }
                }
            }
        } else {
            lines.add(Line.from(Span.styled(" (no node selected)", Style.EMPTY.dim())));
        }

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Info ").build())
                .build();
        frame.renderWidget(paragraph, area);
    }

    private Line buildBreadcrumbTitle() {
        Style nameStyle = Theme.label().bold();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" Route ["));
        if (routeNavigationStack.isEmpty()) {
            spans.add(Span.styled(drillDownRouteId, nameStyle));
        } else {
            for (var it = routeNavigationStack.descendingIterator(); it.hasNext();) {
                spans.add(Span.styled(it.next(), nameStyle));
                spans.add(Span.raw(" → "));
            }
            spans.add(Span.styled(drillDownRouteId, nameStyle));
        }
        spans.add(Span.raw("] "));
        return Line.from(spans);
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
                Style nameStyle = proc.failed > 0 ? Theme.error() : Style.EMPTY.fg(Theme.accent());
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
                    barStyle = Style.EMPTY.fg(Theme.accent());
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
                        rightCell(proc.p50Time >= 0 ? String.valueOf(proc.p50Time) : "", 6),
                        rightCell(proc.p95Time >= 0 ? String.valueOf(proc.p95Time) : "", 6),
                        rightCell(proc.p99Time >= 0 ? String.valueOf(proc.p99Time) : "", 6),
                        rightCell(String.valueOf(proc.total), 8),
                        rightCell(String.valueOf(proc.failed), 6,
                                proc.failed > 0 ? Theme.error() : Style.EMPTY),
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
                            rightCell(routeTopSortLabel("P50", "p50"), 6, routeTopSortStyle("p50")),
                            rightCell(routeTopSortLabel("P95", "p95"), 6, routeTopSortStyle("p95")),
                            rightCell(routeTopSortLabel("P99", "p99"), 6, routeTopSortStyle("p99")),
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
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(9))
                    .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                            .title(" Processors [" + route.routeId + "] ")
                            .build())
                    .build();
        } else {
            boolean hasProcPercentiles = route.p50Time >= 0
                    || route.processors.stream().anyMatch(p -> p.p50Time >= 0);

            List<Row> rows = new ArrayList<>();

            // Synthetic top row representing the route itself
            Style routeStyle = route.failed > 0 ? Theme.error() : Style.EMPTY.fg(Theme.accent());
            String routeTimingCol;
            if (hasProcPercentiles && route.p50Time >= 0) {
                routeTimingCol = route.p50Time + "/" + route.p95Time + "/" + route.p99Time;
            } else if (route.total > 0) {
                routeTimingCol = route.minTime + "/" + route.maxTime + "/" + route.meanTime;
            } else {
                routeTimingCol = "";
            }
            rows.add(Row.from(
                    Cell.from("   route"),
                    Cell.from(Span.styled(route.from != null ? route.from : route.routeId, routeStyle)),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    rightCell(String.valueOf(route.total), 8),
                    rightCell(String.valueOf(route.failed), 6,
                            route.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(String.valueOf(route.inflight), 8),
                    rightCell(routeTimingCol, 14),
                    Cell.from("")));

            for (ProcessorInfo proc : route.processors) {
                String indent = "  ".repeat(proc.level);
                Style nameStyle = proc.failed > 0 ? Theme.error() : Style.EMPTY.fg(Theme.accent());

                String procTimingCol;
                if (hasProcPercentiles && proc.p50Time >= 0) {
                    procTimingCol = proc.p50Time + "/" + proc.p95Time + "/" + proc.p99Time;
                } else if (proc.total > 0) {
                    procTimingCol = proc.minTime + "/" + proc.maxTime + "/" + proc.meanTime;
                } else {
                    procTimingCol = "";
                }

                rows.add(Row.from(
                        Cell.from("   " + (proc.processor != null ? proc.processor : "")),
                        Cell.from(Span.styled(indent + (proc.id != null ? proc.id : ""), nameStyle)),
                        Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                        rightCell(String.valueOf(proc.total), 8),
                        rightCell(String.valueOf(proc.failed), 6,
                                proc.failed > 0 ? Theme.error() : Style.EMPTY),
                        rightCell(String.valueOf(proc.inflight), 8),
                        rightCell(procTimingCol, 14),
                        Cell.from("")));
            }
            String procTimingHeader = hasProcPercentiles ? "P50/P95/P99" : "MIN/MAX/MEAN";

            table = Table.builder()
                    .rows(rows)
                    .header(Row.from(
                            Cell.from(Span.styled("   TYPE", Style.EMPTY.bold())),
                            Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                            Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                            rightCell("TOTAL", 8, Style.EMPTY.bold()),
                            rightCell("FAIL", 6, Style.EMPTY.bold()),
                            rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                            rightCell(procTimingHeader, 14, Style.EMPTY.bold()),
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
                            Constraint.length(13))
                    .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                            .title(" Processors [" + route.routeId + "] ")
                            .build())
                    .build();
        }

        frame.renderStatefulWidget(table, area, processorTableState);
        int processorRowCount = routeTopMode ? route.processors.size() : route.processors.size() + 1;
        renderTableScrollbar(frame, area, processorTableState, processorTableScrollState,
                processorRowCount);
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
            case "p50" -> Long.compare(b.p50Time, a.p50Time);
            case "p95" -> Long.compare(b.p95Time, a.p95Time);
            case "p99" -> Long.compare(b.p99Time, a.p99Time);
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
            case "p50" -> Long.compare(b.p50Time, a.p50Time);
            case "p95" -> Long.compare(b.p95Time, a.p95Time);
            case "p99" -> Long.compare(b.p99Time, a.p99Time);
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
            case "p50" -> proc.p50Time;
            case "p95" -> proc.p95Time;
            case "p99" -> proc.p99Time;
            default -> proc.meanTime;
        };
    }

    private String routeSortLabel(String label, String column) {
        return sortLabel(label, column, routeSort, routeSortReversed);
    }

    private Style routeSortStyle(String column) {
        return sortStyle(column, routeSort);
    }

    private String routeTopSortLabel(String label, String column) {
        return sortLabel(label, column, routeTopSort, routeTopSortReversed);
    }

    private Style routeTopSortStyle(String column) {
        return sortStyle(column, routeTopSort);
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

    private void loadDiagram(boolean showPlaceholder) {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!diagram.beginLoad()) {
            return;
        }

        String pid = ctx.selectedPid;
        boolean showMetrics = diagramMetrics;
        int external = externalMode;

        if (showPlaceholder) {
            diagram.setLoadingPlaceholder();
        }

        ctx.runner.scheduler().execute(() -> {
            try {
                diagram.setTopologyMode(topologyMode);
                diagram.loadAllDiagramsInBackground(ctx, pid, showMetrics, external);
            } finally {
                diagram.endLoad();
            }
        });
    }

    private void reloadDiagram() {
        loadDiagram(false);
    }

    private void loadSourceForSelectedRoute() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return;
        }
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);
        Integer sel = routeTableState.selected();
        RouteInfo selectedRoute = (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
        String routeId = selectedRoute.routeId;
        sourceViewer.setOnLineSelected(sourceLine -> {
            sourceViewer.hide();
            // Open drill-down diagram for this route
            topologyMode = false;
            drillDownRouteId = routeId;
            routeNavigationStack.clear();
            diagram.setTopologyMode(false);
            diagram.selectFromNode(routeId);
            if (diagram.hasCachedData(ctx.selectedPid)) {
                diagram.showCached();
            } else {
                loadDiagram(true);
            }
            // Select the closest EIP node after diagram is available
            int bestIdx = findClosestEipNode(sourceLine);
            if (bestIdx >= 0) {
                diagram.setSelectedEipNodeIndex(bestIdx);
                diagram.scrollToSelectedEipNode();
            }
        });
        var rl = diagram.getRouteLayout(routeId);
        sourceViewer.loadSource(ctx, routeId, 0, rl != null ? rl.source : null);
    }

    private void loadSourceForSelectedTopologyRoute() {
        String routeId = diagram.getSelectedRouteId();
        if (routeId == null) {
            return;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.stream().noneMatch(r -> routeId.equals(r.routeId))) {
            return;
        }
        sourceViewer.setOnLineSelected(sourceLine -> {
            sourceViewer.hide();
            // Switch to drill-down for this route
            routeNavigationStack.clear();
            drillDownRouteId = routeId;
            topologyMode = false;
            diagram.setTopologyMode(false);
            diagram.selectFromNode(routeId);
            diagram.resetScroll();
            diagram.endLoad();
            if (diagram.getRouteLayout(routeId) == null) {
                reloadDiagram();
            }
            int bestIdx = findClosestEipNode(sourceLine);
            if (bestIdx >= 0) {
                diagram.setSelectedEipNodeIndex(bestIdx);
                diagram.scrollToSelectedEipNode();
            }
        });
        var rl2 = diagram.getRouteLayout(routeId);
        sourceViewer.loadSource(ctx, routeId, 0, rl2 != null ? rl2.source : null);
    }

    private void loadSourceForSelectedNode() {
        if (drillDownRouteId == null) {
            return;
        }
        int targetLine = 0;
        var selected = diagram.getSelectedEipNodeBox();
        if (selected != null && selected.layoutNode() != null
                && selected.layoutNode().treeNode != null) {
            targetLine = selected.layoutNode().treeNode.info.line;
        }
        sourceViewer.setOnLineSelected(sourceLine -> {
            int bestIdx = findClosestEipNode(sourceLine);
            if (bestIdx >= 0) {
                diagram.setSelectedEipNodeIndex(bestIdx);
                diagram.scrollToSelectedEipNode();
                sourceViewer.hide();
            }
        });
        var rl3 = diagram.getRouteLayout(drillDownRouteId);
        sourceViewer.loadSource(ctx, drillDownRouteId, targetLine, rl3 != null ? rl3.source : null);
    }

    private int findClosestEipNode(int sourceLine) {
        var boxes = diagram.getEipNodeBoxes();
        if (boxes.isEmpty()) {
            return -1;
        }
        // Prefer the closest node at or before the cursor line
        int bestBeforeIdx = -1;
        int bestBeforeDist = Integer.MAX_VALUE;
        int bestAfterIdx = -1;
        int bestAfterDist = Integer.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            var box = boxes.get(i);
            if (box.layoutNode() == null || box.layoutNode().treeNode == null) {
                continue;
            }
            int nodeLine = box.layoutNode().treeNode.info.line;
            if (nodeLine <= 0) {
                continue;
            }
            if (nodeLine == sourceLine) {
                return i;
            }
            if (nodeLine < sourceLine) {
                int dist = sourceLine - nodeLine;
                if (dist < bestBeforeDist) {
                    bestBeforeDist = dist;
                    bestBeforeIdx = i;
                }
            } else {
                int dist = nodeLine - sourceLine;
                if (dist < bestAfterDist) {
                    bestAfterDist = dist;
                    bestAfterIdx = i;
                }
            }
        }
        return bestBeforeIdx >= 0 ? bestBeforeIdx : bestAfterIdx;
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
    public String description() {
        return "Route list with state, message counts, throughput, and failure statistics";
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
                - **SINCE-LAST** — Time since the last exchange activity on this route, shown as up to three values separated by `/`: started/completed/failed (e.g., `1s/3s/1m14s`). Values are omitted when there is no activity of that type

                ## Percentile Latency (P50/P95/P99)

                When **Extended** statistics level is enabled, the timing columns show
                percentile latencies instead of MIN/MEAN/MAX — both for routes and processors:

                - **P50** — Median processing time (50th percentile). Half of all exchanges completed faster than this
                - **P95** — 95th percentile. 95% of exchanges completed faster than this. Useful for SLA monitoring
                - **P99** — 99th percentile. Only 1% of exchanges were slower. Highlights worst-case tail latency

                Percentiles are computed over a sliding window of recent exchanges, making
                them more meaningful than MIN/MAX for understanding real-world performance.
                With very few messages (e.g., 10), P95 and P99 may equal the MAX value since
                there aren't enough samples to differentiate.

                To enable Extended statistics, set `camel.main.load-statistics-enabled = true`
                in your application configuration. Without Extended statistics, the columns
                show MIN/MEAN/MAX instead.

                The TOTAL summary row shows the overall percentiles across all routes.

                ## Example Screen

                ```
                 ROUTE           FROM                  STATUS   COVER  MSG/S  TOTAL  FAIL  INFLIGHT  P50/P95/P99  SINCE-LAST
                 timer-to-log    timer://hello?p=2000  Started  5/5    0.50   142    0     0           1/10/31    1s
                 timer-to-seda   timer://pump?p=3000   Started  2/2    0.33   95     0     0           0/1/2      2s
                 seda-consumer   seda://queue          Started  1/1    0.33   95     0     0           0/0/1      2s
                ```

                ## Top Mode

                Press `t` to switch to **Top mode** — a performance-focused view that
                includes processor-level breakdown and load averages. This shows every
                processor (step) inside a route, not just the route totals.

                - **LOAD** — Three throughput averages over 1m/5m/15m windows, similar to Unix load average but measuring message throughput instead of CPU. Higher values mean more messages flowing through. The three windows help you see if traffic is increasing or decreasing

                ## Route Diagram

                Press `d` to see a topology diagram showing how all routes connect to each
                other. This is the same view as the Diagram tab. Use arrow keys to navigate
                between route boxes and press `Enter` to drill down into a route's internal
                EIP structure.

                ## Navigation

                In the topology view, use arrow keys to select route boxes:
                - `↑↓` moves between layers (upstream/downstream routes)
                - `←→` moves between routes in the same layer

                When a route is selected, an **Info panel** appears on the left
                showing key metrics: state, uptime, throughput, exchange counts,
                and processing times.

                Press `Enter` on a selected route to **drill down** into its
                internal EIP structure (the route diagram). Press `Esc` to
                return to the topology view.

                ## Route Diagram (drill-down)

                In the route diagram, each EIP node shows its type tag (colored)
                and endpoint URI or description. Nodes that connect to other routes
                display a `↵` indicator — press `Enter` to jump directly to the
                linked route's diagram.

                Navigation history is maintained as a stack: pressing `Esc` goes
                back to the previous route, and eventually back to the topology view.

                ## Route Structure Preview

                A compact tree structure preview appears in the bottom-right corner
                of the diagram area — like a minimap of the route's EIP structure.

                In **topology mode**, the preview shows the structure of the currently
                selected route and updates as you navigate between route boxes.

                In **drill-down mode**, the preview highlights the currently selected
                EIP node (shown in yellow) as you navigate with arrow keys, giving
                you an at-a-glance view of where you are in the route.

                ## Source View

                Press `c` to see the original route source code (YAML, XML, or Java).
                A `>>` cursor highlights the current line. When opened from a diagram
                node, the matching source line is positioned at 2/3 of the viewport.

                Use `↑↓` to move the cursor, `Ctrl+↑↓` to scroll the viewport without
                moving the cursor. Press `Enter` to select the closest diagram node at
                the cursor line — this closes the source view and highlights that node
                in the diagram.

                ## Keys

                **Route table:**
                - `Up/Down` — select route
                - `p` — start/stop selected route
                - `P` — suspend/resume selected route
                - `d` — show topology diagram
                - `c` — show route source code
                - `n` — toggle description labels
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `t` — toggle Top mode

                **Topology view:**
                - `↑↓←→` — navigate between route boxes
                - `Enter` — drill down into selected route
                - `c` — show route source code
                - `Esc` — close diagram (back to route table)
                - `m` — toggle metrics on/off
                - `e` — toggle external systems on/off
                - `n` — toggle description labels

                **Route diagram (drill-down):**
                - `↑↓←→` — navigate between EIP nodes
                - `Enter` — jump to linked route (when `↵` indicator shown)
                - `c` — show source code at selected node
                - `Esc` — go back (previous route or topology)
                - `t` — jump back to topology view
                - `m` — toggle metrics
                - `n` — toggle description labels

                **Source view:**
                - `↑↓` — move cursor between lines
                - `Ctrl+↑↓` — scroll viewport without moving cursor
                - `←→` — horizontal scroll
                - `PgUp/PgDn` — page jump
                - `Home/End` — go to top/bottom
                - `Enter` — select the closest diagram node at cursor line
                - `Esc/c` — close source view
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Routes");
        JsonArray rows = new JsonArray();
        for (RouteInfo ri : info.routes) {
            JsonObject row = new JsonObject();
            row.put("routeId", ri.routeId);
            row.put("from", ri.from);
            row.put("state", ri.state);
            row.put("uptime", ri.uptime);
            row.put("total", ri.total);
            row.put("failed", ri.failed);
            row.put("inflight", ri.inflight);
            row.put("mean", ri.meanTime);
            row.put("max", ri.maxTime);
            row.put("min", ri.minTime);
            row.put("last", ri.lastTime);
            row.put("throughput", ri.throughput);
            if (ri.p50Time >= 0) {
                row.put("p50", ri.p50Time);
                row.put("p95", ri.p95Time);
                row.put("p99", ri.p99Time);
            }
            if (ri.group != null) {
                row.put("group", ri.group);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.routes.size());
        Integer sel = routeTableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    private static int numWidth(long... values) {
        long max = 0;
        for (long v : values) {
            max = Math.max(max, Math.abs(v));
        }
        return Math.max(1, String.valueOf(max).length());
    }
}
