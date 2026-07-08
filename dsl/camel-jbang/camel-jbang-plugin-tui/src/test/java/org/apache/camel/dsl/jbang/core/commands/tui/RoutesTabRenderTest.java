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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link RoutesTab}. Renders the routes table into a virtual terminal buffer and
 * inspects the rendered output (text content, colors, layout).
 */
class RoutesTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "5678";
        info.name = "my-integration";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "5678";
    }

    @Test
    void renderShowsRouteTableHeaders() {
        addRoute("route1", "timer://tick?period=1000", "Started");

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        assertTrue(rendered.contains("ROUTE"), "Should show ROUTE header");
        assertTrue(rendered.contains("FROM"), "Should show FROM header");
        assertTrue(rendered.contains("STATUS"), "Should show STATUS header");
        assertTrue(rendered.contains("TOTAL"), "Should show TOTAL header");
        assertTrue(rendered.contains("FAIL"), "Should show FAIL header");
    }

    @Test
    void renderShowsRouteIdAndFrom() {
        addRoute("timer-to-log", "timer://hello?period=2000", "Started");

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        assertTrue(rendered.contains("timer-to-log"), "Should render route ID");
        assertTrue(rendered.contains("timer://hello"), "Should render FROM endpoint");
    }

    @Test
    void renderStartedRouteUsesGreenColor() {
        addRoute("my-route", "direct://start", "Started");

        RoutesTab tab = new RoutesTab(ctx);

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundGreenStarted = findCellWithColorContaining(buffer, "S", Theme.success().fg().orElse(Color.GREEN));
        assertTrue(foundGreenStarted, "Started status should be rendered in GREEN");
    }

    @Test
    void renderStoppedRouteUsesRedColor() {
        addRoute("stopped-route", "seda://queue", "Stopped");

        RoutesTab tab = new RoutesTab(ctx);

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRedStopped = findCellWithColorContaining(buffer, "S", Theme.error().fg().orElse(Color.LIGHT_RED));
        assertTrue(foundRedStopped, "Stopped status should be rendered in LIGHT_RED");
    }

    @Test
    void renderRouteIdUsesCyanColor() {
        addRoute("my-cyan-route", "timer://tick", "Started");

        RoutesTab tab = new RoutesTab(ctx);

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundCyanRouteId = findCellWithColorContaining(buffer, "m", Theme.accent());
        assertTrue(foundCyanRouteId, "Route ID should be rendered in CYAN");
    }

    @Test
    void renderMultipleRoutesAllAppear() {
        addRoute("route-alpha", "timer://a", "Started");
        addRoute("route-beta", "seda://b", "Started");
        addRoute("route-gamma", "direct://c", "Stopped");

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        assertTrue(rendered.contains("route-alpha"), "Should render route-alpha");
        assertTrue(rendered.contains("route-beta"), "Should render route-beta");
        assertTrue(rendered.contains("route-gamma"), "Should render route-gamma");
    }

    @Test
    void renderFailedCountHighlightedInRed() {
        RouteInfo route = addRoute("failing-route", "timer://fail", "Started");
        route.total = 100;
        route.failed = 5;

        RoutesTab tab = new RoutesTab(ctx);

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRedFailed = findCellWithColorContaining(buffer, "5", Theme.error().fg().orElse(Color.LIGHT_RED));
        assertTrue(foundRedFailed, "Failed count should be rendered in LIGHT_RED");
    }

    @Test
    void renderShowsProcessorsSection() {
        RouteInfo route = addRoute("my-route", "timer://tick", "Started");
        ProcessorInfo proc = new ProcessorInfo();
        proc.id = "log1";
        proc.processor = "log";
        proc.level = 1;
        proc.total = 50;
        route.processors.add(proc);

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        assertTrue(rendered.contains("Processors"), "Should show Processors section");
        assertTrue(rendered.contains("log"), "Should show processor type");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 100, 20);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderNoRoutesShowsEmpty() {
        // info has no routes
        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        assertTrue(rendered.contains("No routes") || rendered.contains("Routes"),
                "Should show Routes section even with no routes");
    }

    @Test
    void toggleTopModeChangesHeaders() {
        addRoute("route1", "timer://tick", "Started");

        RoutesTab tab = new RoutesTab(ctx);

        // Render in normal mode first
        String normalRender = renderToString(tab, 140, 30);
        assertTrue(normalRender.contains("AGE"), "Normal mode should show AGE header");

        // Press 't' to toggle top mode
        tab.handleKeyEvent(KeyEvent.ofChar('t', KeyModifiers.NONE));
        assertTrue(tab.isTopMode(), "Should be in top mode after pressing 't'");

        String topRender = renderToString(tab, 140, 30);
        assertTrue(topRender.contains("MEAN"), "Top mode should show MEAN header");
        assertTrue(topRender.contains("MAX"), "Top mode should show MAX header");
        assertTrue(topRender.contains("LOAD"), "Top mode should show LOAD header");
    }

    @Test
    void renderTopModeShowsTimingData() {
        RouteInfo route = addRoute("route1", "timer://tick", "Started");
        route.total = 1000;
        route.meanTime = 42;
        route.maxTime = 500;
        route.minTime = 1;
        route.lastTime = 35;

        RoutesTab tab = new RoutesTab(ctx);
        tab.handleKeyEvent(KeyEvent.ofChar('t', KeyModifiers.NONE));

        String rendered = renderToString(tab, 140, 30);
        assertTrue(rendered.contains("42"), "Should show mean time");
        assertTrue(rendered.contains("500"), "Should show max time");
    }

    @Test
    void renderShowsTitleWithSortColumn() {
        addRoute("route1", "timer://tick", "Started");

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        assertTrue(rendered.contains("ROUTE▼"), "Column header should show sort indicator on default sort column");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addRoute("route1", "timer://tick", "Started");

        RoutesTab tab = new RoutesTab(ctx);

        // Press 's' to cycle sort
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered = renderToString(tab, 140, 30);
        assertTrue(rendered.contains("FROM▼"), "Sort should cycle to 'from'");
    }

    @Test
    void renderFooterHintsContainExpectedKeys() {
        RoutesTab tab = new RoutesTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
        assertTrue(footer.contains("topology"), "Footer should contain topology hint");
    }

    @Test
    void renderRouteWithCoverageShowsCoverage() {
        RouteInfo route = addRoute("route1", "timer://tick", "Started");
        route.coverage = "5/5";

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        assertTrue(rendered.contains("5/5"), "Should show coverage value");
    }

    @Test
    void renderRouteWithThroughputShowsValue() {
        RouteInfo route = addRoute("route1", "timer://tick", "Started");
        route.throughput = "1.50";

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        assertTrue(rendered.contains("1.50"), "Should show throughput value");
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

    private static String renderToString(MonitorTab tab, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
        return HealthTabRenderTest.bufferToString(buffer);
    }

    /**
     * Search the buffer for any cell containing the given symbol text rendered with the specified foreground color.
     */
    private static boolean findCellWithColorContaining(Buffer buffer, String symbol, Color expectedFg) {
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                var cell = buffer.get(x, y);
                if (symbol.equals(cell.symbol())) {
                    var fg = cell.style().fg().orElse(null);
                    if (expectedFg.equals(fg)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
