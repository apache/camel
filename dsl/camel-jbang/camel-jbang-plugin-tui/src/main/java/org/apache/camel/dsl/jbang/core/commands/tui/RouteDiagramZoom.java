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

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;
import org.apache.camel.diagram.RouteErrorFrame;
import org.apache.camel.diagram.TopologyErrorFrame;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDensity;

/**
 * The zoom of the route diagram: how much room a step takes ({@link RouteDensity}), or the most detail that fits the
 * screen, and the blocks folded into their first step. A folded block is left out of the route before the engine lays
 * it out, so the steps after it move up and its branches give their columns back.
 * <p>
 * The keys change the zoom on the UI thread; the layout reads it on the background thread, so the state is volatile or
 * concurrent. When the zoom changes, the selected step stays selected and on the same row of the screen.
 */
final class RouteDiagramZoom {

    /** The zoom chosen last: a density, or fit; the folded blocks are not kept. */
    static final String PROP_ROUTE_ZOOM = "camel.tui.routeZoom";

    /** The selected step to select again once the route is laid out anew, and its row on the screen. */
    record Restore(int index, int row, int screenRow) {
    }

    private volatile RouteDensity density = RouteDensity.DETAILED;
    private volatile boolean fit;
    /** The folded blocks of each route: the ids of their first steps. */
    private final Map<String, Set<String>> folded = new ConcurrentHashMap<>();
    /** The steps of each route as last laid out, without its folds: fold all folds the blocks hidden in others too. */
    private final Map<String, RouteInfo> unfolded = new ConcurrentHashMap<>();
    /** What each layout was made with: its density, and the steps folded into each folded step. */
    private final Map<LayoutRoute, RouteDensity> densities = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<LayoutRoute, Map<String, Integer>> foldedSteps
            = Collections.synchronizedMap(new WeakHashMap<>());
    // the step to select again after the next layout, and the layout it was selected on
    private LayoutRoute anchorLayout;
    private String anchorNodeId;
    private int anchorScreenRow;

    /** The zoom with the density or fit chosen last time. */
    static RouteDiagramZoom persisted() {
        RouteDiagramZoom zoom = new RouteDiagramZoom();
        try {
            zoom.setting(TuiUserConfig.read(PROP_ROUTE_ZOOM));
        } catch (RuntimeException e) {
            // best-effort: the default zoom
        }
        return zoom;
    }

    /** Keeps the density or fit for the next start; file work, so not on the UI thread. */
    void persist() {
        try {
            TuiUserConfig.write(PROP_ROUTE_ZOOM, setting());
        } catch (RuntimeException e) {
            // best-effort: a persist failure must not disrupt the TUI
        }
    }

    /** The density, or fit; null for the default (detailed), so the file does not carry it. */
    String setting() {
        if (fit) {
            return "fit";
        }
        return density == RouteDensity.DETAILED ? null : density.label();
    }

    void setting(String value) {
        fit = "fit".equals(value);
        density = RouteDensity.DETAILED;
        for (RouteDensity d : RouteDensity.values()) {
            if (d.label().equals(value)) {
                density = d;
            }
        }
    }

    RouteDensity density() {
        return density;
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
        RouteDensity d = shown != null ? densityOf(shown) : density;
        String label = d.label() + " " + d.position();
        return fit ? "fit " + label : label;
    }

    RouteDensity densityOf(LayoutRoute lr) {
        RouteDensity d = densities.get(lr);
        return d != null ? d : RouteDensity.DETAILED;
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
        String text = steps + (steps == 1 ? " step" : " steps") + (hidden > 0 ? " · " + hidden + " folded" : "") + " ";
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

    /** Remembers the selected step and its row on the screen, to keep it there once the route is laid out anew. */
    void anchor(LayoutRoute shown, String nodeId, int screenRow) {
        anchorLayout = shown;
        anchorNodeId = nodeId;
        anchorScreenRow = screenRow;
    }

    /** The step to select in the layout to draw, when it is the new layout the anchor waited for; else null. */
    Restore restore(LayoutRoute lr) {
        if (anchorNodeId == null || lr == null || lr == anchorLayout) {
            return null;
        }
        String id = anchorNodeId;
        anchorNodeId = null;
        anchorLayout = null;
        int index = 0;
        for (LayoutNode ln : lr.nodes) {
            if ("route".equals(ln.type)) {
                continue;
            }
            if (id.equals(ln.id)) {
                return new Restore(index, ln.y / RouteDensity.ROW, anchorScreenRow);
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
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE, labelMode);
        // a Switch is drawn as a decision table, a row per case
        engine.setTableLayout(true);
        Map<String, LayoutRoute> answer = new LinkedHashMap<>();
        for (RouteInfo r : routes) {
            // the happy path; the onException clauses are not its steps, and are shown in the error frame
            RouteErrorFrame.Split split = RouteErrorFrame.split(r);
            unfolded.put(r.routeId, split.happy());
            Map<String, Integer> steps = new HashMap<>();
            LayoutRoute lr = engine.layoutRoute(fold(split.happy(), steps), 0);
            RouteDensity d = fit ? RouteDensity.DETAILED : density;
            // each density places the steps from where the engine put the route, before it was moved to the top
            int rootY = lr.nodes.isEmpty() ? 0 : lr.nodes.get(0).y;
            d.apply(lr);
            DiagramLayouts.normalizeRouteLayoutY(lr);
            while (fit && d != RouteDensity.BARE && visibleRows > 0 && RouteDensity.rows(lr) > visibleRows) {
                d = d.zoomOut();
                lr.nodes.get(0).y = rootY;
                d.apply(lr);
                DiagramLayouts.normalizeRouteLayoutY(lr);
            }
            if (showErrors) {
                RouteErrorFrame frame = DiagramSupport.errorFrame(engine, lr, split.blocks(), r.routeId, errorPaths);
                if (frame != null) {
                    frames.put(r.routeId, frame);
                }
            }
            densities.put(lr, d);
            foldedSteps.put(lr, steps);
            answer.put(r.routeId, lr);
        }
        return answer;
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
