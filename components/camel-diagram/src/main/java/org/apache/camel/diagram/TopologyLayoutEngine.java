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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Layered directed graph layout engine for route topology diagrams. Uses a simplified Sugiyama algorithm: layer
 * assignment, crossing minimization, and coordinate assignment.
 *
 * When external endpoint nodes are present (nodeType "external-in" or "external-out"), the layout uses a three-band
 * approach: consumers at top, routes in middle, producers at bottom.
 */
public class TopologyLayoutEngine {

    static final int SCALE = RouteDiagramLayoutEngine.SCALE;
    static final int V_GAP = 50 * SCALE;
    static final int H_GAP = 30 * SCALE;
    static final int BAND_GAP = 80 * SCALE;
    static final int PADDING = RouteDiagramLayoutEngine.PADDING;
    public static final int DEFAULT_NODE_WIDTH = 180;
    static final int DEFAULT_NODE_HEIGHT = 40;
    public static final int DEFAULT_FONT_SIZE = 12;

    private final int nodeWidth;
    private final int nodeHeight;

    public TopologyLayoutEngine() {
        this(DEFAULT_NODE_WIDTH);
    }

    public TopologyLayoutEngine(int nodeWidth) {
        this.nodeWidth = nodeWidth * SCALE;
        this.nodeHeight = DEFAULT_NODE_HEIGHT * SCALE;
    }

    public int getNodeWidth() {
        return nodeWidth;
    }

    public int getNodeHeight() {
        return nodeHeight;
    }

    public TopologyLayoutResult layout(List<TopologyNodeInfo> nodes, List<TopologyEdgeInfo> edges) {
        if (nodes.isEmpty()) {
            return new TopologyLayoutResult(Collections.emptyList(), Collections.emptyList(), 0, 0);
        }

        // Separate external nodes from route nodes
        List<TopologyNodeInfo> externalInNodes = new ArrayList<>();
        List<TopologyNodeInfo> externalOutNodes = new ArrayList<>();
        List<TopologyNodeInfo> routeNodes = new ArrayList<>();
        for (TopologyNodeInfo n : nodes) {
            if ("external-in".equals(n.nodeType)) {
                externalInNodes.add(n);
            } else if ("external-out".equals(n.nodeType)) {
                externalOutNodes.add(n);
            } else {
                routeNodes.add(n);
            }
        }

        Map<String, TopologyNodeInfo> nodeMap = new HashMap<>();
        for (TopologyNodeInfo n : nodes) {
            nodeMap.put(n.routeId, n);
        }

        Map<String, List<String>> successors = new HashMap<>();
        Map<String, List<String>> predecessors = new HashMap<>();
        for (TopologyNodeInfo n : nodes) {
            successors.put(n.routeId, new ArrayList<>());
            predecessors.put(n.routeId, new ArrayList<>());
        }
        for (TopologyEdgeInfo e : edges) {
            if (nodeMap.containsKey(e.fromRouteId) && nodeMap.containsKey(e.toRouteId)) {
                successors.get(e.fromRouteId).add(e.toRouteId);
                predecessors.get(e.toRouteId).add(e.fromRouteId);
            }
        }

        boolean hasExternalIn = !externalInNodes.isEmpty();
        boolean hasExternalOut = !externalOutNodes.isEmpty();

        // Layer assignment for route nodes only
        Map<String, Integer> layers = assignRouteLayers(routeNodes, successors, predecessors);

        // Shift route layers to make room for external-in band
        if (hasExternalIn) {
            for (Map.Entry<String, Integer> entry : layers.entrySet()) {
                entry.setValue(entry.getValue() + 1);
            }
        }

        // Place external-in nodes at layer 0
        for (TopologyNodeInfo n : externalInNodes) {
            layers.put(n.routeId, 0);
        }

        // Place external-out nodes at max route layer + 1
        int maxRouteLayer = layers.values().stream()
                .filter(l -> !externalOutNodes.stream().anyMatch(n -> layers.getOrDefault(n.routeId, -1).equals(l)))
                .mapToInt(Integer::intValue).max().orElse(0);
        int outLayer = maxRouteLayer + 1;
        for (TopologyNodeInfo n : externalOutNodes) {
            layers.put(n.routeId, outLayer);
        }

        // Group nodes by layer
        int maxLayer = layers.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<List<String>> layerGroups = new ArrayList<>();
        for (int i = 0; i <= maxLayer; i++) {
            layerGroups.add(new ArrayList<>());
        }
        for (TopologyNodeInfo n : nodes) {
            int layer = layers.getOrDefault(n.routeId, 0);
            layerGroups.get(layer).add(n.routeId);
        }

        // Minimize crossings (barycenter heuristic)
        minimizeCrossings(layerGroups, successors, predecessors);

        // Assign coordinates with extra gap between bands
        int externalInLayer = hasExternalIn ? 0 : -1;
        int externalOutLayer = hasExternalOut ? outLayer : -1;
        Map<String, TopologyLayoutNode> layoutNodes
                = assignCoordinates(layerGroups, nodeMap, externalInLayer, externalOutLayer);

        // Build layout edges
        List<TopologyLayoutEdge> layoutEdges = new ArrayList<>();
        for (TopologyEdgeInfo e : edges) {
            TopologyLayoutNode from = layoutNodes.get(e.fromRouteId);
            TopologyLayoutNode to = layoutNodes.get(e.toRouteId);
            if (from != null && to != null) {
                boolean backEdge = layers.getOrDefault(e.fromRouteId, 0) >= layers.getOrDefault(e.toRouteId, 0)
                        && !e.fromRouteId.equals(e.toRouteId);
                boolean selfLoop = e.fromRouteId.equals(e.toRouteId);
                layoutEdges.add(new TopologyLayoutEdge(from, to, e.endpoint, e.connectionType, backEdge, selfLoop));
            }
        }

        int totalWidth = layoutNodes.values().stream().mapToInt(n -> n.x + nodeWidth).max().orElse(0) + PADDING;
        int totalHeight = layoutNodes.values().stream().mapToInt(n -> n.y + n.height).max().orElse(0) + PADDING;

        return new TopologyLayoutResult(
                new ArrayList<>(layoutNodes.values()), layoutEdges, totalWidth, totalHeight);
    }

