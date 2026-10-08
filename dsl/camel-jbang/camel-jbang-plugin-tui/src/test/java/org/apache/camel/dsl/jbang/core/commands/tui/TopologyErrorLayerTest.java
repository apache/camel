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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.ErrorLayer;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.ErrorLayer.ErrorPath;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25429: the error handling of the topology, in a frame of its own below the happy path (x): the routes reached
 * only on error, the error paths into them, and where each one's failures come from.
 */
class TopologyErrorLayerTest {

    // fail-well/error-handling: checkout calls payment-provider; a dead letter channel and an onException send to parked
    private final TopologyEdgeInfo charge = edge("checkout", "payment-provider", "call", null, null);
    private final List<TopologyEdgeInfo> errors = List.of(
            edge("checkout", "parked", "error", "onException", "handled"),
            edge("checkout", "parked", "error", "errorHandler", "handled"),
            edge("parked", "parked", "error", "errorHandler", "handled"));

    @Test
    void theRoutesReachedOnlyOnError() {
        assertThat(DiagramSupport.errorOnlyRoutes(List.of(charge), errors)).containsExactly("parked");
        // a route that is also called is on the happy path
        assertThat(DiagramSupport.errorOnlyRoutes(
                List.of(charge, edge("checkout", "parked", "call", null, null)), errors)).isEmpty();
    }

    @Test
    void theLabelSaysWhatHappensToTheFailure() {
        assertThat(new ErrorPath("a", "b", "errorHandler", "handled").label()).isEqualTo("dead letter, handled");
        assertThat(new ErrorPath("a", "b", "onException", "notHandled").label())
                .isEqualTo("onException, not handled, goes on to the caller");
        assertThat(new ErrorPath("a", "b", "onException", null).label()).isEqualTo("onException, decided at runtime");
    }

    @Test
    void theErrorRoutesAreDrawnInAFrameBelowTheHappyPath() {
        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult happy = engine.layout(
                new ArrayList<>(List.of(node("checkout", "file:orders"), node("payment-provider", "direct:charge"))),
                new ArrayList<>(List.of(charge)));
        List<ErrorPath> paths = errors.stream()
                .map(e -> new ErrorPath(e.fromRouteId, e.toRouteId, e.via, e.handling)).toList();
        DiagramSupport.PlacedErrors placed = DiagramSupport.placeErrorRoutes(
                happy, List.of(node("parked", "direct:parked")), paths, Set.of("parked"), engine.getNodeWidth(),
                engine.getNodeHeight());

        int happyBottom = happy.nodes.stream().mapToInt(n -> n.y + n.height).max().orElseThrow();
        assertThat(placed.layout().nodes).extracting(n -> n.routeId)
                .containsExactlyInAnyOrder("checkout", "payment-provider", "parked");
        assertThat(placed.layout().nodes).filteredOn(n -> "parked".equals(n.routeId))
                .allMatch(n -> n.y > happyBottom);
        assertThat(placed.layer().frameTopY()).isGreaterThan(happyBottom);

        String screen = render(placed.layout(), engine.getNodeWidth(), placed.layer());
        assertThat(screen).contains("Error handling");
        assertThat(screen).contains("◂ checkout: onException, handled · dead letter, handled");
        assertThat(screen).contains("⚠ own failures: dead letter, handled");
        assertThat(screen).contains("parked");
    }

    @Test
    void withoutTheLayerTheHappyPathOnly() {
        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult happy = engine.layout(
                new ArrayList<>(List.of(node("checkout", "file:orders"), node("payment-provider", "direct:charge"))),
                new ArrayList<>(List.of(charge)));

        String screen = render(happy, engine.getNodeWidth(), null);
        assertThat(screen).doesNotContain("Error handling").contains("checkout").contains("payment-provider");
    }

    @Test
    void theRouteViewSplitsTheOnExceptionClausesFromTheHappyPath() {
        RouteDiagramLayoutEngine.RouteInfo route = new RouteDiagramLayoutEngine.RouteInfo();
        route.routeId = "checkout";
        route.nodes.add(step("from", 0));
        route.nodes.add(step("onException", 1));
        route.nodes.add(step("log", 2));
        route.nodes.add(step("to", 2));
        route.nodes.add(step("unmarshal", 1));
        route.nodes.add(step("to", 1));

        DiagramSupport.RouteSplit split = DiagramSupport.splitErrorHandling(route);

        assertThat(split.happy().nodes).extracting(n -> n.type).containsExactly("from", "unmarshal", "to");
        assertThat(split.blocks()).hasSize(1);
        // the clause starts its own block
        assertThat(split.blocks().get(0).nodes).extracting(n -> n.type + n.level)
                .containsExactly("onException0", "log1", "to1");
    }

    @Test
    void theRouteViewLinesSayWhereFailuresGoAndComeFrom() {
        List<ErrorPath> paths = errors.stream()
                .map(e -> new ErrorPath(e.fromRouteId, e.toRouteId, e.via, e.handling)).toList();

        assertThat(DiagramSupport.routeErrorLines("checkout", paths))
                .containsExactly("\u25b8 on failure to parked: onException, handled \u00b7 dead letter, handled");
        assertThat(DiagramSupport.routeErrorLines("parked", paths)).containsExactly(
                "\u26a0 own failures: dead letter, handled",
                "\u25c2 reached when checkout handles a failure: onException, handled \u00b7 dead letter, handled");
        assertThat(DiagramSupport.routeErrorLines("payment-provider", paths)).isEmpty();
    }

    private static RouteDiagramLayoutEngine.NodeInfo step(String type, int level) {
        RouteDiagramLayoutEngine.NodeInfo n = new RouteDiagramLayoutEngine.NodeInfo();
        n.type = type;
        n.code = type;
        n.level = level;
        return n;
    }

    private static String render(TopologyLayoutResult layout, int nodeWidth, ErrorLayer layer) {
        TopologyDiagramWidget widget = new TopologyDiagramWidget(layout, nodeWidth, -1, 0, 0, false, false)
                .withErrorLayer(layer);
        Rect area = new Rect(0, 0, Math.max(100, widget.getTotalCols()), widget.getTotalRows());
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        return TuiTestHelper.bufferToString(buffer);
    }

    private static TopologyNodeInfo node(String routeId, String from) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = routeId;
        n.from = from;
        n.nodeType = "route";
        return n;
    }

    private static TopologyEdgeInfo edge(String from, String to, String kind, String via, String handling) {
        TopologyEdgeInfo e = new TopologyEdgeInfo();
        e.fromRouteId = from;
        e.toRouteId = to;
        e.endpoint = "direct:" + to;
        e.connectionType = "internal";
        e.kind = kind;
        e.via = via;
        e.handling = handling;
        return e;
    }
}
