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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;
import org.apache.camel.diagram.TopologyAsciiRenderer;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget.EipNodeBox;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The diagram the Diagram tab draws, as data an agent can read or sketch in any way it likes (tui_get_diagram): its
 * boxes, where each one is drawn (in characters, the top left box at 0,0) and how they connect.
 */
final class DiagramJson {

    private DiagramJson() {
    }

    /**
     * The boxes and links of a graph of boxes: the topology of the routes, or the architecture of their groups. Each
     * node is as tui_get_topology has it, with where it is drawn.
     */
    static JsonObject boxes(DiagramSupport support) {
        JsonObject json = support.getTopologyDataAsJson();
        if (json == null) {
            return null;
        }
        List<int[]> at = new ArrayList<>();
        Map<String, int[]> byId = new HashMap<>();
        for (TopologyAsciiRenderer.NodeBox b : support.getNodeBoxes()) {
            int[] box = { b.startRow(), b.startCol(), b.endRow() - b.startRow() + 1, b.endCol() - b.startCol() + 1 };
            at.add(box);
            byId.putIfAbsent(b.routeId(), box);
        }
        int[] origin = origin(at);
        for (Object o : (JsonArray) json.get("nodes")) {
            JsonObject n = (JsonObject) o;
            putBox(n, byId.get(n.getString("routeId")), origin);
        }
        return json;
    }

    /**
     * The steps of a route, in the order they are drawn: each with its id, type and label, the step it is in
     * ({@code parent}) and the steps in it in order ({@code children}), where it is drawn, and its numbers.
     */
    static JsonObject route(LayoutRoute route, List<EipNodeBox> drawn) {
        if (route == null) {
            return null;
        }
        List<int[]> at = new ArrayList<>();
        Map<LayoutNode, int[]> byNode = new HashMap<>();
        for (EipNodeBox b : drawn) {
            int[] box = { b.startRow(), b.startCol(), b.endRow() - b.startRow() + 1, b.endCol() - b.startCol() + 1 };
            at.add(box);
            byNode.putIfAbsent(b.layoutNode(), box);
        }
        int[] origin = origin(at);
        JsonArray nodes = new JsonArray();
        for (LayoutNode ln : route.nodes) {
            if ("route".equals(ln.type)) {
                continue;
            }
            JsonObject n = new JsonObject();
            n.put("id", ln.id);
            n.put("type", ln.type);
            String label = ln.wrappedLines != null ? String.join(" ", ln.wrappedLines).strip() : "";
            if (!label.isEmpty()) {
                n.put("label", label);
            }
            TreeNode tn = ln.treeNode;
            if (tn != null) {
                addStep(n, tn);
            }
            putBox(n, byNode.get(ln), origin);
            nodes.add(n);
        }
        JsonObject json = new JsonObject();
        json.put("routeId", route.routeId);
        if (route.source != null) {
            json.put("source", route.source);
        }
        json.put("nodes", nodes);
        json.put("totalNodes", nodes.size());
        return json;
    }

    private static void addStep(JsonObject n, TreeNode tn) {
        NodeInfo info = tn.info;
        if (tn.parent != null && tn.parent.info != null && !"route".equals(tn.parent.info.type)) {
            n.put("parent", tn.parent.info.id);
        }
        if (!tn.children.isEmpty()) {
            JsonArray children = new JsonArray();
            tn.children.forEach(c -> children.add(c.info.id));
            n.put("children", children);
        }
        if (info.uri != null) {
            n.put("uri", info.uri);
            n.put("remote", info.remote);
        }
        if (info.description != null) {
            n.put("description", info.description);
        }
        if (info.line > 0) {
            n.put("line", info.line);
        }
        if (info.stat != null) {
            n.put("exchangesTotal", info.stat.exchangesTotal);
            n.put("exchangesFailed", info.stat.exchangesFailed);
            n.put("meanProcessingTime", info.stat.meanProcessingTime);
        }
        if (!info.bodyTypes.isEmpty()) {
            n.put("bodyTypes", new JsonArray(info.bodyTypes));
        }
        if (!info.problems.isEmpty()) {
            n.put("problems", new JsonArray(info.problems));
        }
    }

    /** The top left of the drawing: the first row and column with a box. */
    private static int[] origin(List<int[]> boxes) {
        int row = Integer.MAX_VALUE;
        int col = Integer.MAX_VALUE;
        for (int[] b : boxes) {
            row = Math.min(row, b[0]);
            col = Math.min(col, b[1]);
        }
        return boxes.isEmpty() ? new int[] { 0, 0 } : new int[] { row, col };
    }

    /** Where a box is drawn: row and col from the top left box, and its size; nothing for a box not drawn (folded). */
    private static void putBox(JsonObject n, int[] box, int[] origin) {
        if (box != null) {
            n.put("row", box[0] - origin[0]);
            n.put("col", box[1] - origin[1]);
            n.put("height", box[2]);
            n.put("width", box[3]);
        }
    }
}