    private Map<String, Integer> assignRouteLayers(
            List<TopologyNodeInfo> routeNodes,
            Map<String, List<String>> successors,
            Map<String, List<String>> predecessors) {

        Map<String, Integer> layers = new HashMap<>();
        Set<String> routeIds = new HashSet<>();
        for (TopologyNodeInfo n : routeNodes) {
            routeIds.add(n.routeId);
        }

        // Triggers and nodes with no route predecessors go to layer 0
        Set<String> assigned = new HashSet<>();
        for (TopologyNodeInfo n : routeNodes) {
            boolean hasRoutePredecessor = predecessors.get(n.routeId).stream().anyMatch(routeIds::contains);
            if ("trigger".equals(n.nodeType) || !hasRoutePredecessor) {
                layers.put(n.routeId, 0);
                assigned.add(n.routeId);
            }
        }

        // If nothing assigned (all cycles), pick first node
        if (assigned.isEmpty() && !routeNodes.isEmpty()) {
            layers.put(routeNodes.get(0).routeId, 0);
            assigned.add(routeNodes.get(0).routeId);
        }

        // BFS-style layer assignment (only follow edges to other route nodes)
        boolean changed = true;
        while (changed) {
            changed = false;
            for (TopologyNodeInfo n : routeNodes) {
                if (assigned.contains(n.routeId)) {
                    for (String succ : successors.get(n.routeId)) {
                        if (succ.equals(n.routeId) || !routeIds.contains(succ)) {
                            continue;
                        }
                        int newLayer = layers.get(n.routeId) + 1;
                        if (!assigned.contains(succ) || layers.get(succ) < newLayer) {
                            layers.put(succ, newLayer);
                            assigned.add(succ);
                            changed = true;
                        }
                    }
                }
            }
        }

        // Handle any unassigned route nodes (isolated or in pure cycles)
        for (TopologyNodeInfo n : routeNodes) {
            layers.putIfAbsent(n.routeId, 0);
        }

        return layers;
    }

    private void minimizeCrossings(
            List<List<String>> layerGroups,
            Map<String, List<String>> successors,
            Map<String, List<String>> predecessors) {

        // Barycenter heuristic: order nodes by average position of neighbors in adjacent layer
        for (int pass = 0; pass < 4; pass++) {
            // Forward pass
            for (int i = 1; i < layerGroups.size(); i++) {
                orderByBarycenter(layerGroups.get(i), layerGroups.get(i - 1), predecessors);
            }
            // Backward pass
            for (int i = layerGroups.size() - 2; i >= 0; i--) {
                orderByBarycenter(layerGroups.get(i), layerGroups.get(i + 1), successors);
            }
        }
    }

