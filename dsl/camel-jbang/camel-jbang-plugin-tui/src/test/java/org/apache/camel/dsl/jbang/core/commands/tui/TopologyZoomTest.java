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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDensity;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyZoom;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The topology zooms as a route does: compact boxes of one line with the count in the border, bare routes a mark and a
 * name on a row, the layers closer together and the boxes narrower.
 */
class TopologyZoomTest {

    private static TopologyNodeInfo route(String id, long total) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = id;
        n.from = "direct:" + id;
        n.nodeType = "route";
        n.exchangesTotal = total;
        return n;
    }

    private static TopologyEdgeInfo call(String from, String to) {
        TopologyEdgeInfo e = new TopologyEdgeInfo();
        e.fromRouteId = from;
        e.toRouteId = to;
        e.endpoint = "direct:" + to;
        return e;
    }

    private record Laid(TopologyLayoutResult layout, int nodeWidth) {
    }

    /** orders calls billing, shipping and notify; shipping calls tracking. */
    private static Laid layout(RouteDensity density) {
        List<TopologyNodeInfo> nodes = new ArrayList<>(
                List.of(route("orders", 42), route("billing", 40),
                        route("shipping", 41), route("notify", 42), route("tracking", 41)));
        List<TopologyEdgeInfo> edges = new ArrayList<>(
                List.of(call("orders", "billing"), call("orders", "shipping"),
                        call("orders", "notify"), call("shipping", "tracking")));
        TopologyLayoutEngine engine = DiagramSupport.engineFor(TopologyZoom.columns(density, 24));
        TopologyLayoutResult result = engine.layout(nodes, edges);
        DiagramLayouts.normalizeTopologyLayoutY(result);
        return new Laid(TopologyZoom.apply(density, result, null).layout(), engine.getNodeWidth());
    }

    private static String render(RouteDensity density) {
        Laid laid = layout(density);
        TopologyDiagramWidget widget = new TopologyDiagramWidget(laid.layout(), laid.nodeWidth(), -1, 0, 0, true, false)
                .withDensity(density);
        Rect area = new Rect(0, 0, 140, 40);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        return TuiTestHelper.bufferToString(buffer);
    }

    private static int y(TopologyLayoutResult layout, String routeId) {
        return layout.nodes.stream().filter(n -> routeId.equals(n.routeId)).mapToInt(n -> n.y).findFirst().orElseThrow();
    }

    @Test
    void theLayersComeCloserTogetherAndTheBoxesNarrower() {
        TopologyLayoutResult detailed = layout(RouteDensity.DETAILED).layout();
        TopologyLayoutResult compact = layout(RouteDensity.COMPACT).layout();
        TopologyLayoutResult bare = layout(RouteDensity.BARE).layout();

        assertThat(compact.totalHeight).isLessThan(detailed.totalHeight);
        assertThat(bare.totalHeight).isLessThan(compact.totalHeight);
        assertThat(bare.totalWidth).isLessThan(detailed.totalWidth);
        // the layers keep their order, each on a row of its own
        for (TopologyLayoutResult l : List.of(compact, bare)) {
            assertThat(y(l, "orders")).isLessThan(y(l, "billing"));
            assertThat(y(l, "billing")).isEqualTo(y(l, "shipping"));
            assertThat(y(l, "shipping")).isLessThan(y(l, "tracking"));
            assertThat(l.nodes).allMatch(n -> n.y % RouteDensity.ROW == 0);
        }
    }

    @Test
    void aCompactRouteIsABoxOfOneLineWithItsCountInTheBorder() {
        String screen = render(RouteDensity.COMPACT);
        assertThat(screen).contains("orders").contains("tracking").contains("▼");
        // the count in the bottom border, not on a line of its own
        assertThat(screen.lines()).anyMatch(l -> l.contains("─ 42 ─┘"));
        assertThat(screen).doesNotContain("(direct:orders)");
    }

    @Test
    void aBareRouteIsAMarkItsNameAndItsCount() {
        String screen = render(RouteDensity.BARE);
        assertThat(screen).contains("● orders 42").contains("● tracking 41").doesNotContain("┌──");
    }

    @Test
    void fitsTheScreen() {
        Laid compact = layout(RouteDensity.COMPACT);
        assertThat(TopologyZoom.fits(compact.layout(), compact.nodeWidth(), 100, 300)).isTrue();
        assertThat(TopologyZoom.fits(compact.layout(), compact.nodeWidth(), 3, 300)).isFalse();
        assertThat(TopologyZoom.fits(compact.layout(), compact.nodeWidth(), 100, 20)).isFalse();
    }
}
