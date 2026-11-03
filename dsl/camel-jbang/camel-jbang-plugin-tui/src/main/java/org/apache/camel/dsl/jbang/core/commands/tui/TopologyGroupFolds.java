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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget.NodeLine;

/**
 * The groups of routes folded in the topology (Space on a route of a group): the routes of a folded group are one box,
 * named after the group, which says how many routes are in it. The links to and from its routes go to the box, the
 * links between them are left out. The box has the count of the busiest route of the group, and the failures of all.
 */
final class TopologyGroupFolds {

    /** The id of the box of a folded group, which the topology selects as it would a route. */
    static final String PREFIX = "group:";

    private TopologyGroupFolds() {
    }

    /** The group of a folded box, or null when the id is a route's. */
    static String groupOf(String nodeId) {
        return nodeId != null && nodeId.startsWith(PREFIX) ? nodeId.substring(PREFIX.length()) : null;
    }

    /**
     * Folds the routes of the folded groups into a box each, in the nodes and edges of the topology.
     *
     * @return the lines of the boxes of the folded groups, by their ids
     */
    static Map<String, List<NodeLine>> fold(
            List<TopologyNodeInfo> nodes, List<TopologyEdgeInfo> edges, Map<String, RouteGroups.Tag> tags,
            Set<String> folded) {
        Map<String, List<NodeLine>> lines = new LinkedHashMap<>();
        if (folded.isEmpty() || tags.isEmpty()) {
            return lines;
        }
        Map<String, String> boxOf = new LinkedHashMap<>();
        Map<String, TopologyNodeInfo> boxes = new LinkedHashMap<>();
        Map<String, List<String>> routes = new LinkedHashMap<>();
        List<TopologyNodeInfo> kept = new ArrayList<>();
        Map<String, NodeLine> tagLines = RouteGroups.tagLines(tags);
        for (TopologyNodeInfo n : nodes) {
            RouteGroups.Tag tag = n.routeId != null ? tags.get(n.routeId) : null;
            if (tag == null || !folded.contains(tag.group())) {
                kept.add(n);
                continue;
            }
            String id = PREFIX + tag.group();
            boxOf.put(n.routeId, id);
            TopologyNodeInfo box = boxes.computeIfAbsent(id, k -> {
                TopologyNodeInfo b = new TopologyNodeInfo();
                b.routeId = k;
                b.description = tag.name();
                b.from = tag.name();
                b.nodeType = "route";
                kept.add(b);
                lines.put(k, new ArrayList<>(List.of(tagLines.get(n.routeId))));
                return b;
            });
            box.exchangesTotal = Math.max(box.exchangesTotal, n.exchangesTotal);
            box.exchangesFailed += n.exchangesFailed;
            routes.computeIfAbsent(id, k -> new ArrayList<>()).add(n.routeId);
        }
        routes.forEach((id, ids) -> {
            lines.get(id).add(new NodeLine(
                    ids.size() + (ids.size() == 1 ? " route" : " routes") + " folded", Theme.muted()));
            // the box draws its lines; its from names its routes, for the Info panel
            boxes.get(id).from = String.join(", ", ids);
        });
        nodes.clear();
        nodes.addAll(kept);

        List<TopologyEdgeInfo> moved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TopologyEdgeInfo e : edges) {
            String from = boxOf.getOrDefault(e.fromRouteId, e.fromRouteId);
            String to = boxOf.getOrDefault(e.toRouteId, e.toRouteId);
            if (from != null && from.equals(to) && (boxOf.containsKey(e.fromRouteId))) {
                // between the routes of the group
                continue;
            }
            if (seen.add(from + "\u0000" + to + "\u0000" + e.kind)) {
                e.fromRouteId = from;
                e.toRouteId = to;
                moved.add(e);
            }
        }
        edges.clear();
        edges.addAll(moved);
        return lines;
    }
}
