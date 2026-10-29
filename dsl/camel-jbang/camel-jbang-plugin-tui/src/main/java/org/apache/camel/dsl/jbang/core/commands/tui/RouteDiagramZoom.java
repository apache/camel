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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;
import org.apache.camel.diagram.RouteErrorFrame;
import org.apache.camel.diagram.TopologyErrorFrame;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDensity;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget;

/**
 * The zoom of the route diagram: how much room a step takes ({@link RouteDensity}), or the most detail that fits the
 * screen, and the blocks folded into their first step. A folded block is left out of the route before the engine lays
 * it out, so the steps after it move up and its branches give their columns back. It also has whether the mini panels
 * over the diagram are shown (p), a view setting kept with the zoom.
 * <p>
 * The keys change the zoom on the UI thread; the layout reads it on the background thread, so the state is volatile or
 * concurrent. When the zoom changes, the selected step stays selected and on the same row of the screen.
 */
final class RouteDiagramZoom {

    /** The zoom chosen last: a density, or fit. */
    static final String PROP_ROUTE_ZOOM = "camel.tui.routeZoom";
    /** The folded blocks of each route, by route id: route:step,step;route:step. */
    static final String PROP_ROUTE_FOLDS = "camel.tui.routeFolds";
    /** The groups of routes folded in the topology, by group id: group,group. */
    static final String PROP_GROUP_FOLDS = "camel.tui.groupFolds";
    /** Whether the mini panels over the diagram (the map and the route tree) are shown: false when hidden. */
    static final String PROP_DIAGRAM_PANELS = "camel.tui.diagramPanels";

    /** The selected step to select again once the route is laid out anew, and its row on the screen. */
    record Restore(int index, int row, int screenRow) {
    }

    /** The step to select again after the next layout, its row on the screen, and the layout it was selected on. */
    private record Anchor(LayoutRoute layout, String nodeId, int screenRow) {
    }

    /** The density until another is chosen: boxes of one line, about twice the steps of the detailed on a screen. */
    static final RouteDensity DEFAULT = RouteDensity.COMPACT;

    private volatile RouteDensity density = DEFAULT;
    private volatile boolean fit;
    private volatile boolean panels = true;
    // the routes the History tab laid out whole, to lay them out again when the zoom changes
    private volatile Whole whole;

    /**
     * The routes last laid out whole and their label mode, in one reference: the layout thread sets them, the UI thread
     * reads them, and two fields could be read half new.
     */
    private record Whole(List<RouteInfo> routes, RouteDiagramLayoutEngine.NodeLabelMode labelMode) {
    }

    /** The folded blocks of each route: the ids of their first steps. */
    private final Map<String, Set<String>> folded = new ConcurrentHashMap<>();
    /** The steps of each route as last laid out, without its folds: fold all folds the blocks hidden in others too. */
    private final Map<String, RouteInfo> unfolded = new ConcurrentHashMap<>();
    /** What each layout was made with: its density, and the steps folded into each folded step. */
    private final Map<LayoutRoute, RouteDensity> densities = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<LayoutRoute, Map<String, Integer>> foldedSteps
            = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<LayoutRoute, Integer> nodeWidths = Collections.synchronizedMap(new WeakHashMap<>());
    // set on the UI thread, taken on the layout thread: one reference, so the layout never sees half an anchor
    private final AtomicReference<Anchor> anchor = new AtomicReference<>();

    /** The zoom with the density or fit chosen last time, and the mini panels shown or hidden. */
    static RouteDiagramZoom persisted() {
        RouteDiagramZoom zoom = new RouteDiagramZoom();
        try {
            zoom.setting(TuiUserConfig.read(PROP_ROUTE_ZOOM));
            zoom.panels = !"false".equals(TuiUserConfig.read(PROP_DIAGRAM_PANELS));
            zoom.foldSetting(TuiUserConfig.read(PROP_ROUTE_FOLDS));
            String groups = TuiUserConfig.read(PROP_GROUP_FOLDS);
            if (groups != null && !groups.isBlank()) {
                zoom.foldedGroups.addAll(List.of(groups.split(",")));
            }
        } catch (RuntimeException e) {
            // best-effort: the default zoom
        }
        return zoom;
    }

