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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class DiagramTab extends AbstractTab {

    private final DiagramSupport diagram = new DiagramSupport();
    private final SourceViewer sourceViewer = new SourceViewer();
    private final GotoNodePopup gotoNodePopup = new GotoNodePopup();
    private boolean diagramMetrics = true;
    private static final String[] EXTERNAL_LABELS = { " [off]", " [edges]", " [all]" };
    private int externalMode;
    private boolean topologyMode = true;
    private String drillDownRouteId;
    private final Deque<String> routeNavigationStack = new ArrayDeque<>();
    private int infoPanelWidth = 30;
    private final DragSplit hSplit = new DragSplit();

    private boolean detailMode;
    private final DiagramDetailSupport detail = new DiagramDetailSupport(ctx, diagram);
    private final DragSplit vSplit = new DragSplit();

    DiagramTab(MonitorContext ctx) {
        super(ctx);
    }

    boolean isShowDiagram() {
        return diagram.isShowDiagram();
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

        if (handleNodeNavigationKeys(ke)) {
            return true;
        }
        return handleDiagramActionKeys(ke);
    }

    /**
     * Arrow/Home/End navigation between topology nodes or EIP nodes of the drilled-down route.
     */
    private boolean handleNodeNavigationKeys(KeyEvent ke) {
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
        return false;
    }

    /**
     * Detail panel, go-to node, jump back to topology, scrolling, metrics/external/description toggles, and Enter to
     * jump into a linked route or drill down.
     */
    private boolean handleDiagramActionKeys(KeyEvent ke) {
        // Toggle detail panel in drill-down mode
        if (!topologyMode && diagram.isShowDiagram() && !diagram.getEipNodeBoxes().isEmpty()
                && ke.isCharIgnoreCase('d')) {
            detailMode = !detailMode;
            detail.reset();
            return true;
        }

        // Go-to node popup in drill-down mode
        if (!topologyMode && diagram.isShowDiagram() && !diagram.getEipNodeBoxes().isEmpty()
                && ke.isChar('g')) {
            gotoNodePopup.open(diagram.getEipNodeBoxes(), diagram.getSelectedEipNodeIndex());
            return true;
        }

        // Detail panel scroll
        if (!topologyMode && detailMode && diagram.isShowDiagram()) {
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                detail.scrollBy(-10);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                detail.scrollBy(10);
                return true;
            }
        }

        // Jump back to topology from any depth
        if (!topologyMode && diagram.isShowDiagram() && ke.isChar('t')) {
            routeNavigationStack.clear();
            diagram.setPendingSelectionRouteId(drillDownRouteId);
            drillDownRouteId = null;
            topologyMode = true;
            detailMode = false;
            detail.reset();
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
        if (gotoNodePopup.isVisible()) {
            return true;
        }
        if (detailMode && detail.containsMouse(me.x(), me.y())) {
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                detail.scrollBy(-3);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                detail.scrollBy(3);
                return true;
            }
        }
        if (vSplit.handleMouse(me, me.y())) {
            return true;
        }
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
        if (gotoNodePopup.isVisible()) {
            gotoNodePopup.close();
            return true;
        }
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
            detailMode = false;
            detail.reset();
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
        detailMode = false;
        detail.reset();
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
                if (area.width() > 60) {
                    infoPanelWidth = Math.max(10, Math.min(infoPanelWidth, area.width() - 20));
                    List<Rect> hChunks = Layout.horizontal()
                            .constraints(Constraint.length(infoPanelWidth), Constraint.fill())
                            .split(area);
                    hSplit.setBorderPos(hChunks.get(1).x());
                    detail.renderEipInfoPanel(frame, hChunks.get(0), drillDownRouteId);
                    if (detailMode) {
                        int detailH = Math.max(5, hChunks.get(1).height() * 60 / 100);
                        List<Rect> vChunks = Layout.vertical()
                                .constraints(Constraint.fill(), Constraint.length(detailH))
                                .split(hChunks.get(1));
                        vSplit.setBorderPos(vChunks.get(1).y());
                        diagram.renderNativeRouteDiagram(
                                frame, vChunks.get(0), title, diagramMetrics, drillDownRouteId, routeLayout);
                        detail.renderDetail(frame, vChunks.get(1), info, drillDownRouteId);
                    } else {
                        diagram.renderNativeRouteDiagram(
                                frame, hChunks.get(1), title, diagramMetrics, drillDownRouteId, routeLayout);
                    }
                } else {
                    if (detailMode) {
                        int detailH = Math.max(5, area.height() * 60 / 100);
                        List<Rect> vChunks = Layout.vertical()
                                .constraints(Constraint.fill(), Constraint.length(detailH))
                                .split(area);
                        vSplit.setBorderPos(vChunks.get(1).y());
                        diagram.renderNativeRouteDiagram(
                                frame, vChunks.get(0), title, diagramMetrics, drillDownRouteId, routeLayout);
                        detail.renderDetail(frame, vChunks.get(1), info, drillDownRouteId);
                    } else {
                        diagram.renderNativeRouteDiagram(
                                frame, area, title, diagramMetrics, drillDownRouteId, routeLayout);
                    }
                }

                // Render go-to popup overlay
                if (gotoNodePopup.isVisible()) {
                    gotoNodePopup.render(frame, area);
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
                hint(spans, TuiIcons.HINT_NAV, "navigate");
                hint(spans, "PgUp/PgDn", "page");
                hint(spans, "c", "source");
                hint(spans, "d", "detail" + (detailMode ? " [on]" : " [off]"));
                hint(spans, "g", "go to");
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

        ctx.backgroundExecutor.execute(() -> {
            try {
                diagram.setTopologyMode(topologyMode);
                diagram.loadAllDiagramsInBackground(ctx, pid, showMetrics, external);
            } finally {
                diagram.endLoad();
            }
        });
    }

    @Override
    public String description() {
        return "Route topology diagram showing how routes connect to each other and external systems";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("diagram");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        return null;
    }

    JsonObject getTopologyDataAsJson() {
        return diagram.getTopologyDataAsJson();
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
        var rl2 = diagram.getRouteLayout(drillDownRouteId);
        sourceViewer.loadSource(ctx, drillDownRouteId, targetLine, rl2 != null ? rl2.source : null);
    }

    // ---- Quick doc (i toggle in source viewer) ----

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
                    if (route.p50Time >= 0) {
                        ri.put("p50Time", route.p50Time);
                        ri.put("p95Time", route.p95Time);
                        ri.put("p99Time", route.p99Time);
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
                    if (stat.exchangesThroughput != null) {
                        ni.put("throughput", stat.exchangesThroughput);
                    }
                    if (stat.exchangesTotal > 0) {
                        ni.put("meanTime", stat.meanProcessingTime);
                        ni.put("maxTime", stat.maxProcessingTime);
                        ni.put("minTime", stat.minProcessingTime);
                        ni.put("lastTime", stat.lastProcessingTime);
                    }
                    if (stat.p50ProcessingTime >= 0) {
                        ni.put("p50Time", stat.p50ProcessingTime);
                        ni.put("p95Time", stat.p95ProcessingTime);
                        ni.put("p99Time", stat.p99ProcessingTime);
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