    private void orderByBarycenter(
            List<String> layer, List<String> referenceLayer,
            Map<String, List<String>> neighbors) {

        Map<String, Integer> refPositions = new HashMap<>();
        for (int i = 0; i < referenceLayer.size(); i++) {
            refPositions.put(referenceLayer.get(i), i);
        }

        Map<String, Double> barycenters = new HashMap<>();
        for (String nodeId : layer) {
            List<String> connected = neighbors.get(nodeId);
            if (connected == null || connected.isEmpty()) {
                barycenters.put(nodeId, Double.MAX_VALUE);
                continue;
            }
            double sum = 0;
            int count = 0;
            for (String neighbor : connected) {
                Integer pos = refPositions.get(neighbor);
                if (pos != null) {
                    sum += pos;
                    count++;
                }
            }
            barycenters.put(nodeId, count > 0 ? sum / count : Double.MAX_VALUE);
        }

        layer.sort((a, b) -> Double.compare(barycenters.get(a), barycenters.get(b)));
    }

    private Map<String, TopologyLayoutNode> assignCoordinates(
            List<List<String>> layerGroups,
            Map<String, TopologyNodeInfo> nodeMap,
            int externalInLayer,
            int externalOutLayer) {

        Map<String, TopologyLayoutNode> layoutNodes = new HashMap<>();

        // Find widest layer for centering
        int maxLayerWidth = 0;
        for (List<String> layer : layerGroups) {
            int width = layer.size() * (nodeWidth + H_GAP) - H_GAP;
            maxLayerWidth = Math.max(maxLayerWidth, width);
        }

        int cumulativeY = PADDING;
        for (int layerIdx = 0; layerIdx < layerGroups.size(); layerIdx++) {
            List<String> layer = layerGroups.get(layerIdx);
            int layerWidth = layer.size() * (nodeWidth + H_GAP) - H_GAP;
            int startX = PADDING + (maxLayerWidth - layerWidth) / 2;

            for (int i = 0; i < layer.size(); i++) {
                String routeId = layer.get(i);
                TopologyNodeInfo info = nodeMap.get(routeId);
                int x = startX + i * (nodeWidth + H_GAP);
                TopologyLayoutNode ln = new TopologyLayoutNode(
                        routeId, info.description, info.from, info.nodeType, info.connectionType,
                        x, cumulativeY, nodeWidth, nodeHeight, layerIdx);
                ln.exchangesTotal = info.exchangesTotal;
                ln.exchangesFailed = info.exchangesFailed;
                layoutNodes.put(routeId, ln);
            }

            // Add vertical gap; use larger gap at band boundaries
            int gap = V_GAP;
            if (layerIdx == externalInLayer || (externalOutLayer >= 0 && layerIdx == externalOutLayer - 1)) {
                gap = BAND_GAP;
            }
            cumulativeY += nodeHeight + gap;
        }

        return layoutNodes;
    }

    // Input data structures
    public static class TopologyNodeInfo {
        public String routeId;
        public String description;
        public String from;
        public String fromScheme;
        public String nodeType;
        public String connectionType;
        public long exchangesTotal;
        public long exchangesFailed;
    }

    public static class TopologyEdgeInfo {
        public String fromRouteId;
        public String toRouteId;
        public String endpoint;
        public String connectionType;
    }

    // Layout output
    public static class TopologyLayoutNode {
        public final String routeId;
        public final String description;
        public final String from;
        public final String nodeType;
        public final String connectionType;
        public int x;
        public int y;
        public int width;
        public int height;
        public final int layer;
        public long exchangesTotal;
        public long exchangesFailed;

        public TopologyLayoutNode(
                                  String routeId, String description, String from, String nodeType, String connectionType,
                                  int x, int y, int width, int height, int layer) {
            this.routeId = routeId;
            this.description = description;
            this.from = from;
            this.nodeType = nodeType;
            this.connectionType = connectionType;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.layer = layer;
        }
    }

    public static class TopologyLayoutEdge {
        public final TopologyLayoutNode from;
        public final TopologyLayoutNode to;
        public final String endpoint;
        public final String connectionType;
        public final boolean backEdge;
        public final boolean selfLoop;

        public TopologyLayoutEdge(
                                  TopologyLayoutNode from, TopologyLayoutNode to,
                                  String endpoint, String connectionType,
                                  boolean backEdge, boolean selfLoop) {
            this.from = from;
            this.to = to;
            this.endpoint = endpoint;
            this.connectionType = connectionType;
            this.backEdge = backEdge;
            this.selfLoop = selfLoop;
        }
    }

    public static class TopologyLayoutResult {
        public final List<TopologyLayoutNode> nodes;
        public final List<TopologyLayoutEdge> edges;
        public final int totalWidth;
        public final int totalHeight;

        public TopologyLayoutResult(
                                    List<TopologyLayoutNode> nodes, List<TopologyLayoutEdge> edges,
                                    int totalWidth, int totalHeight) {
            this.nodes = nodes;
            this.edges = edges;
            this.totalWidth = totalWidth;
            this.totalHeight = totalHeight;
        }
    }

}
