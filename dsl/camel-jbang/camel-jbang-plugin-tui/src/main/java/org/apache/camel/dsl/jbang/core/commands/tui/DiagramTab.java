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
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class DiagramTab implements MonitorTab {

    private final MonitorContext ctx;
    private final DiagramSupport diagram = new DiagramSupport();
    private final SourceViewer sourceViewer = new SourceViewer();
    private boolean diagramMetrics = true;
    private static final String[] EXTERNAL_LABELS = { " [off]", " [edges]", " [all]" };
    private int externalMode;
    private boolean topologyMode = true;
    private String drillDownRouteId;
    private final Deque<String> routeNavigationStack = new ArrayDeque<>();
    private int infoPanelWidth = 30;
    private final DragSplit hSplit = new DragSplit();

    DiagramTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    boolean isShowDiagram() {
        return diagram.isShowDiagram();
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

        // Node selection navigation in topology mode
        if (topologyMode && diagram.isShowDiagram() && diagram.hasDiagramData()
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
        if (!topologyMode && diagram.isShowDiagram() && !diagram.getEipNodeBoxes().isEmpty()) {
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

        // Jump back to topology from any depth
        if (!topologyMode && diagram.isShowDiagram() && ke.isChar('t')) {
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

        if (diagram.handleScrollKeys(ke)) {
            return true;
        }

        // Toggle metrics
        if (diagram.isShowDiagram() && ke.isCharIgnoreCase('m')) {
            diagramMetrics = !diagramMetrics;
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Cycle external systems: off → edges → all → off
        if (diagram.isShowDiagram() && topologyMode && ke.isCharIgnoreCase('e')) {
            externalMode = (externalMode + 1) % 3;
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Toggle description
        if (diagram.isShowDiagram() && ke.isCharIgnoreCase('n')) {
            diagram.setShowDescription(!diagram.isShowDescription());
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Jump to linked route from EIP node (Enter in route mode)
        if (!topologyMode && ke.isConfirm() && !diagram.getEipNodeBoxes().isEmpty()) {
            String linkedRouteId = diagram.findLinkedRouteId(drillDownRouteId);
            if (linkedRouteId != null && diagram.getRouteLayout(linkedRouteId) != null) {
                if (linkedRouteId.equals(drillDownRouteId)) {
                    return true;
                }
                // Collapse breadcrumb if navigating back to a route already in the stack
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
        if (topologyMode && ke.isConfirm()) {
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
                    // Use cached route layout if available (no IPC needed)
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

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (hSplit.handleMouse(me, me.x())) {
            if (hSplit.isDragging()) {
                infoPanelWidth = Math.max(10, Math.min(me.x() - area.x(), area.width() - 20));
            }
            return true;
        }
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
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (sourceViewer.isVisible()) {
            sourceViewer.hide();
            return true;
        }
        if (!topologyMode) {
            if (!routeNavigationStack.isEmpty()) {
                // Go back to the previous route in the stack
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
            // If topology layout is cached, just switch view without IPC
            if (diagram.hasNativeLayout()) {
                return true;
            }
            diagram.endLoad();
            reloadDiagram();
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        // Scroll diagram up
    }

    @Override
    public void navigateDown() {
        // Scroll diagram down
    }

    @Override
    public void onTabSelected() {
        if (!diagram.isShowDiagram()) {
            if (ctx.selectedPid != null && diagram.hasCachedData(ctx.selectedPid)) {
                diagram.showCached();
            } else {
                // Show diagram immediately so preload results are rendered when they arrive
                diagram.setShowDiagram(true);
                if (!diagram.isLoading()) {
                    loadDiagram();
                }
            }
        }
    }

    @Override
    public void onIntegrationChanged() {
        topologyMode = true;
        drillDownRouteId = null;
        routeNavigationStack.clear();
        diagram.reset();
        diagram.setTopologyMode(true);
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

        if (sourceViewer.isVisible()) {
            sourceViewer.render(frame, area);
            return;
        }

        if (diagram.isShowDiagram() && diagram.hasDiagramData()) {
            String selectedRouteId = topologyMode ? diagram.getSelectedRouteId() : drillDownRouteId;

            if (topologyMode && diagram.hasNativeLayout()) {
                Line title;
                if (info.name != null) {
                    title = Line.from(
                            Span.raw(" Topology ["),
                            Span.styled(info.name, Style.EMPTY.fg(Color.YELLOW).bold()),
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
        }

        // Show placeholder when no diagram is loaded yet
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
                    Span.styled(" Route: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled(route.routeId, Style.EMPTY.fg(Color.WHITE).bold())));
            lines.add(Line.from(
                    Span.styled(" From:  ", Style.EMPTY.dim()),
                    Span.raw(route.from != null ? route.from : "")));
            String stateLabel = route.state != null ? route.state : "";
            Style stateStyle = "Started".equals(route.state) ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.fg(Color.LIGHT_RED);
            lines.add(Line.from(
                    Span.styled(" State: ", Style.EMPTY.dim()),
                    Span.styled(stateLabel, stateStyle)));

            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled(" Uptime:     ", Style.EMPTY.dim()),
                    Span.raw(route.uptime != null ? route.uptime : "")));
            lines.add(Line.from(
                    Span.styled(" Throughput: ", Style.EMPTY.dim()),
                    Span.raw(route.throughput != null ? route.throughput : "")));
            if (route.coverage != null) {
                lines.add(Line.from(
                        Span.styled(" Coverage:   ", Style.EMPTY.dim()),
                        Span.raw(route.coverage)));
            }

            lines.add(Line.from(Span.raw("")));
            int w = numWidth(route.total, route.failed, route.inflight);
            lines.add(Line.from(
                    Span.styled(" Total:    ", Style.EMPTY.dim()),
                    Span.raw(String.format("%" + w + "d", route.total))));
            Style failStyle = route.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY;
            lines.add(Line.from(
                    Span.styled(" Failed:   ", Style.EMPTY.dim()),
                    Span.styled(String.format("%" + w + "d", route.failed), failStyle)));
            lines.add(Line.from(
                    Span.styled(" Inflight: ", Style.EMPTY.dim()),
                    Span.raw(String.format("%" + w + "d", route.inflight))));

            lines.add(Line.from(Span.raw("")));
            if (route.total > 0) {
                int tw = numWidth(route.meanTime, route.maxTime, route.minTime);
                lines.add(Line.from(
                        Span.styled(" Mean: ", Style.EMPTY.dim()),
                        Span.raw(String.format("%" + tw + "d ms", route.meanTime))));
                lines.add(Line.from(
                        Span.styled(" Max:  ", Style.EMPTY.dim()),
                        Span.raw(String.format("%" + tw + "d ms", route.maxTime))));
                lines.add(Line.from(
                        Span.styled(" Min:  ", Style.EMPTY.dim()),
                        Span.raw(String.format("%" + tw + "d ms", route.minTime))));
            }

            if (route.sinceLastCompleted != null || route.sinceLastFailed != null) {
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" Since last:", Style.EMPTY.dim())));
                if (route.sinceLastCompleted != null) {
                    lines.add(Line.from(
                            Span.styled("   success: ", Style.EMPTY.dim()),
                            Span.raw(route.sinceLastCompleted)));
                }
                if (route.sinceLastFailed != null) {
                    lines.add(Line.from(
                            Span.styled("   fail:    ", Style.EMPTY.dim()),
                            Span.styled(route.sinceLastFailed,
                                    Style.EMPTY.fg(Color.LIGHT_RED))));
                }
            }

        } else {
            // External endpoint — show topology node data
            var topoNode = diagram.getSelectedTopologyNode();
            if (topoNode != null) {
                boolean isInbound = "external-in".equals(topoNode.nodeType);
                boolean isBridge = "external".equals(topoNode.nodeType);
                String label = isBridge ? " External" : isInbound ? " Inbound" : " Outbound";
                lines.add(Line.from(
                        Span.styled(label, Style.EMPTY.fg(Color.CYAN).bold())));
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" URI: ", Style.EMPTY.dim()),
                        Span.raw(topoNode.from != null ? topoNode.from : "")));
                if (topoNode.description != null && !topoNode.description.isBlank()) {
                    lines.add(Line.from(
                            Span.styled(" Path: ", Style.EMPTY.dim()),
                            Span.raw(topoNode.description)));
                }
                if (!isBridge) {
                    String connectedRoute = diagram.getConnectedRouteId(routeId);
                    if (connectedRoute != null) {
                        lines.add(Line.from(Span.raw("")));
                        lines.add(Line.from(
                                Span.styled(isInbound ? " To route: " : " From route: ", Style.EMPTY.dim()),
                                Span.styled(connectedRoute, Style.EMPTY.fg(Color.WHITE))));
                    }
                }
                if (topoNode.exchangesTotal > 0 || topoNode.exchangesFailed > 0) {
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(" Total:  ", Style.EMPTY.dim()),
                            Span.raw(String.valueOf(topoNode.exchangesTotal))));
                    if (topoNode.exchangesFailed > 0) {
                        lines.add(Line.from(
                                Span.styled(" Failed: ", Style.EMPTY.dim()),
                                Span.styled(String.valueOf(topoNode.exchangesFailed),
                                        Style.EMPTY.fg(Color.LIGHT_RED).bold())));
                    }
                }
            } else {
                lines.add(Line.from(
                        Span.styled(" " + routeId, Style.EMPTY.fg(Color.CYAN).bold())));
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
                        Span.styled(" ↵ ", Style.EMPTY.fg(Color.YELLOW).bold()),
                        Span.styled(linkedRoute, Style.EMPTY.fg(Color.WHITE))));
            } else if (ln.treeNode != null && ln.treeNode.info.remote) {
                String arrow = "from".equals(ln.type) ? " external → " : " → external";
                lines.add(Line.from(
                        Span.styled(arrow, Style.EMPTY.fg(Color.DARK_GRAY))));
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
                        ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY;
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
                                            Style.EMPTY.fg(Color.LIGHT_RED))));
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

    @Override
    public void renderFooter(List<Span> spans) {
        if (sourceViewer.isVisible()) {
            sourceViewer.renderFooter(spans);
            return;
        }
        if (diagram.isShowDiagram()) {
            if (!topologyMode && !diagram.getEipNodeBoxes().isEmpty()) {
                hint(spans, "Esc", "back");
                hint(spans, "t", "topology");
                hint(spans, "↑↓←→", "navigate");
                hint(spans, "PgUp/PgDn", "page");
                hint(spans, "c", "source");
            } else if (!topologyMode) {
                hint(spans, "Esc", "back");
                hint(spans, "t", "topology");
                hint(spans, "↑↓←→", "scroll");
                hint(spans, "PgUp/PgDn", "page");
            } else if (!diagram.getNodeBoxes().isEmpty()) {
                hint(spans, "Esc", "close");
                hint(spans, "↑↓←→", "navigate");
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
        }
    }

    void refreshDiagramIfNeeded() {
        if (diagram.isShowDiagram() && diagramMetrics) {
            reloadDiagram();
        }
    }

    private void loadDiagram() {
        loadDiagram(true);
    }

    private void reloadDiagram() {
        loadDiagram(false);
    }

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

    @Override
    public String getHelpText() {
        return """
                                # Diagram

                                The Diagram tab shows a visual topology of how routes connect to each other.
                                This helps you understand the overall message flow in your integration.

                                ## Topology View

                                The topology view shows all routes and their connections:
                                - **Trigger routes** (timer, cron, etc.) appear at the top
                                - **Downstream routes** appear below, connected by arrows
                                - Routes that are connected show edges between them

                                ## Example Topology

                                ```
                                ┌──────────────────┐
                                │ order-generator  │
                                │  (timer://gen)   │
                                │                  │
                                │      3748        │
                                └──────────────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │  process-order   │
                                │ (direct:process) │
                                │                  │
                                │    3748/12!      │
                                └──────────────────┘
                                ```

                                Each box represents a route. The first line is the route ID,
                                the second line shows the `from` endpoint, and the bottom line
                                shows metrics when enabled. Arrows show how routes connect.

                                ## Metrics

                                When metrics are enabled, each route box shows exchange counts:
                                - **Green** number — successful exchanges
                                - **Red** number — failed exchanges
                                - Combined as `3748/12` means 3748 ok and 12 failed

                                ## External Systems

                                Press `e` to cycle through three external modes:

                                - **off** — no external endpoints shown
                                - **edges** — external endpoints that are truly outside Camel are shown
                                  as dashed boxes in top/bottom bands. Routes sharing an external
                                  endpoint (e.g. kafka) are connected with a direct arrow.
                                - **all** — same as edges, but routes sharing an external endpoint
                                  are connected through an intermediary dashed box showing the
                                  endpoint name, instead of a direct arrow.

                                External system boxes are drawn with dashed borders to distinguish
                                them from route boxes. Dashed edges connect routes to external systems.

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

                                ## Route Diagram

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

                                ## Keys

                                **Topology view:**
                                - `↑↓←→` — navigate between route boxes
                                - `Enter` — drill down into selected route
                                - `c` — show route source code
                                - `Esc` — close diagram

                                **Route diagram:**
                                - `↑↓←→` — navigate between EIP nodes
                                - `Enter` — jump to linked route (when `↵` indicator shown)
                                - `c` — show source code at selected node
                                - `Esc` — go back (previous route or topology)
                                - `t` — jump back to topology view

                                **Source view:**
                                - `↑↓` — move cursor between lines
                                - `Ctrl+↑↓` — scroll viewport without moving cursor
                                - `←→` — horizontal scroll
                                - `PgUp/PgDn` — page jump
                                - `Home/End` — go to top/bottom
                                - `Enter` — select the closest diagram node at cursor line
                                - `Esc/c` — close source view

                                **Common:**
                                - `m` — toggle metrics on/off (default: on)
                                - `e` — toggle external systems on/off (topology only)
                                - `n` — toggle description labels on/off
                                - `PgUp/PgDn` — page scroll
                                - `Home/End` — top/end
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        return null;
    }

    JsonObject getTopologyDataAsJson() {
        return diagram.getTopologyDataAsJson();
    }

    private Line buildBreadcrumbTitle() {
        Style nameStyle = Style.EMPTY.fg(Color.YELLOW).bold();
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
        var rl = diagram.getRouteLayout(routeId);
        sourceViewer.loadSource(ctx, routeId, 0, rl != null ? rl.source : null);
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
        var rl2 = diagram.getRouteLayout(drillDownRouteId);
        sourceViewer.loadSource(ctx, drillDownRouteId, targetLine, rl2 != null ? rl2.source : null);
    }

    private int findClosestEipNode(int sourceLine) {
        var boxes = diagram.getEipNodeBoxes();
        if (boxes.isEmpty()) {
            return -1;
        }
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

    private static int numWidth(long... values) {
        long max = 0;
        for (long v : values) {
            max = Math.max(max, Math.abs(v));
        }
        return Math.max(1, String.valueOf(max).length());
    }

    // ---- MCP programmatic navigation ----

    boolean selectRoute(String routeId) {
        int idx = diagram.findNodeIndexByRouteId(routeId);
        if (idx < 0) {
            return false;
        }
        diagram.setSelectedNodeIndex(idx);
        diagram.scrollToSelectedNode();
        return true;
    }

    boolean selectNode(String routeId, String nodeId) {
        // Ensure we're on the Diagram tab in topology mode first
        if (routeId != null) {
            int routeIdx = diagram.findNodeIndexByRouteId(routeId);
            if (routeIdx < 0) {
                return false;
            }
            // Drill down into the route (mirrors Enter-key logic)
            IntegrationInfo info = ctx.findSelectedIntegration();
            if (info == null || info.routes.stream().noneMatch(r -> routeId.equals(r.routeId))) {
                return false;
            }
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
        }
        if (nodeId != null) {
            int nodeIdx = diagram.findEipNodeIndexByNodeId(nodeId);
            if (nodeIdx < 0) {
                return false;
            }
            diagram.setSelectedEipNodeIndex(nodeIdx);
            diagram.scrollToSelectedEipNode();
        }
        return true;
    }

    @Override
    public SelectionContext getSelectionContext() {
        if (!diagram.isShowDiagram()) {
            return null;
        }
        if (topologyMode) {
            var boxes = diagram.getNodeBoxes();
            if (boxes.isEmpty()) {
                return null;
            }
            List<String> items = new ArrayList<>();
            for (var box : boxes) {
                items.add(box.routeId());
            }
            return new SelectionContext(
                    "topology-routes", items,
                    diagram.getSelectedNodeIndex(), items.size(), "Topology routes");
        } else {
            var boxes = diagram.getEipNodeBoxes();
            if (boxes.isEmpty()) {
                return null;
            }
            List<String> items = new ArrayList<>();
            for (var box : boxes) {
                items.add(box.nodeId());
            }
            return new SelectionContext(
                    "eip-nodes", items,
                    diagram.getSelectedEipNodeIndex(), items.size(),
                    "EIP nodes [" + drillDownRouteId + "]");
        }
    }

    JsonObject getDiagramStateAsJson() {
        if (!diagram.isShowDiagram()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("diagramMode", topologyMode ? "topology" : "route");

        if (!topologyMode && drillDownRouteId != null) {
            result.put("routeId", drillDownRouteId);
            JsonArray stack = new JsonArray();
            for (String s : routeNavigationStack) {
                stack.add(s);
            }
            if (!stack.isEmpty()) {
                result.put("navigationStack", stack);
            }
        }

        // Selected node info
        if (topologyMode) {
            String routeId = diagram.getSelectedRouteId();
            if (routeId != null) {
                JsonObject node = new JsonObject();
                node.put("routeId", routeId);
                result.put("selectedRoute", node);
            }
        } else {
            var eipBox = diagram.getSelectedEipNodeBox();
            if (eipBox != null && eipBox.layoutNode() != null) {
                JsonObject node = new JsonObject();
                node.put("id", eipBox.nodeId());
                node.put("type", eipBox.type());
                String label = String.join("", eipBox.layoutNode().wrappedLines);
                if (!label.isBlank()) {
                    node.put("label", label);
                }
                if (eipBox.layoutNode().id != null) {
                    node.put("processorId", eipBox.layoutNode().id);
                }
                String linkedRoute = diagram.findLinkedRouteId(drillDownRouteId);
                if (linkedRoute != null) {
                    node.put("linkedRoute", linkedRoute);
                }
                result.put("selectedNode", node);
            }
        }

        // Info panel stats
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null) {
            String routeId = topologyMode ? diagram.getSelectedRouteId() : drillDownRouteId;
            if (routeId != null) {
                RouteInfo route = null;
                for (RouteInfo r : info.routes) {
                    if (routeId.equals(r.routeId)) {
                        route = r;
                        break;
                    }
                }
                if (route != null) {
                    JsonObject ri = new JsonObject();
                    ri.put("routeId", route.routeId);
                    ri.put("from", route.from);
                    ri.put("state", route.state);
                    ri.put("uptime", route.uptime);
                    ri.put("throughput", route.throughput);
                    if (route.coverage != null) {
                        ri.put("coverage", route.coverage);
                    }
                    ri.put("total", route.total);
                    ri.put("failed", route.failed);
                    ri.put("inflight", route.inflight);
                    if (route.total > 0) {
                        ri.put("meanTime", route.meanTime);
                        ri.put("maxTime", route.maxTime);
                        ri.put("minTime", route.minTime);
                    }
                    if (route.sinceLastCompleted != null) {
                        ri.put("sinceLastSuccess", route.sinceLastCompleted);
                    }
                    if (route.sinceLastFailed != null) {
                        ri.put("sinceLastFail", route.sinceLastFailed);
                    }
                    result.put("info", ri);
                }
            }

            // EIP node stats (when drilled down)
            if (!topologyMode) {
                var eipBox = diagram.getSelectedEipNodeBox();
                if (eipBox != null && eipBox.layoutNode() != null
                        && eipBox.layoutNode().treeNode != null
                        && eipBox.layoutNode().treeNode.info.stat != null) {
                    var stat = eipBox.layoutNode().treeNode.info.stat;
                    JsonObject ni = new JsonObject();
                    ni.put("total", stat.exchangesTotal);
                    ni.put("failed", stat.exchangesFailed);
                    ni.put("inflight", stat.exchangesInflight);
                    if (stat.exchangesTotal > 0) {
                        ni.put("meanTime", stat.meanProcessingTime);
                        ni.put("maxTime", stat.maxProcessingTime);
                        ni.put("minTime", stat.minProcessingTime);
                        ni.put("lastTime", stat.lastProcessingTime);
                    }
                    result.put("nodeInfo", ni);
                }
            }
        }

        return result;
    }

    JsonObject locateNodes(List<String> nodeIds) {
        return diagram.locateNodes(nodeIds);
    }
}
