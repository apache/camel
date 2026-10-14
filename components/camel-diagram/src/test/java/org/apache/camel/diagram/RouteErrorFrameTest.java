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
import java.util.Set;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.diagram.TopologyErrorFrame.ErrorPath;
import org.apache.camel.diagram.TopologyErrorFrame.LineStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25466: a route diagram draws the onException clauses of a route (a global one is copied into every route) in a
 * frame below its steps, with where its failures go, instead of among its steps.
 */
class RouteErrorFrameTest {

    // fail-well/error-handling: a dead letter channel and an onException send to parked, which uses them too
    private static final List<ErrorPath> PATHS = List.of(
            new ErrorPath("checkout", "parked", "onException", "handled"),
            new ErrorPath("checkout", "parked", "errorHandler", "handled"),
            new ErrorPath("parked", "parked", "errorHandler", "handled"));

    /** checkout: from, the global onException (a log and a to parked), then its own steps. */
    private static RouteInfo checkout() {
        RouteInfo route = new RouteInfo();
        route.routeId = "checkout";
        route.nodes.add(step("from", "from1", "file:orders", 0));
        route.nodes.add(step("onException", "onException1", null, 1));
        route.nodes.add(step("log", "log1", null, 2));
        route.nodes.add(step("to", "to1", "direct:parked", 2));
        route.nodes.add(step("unmarshal", "unmarshal1", null, 1));
        route.nodes.add(step("to", "to2", "direct:charge", 1));
        return route;
    }

    @Test
    void theOnExceptionClausesAreNotStepsOfTheRoute() {
        RouteErrorFrame.Split split = RouteErrorFrame.split(checkout());

        assertThat(split.happy().nodes).extracting(n -> n.id).containsExactly("from1", "unmarshal1", "to2");
        assertThat(split.blocks()).singleElement().satisfies(block -> assertThat(block.nodes)
                .extracting(n -> n.id + "@" + n.level).containsExactly("onException1@0", "log1@1", "to1@1"));
    }

    @Test
    void theLinesSayWhereTheFailuresGoAndComeFrom() {
        assertThat(RouteErrorFrame.lines("checkout", PATHS, LineStyle.UNICODE))
                .containsExactly("▸ on failure to parked: onException, handled · dead letter, handled");
        assertThat(RouteErrorFrame.lines("parked", PATHS, LineStyle.ASCII)).containsExactly(
                "! own failures: dead letter, handled",
                "< reached when checkout handles a failure: onException, handled; dead letter, handled");
        assertThat(RouteErrorFrame.lines("parked", PATHS, LineStyle.TEXT)).first().asString()
                .isEqualTo("own failures: dead letter, handled");
        assertThat(RouteErrorFrame.lines("payment-provider", PATHS, LineStyle.UNICODE)).isEmpty();
    }

    @Test
    void theFrameIsBelowTheStepsWithTheBlocksInIt() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        RouteErrorFrame.Split split = RouteErrorFrame.split(checkout());
        LayoutRoute lr = engine.layoutRoute(split.happy(), 0);
        int steps = lr.maxY;

        RouteErrorFrame frame = RouteErrorFrame.add(engine, lr, split.blocks(),
                RouteErrorFrame.lines("checkout", PATHS, LineStyle.ASCII), RouteDiagramAsciiRenderer.ROW_HEIGHT);

