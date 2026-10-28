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
package org.apache.camel.diagram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutEdge;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;

/**
 * The error handling of a route topology, drawn in a frame of its own below the routes: the routes reached only when a
 * failure is handled (such as the target of a dead letter channel), and under each one where its failures come from and
 * how they are handled. There are no arrows into the frame: with a global error handler every route would have one. The
 * camel-jbang monitor, the diagram exports and the developer console draw it the same.
 *
 * @param topY     where the frame starts, in layout units (below the lowest route of the happy path)
 * @param routeIds the routes in the frame: reached only on error
 * @param paths    the error paths, into the routes of the frame and between routes of the happy path
 */
public record TopologyErrorFrame(int topY, Set<String> routeIds, List<ErrorPath> paths) {

    /** The title of the frame. */
    public static final String TITLE = "Error handling";

    /**
     * An error path: the route sends to the target only when it handles a failure.
     *
     * @param via      what sends: errorHandler (a dead letter channel) or onException
     * @param handling what happens to the failure: handled, continued, notHandled, or null when a predicate decides
     */
    public record ErrorPath(String fromRouteId, String toRouteId, String via, String handling) {

        /** How the path reads in the frame: "dead letter, handled", "onException, not handled, ..." */
        public String label() {
            String how = "errorHandler".equals(via) ? "dead letter" : via;
            String what = handling == null
                    ? "decided at runtime"
                    : switch (handling) {
                        case "handled" -> "handled";
                        case "continued" -> "continued";
                        case "notHandled" -> "not handled, goes on to the caller";
                        default -> handling;
                    };
            return how + ", " + what;
        }
    }

    /** How the lines under a route of the frame are marked. */
    public enum LineStyle {
        /** For a terminal that draws Unicode: \u25c2 from a route, \u26a0 its own failures. */
        UNICODE,
        /** Plain ASCII: &lt; from a route, ! its own failures. */
        ASCII,
        /** For an image: in words. */
        TEXT
    }

    /** The routes reached only on error: a target of an error path that no route calls. */
    public static Set<String> errorOnlyRoutes(List<TopologyEdgeInfo> calls, List<TopologyEdgeInfo> errors) {
        Set<String> called = new HashSet<>();
        for (TopologyEdgeInfo e : calls) {
            called.add(e.toRouteId);
        }
        Set<String> answer = new LinkedHashSet<>();
        for (TopologyEdgeInfo e : errors) {
            if (e.toRouteId != null && !called.contains(e.toRouteId)) {
                answer.add(e.toRouteId);
            }
        }
        return answer;
    }

    /** The error paths of the error edges, once each. */
    public static List<ErrorPath> paths(List<TopologyEdgeInfo> errors) {
        return errors.stream().map(e -> new ErrorPath(e.fromRouteId, e.toRouteId, e.via, e.handling)).distinct()
                .toList();
    }

