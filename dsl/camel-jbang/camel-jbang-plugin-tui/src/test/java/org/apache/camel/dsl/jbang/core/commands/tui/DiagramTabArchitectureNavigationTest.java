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

import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26864: tui_navigate drilled into a route while the Diagram tab showed the architecture. The state said route,
 * the screen still drew the architecture, and the route's node boxes stayed those of the route drawn before.
 */
class DiagramTabArchitectureNavigationTest {

    @Test
    void goingToARouteOrSelectingOneLeavesTheArchitecture() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "hello";
        info.routes.add(route("hello"));
        info.routes.add(route("seven"));
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        DiagramTab tab = new DiagramTab(ctx);
        // the topology lists the routes in another order than the integration
        tab.diagram.showCustomTopology(List.of(node("seven"), node("hello")), List.of(), Map.of(), null);

        tab.goToLevel(DiagramTab.Level.ARCHITECTURE);
        assertThat(tab.level()).isEqualTo(DiagramTab.Level.ARCHITECTURE);
        assertThat(tab.getDiagramStateAsJson().getString("diagramMode")).as("what the screen shows")
                .isEqualTo("architecture");

        assertThat(tab.selectNode("hello", null)).isTrue();
        assertThat(tab.level()).isEqualTo(DiagramTab.Level.ROUTE);
        assertThat(tab.getDiagramStateAsJson().getString("diagramMode")).isEqualTo("route");
        assertThat(tab.getDiagramStateAsJson().getString("routeId")).isEqualTo("hello");

        tab.goToLevel(DiagramTab.Level.ARCHITECTURE);
        assertThat(tab.selectRoute("seven")).isTrue();
        assertThat(tab.level()).isEqualTo(DiagramTab.Level.TOPOLOGY);
    }

    private static RouteInfo route(String id) {
        RouteInfo r = new RouteInfo();
        r.routeId = id;
        return r;
    }

    private static TopologyNodeInfo node(String id) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = id;
        n.from = "";
        n.nodeType = "route";
        return n;
    }
}