        assertThat(lr.errorFrame).isSameAs(frame);
        assertThat(frame.topY()).isGreaterThan(steps);
        assertThat(lr.nodes).filteredOn(n -> "onException1".equals(n.id) || "to1".equals(n.id))
                .extracting((LayoutNode n) -> n.y).allMatch(y -> y > frame.topY() && y < frame.bottomY());
        assertThat(lr.maxY).isGreaterThan(frame.bottomY());
        // nothing to show: no frame
        LayoutRoute plain = engine.layoutRoute(RouteErrorFrame.split(checkout()).happy(), 0);
        assertThat(RouteErrorFrame.add(engine, plain, List.of(), List.of(), RouteDiagramAsciiRenderer.ROW_HEIGHT)).isNull();
        assertThat(plain.errorFrame).isNull();
    }

    @Test
    void theRenderersDrawTheFrame() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        RouteErrorFrame.Split split = RouteErrorFrame.split(checkout());
        LayoutRoute lr = engine.layoutRoute(split.happy(), RouteDiagramLayoutEngine.PADDING);
        RouteErrorFrame.add(engine, lr, split.blocks(),
                RouteErrorFrame.lines("checkout", PATHS, LineStyle.ASCII), RouteDiagramAsciiRenderer.ROW_HEIGHT);

        String text = new RouteDiagramAsciiRenderer(engine.getNodeWidth()).renderDiagram(List.of(lr), lr.maxY);

        assertThat(text).contains("+- " + TopologyErrorFrame.TITLE + " -")
                .contains("> on failure to parked: onException, handled; dead letter, handled");
        // the steps first, then the frame with the onException clause in it
        assertThat(text.indexOf("direct:charge")).isLessThan(text.indexOf(TopologyErrorFrame.TITLE));
        assertThat(text.indexOf(TopologyErrorFrame.TITLE)).isLessThan(text.indexOf("onException"));

        RouteDiagramRenderer png = new RouteDiagramRenderer();
        LayoutRoute image = engine.layoutRoute(split.happy(), RouteDiagramLayoutEngine.PADDING);
        RouteErrorFrame.add(engine, image, split.blocks(),
                RouteErrorFrame.lines("checkout", PATHS, LineStyle.TEXT), RouteDiagramRenderer.rowHeight(12));
        BufferedImage withFrame = png.renderDiagram(List.of(image), image.maxY, DiagramColors.parse("light"));
        assertThat(withFrame.getHeight()).isGreaterThan(image.errorFrame.bottomY());
    }

    @Test
    void aRouteDiagramDrawsTheStepsOfTheRouteOnlyUnlessTheErrorHandlingIsShown() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();

        LayoutRoute steps = RouteErrorFrame.layout(
                engine, checkout(), 0, false, null, PATHS, LineStyle.UNICODE, RouteDiagramAsciiRenderer.ROW_HEIGHT);
        assertThat(steps.errorFrame).isNull();
        assertThat(steps.nodes).extracting(n -> n.id).doesNotContain("onException1", "log1", "to1");

        LayoutRoute errors = RouteErrorFrame.layout(
                engine, checkout(), 0, true, null, PATHS, LineStyle.UNICODE, RouteDiagramAsciiRenderer.ROW_HEIGHT);
        assertThat(errors.errorFrame).isNotNull();
        assertThat(errors.errorFrame.lines()).containsExactly(
                "▸ on failure to parked: onException, handled · dead letter, handled");
        assertThat(errors.nodes).extracting(n -> n.id).contains("onException1", "log1", "to1");
    }

    @Test
    void aHighlightedPathIntoAnOnExceptionClauseShowsTheFrame() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();

        // the message failed in to2 and the onException sent it on to parked
        LayoutRoute into = RouteErrorFrame.layout(engine, checkout(), 0, false, Set.of("from1", "to2", "onException1", "to1"),
                List.of(), LineStyle.UNICODE, RouteDiagramAsciiRenderer.ROW_HEIGHT);
        assertThat(into.errorFrame).isNotNull();
        assertThat(into.nodes).extracting(n -> n.id).contains("onException1", "to1");

        // a path that stays on the steps of the route
        LayoutRoute steps = RouteErrorFrame.layout(engine, checkout(), 0, false, Set.of("from1", "unmarshal1", "to2"),
                List.of(), LineStyle.UNICODE, RouteDiagramAsciiRenderer.ROW_HEIGHT);
        assertThat(steps.errorFrame).isNull();
    }

    private static NodeInfo step(String type, String id, String uri, int level) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = id;
        n.uri = uri;
        n.code = uri != null ? type + "[" + uri + "]" : type;
        n.level = level;
        return n;
    }
}
