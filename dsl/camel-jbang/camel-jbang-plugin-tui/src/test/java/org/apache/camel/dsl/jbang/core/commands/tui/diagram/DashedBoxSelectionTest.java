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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The box of a remote system is drawn dashed, in the route diagram as in the topology, but a selected box has a solid
 * frame like any other: dashes on the selection colour break up.
 */
class DashedBoxSelectionTest {

    @Test
    void aRemoteEndpointInTheRouteDiagramIsSolidWhileSelected() {
        RouteDiagramLayoutEngine.RouteInfo route = new RouteDiagramLayoutEngine.RouteInfo();
        route.routeId = "checkout";
        route.nodes.add(node("from", "checkout", "from[file:orders]", "file:orders", 0, true));
        route.nodes.add(node("to", "to1", "to[direct:charge]", "direct:charge", 1, false));
        RouteDiagramLayoutEngine.LayoutRoute layout = new RouteDiagramLayoutEngine().layoutRoute(route, 0);
        int nodeWidth = RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE;

        // nothing selected: the file box is dashed, the box of the local step solid
        RouteDiagramWidget widget = new RouteDiagramWidget(layout, nodeWidth, -1, 0, 0, false, Map.of(), false, Map.of());
        Buffer buffer = render(widget.getTotalCols(), widget.getTotalRows(), (area, b) -> widget.render(area, b));
        RouteDiagramWidget.EipNodeBox file = widget.getNodeBoxes().get(0);
        RouteDiagramWidget.EipNodeBox to = widget.getNodeBoxes().get(1);
        assertThat(frame(buffer, file.startRow(), file.endRow(), file.startCol(), file.endCol())).isEqualTo("┆┄");
        assertThat(frame(buffer, to.startRow(), to.endRow(), to.startCol(), to.endCol())).isEqualTo("│─");

        // the file box selected: solid
        RouteDiagramWidget selected = new RouteDiagramWidget(layout, nodeWidth, 0, 0, 0, false, Map.of(), false, Map.of());
        buffer = render(selected.getTotalCols(), selected.getTotalRows(), (area, b) -> selected.render(area, b));
        file = selected.getNodeBoxes().get(0);
        assertThat(frame(buffer, file.startRow(), file.endRow(), file.startCol(), file.endCol())).isEqualTo("│─");
    }

    @Test
    void anExternalSystemInTheTopologyIsSolidWhileSelected() {
        TopologyNodeInfo checkout = new TopologyNodeInfo();
        checkout.routeId = "checkout";
        checkout.from = "file:orders";
        checkout.nodeType = "route";
        TopologyNodeInfo files = new TopologyNodeInfo();
        files.routeId = "in-checkout-0";
        files.from = "file:orders";
        files.nodeType = "external-in";
        TopologyEdgeInfo reads = new TopologyEdgeInfo();
        reads.fromRouteId = "in-checkout-0";
        reads.toRouteId = "checkout";
        reads.endpoint = "file:orders";
        reads.connectionType = "external";
        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult layout = engine.layout(
                new ArrayList<>(List.of(checkout, files)), new ArrayList<>(List.of(reads)));

        TopologyDiagramWidget widget = new TopologyDiagramWidget(layout, engine.getNodeWidth(), -1, 0, 0, false, false);
        Buffer buffer = render(widget.getTotalCols(), widget.getTotalRows(), (area, b) -> widget.render(area, b));
        int index = indexOf(widget.getNodeBoxes(), "in-checkout-0");
        TopologyDiagramWidget.NodeBox box = widget.getNodeBoxes().get(index);
        assertThat(frame(buffer, box.startRow(), box.endRow(), box.startCol(), box.endCol())).isEqualTo("┆┄");

        TopologyDiagramWidget selected
                = new TopologyDiagramWidget(layout, engine.getNodeWidth(), index, 0, 0, false, false);
        buffer = render(selected.getTotalCols(), selected.getTotalRows(), (area, b) -> selected.render(area, b));
        box = selected.getNodeBoxes().get(index);
        assertThat(frame(buffer, box.startRow(), box.endRow(), box.startCol(), box.endCol())).isEqualTo("│─");
    }

    private interface Renderer {
        void render(Rect area, Buffer buffer);
    }

    private static Buffer render(int cols, int rows, Renderer renderer) {
        Rect area = new Rect(0, 0, Math.max(100, cols), Math.max(20, rows));
        Buffer buffer = Buffer.empty(area);
        renderer.render(area, buffer);
        return buffer;
    }

    /** The left side of a box below its top corner, then the top side right of its corner. */
    private static String frame(Buffer buffer, int top, int bottom, int left, int right) {
        assertThat(bottom).isGreaterThan(top + 1);
        assertThat(right).isGreaterThan(left + 1);
        return buffer.get(left, top + 1).symbol() + buffer.get(right - 1, top).symbol();
    }

    private static int indexOf(List<TopologyDiagramWidget.NodeBox> boxes, String routeId) {
        for (int i = 0; i < boxes.size(); i++) {
            if (routeId.equals(boxes.get(i).routeId())) {
                return i;
            }
        }
        throw new AssertionError("no box for " + routeId + " in " + boxes);
    }

    private static RouteDiagramLayoutEngine.NodeInfo node(
            String type, String id, String code, String uri, int level, boolean remote) {
        RouteDiagramLayoutEngine.NodeInfo n = new RouteDiagramLayoutEngine.NodeInfo();
        n.type = type;
        n.id = id;
        n.code = code;
        n.uri = uri;
        n.level = level;
        n.remote = remote;
        return n;
    }
}
