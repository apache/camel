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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
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
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
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

    // Detail panel (d toggle in drill-down mode)
    private boolean detailMode;
    private final DiagramDetailSupport detail = new DiagramDetailSupport(ctx, diagram);

    // Go-to node popup (g in drill-down mode)
    private final GotoNodePopup gotoNodePopup = new GotoNodePopup();

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
    public boolean isOverlayActive() {
        return sourceViewer.isVisible() || gotoNodePopup.isVisible();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        // Go-to node popup (takes priority when active)
        if (gotoNodePopup.isVisible()) {
            gotoNodePopup.handleKeyEvent(ke);
            int sel = gotoNodePopup.consumeSelectedIndex();
            if (sel >= 0) {
                diagram.setSelectedEipNodeIndex(sel);
                diagram.scrollToSelectedEipNode();
            }
            return true;
        }

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

        if (handleDiagramKeys(ke)) {
            return true;
        }
        return handleTableKeys(ke);
    }

    /**
     * Keys while the topology or route diagram is shown: node navigation, jump back to topology, detail panel
     * scrolling, diagram scrolling, metrics/external/description toggles, and Enter to jump into a linked route or
     * drill down.
     */
    private boolean handleDiagramKeys(KeyEvent ke) {
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

        // Detail panel scrolling (PgUp/PgDn when detail mode is active)
        if (detailMode && diagram.isShowDiagram() && !topologyMode) {
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                detail.scrollBy(-5);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                detail.scrollBy(5);
                return true;
            }
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
        return false;
    }

    /**
     * Keys in the route table view (sort, top mode, description, open diagram, source, start/stop, suspend/resume) plus
     * the detail and go-to-node toggles in drill-down mode.
     */
    private boolean handleTableKeys(KeyEvent ke) {
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

        // Enter in table mode opens route diagram
        if (!diagram.isShowDiagram() && !sourceViewer.isVisible() && ke.isConfirm()) {
            openRouteDiagram();
            return true;
        }

        // d in drill-down mode toggles detail panel
        if (diagram.isShowDiagram() && !topologyMode && !diagram.getEipNodeBoxes().isEmpty()
                && ke.isCharIgnoreCase('d')) {
            detailMode = !detailMode;
            detail.reset();
            return true;
        }

        // g in drill-down mode opens go-to node popup
        if (diagram.isShowDiagram() && !topologyMode && !diagram.getEipNodeBoxes().isEmpty()
                && ke.isChar('g')) {
            gotoNodePopup.open(diagram.getEipNodeBoxes(), diagram.getSelectedEipNodeIndex());
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
        if (gotoNodePopup.isVisible()) {
            gotoNodePopup.close();
            return true;
        }
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
        if (gotoNodePopup.isVisible()) {
            return true;
        }
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
            renderDiagramView(frame, area, info);
            return;
        }

        // Normal table view
        List<RouteInfo> sortedRoutes = displayedRoutes(info);

        if (topPanelHeight < 0) {
            topPanelHeight = area.height() * 45 / 100;
        }
        topPanelHeight = Math.max(3, Math.min(topPanelHeight, area.height() - 5));
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(topPanelHeight), Constraint.fill())
                .split(area);

        // Routes table
        Table routeTable = routeTopMode ? buildRouteTopTable(sortedRoutes) : buildRouteTable(sortedRoutes);

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

    /**
     * Fullscreen topology or route diagram with its info/detail side panels and breadcrumb title.
     */
    private void renderDiagramView(Frame frame, Rect area, IntegrationInfo info) {
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
                detail.renderRouteInfoPanel(frame, hChunks.get(0), info, selectedRouteId);
                diagram.renderNativeDiagram(frame, hChunks.get(1), title, diagramMetrics);
            } else {
                diagram.renderNativeDiagram(frame, area, title, diagramMetrics);
            }
            return;
        } else if (!topologyMode && drillDownRouteId != null
                && diagram.getRouteLayout(drillDownRouteId) != null) {
            Line title = DiagramDetailSupport.buildBreadcrumbTitle(routeNavigationStack, drillDownRouteId);
            var routeLayout = diagram.getRouteLayout(drillDownRouteId);

            // Split for detail panel when active
            Rect diagramArea = area;
            if (detailMode) {
                int detailH = Math.max(5, area.height() * 60 / 100);
                List<Rect> vChunks = Layout.vertical()
                        .constraints(Constraint.fill(), Constraint.length(detailH))
                        .split(area);
                diagramArea = vChunks.get(0);
                detail.renderDetail(frame, vChunks.get(1), info, drillDownRouteId);
            }

            if (diagramArea.width() > 60) {
                infoPanelWidth = Math.max(10, Math.min(infoPanelWidth, diagramArea.width() - 20));
                List<Rect> hChunks = Layout.horizontal()
                        .constraints(Constraint.length(infoPanelWidth), Constraint.fill())
                        .split(diagramArea);
                hSplit.setBorderPos(hChunks.get(1).x());
                detail.renderEipInfoPanel(frame, hChunks.get(0), drillDownRouteId);
                diagram.renderNativeRouteDiagram(
                        frame, hChunks.get(1), title, diagramMetrics, drillDownRouteId, routeLayout);
            } else {
                diagram.renderNativeRouteDiagram(frame, diagramArea, title, diagramMetrics, drillDownRouteId,
                        routeLayout);
            }

            // Render go-to popup overlay
            if (gotoNodePopup.isVisible()) {
                gotoNodePopup.render(frame, area);
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
    }

    /**
     * The routes table in top mode (processing time columns).
     */
    private Table buildRouteTopTable(List<RouteInfo> sortedRoutes) {
        List<Row> routeRows = new ArrayList<>();
        for (RouteInfo route : sortedRoutes) {
            Style failStyle = route.failed > 0
                    ? Theme.error().bold()
                    : Style.EMPTY;

            routeRows.add(Row.from(
                    Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(routeFromLabel(route)),
                    rightCell(route.total > 0 ? formatDurationMs(route.meanTime) : "", 8,
                            topTimeStyle(route.meanTime)),
                    rightCell(route.total > 0 ? formatDurationMs(route.maxTime) : "", 8,
                            topTimeStyle(route.maxTime)),
                    rightCell(route.total > 0 ? formatDurationMs(route.minTime) : "", 8),
                    rightCell(route.total > 0 ? formatDurationMs(route.lastTime) : "", 8),
                    rightCell(route.deltaTime != 0 ? formatDurationMs(route.deltaTime) : "", 8,
                            topDeltaStyle(route.deltaTime)),
                    rightCell(route.p50Time >= 0 ? formatDurationMs(route.p50Time) : "", 8),
                    rightCell(route.p95Time >= 0 ? formatDurationMs(route.p95Time) : "", 8),
                    rightCell(route.p99Time >= 0 ? formatDurationMs(route.p99Time) : "", 8),
                    rightCell(String.valueOf(route.total), 8),
                    rightCell(String.valueOf(route.failed), 6, failStyle),
                    rightCell(String.valueOf(route.inflight), 8),
                    rightCell(formatThroughput(route.throughput), 8),
                    rightCell(formatLoad(route.load01, route.load05, route.load15), 12)));
        }

        IntegrationInfo selTop = ctx.findSelectedIntegration();
        if (selTop != null && selTop.exchangesTotal > 0) {
            Style ts = Theme.label();
            routeRows.add(Row.from(
                    Cell.from(Span.styled("GLOBAL", ts)),
                    Cell.from(""),
                    rightCell(formatDurationMs(selTop.meanTime), 8, ts),
                    rightCell(formatDurationMs(selTop.maxTime), 8, ts),
                    rightCell(formatDurationMs(selTop.minTime), 8, ts),
                    rightCell(formatDurationMs(selTop.lastTime), 8, ts),
                    rightCell(selTop.deltaTime != 0 ? formatDurationMs(selTop.deltaTime) : "", 8, ts),
                    rightCell(selTop.p50Time >= 0 ? formatDurationMs(selTop.p50Time) : "", 8, ts),
                    rightCell(selTop.p95Time >= 0 ? formatDurationMs(selTop.p95Time) : "", 8, ts),
                    rightCell(selTop.p99Time >= 0 ? formatDurationMs(selTop.p99Time) : "", 8, ts),
                    rightCell(String.valueOf(selTop.exchangesTotal), 8, ts),
                    rightCell(String.valueOf(selTop.failed), 6,
                            selTop.failed > 0 ? Theme.error().bold() : ts),
                    rightCell(String.valueOf(selTop.inflight), 8, ts),
                    rightCell(formatThroughput(selTop.throughput), 8, ts),
                    rightCell(TuiHelper.formatLoad(
                            selTop.inflightLoad01, selTop.inflightLoad05, selTop.inflightLoad15), 12, ts)));
        }

        return Table.builder()
                .rows(routeRows)
                .header(Row.from(
                        Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("FROM", Style.EMPTY.bold())),
                        rightCell(routeTopSortLabel("MEAN", "mean"), 8, routeTopSortStyle("mean")),
                        rightCell(routeTopSortLabel("MAX", "max"), 8, routeTopSortStyle("max")),
                        rightCell(routeTopSortLabel("MIN", "min"), 8, routeTopSortStyle("min")),
                        rightCell(routeTopSortLabel("LAST", "last"), 8, routeTopSortStyle("last")),
                        rightCell(routeTopSortLabel("DELTA", "delta"), 8, routeTopSortStyle("delta")),
                        rightCell(routeTopSortLabel("P50", "p50"), 8, routeTopSortStyle("p50")),
                        rightCell(routeTopSortLabel("P95", "p95"), 8, routeTopSortStyle("p95")),
                        rightCell(routeTopSortLabel("P99", "p99"), 8, routeTopSortStyle("p99")),
                        rightCell("TOTAL", 8, Style.EMPTY.bold()),
                        rightCell("FAIL", 6, Style.EMPTY.bold()),
                        rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                        rightCell(ctx.ratePerMinute ? "MSG/M" : "MSG/S", 8, Style.EMPTY.bold()),
                        rightCell("LOAD", 12, Style.EMPTY.bold())))
                .widths(
                        Constraint.length(24),
                        Constraint.fill(),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
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
    }

    /**
     * The routes table in the default mode (status, counters, timing).
     */
    private Table buildRouteTable(List<RouteInfo> sortedRoutes) {
        boolean hasPercentiles = sortedRoutes.stream().anyMatch(r -> r.p50Time >= 0);

        long maxTotal = sortedRoutes.stream().mapToLong(r -> r.total).max().orElse(0);
        long maxFailed = sortedRoutes.stream().mapToLong(r -> r.failed).max().orElse(0);
        int tw = Math.max(numWidth(maxTotal), 6);
        int fw = Math.max(numWidth(maxFailed), 6);

        List<Row> routeRows = new ArrayList<>();
        for (RouteInfo route : sortedRoutes) {
            Style stateStyle = "Started".equals(route.state)
                    ? Theme.success()
                    : Theme.error();

            Style failStyle = route.failed > 0
                    ? Theme.error().bold()
                    : Style.EMPTY;

            String timingCol;
            if (hasPercentiles && route.p50Time >= 0) {
                timingCol = formatDurationMs(route.p50Time) + "/" + formatDurationMs(route.p95Time) + "/"
                            + formatDurationMs(route.p99Time);
            } else if (route.total > 0) {
                timingCol = formatDurationMs(route.minTime) + "/" + formatDurationMs(route.maxTime) + "/"
                            + formatDurationMs(route.meanTime);
            } else {
                timingCol = "";
            }

            Line totalCell = route.sinceLastCompleted != null
                    ? Line.from(Span.raw(String.format("%" + tw + "d", route.total)),
                            Span.styled(" (" + route.sinceLastCompleted + ")", Theme.muted()))
                    : Line.from(Span.raw(String.format("%" + tw + "d", route.total)));
            Line failCell = route.sinceLastFailed != null
                    ? Line.from(Span.styled(String.format("%" + fw + "d", route.failed), failStyle),
                            Span.styled(" (" + route.sinceLastFailed + ")", Theme.muted()))
                    : Line.from(Span.styled(String.format("%" + fw + "d", route.failed), failStyle));

            routeRows.add(Row.from(
                    Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(routeFromLabel(route)),
                    Cell.from(Span.styled(route.state != null ? route.state : "", stateStyle)),
                    rightCell(formatThroughput(route.throughput), 8),
                    Cell.from(totalCell),
                    Cell.from(failCell),
                    rightCell(timingCol, 20),
                    Cell.from(buildPercentileBarLine(route.p50Time, route.p95Time, route.p99Time, 10))));
        }

        IntegrationInfo selDef = ctx.findSelectedIntegration();
        if (selDef != null && selDef.exchangesTotal > 0) {
            Style ts = Theme.label();
            String totalTimingCol;
            if (hasPercentiles && selDef.p50Time >= 0) {
                totalTimingCol = formatDurationMs(selDef.p50Time) + "/" + formatDurationMs(selDef.p95Time) + "/"
                                 + formatDurationMs(selDef.p99Time);
            } else {
                totalTimingCol = formatDurationMs(selDef.minTime) + "/" + formatDurationMs(selDef.maxTime) + "/"
                                 + formatDurationMs(selDef.meanTime);
            }
            routeRows.add(Row.from(
                    Cell.from(Span.styled("GLOBAL", ts)),
                    Cell.from(""),
                    Cell.from(""),
                    rightCell(formatThroughput(selDef.throughput), 8, ts),
                    Cell.from(Span.styled(String.format("%" + tw + "d", selDef.exchangesTotal), ts)),
                    Cell.from(Span.styled(String.format("%" + fw + "d", selDef.failed),
                            selDef.failed > 0 ? Theme.error().bold() : ts)),
                    rightCell(totalTimingCol, 20, ts),
                    Cell.from(buildPercentileBarLine(selDef.p50Time, selDef.p95Time, selDef.p99Time, 10))));
        }

        String timingHeader = hasPercentiles ? "P50/P95/P99" : "MIN/MAX/MEAN";

        return Table.builder()
                .rows(routeRows)
                .header(Row.from(
                        Cell.from(Span.styled(routeSortLabel("ROUTE", "name"), routeSortStyle("name"))),
                        Cell.from(Span.styled(routeSortLabel("FROM", "from"), routeSortStyle("from"))),
                        Cell.from(Span.styled(routeSortLabel("STATUS", "status"), routeSortStyle("status"))),
                        rightCell(ctx.ratePerMinute ? "MSG/M" : "MSG/S", 8, Style.EMPTY.bold()),
                        centerCell(routeSortLabel("TOTAL", "total"), 14, routeSortStyle("total")),
                        centerCell(routeSortLabel("FAIL", "failed"), 14, routeSortStyle("failed")),
                        rightCell(timingHeader, 20, Style.EMPTY.bold()),
                        Cell.from("")))
                .widths(
                        Constraint.length(24),
                        Constraint.fill(),
                        Constraint.length(10),
                        Constraint.length(10),
                        Constraint.length(14),
                        Constraint.length(14),
                        Constraint.min(20),
                        Constraint.length(12))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Routes ").build())
                .build();
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
                hint(spans, "d", "detail" + (detailMode ? " [on]" : " [off]"));
                hint(spans, "g", "go to");
                hint(spans, "PgUp/PgDn", detailMode ? "detail" : "page");
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
            hint(spans, "Enter", "diagram");
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
                routeTimingCol = formatDurationMs(route.p50Time) + "/" + formatDurationMs(route.p95Time) + "/"
                                 + formatDurationMs(route.p99Time);
            } else if (route.total > 0) {
                routeTimingCol = formatDurationMs(route.minTime) + "/" + formatDurationMs(route.maxTime) + "/"
                                 + formatDurationMs(route.meanTime);
            } else {
                routeTimingCol = "";
            }
            rows.add(Row.from(
                    Cell.from("   route"),
                    Cell.from(Span.styled(route.from != null ? route.from : route.routeId, routeStyle)),
                    rightCell(formatThroughput(route.throughput), 8),
                    rightCell(String.valueOf(route.total), 8),
                    rightCell(String.valueOf(route.failed), 6,
                            route.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(String.valueOf(route.inflight), 8),
                    rightCell(routeTimingCol, 20),
                    Cell.from(buildPercentileBarLine(route.p50Time, route.p95Time, route.p99Time, 10))));

            for (ProcessorInfo proc : route.processors) {
                String indent = "  ".repeat(proc.level);
                Style nameStyle = proc.failed > 0 ? Theme.error() : Style.EMPTY.fg(Theme.accent());

                String procTimingCol;
                if (hasProcPercentiles && proc.p50Time >= 0) {
                    procTimingCol = formatDurationMs(proc.p50Time) + "/" + formatDurationMs(proc.p95Time) + "/"
                                    + formatDurationMs(proc.p99Time);
                } else if (proc.total > 0) {
                    procTimingCol = formatDurationMs(proc.minTime) + "/" + formatDurationMs(proc.maxTime) + "/"
                                    + formatDurationMs(proc.meanTime);
                } else {
                    procTimingCol = "";
                }

                rows.add(Row.from(
                        Cell.from("   " + (proc.processor != null ? proc.processor : "")),
                        Cell.from(Span.styled(indent + (proc.id != null ? proc.id : ""), nameStyle)),
                        rightCell(formatThroughput(proc.throughput), 8),
                        rightCell(String.valueOf(proc.total), 8),
                        rightCell(String.valueOf(proc.failed), 6,
                                proc.failed > 0 ? Theme.error() : Style.EMPTY),
                        rightCell(String.valueOf(proc.inflight), 8),
                        rightCell(procTimingCol, 20),
                        Cell.from(buildPercentileBarLine(proc.p50Time, proc.p95Time, proc.p99Time, 10))));
            }
            String procTimingHeader = hasProcPercentiles ? "P50/P95/P99" : "MIN/MAX/MEAN";

            table = Table.builder()
                    .rows(rows)
                    .header(Row.from(
                            Cell.from(Span.styled("   TYPE", Style.EMPTY.bold())),
                            Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                            rightCell(ctx.ratePerMinute ? "MSG/M" : "MSG/S", 8, Style.EMPTY.bold()),
                            rightCell("TOTAL", 8, Style.EMPTY.bold()),
                            rightCell("FAIL", 6, Style.EMPTY.bold()),
                            rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                            rightCell(procTimingHeader, 20, Style.EMPTY.bold()),
                            Cell.from("")))
                    .widths(
                            Constraint.length(20),
                            Constraint.fill(),
                            Constraint.length(10),
                            Constraint.length(8),
                            Constraint.length(6),
                            Constraint.length(8),
                            Constraint.min(20),
                            Constraint.length(12))
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

    private String formatThroughput(String throughput) {
        if (throughput == null || throughput.isEmpty()) {
            return "";
        }
        if (ctx.ratePerMinute) {
            return TuiHelper.throughputPerMinute(throughput);
        }
        return throughput;
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

    private List<RouteInfo> displayedRoutes(IntegrationInfo info) {
        List<RouteInfo> sorted = new ArrayList<>(info.routes);
        sorted.sort(this::sortRoute);
        if (routeTopMode) {
            sorted.sort(this::sortRouteTop);
        }
        return sorted;
    }

    private RouteInfo selectedRoute() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return null;
        }
        List<RouteInfo> sortedRoutes = displayedRoutes(info);
        Integer sel = routeTableState.selected();
        return (sel != null && sel >= 0 && sel < sortedRoutes.size())
                ? sortedRoutes.get(sel) : sortedRoutes.get(0);
    }

    String selectedRouteId() {
        RouteInfo route = selectedRoute();
        return route != null ? route.routeId : null;
    }

    private String selectedRouteState() {
        RouteInfo route = selectedRoute();
        return route != null ? route.state : null;
    }

    private boolean selectedRouteSupportsSuspension() {
        RouteInfo route = selectedRoute();
        return route != null && route.supportsSuspension;
    }

    private void toggleRouteStartStop() {
        if (ctx.selectedPid == null) {
            return;
        }
        RouteInfo route = selectedRoute();
        if (route == null) {
            return;
        }
        String command = "Started".equals(route.state) ? "stop" : "start";
        sendRouteCommand(ctx.selectedPid, route.routeId, command);
    }

    private void toggleRouteSuspendResume() {
        if (ctx.selectedPid == null) {
            return;
        }
        RouteInfo route = selectedRoute();
        if (route == null) {
            return;
        }
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
        ctx.fireAction(pid, root);
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

        ctx.backgroundExecutor.execute(() -> {
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
        RouteInfo selected = selectedRoute();
        if (selected == null) {
            return;
        }
        String routeId = selected.routeId;
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
            int bestIdx = diagram.findClosestEipNode(sourceLine);
            if (bestIdx >= 0) {
                diagram.setSelectedEipNodeIndex(bestIdx);
                diagram.scrollToSelectedEipNode();
            }
        });
        sourceViewer.setQuickDocProvider(detail::provideAllQuickDocs);
        detail.ensureProcessorDetailLoaded(routeId);
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
            int bestIdx = diagram.findClosestEipNode(sourceLine);
            if (bestIdx >= 0) {
                diagram.setSelectedEipNodeIndex(bestIdx);
                diagram.scrollToSelectedEipNode();
            }
        });
        sourceViewer.setQuickDocProvider(detail::provideAllQuickDocs);
        detail.ensureProcessorDetailLoaded(routeId);
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
            int bestIdx = diagram.findClosestEipNode(sourceLine);
            if (bestIdx >= 0) {
                diagram.setSelectedEipNodeIndex(bestIdx);
                diagram.scrollToSelectedEipNode();
                sourceViewer.hide();
            }
        });
        sourceViewer.setQuickDocProvider(detail::provideAllQuickDocs);
        detail.ensureProcessorDetailLoaded(drillDownRouteId);
        var rl3 = diagram.getRouteLayout(drillDownRouteId);
        sourceViewer.loadSource(ctx, drillDownRouteId, targetLine, rl3 != null ? rl3.source : null);
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routes.isEmpty()) {
            return null;
        }
        List<RouteInfo> sorted = displayedRoutes(info);
        List<String> items = sorted.stream().map(r -> r.routeId != null ? r.routeId : "").toList();
        Integer sel = routeTableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Routes");
    }

    // ---- Detail panel ----

    // ---- Quick doc (q toggle in source viewer) ----

    @Override
    public String description() {
        return "Route list with state, message counts, throughput, and failure statistics";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("routes");
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

}
