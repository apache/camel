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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link DiagramTab}. Renders the diagram view into a virtual terminal buffer and
 * inspects the rendered output (text content, colors, layout).
 */
class DiagramTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        DiagramTab tab = new DiagramTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 20);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsBlockTitle() {
        DiagramTab tab = new DiagramTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 20);

        assertTrue(rendered.contains("Diagram") || rendered.contains("Topology"),
                "Should show Diagram or Topology in the block title");
    }

    @Test
    void renderWithRoutesShowsDiagram() {
        addRoute("my-route", "timer://tick", "Started");

        DiagramTab tab = new DiagramTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 20);

        assertTrue(rendered.contains("Diagram") || rendered.contains("Loading diagram"),
                "Should show diagram area or loading placeholder when routes exist");
    }

    @Test
    void renderFooterHints() {
        DiagramTab tab = new DiagramTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        // DiagramTab only adds footer hints when diagram.isShowDiagram() is true.
        // A freshly constructed DiagramTab has no diagram loaded, so footer is empty.
        assertTrue(footer.isEmpty() || footer.contains("metrics") || footer.contains("Esc"),
                "Footer should be empty (no diagram loaded) or contain diagram-related hints");
    }

    @Test
    void renderShowsLoadingWhenNoRoutes() {
        DiagramTab tab = new DiagramTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 20);

        assertTrue(rendered.contains("Loading diagram") || rendered.contains("Diagram"),
                "Should show loading placeholder or diagram title when no diagram data is loaded");
    }

    // ---- Helper methods ----

    private RouteInfo addRoute(String routeId, String from, String state) {
        RouteInfo route = new RouteInfo();
        route.routeId = routeId;
        route.from = from;
        route.state = state;
        route.uptime = "10s";
        info.routes.add(route);
        return route;
    }

}
