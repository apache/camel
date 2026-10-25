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
import java.util.List;
import java.util.Set;

import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.TopologyAsciiRenderer;
import org.apache.camel.diagram.TopologyErrorFrame;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;

/**
 * What {@link DiagramSupport} does to a layout before it draws it: the diagram moved up to the top, the steps a
 * highlighted step is inside highlighted too, and the steps that send to an external system marked.
 */
final class DiagramLayouts {

    private DiagramLayouts() {
    }

    static void addParentNodes(List<RouteDiagramLayoutEngine.NodeInfo> nodes, Set<String> nodeIds) {
        for (int i = 0; i < nodes.size(); i++) {
            RouteDiagramLayoutEngine.NodeInfo node = nodes.get(i);
            if (node.id == null || nodeIds.contains(node.id)) {
                continue;
            }
            boolean hasHighlightedChild = false;
            for (int j = i + 1; j < nodes.size(); j++) {
                RouteDiagramLayoutEngine.NodeInfo child = nodes.get(j);
                if (child.level <= node.level) {
                    break;
                }
                if (child.id != null && nodeIds.contains(child.id)) {
                    hasHighlightedChild = true;
                    break;
                }
            }
            if (hasHighlightedChild) {
                nodeIds.add(node.id);
            }
        }
    }

    /** Marks the steps that send to an external system of the topology as remote: the diagram draws them as an edge. */
    static void markRemote(List<RouteDiagramLayoutEngine.RouteInfo> routes, Set<String> externalUris) {
        if (externalUris.isEmpty()) {
            return;
        }
        for (RouteDiagramLayoutEngine.RouteInfo r : routes) {
            for (RouteDiagramLayoutEngine.NodeInfo ni : r.nodes) {
                if (!ni.remote) {
                    String baseUri = DiagramSupport.getBaseUri(ni);
                    if (baseUri != null && externalUris.contains(baseUri)) {
                        ni.remote = true;
                    }
                }
            }
        }
    }

    /**
     * Lays out the topology with boxes the given number of columns wide, and the routes reached only on error (when
     * any) in a frame below: no layout when there are neither.
     */
    static TopologyErrorFrame.Placed layoutTopology(
            List<TopologyNodeInfo> nodes, List<TopologyEdgeInfo> edges, List<TopologyNodeInfo> errorNodes,
            TopologyErrorFrame.Externals externals, List<TopologyErrorFrame.ErrorPath> paths, int columns) {
        TopologyLayoutEngine engine = DiagramSupport.engineFor(columns);
        TopologyLayoutResult result = null;
        if (!nodes.isEmpty()) {
            result = engine.layout(nodes, edges);
            normalizeTopologyLayoutY(result);
        }
        if (errorNodes.isEmpty()) {
            return new TopologyErrorFrame.Placed(result, null);
        }
        if (result == null) {
            result = new TopologyLayoutResult(new ArrayList<>(), new ArrayList<>(), 0, 0);
        }
        return TopologyErrorFrame.place(result, errorNodes, externals, paths, engine.getNodeWidth(),
                engine.getNodeHeight(), TopologyAsciiRenderer.ROW_HEIGHT);
    }

    static void normalizeTopologyLayoutY(TopologyLayoutResult result) {
        if (result.nodes.isEmpty()) {
            return;
        }
        int minY = Integer.MAX_VALUE;
        for (TopologyLayoutNode node : result.nodes) {
            minY = Math.min(minY, node.y);
        }
        if (minY <= 0) {
            return;
        }
        int shift = minY - 40;
        for (TopologyLayoutNode node : result.nodes) {
            node.y -= shift;
        }
    }

    static void normalizeRouteLayoutY(RouteDiagramLayoutEngine.LayoutRoute lr) {
        int minY = Integer.MAX_VALUE;
        for (RouteDiagramLayoutEngine.LayoutNode ln : lr.nodes) {
            if (!"route".equals(ln.type)) {
                minY = Math.min(minY, ln.y);
            }
        }
        if (minY <= 0 || minY == Integer.MAX_VALUE) {
            return;
        }
        int shift = minY - 40;
        for (RouteDiagramLayoutEngine.LayoutNode ln : lr.nodes) {
            ln.y -= shift;
            if (ln.connectFromMerge) {
                ln.mergeY -= shift;
            }
        }
        lr.labelY -= shift;
        lr.maxY -= shift;
    }
}
