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

import java.awt.image.BufferedImage;
import java.util.List;

import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.diagram.TopologyErrorFrame.LineStyle;
import org.apache.camel.diagram.TopologyErrorFrame.Placed;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25466: the diagram exports draw the error handling as the camel-jbang monitor does, in a frame of its own below
 * the routes: the routes reached only on error, and under each where its failures come from.
 */
class TopologyErrorFrameTest {

    /** fail-well/error-handling: a dead letter channel and an onException send to parked, which uses them too. */
    private static JsonObject shop(boolean external) {
        JsonObject root = new JsonObject();
        JsonArray nodes = new JsonArray();
        nodes.add(node("checkout", "file:orders"));
        nodes.add(node("payment-provider", "direct:charge"));
        nodes.add(node("parked", "direct:parked"));
        root.put("nodes", nodes);
        JsonArray edges = new JsonArray();
        edges.add(edge("checkout", "payment-provider", "call", null, null));
        edges.add(edge("checkout", "parked", "error", "onException", "handled"));
        edges.add(edge("checkout", "parked", "error", "errorHandler", "handled"));
        edges.add(edge("parked", "parked", "error", "onException", "handled"));
        edges.add(edge("parked", "parked", "error", "errorHandler", "handled"));
        root.put("edges", edges);
        if (external) {
            JsonArray ext = new JsonArray();
            ext.add(external("in-checkout", "file:orders", "in", "checkout"));
            ext.add(external("out-parked-0", "file:parked", "out", "parked"));
            root.put("externalEndpoints", ext);
        }
        return root;
    }

    private static Placed layout(JsonObject topology, int rowHeight) {
        List<TopologyNodeInfo> nodes = TopologyHelper.parseNodes(topology);
        List<TopologyEdgeInfo> calls = TopologyHelper.parseEdges(topology);
        TopologyHelper.addExternalEndpoints(nodes, calls, topology);
        List<TopologyEdgeInfo> errors
                = TopologyHelper.parseEdges(topology, true).stream().filter(TopologyEdgeInfo::isErrorPath).toList();
        return TopologyErrorFrame.layout(new TopologyLayoutEngine(), nodes, calls, errors, rowHeight);
    }

    private static TopologyLayoutNode find(Placed placed, String routeId) {
        return placed.layout().nodes.stream().filter(n -> routeId.equals(n.routeId)).findFirst().orElseThrow();
    }

    @Test
    void theRoutesReachedOnlyOnErrorGoIntoAFrameBelowTheOthers() {
        Placed placed = layout(shop(false), TopologyAsciiRenderer.ROW_HEIGHT);

        assertThat(placed.frame().routeIds()).containsExactly("parked");
        TopologyLayoutNode checkout = find(placed, "checkout");
        TopologyLayoutNode payment = find(placed, "payment-provider");
        TopologyLayoutNode parked = find(placed, "parked");
        assertThat(placed.frame().topY()).isGreaterThan(payment.y + payment.height)
                .isGreaterThan(checkout.y + checkout.height);
        assertThat(parked.y).isGreaterThan(placed.frame().topY());
        // no arrows into the frame: with a global error handler every route would have one
        assertThat(placed.layout().edges).noneMatch(e -> "parked".equals(e.to.routeId) || "parked".equals(e.from.routeId));
        assertThat(placed.layout().totalHeight).isGreaterThan(parked.y + parked.height);
    }

    @Test
    void underEachRouteWhereItsFailuresComeFrom() {
        TopologyErrorFrame frame = layout(shop(false), TopologyAsciiRenderer.ROW_HEIGHT).frame();

        assertThat(frame.lines("parked", LineStyle.UNICODE)).containsExactly(
                "◂ checkout: onException, handled · dead letter, handled",
                "⚠ own failures: onException, handled · dead letter, handled");
        assertThat(frame.lines("parked", LineStyle.ASCII)).containsExactly(
                "< checkout: onException, handled; dead letter, handled",
                "! own failures: onException, handled; dead letter, handled");
        assertThat(frame.lines("parked", LineStyle.TEXT)).first().asString()
                .isEqualTo("from checkout: onException, handled · dead letter, handled");
        assertThat(frame.maxLines()).isEqualTo(2);
    }

