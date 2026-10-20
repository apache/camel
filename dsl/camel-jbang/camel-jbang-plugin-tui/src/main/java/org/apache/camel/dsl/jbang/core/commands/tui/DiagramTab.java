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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
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
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class DiagramTab extends AbstractDiagramTab {

    /** The zoom levels of the tab (CAMEL-25147): Architecture › Topology › Route. */
    enum Level {
        ARCHITECTURE,
        TOPOLOGY,
        ROUTE
    }

    private int infoPanelWidth = 30;
    private final DragSplit hSplit = new DragSplit();

    private final ArchitectureView architecture
            = new ArchitectureView(
                    ctx, this::focusGroup, () -> diagram.isShowDescription(), () -> diagram.isShowErrorPaths());
    /** Whether the user chose descriptions on or off with n; until then they are on when the routes have any. */
    private boolean descriptionChosen;
    /** Whether the topology shows each route's group; null until the user presses g (then on when there are groups). */
    private Boolean showGroups;
    private final DragSplit vSplit = new DragSplit();

    DiagramTab(MonitorContext ctx) {
        super(ctx);
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        // Go-to node popup (takes priority when active)
        if (handleGotoNodeKeys(ke)) {
            return true;
        }

        // Source view scrolling (takes priority when active)
        if (sourceViewer.handleKeyEvent(ke)) {
            return true;
        }

        // v moves through the zoom levels: architecture, topology, route (CAMEL-25147)
        if (diagram.isShowDiagram() && ke.isCharIgnoreCase('v')) {
            cycleLevel();
            return true;
        }

        // AI-assisted hints shown or not, at every level: off shows what the sources and the runtime say
        if (diagram.isShowDiagram() && ke.isCharIgnoreCase('a') && IntegrationSummaryHints.settingEnabled()) {
            if (!IntegrationSummaryHints.hasSummary(selectedSourceDirectory())) {
                // nothing to show yet: have the AI explain the project, in the AI panel
                IntegrationSummaryHints.setShown(true);
                if (ctx.projectOverviewCallback != null) {
                    ctx.projectOverviewCallback.run();
                }
                return true;
            }
            IntegrationSummaryHints.setShown(!IntegrationSummaryHints.isShown());
            architecture.refresh();
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Architecture view: it takes the keys while shown; b switches its boxes between business and technical too
        if (architecture.isActive()) {
            if (isViewKey(ke)) {
                descriptionChosen = true;
                diagram.setShowDescription(!diagram.isShowDescription());
                architecture.refresh();
                return true;
            }
            if (ke.isChar('s')) {
                IntegrationSummaryDoc.open(ctx, selectedSourceDirectory(), IntegrationSummaryDoc.ARCHITECTURE);
                return true;
            }
            // the error handling: one setting with the topology's, a box of the routes reached only on error here
            if (ke.isCharIgnoreCase('x')) {
                diagram.setShowErrorPaths(!diagram.isShowErrorPaths());
                architecture.refresh();
                return true;
            }
            return architecture.handleKeyEvent(ke);
        }
        if (diagram.isShowDiagram() && ke.isChar('s')) {
            // the integration summary, opened like a README at its routes (CAMEL-25143)
            IntegrationSummaryDoc.open(ctx, selectedSourceDirectory(), IntegrationSummaryDoc.ROUTES);
            return true;
        }

        // the source of the selected node or route
        if (handleSourceKey(ke)) {
            return true;
        }
        if (handleDiagramKeys(ke)) {
            return true;
        }
        return handleViewKeys(ke);
    }

    /**
     * The keys of the Diagram tab's own views, after the keys it shares with the Routes tab: the groups of the
     * topology, the error paths, the utility routes, and the business or technical view.
     */
    private boolean handleViewKeys(KeyEvent ke) {
        if (!diagram.isShowDiagram()) {
            return false;
        }
        // Groups in the topology: each route's route group, capability, shared services or utility
        if (topologyMode && ke.isCharIgnoreCase('g')) {
            showGroups = !isShowGroups();
            return true;
        }

        // Error handling: the routes reached on error and the error paths into them, in a frame below the happy path
        if (ke.isCharIgnoreCase('x') && (topologyMode || drillDownRouteId != null)) {
            diagram.setShowErrorPaths(!diagram.isShowErrorPaths());
            if (!topologyMode) {
                // the route view has no topology to prepare: load again for the route's error frame
                reloadDiagram();
            }
            return true;
        }

        // Utility routes: one setting for the architecture and the topology, so both show the same groups
        if (topologyMode && ke.isCharIgnoreCase('u') && hasUtilityRoutes()) {
            architecture.setShowUtility(!architecture.isShowUtility());
            return true;
        }

        // Business or technical view: labels and what things do, or ids and endpoints
        if (isViewKey(ke)) {
            descriptionChosen = true;
            diagram.setShowDescription(!diagram.isShowDescription());
            diagram.endLoad();
            reloadDiagram();
            return true;
        }
        return false;
    }

    @Override
    protected void onJumpToTopology() {
        // t starts over at the topology: the architecture opens afresh on v
        architecture.close();
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
        // the popup, the source, or back from a route (the architecture is shown only over the topology)
        if (handleDiagramEscape()) {
            return true;
        }
        if (architecture.isActive()) {
            return architecture.handleEscape();
        }
        if (topologyMode && diagram.isShowDiagram()) {
            // Esc zooms out: from the topology up to the architecture
            goToLevel(Level.ARCHITECTURE);
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

    /**
     * Descriptions on by default when a route has one, in its source or suggested by the AI project overview
     * (CAMEL-25143): the boxes then read as what the routes do, with the route id beneath. Until the user presses n.
     */
    private void applyDefaultDescription() {
        if (descriptionChosen) {
            return;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        boolean described = info != null && (info.routes.stream()
                .anyMatch(r -> r.description != null && !r.description.isBlank())
                || !IntegrationSummaryHints.descriptionsIfEnabled(selectedSourceDirectory()).isEmpty());
        if (described != diagram.isShowDescription()) {
            diagram.setShowDescription(described);
            if (diagram.isShowDiagram()) {
                // the route diagrams are laid out with their labels: load them again
                diagram.endLoad();
                reloadDiagram();
            }
        }
    }

    @Override
    public void onTabSelected() {
        applyDefaultDescription();
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

    // ---- zoom levels (CAMEL-25147) ----

    /** The level shown: the capability groups, the topology of all routes, or one route's diagram. */
    Level level() {
        if (architecture.isActive()) {
            return Level.ARCHITECTURE;
        }
        return topologyMode ? Level.TOPOLOGY : Level.ROUTE;
    }

    /**
     * The route the Route level would show: the one shown, else the one selected in the topology when the integration
     * runs it.
     */
    private String routeCandidate() {
        if (!topologyMode) {
            return drillDownRouteId;
        }
        String selected = diagram.getSelectedRouteId();
        IntegrationInfo info = ctx.findSelectedIntegration();
        return selected != null && info != null && info.routes.stream().anyMatch(r -> selected.equals(r.routeId))
                ? selected : null;
    }

    /** v: architecture, topology, route, and round again; the route level is skipped when no route is selected. */
    private void cycleLevel() {
        goToLevel(switch (level()) {
            case ARCHITECTURE -> Level.TOPOLOGY;
            case TOPOLOGY -> routeCandidate() != null ? Level.ROUTE : Level.ARCHITECTURE;
            case ROUTE -> Level.ARCHITECTURE;
        });
    }

    void goToLevel(Level target) {
        Level now = level();
        if (target == now) {
            return;
        }
        switch (target) {
            case ARCHITECTURE -> {
                if (now == Level.ROUTE) {
                    leaveRoute();
                }
                diagram.setFocus(null, null);
                if (architecture.isSuspended()) {
                    architecture.resume();
                } else {
                    architecture.open(selectedSourceDirectory());
                }
            }
            case TOPOLOGY -> {
                if (now == Level.ROUTE) {
                    leaveRoute();
                } else if (!architecture.openSelectedGroup()) {
                    architecture.close();
                }
            }
            case ROUTE -> {
                String route = routeCandidate();
                if (route != null) {
                    drillIntoRoute(route);
                }
            }
        }
    }

    /**
     * Down from a capability: the topology of all routes, so how every route connects stays in view, with the group's
     * routes highlighted and the first of them selected.
     */
    private void focusGroup(ProjectCapabilities.Group group) {
        Set<String> running = new LinkedHashSet<>();
        for (String route : group.routes()) {
            running.add(RouteKeys.runningId(selectedSourceDirectory(), route));
        }
        diagram.setFocus(running, group.name());
        topologyMode = true;
        diagram.setTopologyMode(true);
        if (!group.routes().isEmpty()) {
            String first = RouteKeys.runningId(selectedSourceDirectory(), group.routes().get(0));
            int idx = diagram.findNodeIndexByRouteId(first);
            if (idx >= 0) {
                diagram.setSelectedNodeIndex(idx);
                diagram.scrollToSelectedNode();
            } else {
                diagram.setPendingSelectionRouteId(first);
            }
        }
        if (!diagram.hasNativeLayout()) {
            diagram.endLoad();
            reloadDiagram();
        }
    }

    @Override
    public SubViewBar.Spec subViewBar() {
        if (ctx.findSelectedIntegration() == null || sourceViewer.isVisible() || !diagram.isShowDiagram()) {
            return null;
        }
        // the levels are in the title of the diagram, as the DSLs of the Source tab are (v moves through them); the
        // bar has the view settings of the level shown
        return new SubViewBar.Spec(null, List.of(), viewToggles(), true);
    }

    /**
     * The levels for the title of the diagram: Architecture │ Topology │ Route, the one shown bold and the others dim,
     * as the Source tab shows YAML │ Java │ XML. The Route level is dimmer when no route is selected to go to.
     */
    List<Span> levelSpans() {
        Level current = level();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" "));
        String[] labels = { "Architecture", "Topology", "Route" };
        Level[] levels = { Level.ARCHITECTURE, Level.TOPOLOGY, Level.ROUTE };
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) {
                spans.add(Span.styled(" \u2502 ", Style.EMPTY.dim()));
            }
            Style style = levels[i] == current ? Style.EMPTY.bold()
                    : levels[i] == Level.ROUTE && routeCandidate() == null ? Theme.muted().dim() : Style.EMPTY.dim();
            spans.add(Span.styled(labels[i], style));
        }
        spans.add(Span.raw(" "));
        return spans;
    }

    /** The title with the levels after it. */
    private Line withLevels(Line title) {
        List<Span> spans = new ArrayList<>(title.spans());
        spans.addAll(levelSpans());
        return Line.from(spans);
    }

    /**
     * Before the topology is drawn: the AI hints, each route's group, the utility routes to leave out, and a new layout
     * when those or the box width changed.
     */
    private void prepareTopology() {
        diagram.setAiSourceDirectory(selectedSourceDirectory());
        Map<String, RouteGroups.Tag> all = RouteGroups.of(selectedSourceDirectory(), diagram.isShowErrorPaths());
        Map<String, RouteGroups.Tag> tags = isShowGroups() ? all : Map.of();
        Set<String> hidden = architecture.isShowUtility() ? Set.of() : RouteGroups.utility(all);
        diagram.setGroups(RouteGroups.tagLines(tags), RouteGroups.colors(tags));
        diagram.setGroupLegend(RouteGroups.legend(tags, hidden));
        diagram.setHiddenRoutes(hidden);
        if (diagram.isTopologyStale() && !diagram.isLoading()) {
            reloadDiagram();
        }
    }

    /** Whether there is AI-assisted content to show or hide: a summary, and the AI overview setting not off. */
    private boolean hasAiHints() {
        return IntegrationSummaryHints.settingEnabled() && IntegrationSummaryHints.hasSummary(selectedSourceDirectory());
    }

    private boolean hasUtilityRoutes() {
        return !RouteGroups.utility(RouteGroups.of(selectedSourceDirectory(), diagram.isShowErrorPaths())).isEmpty();
    }

    /** Groups are shown when the user said so, else when the project has any. */
    private boolean isShowGroups() {
        return showGroups != null ? showGroups : RouteGroups.any(selectedSourceDirectory());
    }

    /** b switches the view between business and technical; n, the key of the former description toggle, too. */
    private static boolean isViewKey(KeyEvent ke) {
        return ke.isCharIgnoreCase('b') || ke.isCharIgnoreCase('n');
    }

    /** The view settings of the level shown, with their state: they sit on the level bar, beside the diagram. */
    List<SubViewBar.Toggle> viewToggles() {
        List<SubViewBar.Toggle> toggles = new ArrayList<>();
        if (IntegrationSummaryHints.settingEnabled()) {
            // without a summary yet the setting is off, and a has the AI write one
            toggles.add(new SubViewBar.Toggle(
                    "a", "ai", hasAiHints() && IntegrationSummaryHints.isShown() ? "on" : "off"));
        }
        toggles.addAll(levelToggles());
        return toggles;
    }

    /** On or off; an integration on a Camel before 4.23 reports no error paths, and the toggle says so. */
    private String errorsToggle() {
        if (!diagram.isErrorPathsKnown()) {
            return "4.23+";
        }
        return diagram.isShowErrorPaths() ? "on" : "off";
    }

    private List<SubViewBar.Toggle> levelToggles() {
        SubViewBar.Toggle view
                = new SubViewBar.Toggle("b", "view", diagram.isShowDescription() ? "business" : "technical");
        String metrics = diagramMetrics ? "on" : "off";
        return switch (level()) {
            case ARCHITECTURE -> List.of(view,
                    new SubViewBar.Toggle("u", "utility", architecture.isShowUtility() ? "on" : "off"),
                    new SubViewBar.Toggle("x", "errors", diagram.isShowErrorPaths() ? "on" : "off"),
                    new SubViewBar.Toggle("e", "external", architecture.isShowExternal() ? "edges" : "off"));
            case TOPOLOGY -> {
                List<SubViewBar.Toggle> toggles = new ArrayList<>();
                toggles.add(view);
                toggles.add(new SubViewBar.Toggle("g", "group", isShowGroups() ? "on" : "off"));
                if (hasUtilityRoutes()) {
                    toggles.add(new SubViewBar.Toggle("u", "utility", architecture.isShowUtility() ? "on" : "off"));
                }
                toggles.add(new SubViewBar.Toggle("x", "errors", errorsToggle()));
                toggles.add(new SubViewBar.Toggle("m", "metrics", metrics));
                toggles.add(new SubViewBar.Toggle(
                        "e", "external",
                        switch (externalMode) {
                            case 1 -> "edges";
                            case 2 -> "all";
                            default -> "off";
                        }));
                yield toggles;
            }
            case ROUTE -> List.of(view, new SubViewBar.Toggle("x", "errors", errorsToggle()),
                    new SubViewBar.Toggle("m", "metrics", metrics),
                    new SubViewBar.Toggle("y", "types", diagram.isShowBodyTypes() ? "on" : "off"),
                    new SubViewBar.Toggle("d", "detail", detailMode ? "on" : "off"));
        };
    }

    @Override
    public void onIntegrationChanged() {
        architecture.close();
        diagram.setFocus(null, null);
        topologyMode = true;
        drillDownRouteId = null;
        routeNavigationStack.clear();
        detailMode = false;
        detail.reset();
        diagram.reset();
        diagram.setTopologyMode(true);
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }
        // the running routes, so a source route without an id is known by the id Camel gave it
        RouteKeys.remember(selectedSourceDirectory(), info.routes);

        if (sourceViewer.isVisible()) {
            sourceViewer.render(frame, area);
            return;
        }

        if (architecture.isActive()) {
            architecture.render(frame, area, info.name, levelSpans());
            return;
        }

        if (diagram.isShowDiagram() && diagram.hasDiagramData()) {
            String selectedRouteId = topologyMode ? diagram.getSelectedRouteId() : drillDownRouteId;

            if (topologyMode && diagram.hasNativeLayout()) {
                Line title;
                if (info.name != null) {
                    // the level is named by the levels after it, as on the Source tab
                    title = Line.from(
                            Span.raw(" Diagram ["),
                            Span.styled(info.name, Theme.label().bold()),
                            Span.raw("] "));
                } else {
                    title = Line.from(Span.raw(" Diagram "));
                }
                title = withLevels(title);
                if (selectedRouteId != null && area.width() > 60) {
                    infoPanelWidth = Math.max(10, Math.min(infoPanelWidth, area.width() - 20));
                    List<Rect> hChunks = Layout.horizontal()
                            .constraints(Constraint.length(infoPanelWidth), Constraint.fill())
                            .split(area);
                    hSplit.setBorderPos(hChunks.get(1).x());
                    detail.renderRouteInfoPanel(frame, hChunks.get(0), info, selectedRouteId);
                    prepareTopology();
                    diagram.renderNativeDiagram(frame, hChunks.get(1), title, diagramMetrics);
                } else {
                    prepareTopology();
                    diagram.renderNativeDiagram(frame, area, title, diagramMetrics);
                }
                return;
            } else if (!topologyMode && drillDownRouteId != null
                    && diagram.getRouteLayout(drillDownRouteId) != null) {
                Line title = withLevels(DiagramDetailSupport.withRouteContext(
                        DiagramDetailSupport.buildBreadcrumbTitle(routeNavigationStack, drillDownRouteId), info,
                        drillDownRouteId, selectedSourceDirectory(), isShowGroups(), diagram.isShowErrorPaths()));
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
        if (diagram.isShowDiagram() && ctx.findSelectedIntegration() != null) {
            // the levels are in the title of the diagram
            hint(spans, "v", "level");
        }
        if (architecture.isActive()) {
            architecture.renderFooter(spans);
            return;
        }
        if (diagram.isShowDiagram()) {
            // actions only: the level is in the title, the view settings on the bar above
            if (!topologyMode && !diagram.getEipNodeBoxes().isEmpty()) {
                hint(spans, "Esc", "back");
                hint(spans, "c", "source");
                hint(spans, "g", "go to");
                hint(spans, "s", "summary");
            } else if (!topologyMode) {
                hint(spans, "Esc", "back");
                hint(spans, "s", "summary");
            } else if (!diagram.getNodeBoxes().isEmpty()) {
                hint(spans, "Enter", "drill-down");
                hint(spans, "c", "source");
                hint(spans, "s", "summary");
            } else {
                diagram.renderFooterHints(spans);
            }
        }
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

    // ---- Quick doc (i toggle in source viewer) ----

    // ---- MCP programmatic navigation ----

    /** Whether the diagram has loaded and is drawn: a route or node not found then is not there. */
    boolean isDiagramShown() {
        return !diagram.isLoading() && !diagram.getNodeBoxes().isEmpty();
    }

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
                var treeNode = eipBox.layoutNode().treeNode;
                if (treeNode != null && !treeNode.info.bodyTypes.isEmpty()) {
                    // the body types the step has been reached with; more than one kind is a step that needs attention
                    node.put("bodyTypes", new JsonArray(treeNode.info.bodyTypes));
                    node.put("bodyKinds", new JsonArray(treeNode.info.bodyKinds));
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
