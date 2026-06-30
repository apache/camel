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
 * Higher-level rendering tests for {@link CircuitBreakerTab}. Renders the circuit breaker table into a virtual terminal
 * buffer and inspects the rendered output.
 */
class CircuitBreakerTabRenderTest {

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
    void renderShowsTableHeaders() {
        addCircuitBreaker("route1", "cb1", "resilience4j", "closed");

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("ROUTE"), "Should show ROUTE header");
        assertTrue(rendered.contains("ID"), "Should show ID header");
        assertTrue(rendered.contains("COMPONENT"), "Should show COMPONENT header");
        assertTrue(rendered.contains("STATE"), "Should show STATE header");
    }

    @Test
    void renderShowsCircuitBreakerData() {
        addCircuitBreaker("my-route", "cb-main", "resilience4j", "closed");

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("my-route"), "Should render route ID");
        assertTrue(rendered.contains("cb-main"), "Should render circuit breaker ID");
        assertTrue(rendered.contains("resilience4j"), "Should render component name");
    }

    @Test
    void renderRouteIdInCyan() {
        addCircuitBreaker("my-route", "cb1", "resilience4j", "closed");

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundCyan = findCellWithColor(buffer, "m", Color.CYAN);
        assertTrue(foundCyan, "Route ID should use CYAN color");
    }

    @Test
    void renderClosedStateInGreen() {
        addCircuitBreaker("route1", "cb1", "resilience4j", "closed");

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundGreen = findCellWithColor(buffer, "c", Color.GREEN);
        assertTrue(foundGreen, "closed state should be rendered in GREEN");
    }

    @Test
    void renderOpenStateInRed() {
        addCircuitBreaker("route1", "cb1", "resilience4j", "open");

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRed = findCellWithColor(buffer, "o", Color.LIGHT_RED);
        assertTrue(foundRed, "open state should be rendered in LIGHT_RED");
    }

    @Test
    void renderHalfOpenStateInYellow() {
        addCircuitBreaker("route1", "cb1", "resilience4j", "half_open");

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundYellow = findCellWithColor(buffer, "h", Color.YELLOW);
        assertTrue(foundYellow, "half_open state should be rendered in YELLOW");
    }

    @Test
    void renderMultipleBreakersAllAppear() {
        addCircuitBreaker("route-alpha", "cb-alpha", "resilience4j", "closed");
        addCircuitBreaker("route-beta", "cb-beta", "fault-tolerance", "open");

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("cb-alpha"), "Should render first circuit breaker");
        assertTrue(rendered.contains("cb-beta"), "Should render second circuit breaker");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());
        String rendered = renderToString(tab, 160, 20);

        assertTrue(rendered.contains("No circuit breakers"),
                "Should show placeholder when no circuit breakers exist");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());
        String rendered = renderToString(tab, 120, 15);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addCircuitBreaker("route1", "cb1", "resilience4j", "closed");

        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());

        // Default sort is "route" — header should have a sort indicator on ROUTE
        String rendered1 = renderToString(tab, 160, 30);
        assertTrue(rendered1.contains("ROUTE▼") || rendered1.contains("ROUTE▲"),
                "Default sort should show indicator on ROUTE column");

        // Press 's' to cycle to next sort column (id)
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered2 = renderToString(tab, 160, 30);
        assertTrue(rendered2.contains("ID▼") || rendered2.contains("ID▲"),
                "After one sort cycle, indicator should move to ID");
    }

    @Test
    void renderFooterHints() {
        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
    }

    // ---- Helper methods ----

    private CircuitBreakerInfo addCircuitBreaker(String routeId, String id, String component, String state) {
        CircuitBreakerInfo cb = new CircuitBreakerInfo();
        cb.routeId = routeId;
        cb.id = id;
        cb.component = component;
        cb.state = state;
        info.circuitBreakers.add(cb);
        return cb;
    }

    private static String renderToString(MonitorTab tab, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
        return HealthTabRenderTest.bufferToString(buffer);
    }

    private static boolean findCellWithColor(Buffer buffer, String symbol, Color expectedFg) {
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
