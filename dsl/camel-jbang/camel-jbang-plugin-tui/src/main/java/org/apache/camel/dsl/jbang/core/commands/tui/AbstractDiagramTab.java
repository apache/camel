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
import java.util.Deque;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/**
 * A tab that shows the route diagram: the topology of the routes, and a route drilled down into. The Diagram tab shows
 * it at all times, the Routes tab over its table (Enter on a route). The keys both share are here:
 * <ul>
 * <li>the arrows, Home and End select a node; the scroll keys move the diagram</li>
 * <li>Enter on the topology drills down into the selected route; Enter on a node that calls another route follows it,
 * and Esc walks back along the routes followed, then to the topology; t jumps back to the topology</li>
 * <li>d shows the detail panel of the selected node (PgUp and PgDn scroll it), g goes to a node by name, c shows the
 * source of the selected node or route</li>
 * <li>m shows the metrics, e the external systems of the topology</li>
 * </ul>
 * A tab offers a key to {@link #handleGotoNodeKeys(KeyEvent)}, then to its own keys and those of the source, and to
 * {@link #handleDiagramKeys(KeyEvent)}; Esc to {@link #handleDiagramEscape()}.
 */
abstract class AbstractDiagramTab extends AbstractTab {

    protected static final String[] EXTERNAL_LABELS = { " [off]", " [edges]", " [all]" };

    protected final DiagramSupport diagram = new DiagramSupport();
    protected final SourceViewer sourceViewer = new SourceViewer();
    protected final GotoNodePopup gotoNodePopup = new GotoNodePopup();
    protected final DiagramDetailSupport detail = new DiagramDetailSupport(ctx, diagram);
    protected boolean diagramMetrics = true;
    /** The external systems of the topology: 0 off, 1 the edges to them, 2 all of them. */
    protected int externalMode;
    protected boolean topologyMode = true;
    /** The route drilled down into, while the route diagram is shown. */
    protected String drillDownRouteId;
    /** The routes followed from a node that calls another route, for Esc to walk back. */
    protected final Deque<String> routeNavigationStack = new ArrayDeque<>();
    /** Whether the detail panel of the selected node is shown, beside the route diagram. */
    protected boolean detailMode;

    protected AbstractDiagramTab(MonitorContext ctx) {
        super(ctx);
    }

    boolean isShowDiagram() {
        return diagram.isShowDiagram();
    }

    /**
     * The source shown over the diagram while its search is typed, or the go to node popup, which filters by typing.
     */
    @Override
    public boolean isTextInputActive() {
        return (sourceViewer.isVisible() && sourceViewer.isTextInputActive()) || gotoNodePopup.isVisible();
    }

    @Override
    public boolean handlePaste(String text) {
        if (!sourceViewer.isVisible() || !sourceViewer.isTextInputActive()) {
            return false;
        }
        sourceViewer.handlePaste(text);
        return true;
    }

    /** The keys of the go to node popup while it is open; the node chosen is selected. */
    protected final boolean handleGotoNodeKeys(KeyEvent ke) {
        if (!gotoNodePopup.isVisible()) {
            return false;
        }
        gotoNodePopup.handleKeyEvent(ke);
        int sel = gotoNodePopup.consumeSelectedIndex();
        if (sel >= 0) {
            diagram.setSelectedEipNodeIndex(sel);
            diagram.scrollToSelectedEipNode();
        }
        return true;
    }

    /** c: the source of the selected node of a route, or of the route selected in the topology. */
    protected final boolean handleSourceKey(KeyEvent ke) {
        if (!diagram.isShowDiagram() || !ke.isChar('c')) {
            return false;
        }
        if (topologyMode) {
            loadSourceForSelectedTopologyRoute();
        } else {
            loadSourceForSelectedNode();
        }
        return true;
    }

