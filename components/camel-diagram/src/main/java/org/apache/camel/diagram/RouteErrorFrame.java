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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.TopologyErrorFrame.ErrorPath;
import org.apache.camel.diagram.TopologyErrorFrame.LineStyle;

/**
 * The error handling of one route, drawn in a frame below its own steps: lines saying where its failures go and where
 * it is reached on error from, then its onException clauses as blocks of their own (a global one is copied into every
 * route), so they are not drawn as steps of the route. The camel-jbang monitor (x in the route view) and the route
 * diagram exports draw it the same.
 *
 * @param topY    the top of the frame, in layout units
 * @param bottomY the bottom of the frame, in layout units
 * @param lines   the lines at the top of the frame
 */
public record RouteErrorFrame(int topY, int bottomY, List<String> lines) {

    /** A route split in its own steps and its onException clauses, each a block of its own. */
    public record Split(RouteInfo happy, List<RouteInfo> blocks) {
    }

    /**
     * Takes the onException clauses out of the route: they are not its steps. Each becomes a block of its own, its
     * levels starting at the clause.
     */
    public static Split split(RouteInfo route) {
        RouteInfo happy = new RouteInfo();
        happy.routeId = route.routeId;
        happy.source = route.source;
        happy.stat = route.stat;
        List<RouteInfo> blocks = new ArrayList<>();
        RouteInfo block = null;
        int blockLevel = -1;
        for (NodeInfo n : route.nodes) {
            if (block != null && n.level > blockLevel) {
                block.nodes.add(rebased(n, blockLevel));
                continue;
            }
            block = null;
            if ("onException".equals(n.type)) {
                block = new RouteInfo();
                block.routeId = route.routeId;
                block.source = route.source;
                blockLevel = n.level;
                block.nodes.add(rebased(n, blockLevel));
                blocks.add(block);
            } else {
                happy.nodes.add(n);
            }
        }
        return new Split(happy, blocks);
    }

    private static NodeInfo rebased(NodeInfo n, int base) {
        NodeInfo copy = new NodeInfo();
        copy.type = n.type;
        copy.id = n.id;
        copy.code = n.code;
        copy.uri = n.uri;
        copy.description = n.description;
        copy.level = n.level - base;
        copy.line = n.line;
        copy.remote = n.remote;
        copy.stat = n.stat;
        copy.bodyTypes = n.bodyTypes;
        copy.bodyKinds = n.bodyKinds;
        return copy;
    }

    /**
     * The lines at the top of the frame of a route: where its failures go, where it is reached on error from, and its
     * own failures coming back to it.
     */
    public static List<String> lines(String routeId, List<ErrorPath> paths, LineStyle style) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Map<String, List<String>> in = new LinkedHashMap<>();
        for (ErrorPath p : paths) {
            if (routeId.equals(p.fromRouteId())) {
                out.computeIfAbsent(p.toRouteId(), k -> new ArrayList<>()).add(p.label());
            } else if (routeId.equals(p.toRouteId())) {
                in.computeIfAbsent(p.fromRouteId(), k -> new ArrayList<>()).add(p.label());
            }
        }
        String and = style == LineStyle.ASCII ? "; " : " \u00b7 ";
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : out.entrySet()) {
            String how = String.join(and, e.getValue());
            boolean own = e.getKey().equals(routeId);
            lines.add(switch (style) {
                case UNICODE -> own ? "\u26a0 own failures: " + how : "\u25b8 on failure to " + e.getKey() + ": " + how;
                case ASCII -> own ? "! own failures: " + how : "> on failure to " + e.getKey() + ": " + how;
                case TEXT -> own ? "own failures: " + how : "on failure to " + e.getKey() + ": " + how;
            });
        }
        for (Map.Entry<String, List<String>> e : in.entrySet()) {
            String how = String.join(and, e.getValue());
            String when = "reached when " + e.getKey() + " handles a failure: " + how;
            lines.add(switch (style) {
                case UNICODE -> "\u25c2 " + when;
                case ASCII -> "< " + when;
                case TEXT -> when;
            });
        }
        return lines;
    }

    /**
     * Lays out a route as a route diagram draws it: its own steps, and its error handling in a frame below them when
     * shown, or when a highlighted path steps into one of its onException clauses.
     *
     * @param errors      whether to show the error handling of the route
     * @param highlighted the ids of the highlighted nodes, or null
     * @param paths       the error paths of the topology, for the lines of the frame
     * @param rowHeight   the height of a line of text, in layout units
     */
    public static LayoutRoute layout(
            RouteDiagramLayoutEngine engine, RouteInfo route, int startY, boolean errors, Set<String> highlighted,
            List<ErrorPath> paths, LineStyle style, int rowHeight) {
        Split split = split(route);
        LayoutRoute lr = engine.layoutRoute(split.happy(), startY);
        if (errors || steppedInto(split.blocks(), highlighted)) {
            add(engine, lr, split.blocks(), lines(route.routeId, paths, style), rowHeight);
        }
        return lr;
    }

    /** Whether a highlighted path steps into one of the onException clauses. */
    private static boolean steppedInto(List<RouteInfo> blocks, Set<String> highlighted) {
        if (highlighted == null || highlighted.isEmpty()) {
            return false;
        }
        for (RouteInfo block : blocks) {
            for (NodeInfo n : block.nodes) {
                if (n.id != null && highlighted.contains(n.id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Lays out the error handling of a route below its own steps: a frame with the lines of the route, then its
     * onException blocks under one another. Sets it on the layout of the route and returns it; null when the route has
     * no error handling to show.
     *
     * @param rowHeight the height of a line of text, in layout units
     */
    public static RouteErrorFrame add(
            RouteDiagramLayoutEngine engine, LayoutRoute lr, List<RouteInfo> blocks, List<String> lines, int rowHeight) {
        if (blocks.isEmpty() && lines.isEmpty()) {
            return null;
        }
        int top = lr.maxY + 2 * rowHeight;
        int y = top + (lines.size() + 1) * rowHeight;
        for (RouteInfo block : blocks) {
            LayoutRoute b = engine.layoutRoute(block, y);
            lr.nodes.addAll(b.nodes);
            lr.maxX = Math.max(lr.maxX, b.maxX);
            y = b.maxY + 2 * rowHeight;
        }
        RouteErrorFrame frame = new RouteErrorFrame(top, y, lines);
        lr.maxY = y + rowHeight;
        lr.errorFrame = frame;
        return frame;
    }
}