    /**
     * The lines under a route of the frame: where its failures come from and how they are handled, one line per route
     * that sends there; its own failures coming back to it are on a line of their own.
     */
    public List<String> lines(String routeId, LineStyle style) {
        Map<String, List<String>> bySource = new LinkedHashMap<>();
        for (ErrorPath p : paths) {
            if (routeId.equals(p.toRouteId())) {
                bySource.computeIfAbsent(p.fromRouteId(), k -> new ArrayList<>()).add(p.label());
            }
        }
        String and = style == LineStyle.ASCII ? "; " : " \u00b7 ";
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : bySource.entrySet()) {
            String how = String.join(and, e.getValue());
            boolean own = e.getKey().equals(routeId);
            lines.add(switch (style) {
                case UNICODE -> (own ? "\u26a0 own failures: " : "\u25c2 " + e.getKey() + ": ") + how;
                case ASCII -> (own ? "! own failures: " : "< " + e.getKey() + ": ") + how;
                case TEXT -> (own ? "own failures: " : "from " + e.getKey() + ": ") + how;
            });
        }
        return lines;
    }

    /** The most lines under any route of the frame. */
    public int maxLines() {
        int answer = 0;
        for (String id : routeIds) {
            answer = Math.max(answer, (int) paths.stream().filter(p -> id.equals(p.toRouteId()))
                    .map(ErrorPath::fromRouteId).distinct().count());
        }
        return answer;
    }

    /** A layout with the routes of the frame in it, and the frame. */
    public record Placed(TopologyLayoutResult layout, TopologyErrorFrame frame) {
    }

    /**
     * The external systems only the routes in the frame use (such as where a dead letter channel parks the failed
     * messages), each under the first route that uses it, and the links to them.
     */
    public record Externals(Map<String, List<TopologyNodeInfo>> byRoute, List<TopologyEdgeInfo> links) {

        /** No external systems in the frame. */
        public static final Externals NONE = new Externals(Map.of(), List.of());
    }

    /**
     * The external systems only the routes in the frame link to: they go into the frame, below their route.
     *
     * @param nodes     the routes and the external systems
     * @param calls     the links that are calls, and to the external systems
     * @param errorOnly the routes in the frame
     */
    public static Externals externals(List<TopologyNodeInfo> nodes, List<TopologyEdgeInfo> calls, Set<String> errorOnly) {
        Set<String> linked = new HashSet<>();
        for (TopologyEdgeInfo e : calls) {
            if (!errorOnly.contains(e.fromRouteId) && !errorOnly.contains(e.toRouteId)) {
                linked.add(e.fromRouteId);
                linked.add(e.toRouteId);
            }
        }
        Map<String, TopologyNodeInfo> byId = new HashMap<>();
        for (TopologyNodeInfo n : nodes) {
            byId.put(n.routeId, n);
        }
        Map<String, List<TopologyNodeInfo>> byRoute = new LinkedHashMap<>();
        Set<String> placed = new HashSet<>();
        List<TopologyEdgeInfo> links = new ArrayList<>();
        for (TopologyEdgeInfo e : calls) {
            TopologyNodeInfo target = byId.get(e.toRouteId);
            if (errorOnly.contains(e.fromRouteId) && target != null && isExternal(target)
                    && !linked.contains(e.toRouteId)) {
                if (placed.add(target.routeId)) {
                    byRoute.computeIfAbsent(e.fromRouteId, k -> new ArrayList<>()).add(target);
                }
                links.add(e);
            }
        }
        return new Externals(byRoute, links);
    }

    /** The routes reached only on error and the external systems only they use, taken out of a topology. */
    public record Taken(List<TopologyNodeInfo> routes, Externals externals) {
    }

    /**
     * Takes the routes reached only on error, and the external systems only they use, out of a topology that is laid
     * out on its own, for {@link #place}: they are removed from the nodes, and their links from the calls.
     *
     * @param nodes     the routes and the external systems
     * @param calls     the links that are calls, and to the external systems
     * @param errorOnly the routes in the frame ({@link #errorOnlyRoutes})
     */
    public static Taken take(List<TopologyNodeInfo> nodes, List<TopologyEdgeInfo> calls, Set<String> errorOnly) {
        List<TopologyNodeInfo> routes = nodes.stream().filter(n -> n.routeId != null && errorOnly.contains(n.routeId))
                .toList();
        Externals externals = externals(nodes, calls, errorOnly);
        Set<String> taken = new HashSet<>(errorOnly);
        externals.byRoute().values().forEach(list -> list.forEach(n -> taken.add(n.routeId)));
        nodes.removeIf(n -> n.routeId != null && taken.contains(n.routeId));
        calls.removeIf(e -> taken.contains(e.fromRouteId) || taken.contains(e.toRouteId));
        return new Taken(routes, externals);
    }

    private static boolean isExternal(TopologyNodeInfo n) {
        return n.nodeType != null && n.nodeType.startsWith("external");
    }

    /**
     * Places the routes reached only on error in a row below the happy path, under the title of the frame, with room
     * below them for their lines.
     *
     * @param rowHeight the height of a line of text, in layout units
     */
    public static Placed place(
            TopologyLayoutResult happy, List<TopologyNodeInfo> errorNodes, List<ErrorPath> paths, int nodeW, int nodeH,
            int rowHeight) {
        return place(happy, errorNodes, Externals.NONE, paths, nodeW, nodeH, rowHeight);
    }

    /**
     * Places the routes reached only on error in a row below the happy path, under the title of the frame, with room
     * below them for their lines, and the external systems only they use below that, under their route.
     *
     * @param externals the external systems of the frame ({@link #externals})
     * @param rowHeight the height of a line of text, in layout units
     */
    public static Placed place(
            TopologyLayoutResult happy, List<TopologyNodeInfo> errorNodes, Externals externals, List<ErrorPath> paths,
            int nodeW, int nodeH, int rowHeight) {
        if (errorNodes.isEmpty()) {
            // no route is reached only on error: no frame
            return new Placed(happy, new TopologyErrorFrame(happy.totalHeight, Set.of(), paths));
        }
        int bottom = 0;
        int minX = Integer.MAX_VALUE;
        int maxLayer = -1;
        for (TopologyLayoutNode n : happy.nodes) {
            bottom = Math.max(bottom, n.y + n.height);
            minX = Math.min(minX, n.x);
            maxLayer = Math.max(maxLayer, n.layer);
        }
        if (minX == Integer.MAX_VALUE) {
            minX = 0;
        }
        int frameTopY = happy.nodes.isEmpty() ? 2 * rowHeight : bottom + 3 * rowHeight;
        // the frame title, then the routes
        int boxesY = frameTopY + 2 * rowHeight;
        List<TopologyLayoutNode> nodes = new ArrayList<>(happy.nodes);
        Map<String, TopologyLayoutNode> placed = new HashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        int x = minX;
        int step = nodeW + nodeW / 2;
        for (TopologyNodeInfo info : errorNodes) {
            TopologyLayoutNode ln = layoutNode(info, x, boxesY, nodeW, nodeH, maxLayer + 1);
            nodes.add(ln);
            placed.put(info.routeId, ln);
            ids.add(info.routeId);
            x += step;
        }
        TopologyErrorFrame frame = new TopologyErrorFrame(frameTopY, ids, paths);
        int frameBottom = boxesY + nodeH + (frame.maxLines() + 3) * rowHeight;
        // the external systems below the lines of their route, the arrow from the route passing them
        int externalsY = boxesY + nodeH + (frame.maxLines() + 3) * rowHeight;
        for (Map.Entry<String, List<TopologyNodeInfo>> e : externals.byRoute().entrySet()) {
            TopologyLayoutNode route = placed.get(e.getKey());
            if (route == null) {
                continue;
            }
            int y = externalsY;
            for (TopologyNodeInfo info : e.getValue()) {
                TopologyLayoutNode ln = layoutNode(info, route.x, y, nodeW, nodeH, maxLayer + 2);
                nodes.add(ln);
                placed.put(info.routeId, ln);
                y += nodeH + 2 * rowHeight;
                frameBottom = Math.max(frameBottom, ln.y + nodeH + rowHeight);
            }
        }
        List<TopologyLayoutEdge> edges = new ArrayList<>(happy.edges);
        for (TopologyEdgeInfo e : externals.links()) {
            TopologyLayoutNode from = placed.get(e.fromRouteId);
            TopologyLayoutNode to = placed.get(e.toRouteId);
            if (from != null && to != null) {
                edges.add(new TopologyLayoutEdge(from, to, e.endpoint, e.connectionType, false, false));
            }
        }
        int totalHeight = Math.max(happy.totalHeight, frameBottom);
        int totalWidth = Math.max(happy.totalWidth, x);
        return new Placed(new TopologyLayoutResult(nodes, edges, totalWidth, totalHeight), frame);
    }

    private static TopologyLayoutNode layoutNode(TopologyNodeInfo info, int x, int y, int nodeW, int nodeH, int layer) {
        TopologyLayoutNode ln = new TopologyLayoutNode(
                info.routeId, info.description, info.from, info.nodeType, info.connectionType, x, y, nodeW, nodeH, layer);
        ln.exchangesTotal = info.exchangesTotal;
        ln.exchangesFailed = info.exchangesFailed;
        return ln;
    }

    /**
     * Lays out a topology with its error handling shown: the routes reached only on error go into the frame, with the
     * external systems only they use, and the other routes are laid out as usual above it.
     *
     * @param nodes     the routes, and the external systems when shown
     * @param calls     the links that are calls (and to the external systems)
     * @param errors    the error paths
     * @param rowHeight the height of a line of text, in layout units
     */
    public static Placed layout(
            TopologyLayoutEngine engine, List<TopologyNodeInfo> nodes, List<TopologyEdgeInfo> calls,
            List<TopologyEdgeInfo> errors, int rowHeight) {
        Set<String> errorOnly = errorOnlyRoutes(calls, errors);
        List<TopologyNodeInfo> errorNodes = nodes.stream().filter(n -> errorOnly.contains(n.routeId)).toList();
        List<TopologyEdgeInfo> edges = calls.stream()
                .filter(e -> !errorOnly.contains(e.fromRouteId) && !errorOnly.contains(e.toRouteId)).toList();
        Set<String> linked = new HashSet<>();
        for (TopologyEdgeInfo e : edges) {
            linked.add(e.fromRouteId);
            linked.add(e.toRouteId);
        }
        List<TopologyNodeInfo> happy = nodes.stream()
                .filter(n -> !errorOnly.contains(n.routeId))
                .filter(n -> n.nodeType == null || !n.nodeType.startsWith("external") || linked.contains(n.routeId))
                .toList();
        TopologyLayoutResult layout = engine.layout(happy, edges);
        return place(layout, errorNodes, externals(nodes, calls, errorOnly), paths(errors), engine.getNodeWidth(),
                engine.getNodeHeight(), rowHeight);
    }
}
