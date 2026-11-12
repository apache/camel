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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26864: tui_get_diagram returned "No diagram available" since the text diagram was removed. It returns the
 * diagram the Diagram tab draws as JSON: the boxes, where each is drawn, and how they connect.
 */
class DiagramJsonTest {

    @Test
    void theTopologyHasItsBoxesAndWhereTheyAreDrawn() {
        DiagramTab tab = tab();
        TopologyEdgeInfo edge = new TopologyEdgeInfo();
        edge.fromRouteId = "hello";
        edge.toRouteId = "seven";
        edge.endpoint = "direct://seven";
        tab.diagram.showCustomTopology(List.of(node("hello"), node("seven")), List.of(edge), Map.of(), null);

        JsonObject json = tab.getDiagramAsJson();

        assertThat(json.getString("level")).isEqualTo("topology");
        JsonArray nodes = (JsonArray) json.get("nodes");
        assertThat(nodes).hasSize(2);
        JsonObject hello = (JsonObject) nodes.stream().filter(n -> "hello".equals(((JsonObject) n).getString("routeId")))
                .findFirst().orElseThrow();
        JsonObject seven = (JsonObject) nodes.stream().filter(n -> "seven".equals(((JsonObject) n).getString("routeId")))
                .findFirst().orElseThrow();
        assertThat(hello.getInteger("width")).isPositive();
        assertThat(hello.getInteger("height")).isPositive();
        assertThat(seven.getInteger("row")).as("seven is drawn below hello, which sends to it")
                .isGreaterThan(hello.getInteger("row"));
        JsonObject e = (JsonObject) ((JsonArray) json.get("edges")).get(0);
        assertThat(e.getString("from")).isEqualTo("hello");
        assertThat(e.getString("to")).isEqualTo("seven");
    }

    @Test
    void aRouteHasItsStepsInOrderWithTheirBranches() {
        RouteInfo route = new RouteInfo();
        route.routeId = "hello";
        route.nodes.add(step("route", "hello", 0));
        route.nodes.add(step("from", "hello", 0));
        route.nodes.add(step("setBody", "setBody1", 1));
        route.nodes.add(step("choice", "choice1", 1));
        route.nodes.add(step("when", "when1", 2));
        route.nodes.add(step("to", "to1", 3));
        route.nodes.add(step("otherwise", "otherwise1", 2));
        route.nodes.add(step("log", "log1", 3));
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute layout = engine.layoutRoute(route, 0);
        RouteDiagramWidget widget = new RouteDiagramWidget(layout, engine.getNodeWidth(), -1, 0, 0, false, Map.of());
        widget.render(new Rect(0, 0, 200, 60), Buffer.empty(new Rect(0, 0, 200, 60)));

        JsonObject json = DiagramJson.route(layout, widget.getNodeBoxes());

        assertThat(json.getString("routeId")).isEqualTo("hello");
        JsonArray nodes = (JsonArray) json.get("nodes");
        assertThat(nodes.stream().map(n -> ((JsonObject) n).getString("id")).toList())
                .containsExactly("hello", "setBody1", "choice1", "when1", "to1", "otherwise1", "log1");
        JsonObject from = (JsonObject) nodes.get(0);
        assertThat(from.getString("type")).isEqualTo("from");
        assertThat(from.getInteger("row")).isZero();
        JsonObject choice = (JsonObject) nodes.get(2);
        assertThat(((JsonArray) choice.get("children")).stream().map(Object::toString).toList())
                .containsExactly("when1", "otherwise1");
        JsonObject to = (JsonObject) nodes.get(4);
        assertThat(to.getString("parent")).isEqualTo("when1");
        assertThat(to.getInteger("row")).isGreaterThan(choice.getInteger("row"));
        assertThat(to.getInteger("width")).isPositive();
    }

    private static DiagramTab tab() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "hello";
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        return new DiagramTab(ctx);
    }

    private static TopologyNodeInfo node(String id) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = id;
        n.from = "";
        n.nodeType = "route";
        return n;
    }

    private static NodeInfo step(String type, String id, int level) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = id;
        n.code = type;
        n.level = level;
        return n;
    }
}
