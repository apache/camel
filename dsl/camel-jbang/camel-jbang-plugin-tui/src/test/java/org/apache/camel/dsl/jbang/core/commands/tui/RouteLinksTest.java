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

import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteErrorFrame;
import org.apache.camel.diagram.TopologyAsciiRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ↵ beside the from of a route names the route that calls it; the onException clause in the error frame of another
 * route (a global one is in every route) does not call it (CAMEL-25466).
 */
class RouteLinksTest {

    private final RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();

    @Test
    void aSendInTheErrorFrameOfARouteIsNotACall() {
        // fail-well/error-handling: payment-provider has the global onException, which sends to parked, in its frame
        LayoutRoute payment = engine.layoutRoute(route("payment-provider", step("from", "direct:charge", 0),
                step("log", null, 1)), 0);
        RouteErrorFrame frame = RouteErrorFrame.add(engine, payment,
                List.of(route("payment-provider", step("onException", null, 0), step("to", "direct:parked", 1))),
                List.of(), TopologyAsciiRenderer.ROW_HEIGHT);
        LayoutRoute checkout = engine.layoutRoute(route("checkout", step("from", "file:orders", 0),
                step("to", "direct:charge", 1)), 0);
        LayoutRoute parked = engine.layoutRoute(route("parked", step("from", "direct:parked", 0),
                step("to", "file:parked", 1)), 0);
        Map<String, LayoutRoute> layouts = Map.of("payment-provider", payment, "checkout", checkout, "parked", parked);

        // with the error handling shown, no route calls parked: it says in its own frame where it is reached from
        assertThat(RouteLinks.linkable("parked", layouts, Map.of("payment-provider", frame)))
                .doesNotContainKey("direct:parked");
        assertThat(RouteLinks.sendsTo(payment, "direct:parked", frame)).isFalse();
        // a call stays a call: checkout calls payment-provider
        assertThat(RouteLinks.linkable("payment-provider", layouts, Map.of("payment-provider", frame)))
                .containsEntry("direct:charge", "checkout");
        assertThat(RouteLinks.sendsTo(checkout, "direct:charge", null)).isTrue();
    }

    private static RouteDiagramLayoutEngine.RouteInfo route(String id, RouteDiagramLayoutEngine.NodeInfo... steps) {
        RouteDiagramLayoutEngine.RouteInfo route = new RouteDiagramLayoutEngine.RouteInfo();
        route.routeId = id;
        route.nodes.addAll(List.of(steps));
        return route;
    }

    private static RouteDiagramLayoutEngine.NodeInfo step(String type, String uri, int level) {
        RouteDiagramLayoutEngine.NodeInfo n = new RouteDiagramLayoutEngine.NodeInfo();
        n.type = type;
        n.uri = uri;
        n.code = uri != null ? type + "[" + uri + "]" : type;
        n.level = level;
        return n;
    }
}