    /** The keys of the diagram both tabs share (see the class description), while the diagram is shown. */
    protected final boolean handleDiagramKeys(KeyEvent ke) {
        if (!diagram.isShowDiagram()) {
            return false;
        }
        if (handleNodeNavigationKeys(ke)) {
            return true;
        }
        boolean routeNodes = !topologyMode && !diagram.getEipNodeBoxes().isEmpty();
        if (routeNodes && ke.isCharIgnoreCase('d')) {
            detailMode = !detailMode;
            detail.reset();
            return true;
        }
        if (routeNodes && ke.isChar('g')) {
            gotoNodePopup.open(diagram.getEipNodeBoxes(), diagram.getSelectedEipNodeIndex());
            return true;
        }
        if (!topologyMode && detailMode) {
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                detail.scrollBy(-10);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                detail.scrollBy(10);
                return true;
            }
        }
        if (!topologyMode && ke.isChar('t')) {
            // jump back to the topology from any depth
            onJumpToTopology();
            leaveRoute();
            drillDownRouteId = null;
            return true;
        }
        if (diagram.handleScrollKeys(ke)) {
            return true;
        }
        if (ke.isCharIgnoreCase('m')) {
            diagramMetrics = !diagramMetrics;
            diagram.endLoad();
            reloadDiagram();
            return true;
        }
        if (topologyMode && ke.isCharIgnoreCase('e')) {
            // external systems: off, edges, all
            externalMode = (externalMode + 1) % 3;
            diagram.endLoad();
            reloadDiagram();
            return true;
        }
        if (ke.isConfirm()) {
            if (topologyMode) {
                String selectedRouteId = diagram.getSelectedRouteId();
                if (selectedRouteId != null) {
                    drillIntoRoute(selectedRouteId);
                }
                return true;
            }
            if (routeNodes) {
                followLinkedRoute();
                return true;
            }
        }
        return false;
    }

    /**
     * Esc on the diagram: closes the go to node popup or the source, else from a route goes back along the routes
     * followed, then to the topology. Returns false in the topology, where each tab has its own.
     */
    protected final boolean handleDiagramEscape() {
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
                drillDownRouteId = routeNavigationStack.pop();
                diagram.selectFromNode(drillDownRouteId);
                diagram.resetScroll();
                return true;
            }
            leaveRoute();
            return true;
        }
        return false;
    }

    /** t jumps from a route back to the topology: what else the tab resets for that. */
    protected void onJumpToTopology() {
    }

    /**
     * Opens the route diagram of a route, when the integration runs that route.
     *
     * @return whether it opened
     */
    protected final boolean drillIntoRoute(String routeId) {
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
        return true;
    }

    /** From a route's diagram back to the topology, with the route selected there; the detail panel closes. */
    protected final void leaveRoute() {
        routeNavigationStack.clear();
        diagram.setPendingSelectionRouteId(drillDownRouteId);
        topologyMode = true;
        detailMode = false;
        detail.reset();
        diagram.setTopologyMode(true);
        diagram.setSelectedEipNodeIndex(-1);
        diagram.resetScroll();
        if (!diagram.hasNativeLayout()) {
            diagram.endLoad();
            reloadDiagram();
        }
    }

    void preloadDiagram() {
        if (ctx.selectedPid != null) {
            diagram.preload(ctx, ctx.selectedPid);
        }
    }

    void refreshDiagramIfNeeded() {
        if (diagram.isShowDiagram() && diagramMetrics) {
            reloadDiagram();
        }
    }

    protected final void loadDiagram() {
        loadDiagram(true);
    }

    protected final void reloadDiagram() {
        loadDiagram(false);
    }

    protected final void loadDiagram(boolean showPlaceholder) {
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

    /** Enter on a node that calls another route: that route's diagram, remembered on the trail Esc walks back. */
    private void followLinkedRoute() {
        String linkedRouteId = diagram.findLinkedRouteId(drillDownRouteId);
        if (linkedRouteId == null || diagram.getRouteLayout(linkedRouteId) == null
                || linkedRouteId.equals(drillDownRouteId)) {
            return;
        }
        if (routeNavigationStack.contains(linkedRouteId)) {
            // back to a route already on the trail: the trail is cut there
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
    }

    private boolean handleNodeNavigationKeys(KeyEvent ke) {
        if (topologyMode && diagram.hasDiagramData() && !diagram.getNodeBoxes().isEmpty()) {
            if (ke.isUp()) {
                diagram.selectNodeUp();
            } else if (ke.isDown()) {
                diagram.selectNodeDown();
            } else if (ke.isLeft()) {
                diagram.selectNodeLeft();
            } else if (ke.isRight()) {
                diagram.selectNodeRight();
            } else if (ke.isHome()) {
                diagram.selectFirstNode();
            } else if (ke.isEnd()) {
                diagram.selectLastNode();
            } else {
                return false;
            }
            diagram.scrollToSelectedNode();
            return true;
        }
        if (!topologyMode && !diagram.getEipNodeBoxes().isEmpty()) {
            if (ke.isUp()) {
                diagram.selectEipNodeUp();
            } else if (ke.isDown()) {
                diagram.selectEipNodeDown();
            } else if (ke.isLeft()) {
                diagram.selectEipNodeLeft();
            } else if (ke.isRight()) {
                diagram.selectEipNodeRight();
            } else if (ke.isHome()) {
                diagram.selectFirstEipNode();
            } else if (ke.isEnd()) {
                diagram.selectLastEipNode();
            } else {
                return false;
            }
            diagram.scrollToSelectedEipNode();
            return true;
        }
        return false;
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
        detail.ensureProcessorDetailLoaded(drillDownRouteId);
        var rl = diagram.getRouteLayout(drillDownRouteId);
        sourceViewer.loadSource(ctx, drillDownRouteId, targetLine, rl != null ? rl.source : null);
    }
}