    /** Keeps the density or fit, and the mini panels, for the next start; file work, so not on the UI thread. */
    void persist() {
        try {
            TuiUserConfig.write(PROP_ROUTE_ZOOM, setting());
            TuiUserConfig.write(PROP_DIAGRAM_PANELS, panels ? null : "false");
            TuiUserConfig.write(PROP_ROUTE_FOLDS, foldSetting());
            TuiUserConfig.write(PROP_GROUP_FOLDS, foldedGroups.isEmpty() ? null : String.join(",", foldedGroups));
        } catch (RuntimeException e) {
            // best-effort: a persist failure must not disrupt the TUI
        }
    }

    /** The density, or fit; null for the default (compact), so the file does not carry it. */
    String setting() {
        if (fit) {
            return "fit";
        }
        return density == DEFAULT ? null : density.label();
    }

    /** The folded blocks as a setting, route:step,step;route:step; null when none is folded. */
    String foldSetting() {
        StringBuilder sb = new StringBuilder();
        folded.forEach((route, ids) -> {
            if (!ids.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : ";").append(route).append(':').append(String.join(",", ids));
            }
        });
        return sb.isEmpty() ? null : sb.toString();
    }

    void foldSetting(String value) {
        folded.clear();
        if (value == null || value.isBlank()) {
            return;
        }
        for (String entry : value.split(";")) {
            int colon = entry.lastIndexOf(':');
            if (colon > 0 && colon < entry.length() - 1) {
                Set<String> ids = folded.computeIfAbsent(entry.substring(0, colon), k -> ConcurrentHashMap.newKeySet());
                ids.addAll(List.of(entry.substring(colon + 1).split(",")));
            }
        }
    }

    void setting(String value) {
        fit = "fit".equals(value);
        density = DEFAULT;
        for (RouteDensity d : RouteDensity.values()) {
            if (d.label().equals(value)) {
                density = d;
            }
        }
    }

    RouteDensity density() {
        return density;
    }

    /** Whether the mini panels over the diagram are shown: the map of the topology and the tree of the route. */
    boolean isPanels() {
        return panels;
    }

    void togglePanels() {
        panels = !panels;
    }

    boolean isFit() {
        return fit;
    }

    void zoomIn() {
        fit = false;
        density = density.zoomIn();
    }

    void zoomOut() {
        fit = false;
        density = density.zoomOut();
    }

    void toggleFit() {
        fit = !fit;
    }

    /**
     * What the zoom toggle of the view bar says: the density and where it is among the levels (compact 2/3), and fit
     * when it is chosen for the screen.
     */
    String label(LayoutRoute shown) {
        return label(shown != null ? densityOf(shown) : density);
    }

    /** What the zoom toggle of the view bar says of the topology, laid out with the given density. */
    String label(RouteDensity shown) {
        String label = shown.label() + " " + shown.position();
        return fit ? "fit " + label : label;
    }

    RouteDensity densityOf(LayoutRoute lr) {
        RouteDensity d = densities.get(lr);
        // a layout the zoom did not make is the engine's
        return d != null ? d : RouteDensity.DETAILED;
    }

    /** The width of a step in the layout (the engine's pixels), which the widget turns into columns. */
    int nodeWidthOf(LayoutRoute lr) {
        Integer w = nodeWidths.get(lr);
        return w != null ? w : RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE;
    }

    Map<String, Integer> foldedStepsOf(LayoutRoute lr) {
        Map<String, Integer> steps = foldedSteps.get(lr);
        return steps != null ? steps : Map.of();
    }

    /**
     * Folds the block the step starts, or unfolds it when folded; a step without a block of its own folds the block it
     * is in.
     *
     * @return the id of the step folded or unfolded, to select; null when there is no block to fold
     */
    String toggleFold(String routeId, LayoutNode node) {
        if (routeId == null || node == null || node.treeNode == null || node.id == null) {
            return null;
        }
        Set<String> ids = folded.computeIfAbsent(routeId, k -> ConcurrentHashMap.newKeySet());
        if (ids.remove(node.id)) {
            return node.id;
        }
        TreeNode block = node.treeNode;
        while (block != null && !foldable(block)) {
            block = block.parent;
        }
        if (block == null || block.info.id == null) {
            return null;
        }
        ids.add(block.info.id);
        return block.info.id;
    }

    /**
     * Folds every block of the route, the blocks in other blocks too: unfolding a block shows its own steps, with the
     * blocks in it folded.
     *
     * @return the id of the outermost block the step is in, which is the step to select; the step itself when it is in
     *         no block
     */
    String foldAll(String routeId, LayoutNode selected) {
        RouteInfo route = unfolded.get(routeId);
        if (route == null) {
            return selected != null ? selected.id : null;
        }
        Set<String> ids = folded.computeIfAbsent(routeId, k -> ConcurrentHashMap.newKeySet());
        List<NodeInfo> nodes = route.nodes;
        for (int i = 0; i + 1 < nodes.size(); i++) {
            NodeInfo n = nodes.get(i);
            if (n.id != null && nodes.get(i + 1).level > n.level && foldable(n.type)) {
                ids.add(n.id);
            }
        }
        String answer = selected != null ? selected.id : null;
        TreeNode block = selected != null && selected.treeNode != null ? selected.treeNode.parent : null;
        for (; block != null; block = block.parent) {
            if (block.info.id != null && ids.contains(block.info.id)) {
                answer = block.info.id;
            }
        }
        return answer;
    }

    /** Unfolds every block of the route. */
    void expandAll(String routeId) {
        Set<String> ids = folded.get(routeId);
        if (ids != null) {
            ids.clear();
        }
    }

    /** The steps of the route (not its from), as last laid out: how big the route is. */
    int stepCount(String routeId) {
        RouteInfo route = routeId != null ? unfolded.get(routeId) : null;
        if (route == null) {
            return 0;
        }
        int count = 0;
        for (NodeInfo n : route.nodes) {
            if (!"route".equals(n.type) && !"from".equals(n.type)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The title of a route's diagram with how many steps it has, and how many of them are folded: 38 steps · 12 folded.
     */
    Line withStepCount(Line title, String routeId, LayoutRoute shown) {
        int steps = stepCount(routeId);
        if (steps == 0) {
            return title;
        }
        int hidden = 0;
        for (int n : foldedStepsOf(shown).values()) {
            hidden += n;
        }
        String text = "· " + steps + (steps == 1 ? " step" : " steps") + (hidden > 0 ? " · " + hidden + " folded" : "") + " ";
        List<Span> spans = new ArrayList<>(title.spans());
        spans.add(Span.styled(text, Theme.muted()));
        return Line.from(spans);
    }

    /** How many blocks of the route are folded. */
    int foldedCount(String routeId) {
        Set<String> ids = folded.get(routeId);
        return ids != null ? ids.size() : 0;
    }

    /** A step with steps in it, which is not the route or its from. */
    private static boolean foldable(TreeNode node) {
        return !node.children.isEmpty() && !node.table && foldable(node.info.type);
    }

    /** A step of this type with steps in it folds; a Switch is a table, its cases are not steps after it. */
    private static boolean foldable(String type) {
        return type != null && !"route".equals(type) && !"from".equals(type)
                && !RouteDiagramLayoutEngine.TABLE_EIPS.contains(type);
    }

    /** Where the fold markers were drawn on the screen last, for a click on one to unfold its step. */
    private volatile List<RouteDiagramWidget.FoldHit> foldHits = List.of();

    /** The fold markers as drawn, and where the diagram is on the screen (its top left, less the scroll). */
    void foldHits(List<RouteDiagramWidget.FoldHit> hits, int x, int y) {
        foldHits = hits.stream()
                .map(h -> new RouteDiagramWidget.FoldHit(h.nodeId(), h.row() + y, h.startCol() + x, h.endCol() + x))
                .toList();
    }

    /** The step whose fold marker is at the point of the screen, or null. */
    String foldMarkerAt(int x, int y) {
        for (RouteDiagramWidget.FoldHit h : foldHits) {
            if (h.row() == y && x >= h.startCol() && x <= h.endCol()) {
                return h.nodeId();
            }
        }
        return null;
    }

    /** The groups of routes folded in the topology, each into a box of its own. */
    private final Set<String> foldedGroups = ConcurrentHashMap.newKeySet();

    Set<String> foldedGroups() {
        return foldedGroups;
    }

    /** Folds the group into a box, or unfolds it when folded. */
    void toggleGroup(String group) {
        if (!foldedGroups.remove(group)) {
            foldedGroups.add(group);
        }
    }

    /** Unfolds a step of the route; false when it is not folded. */
    boolean unfold(String routeId, String nodeId) {
        Set<String> ids = folded.get(routeId);
        return ids != null && ids.remove(nodeId);
    }

    /** Remembers the selected step and its row on the screen, to keep it there once the route is laid out anew. */
    void anchor(LayoutRoute shown, String nodeId, int screenRow) {
        anchor.set(nodeId != null ? new Anchor(shown, nodeId, screenRow) : null);
    }

    /** The step to select in the layout to draw, when it is the new layout the anchor waited for; else null. */
    Restore restore(LayoutRoute lr) {
        Anchor a = anchor.get();
        // a newer anchor set meanwhile stays for the layout it waits for
        if (a == null || lr == null || lr == a.layout() || !anchor.compareAndSet(a, null)) {
            return null;
        }
        String id = a.nodeId();
        int index = 0;
        for (LayoutNode ln : lr.nodes) {
            if ("route".equals(ln.type)) {
                continue;
            }
            if (id.equals(ln.id)) {
                return new Restore(index, ln.y / RouteDensity.ROW, a.screenRow());
            }
            index++;
        }
        return null;
    }

    /**
     * Lays out the routes with the zoom: the folded blocks left out, the steps on the rows of the density (with fit,
     * the most detailed one in which the route fits the rows of the screen), the error handling in its frame below.
     */
    Map<String, LayoutRoute> layoutRoutes(
            List<RouteInfo> routes, RouteDiagramLayoutEngine.NodeLabelMode labelMode, boolean showErrors,
            List<TopologyErrorFrame.ErrorPath> errorPaths, Map<String, RouteErrorFrame> frames, int visibleRows) {
        // an engine for each box width a density lays the steps out with
        Map<Integer, RouteDiagramLayoutEngine> engines = new HashMap<>();
        Map<String, LayoutRoute> answer = new LinkedHashMap<>();
        for (RouteInfo r : routes) {
            // the happy path; the onException clauses are not its steps, and are shown in the error frame
            RouteErrorFrame.Split split = RouteErrorFrame.split(r);
            unfolded.put(r.routeId, split.happy());
            Map<String, Integer> steps = new HashMap<>();
            Laid laid = layoutFit(engines, labelMode, fold(split.happy(), steps), visibleRows);
            LayoutRoute lr = laid.layout();
            if (showErrors) {
                RouteErrorFrame frame = addErrorFrame(laid.engine(), laid.density(), lr, split.blocks(),
                        RouteErrorFrame.lines(r.routeId, errorPaths, TopologyErrorFrame.LineStyle.UNICODE));
                if (frame != null) {
                    frames.put(r.routeId, frame);
                }
            }
            foldedSteps.put(lr, steps);
            answer.put(r.routeId, lr);
        }
        return answer;
    }

    /**
     * Lays out the routes whole, their onException clauses with their steps, with the zoom's density (or fit) and no
     * folds: the History tab highlights the steps a message took, wherever they are.
     */
    Map<String, LayoutRoute> layoutWhole(
            List<RouteInfo> routes, RouteDiagramLayoutEngine.NodeLabelMode labelMode, int visibleRows) {
        whole = new Whole(routes, labelMode);
        Map<Integer, RouteDiagramLayoutEngine> engines = new HashMap<>();
        Map<String, LayoutRoute> answer = new LinkedHashMap<>();
        for (RouteInfo r : routes) {
            answer.put(r.routeId, layoutFit(engines, labelMode, r, visibleRows).layout());
        }
        return answer;
    }

    /**
     * The routes last laid out whole, laid out again at the zoom as it is now: the History tab zooms without loading
     * the message again, so its view stays where it is; the layouts as they are when there are none.
     */
    Map<String, LayoutRoute> layoutWholeAgain(int visibleRows, Map<String, LayoutRoute> current) {
        Whole w = whole;
        return w != null ? layoutWhole(w.routes(), w.labelMode(), visibleRows) : current;
    }

    /** A route laid out, the density it was placed with, and the engine that laid it out. */
    private record Laid(LayoutRoute layout, RouteDensity density, RouteDiagramLayoutEngine engine) {
    }

    /** A route at the zoom's density, or with fit at the most detailed one in which it fits the rows of the screen. */
    private Laid layoutFit(
            Map<Integer, RouteDiagramLayoutEngine> engines, RouteDiagramLayoutEngine.NodeLabelMode labelMode,
            RouteInfo route, int visibleRows) {
        RouteDensity d = fit ? RouteDensity.DETAILED : density;
        RouteDiagramLayoutEngine engine = engine(engines, d, labelMode);
        LayoutRoute lr = layout(engine, d, route);
        while (fit && d.zoomOut() != d && visibleRows > 0 && RouteDensity.rows(lr) > visibleRows) {
            d = d.zoomOut();
            engine = engine(engines, d, labelMode);
            lr = layout(engine, d, route);
        }
        densities.put(lr, d);
        nodeWidths.put(lr, engine.getNodeWidth());
        return new Laid(lr, d, engine);
    }

    /**
     * The zoom keys where a diagram has no others: + and - (and =), z for fit. The caller lays the diagram out again.
     *
     * @return whether the key changed the zoom
     */
    boolean handleZoomKey(KeyEvent ke, Executor background) {
        if (ke.isChar('+') || ke.isChar('=')) {
            zoomIn();
        } else if (ke.isChar('-')) {
            zoomOut();
        } else if (ke.isCharIgnoreCase('z')) {
            toggleFit();
        } else {
            return false;
        }
        background.execute(this::persist);
        return true;
    }

    /**
     * The error handling of a route in a frame below its steps, as {@link RouteErrorFrame#add} lays it out: the lines
     * of the route, then its onException blocks under one another, each on the rows of the density, so the frame zooms
     * with the route.
     */
    private static RouteErrorFrame addErrorFrame(
            RouteDiagramLayoutEngine engine, RouteDensity density, LayoutRoute lr, List<RouteInfo> blocks,
            List<String> lines) {
        if (blocks.isEmpty() && lines.isEmpty()) {
            return null;
        }
        int row = RouteDensity.ROW;
        int top = lr.maxY + 2 * row;
        int y = top + (lines.size() + 1) * row;
        for (RouteInfo block : blocks) {
            LayoutRoute b = engine.layoutRoute(block, y);
            density.apply(b);
            lr.nodes.addAll(b.nodes);
            lr.maxX = Math.max(lr.maxX, b.maxX);
            y = b.maxY + 2 * row;
        }
        RouteErrorFrame frame = new RouteErrorFrame(top, y, lines);
        lr.maxY = y + row;
        lr.errorFrame = frame;
        return frame;
    }

    private static RouteDiagramLayoutEngine engine(
            Map<Integer, RouteDiagramLayoutEngine> engines, RouteDensity density,
            RouteDiagramLayoutEngine.NodeLabelMode labelMode) {
        return engines.computeIfAbsent(density.boxWidth(), w -> {
            RouteDiagramLayoutEngine engine
                    = new RouteDiagramLayoutEngine(w, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE, labelMode);
            // a Switch is drawn as a decision table, a row per case
            engine.setTableLayout(true);
            return engine;
        });
    }

    /** A route laid out by the engine, its steps placed on the rows of the density, moved to the top. */
    private static LayoutRoute layout(RouteDiagramLayoutEngine engine, RouteDensity density, RouteInfo route) {
        LayoutRoute lr = engine.layoutRoute(route, 0);
        density.apply(lr);
        DiagramLayouts.normalizeRouteLayoutY(lr);
        return lr;
    }

    /** The route without the steps of its folded blocks; counts the steps left out of each. */
    private RouteInfo fold(RouteInfo route, Map<String, Integer> steps) {
        Set<String> ids = folded.getOrDefault(route.routeId, Set.of());
        if (ids.isEmpty()) {
            return route;
        }
        RouteInfo answer = new RouteInfo();
        answer.routeId = route.routeId;
        answer.source = route.source;
        answer.stat = route.stat;
        Set<String> present = new HashSet<>();
        int foldLevel = -1;
        String foldId = null;
        for (NodeInfo n : route.nodes) {
            if (n.id != null) {
                present.add(n.id);
            }
            if (foldLevel >= 0 && n.level > foldLevel) {
                steps.merge(foldId, 1, Integer::sum);
                continue;
            }
            foldLevel = -1;
            answer.nodes.add(n);
            if (n.id != null && ids.contains(n.id)) {
                foldLevel = n.level;
                foldId = n.id;
            }
        }
        // a step no longer in the route (its source changed) is not folded any more
        ids.retainAll(present);
        return answer;
    }
}
