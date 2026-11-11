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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26823: the head of an edge in the topology says whether the route waits for the answer of the route it sends
 * to: ⇅ there and back (a direct call, a request-reply over a broker), ▼ a hand-off.
 */
class TopologyReplyArrowTest {

    @Test
    void anEdgeTheRouteWaitsOnEndsInATwoWayHead() {
        TopologyEdgeInfo stock = edge("reserve-order", "stock-service", "seda:stock", true);
        TopologyEdgeInfo shipping = edge("reserve-order", "shipping", "seda:reserved", false);
        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult layout = engine.layout(
                new ArrayList<>(List.of(route("reserve-order"), route("stock-service"), route("shipping"))),
                new ArrayList<>(List.of(stock, shipping)));
        assertThat(layout.edges).extracting(e -> e.to.routeId + (e.reply ? " waits" : ""))
                .containsExactlyInAnyOrder("stock-service waits", "shipping");

        TopologyDiagramWidget widget = new TopologyDiagramWidget(layout, engine.getNodeWidth(), -1, 0, 0, false, false);
        Rect area = new Rect(0, 0, Math.max(100, widget.getTotalCols()), Math.max(20, widget.getTotalRows()));
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);

        assertThat(headAbove(buffer, widget, "stock-service")).isEqualTo("⇅");
        assertThat(headAbove(buffer, widget, "shipping")).isEqualTo("▼");
    }

    /** The arrow head on the row above the box of the route. */
    private static String headAbove(Buffer buffer, TopologyDiagramWidget widget, String routeId) {
        TopologyDiagramWidget.NodeBox box = widget.getNodeBoxes().stream()
                .filter(b -> routeId.equals(b.routeId())).findFirst().orElseThrow();
        StringBuilder heads = new StringBuilder();
        for (int col = box.startCol(); col <= box.endCol(); col++) {
            String symbol = buffer.get(col, box.startRow() - 1).symbol();
            if ("⇅".equals(symbol) || "▼".equals(symbol)) {
                heads.append(symbol);
            }
        }
        return heads.toString();
    }

    private static TopologyNodeInfo route(String id) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = id;
        n.from = "seda:" + id;
        n.nodeType = "route";
        return n;
    }

    private static TopologyEdgeInfo edge(String from, String to, String endpoint, boolean reply) {
        TopologyEdgeInfo e = new TopologyEdgeInfo();
        e.fromRouteId = from;
        e.toRouteId = to;
        e.endpoint = endpoint;
        e.connectionType = "internal";
        e.reply = reply;
        return e;
    }
}