    @Test
    void anExternalSystemOnlyAnErrorRouteUsesGoesIntoTheFrameBelowIt() {
        Placed placed = layout(shop(true), TopologyAsciiRenderer.ROW_HEIGHT);

        TopologyLayoutNode parked = find(placed, "parked");
        TopologyLayoutNode file = find(placed, "out-parked-0");
        assertThat(find(placed, "in-checkout").y).isLessThan(placed.frame().topY());
        // where the parked orders end up: below the lines of parked, in its column, with the arrow from parked
        assertThat(file.x).isEqualTo(parked.x);
        assertThat(file.y).isGreaterThan(
                parked.y + parked.height + placed.frame().maxLines() * TopologyAsciiRenderer.ROW_HEIGHT);
        assertThat(placed.layout().edges).anyMatch(e -> e.from == parked && e.to == file);
        assertThat(placed.layout().totalHeight).isGreaterThan(file.y + file.height);
        // it has no lines of its own: only the routes do
        assertThat(placed.frame().routeIds()).containsExactly("parked");

        String text = new TopologyAsciiRenderer(new TopologyLayoutEngine().getNodeWidth(), false)
                .renderDiagramPlain(placed.layout(), placed.frame());
        assertThat(text.indexOf("file:parked")).isGreaterThan(text.indexOf("! own failures"));
    }

    @Test
    void withoutErrorPathsThereIsNoFrame() {
        JsonObject topology = shop(false);
        JsonArray calls = new JsonArray();
        calls.add(edge("checkout", "payment-provider", "call", null, null));
        topology.put("edges", calls);

        Placed placed = layout(topology, TopologyAsciiRenderer.ROW_HEIGHT);

        assertThat(placed.frame().routeIds()).isEmpty();
        String text = new TopologyAsciiRenderer(new TopologyLayoutEngine().getNodeWidth(), false)
                .renderDiagramPlain(placed.layout(), placed.frame());
        assertThat(text).doesNotContain(TopologyErrorFrame.TITLE);
    }

    @Test
    void theTextRendererDrawsTheFrame() {
        Placed placed = layout(shop(false), TopologyAsciiRenderer.ROW_HEIGHT);
        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(new TopologyLayoutEngine().getNodeWidth(), false);

        String text = renderer.renderDiagramPlain(placed.layout(), placed.frame());

        assertThat(text).contains("+- " + TopologyErrorFrame.TITLE + " -")
                .contains("< checkout: onException, handled; dead letter, handled")
                .contains("! own failures: onException, handled; dead letter, handled");
        // the frame is below the happy path, parked is in it
        assertThat(text.indexOf(TopologyErrorFrame.TITLE)).isGreaterThan(text.indexOf("payment-provider"))
                .isLessThan(text.indexOf("parked"));
        String unicode = new TopologyAsciiRenderer(new TopologyLayoutEngine().getNodeWidth(), true)
                .renderDiagramPlain(placed.layout(), placed.frame());
        assertThat(unicode).contains("╭┄ " + TopologyErrorFrame.TITLE + " ┄")
                .contains("⚠ own failures");
    }

    @Test
    void theImageRendererDrawsTheFrame() {
        int fontSize = 12;
        Placed placed = layout(shop(false), TopologyImageRenderer.rowHeight(fontSize));

        BufferedImage image = TopologyImageRenderer.renderImage(
                placed.layout(), DiagramColors.parse("light"), fontSize, 180, false, false, placed.frame());
        BufferedImage without = TopologyImageRenderer.renderImage(
                placed.layout(), DiagramColors.parse("light"), fontSize, 180, false, false);

        assertThat(image.getHeight()).isGreaterThanOrEqualTo(placed.layout().totalHeight);
        // the image takes in the lines under parked, which are wider than its box
        assertThat(image.getWidth()).isGreaterThan(without.getWidth());
    }

    private static JsonObject node(String routeId, String from) {
        JsonObject n = new JsonObject();
        n.put("routeId", routeId);
        n.put("from", from);
        n.put("fromScheme", from.substring(0, from.indexOf(':')));
        n.put("nodeType", "route");
        return n;
    }

    private static JsonObject edge(String from, String to, String kind, String via, String handling) {
        JsonObject e = new JsonObject();
        e.put("fromRouteId", from);
        e.put("toRouteId", to);
        e.put("endpoint", "direct:" + to);
        e.put("connectionType", "internal");
        e.put("kind", kind);
        if (via != null) {
            e.put("via", via);
        }
        if (handling != null) {
            e.put("handling", handling);
        }
        return e;
    }

    private static JsonObject external(String id, String uri, String direction, String routeId) {
        JsonObject e = new JsonObject();
        e.put("id", id);
        e.put("uri", uri);
        e.put("scheme", uri.substring(0, uri.indexOf(':')));
        e.put("direction", direction);
        e.put("routeId", routeId);
        return e;
    }
}
