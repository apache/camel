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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link HealthTab}. These tests render the tab into a virtual terminal buffer via
 * {@link Frame#forTesting(Buffer)} and inspect the rendered cell content.
 */
class HealthTabRenderTest {

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
    void renderHealthChecksShowsUpStatusInGreen() {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = "camel";
        hc.name = "context";
        hc.state = "UP";
        hc.readiness = true;
        hc.liveness = true;
        info.healthChecks.add(hc);

        HealthTab tab = tableTab();
        String rendered = renderToString(tab, 100, 20);

        assertTrue(rendered.contains("context"), "Should render health check name 'context'");
        assertTrue(rendered.contains("UP"), "Should render UP status");
        assertTrue(rendered.contains("camel"), "Should render group name");
    }

    @Test
    void renderHealthChecksShowsDownStatusWithMessage() {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = "routes";
        hc.name = "timer-route";
        hc.state = "DOWN";
        hc.readiness = true;
        hc.message = "Route stopped";
        info.healthChecks.add(hc);

        HealthTab tab = tableTab();
        String rendered = renderToString(tab, 120, 20);

        assertTrue(rendered.contains("timer-route"), "Should render health check name");
        assertTrue(rendered.contains("DOWN"), "Should render DOWN status");
        assertTrue(rendered.contains("Route stopped"), "Should render failure message");
    }

    @Test
    void renderDownStatusCellUsesRedColor() {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = "routes";
        hc.name = "failing-route";
        hc.state = "DOWN";
        hc.readiness = true;
        info.healthChecks.add(hc);

        HealthTab tab = tableTab();

        Rect area = new Rect(0, 0, 100, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        // Find the DOWN text cell and verify it uses red foreground
        boolean foundRedDown = false;
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                var cell = buffer.get(x, y);
                if (TuiIcons.HEALTH_DOWN.equals(cell.symbol()) || "D".equals(cell.symbol())) {
                    var fg = cell.style().fg().orElse(null);
                    if (Theme.error().fg().orElse(Color.LIGHT_RED).equals(fg)) {
                        foundRedDown = true;
                        break;
                    }
                }
            }
            if (foundRedDown) {
                break;
            }
        }
        assertTrue(foundRedDown, "DOWN status should be rendered in LIGHT_RED");
    }

    @Test
    void renderUpStatusCellUsesGreenColor() {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = "camel";
        hc.name = "ctx";
        hc.state = "UP";
        hc.readiness = true;
        hc.liveness = true;
        info.healthChecks.add(hc);

        HealthTab tab = tableTab();

        Rect area = new Rect(0, 0, 100, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        // Find a green-colored cell in the UP row
        boolean foundGreenUp = false;
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                var cell = buffer.get(x, y);
                if (TuiIcons.HEALTH_UP.equals(cell.symbol())) {
                    var fg = cell.style().fg().orElse(null);
                    if (Theme.success().fg().orElse(Color.GREEN).equals(fg)) {
                        foundGreenUp = true;
                        break;
                    }
                }
            }
            if (foundGreenUp) {
                break;
            }
        }
        assertTrue(foundGreenUp, "UP status should be rendered in GREEN");
    }

    @Test
    void renderEmptyHealthChecksShowsPlaceholder() {
        // No health checks added
        HealthTab tab = tableTab();
        // Use wider terminal to avoid column truncation of the placeholder text
        String rendered = renderToString(tab, 140, 10);

        assertTrue(rendered.contains("No health checks"),
                "Should show placeholder when no health checks exist");
    }

    @Test
    void renderMultipleHealthChecksAllAppear() {
        addHealthCheck("camel", "context", "UP");
        addHealthCheck("camel", "route-controller", "UP");
        addHealthCheck("routes", "timer-route", "DOWN");

        HealthTab tab = tableTab();
        String rendered = renderToString(tab, 120, 20);

        assertTrue(rendered.contains("context"), "Should render 'context' check");
        assertTrue(rendered.contains("route-controller"), "Should render 'route-controller' check");
        assertTrue(rendered.contains("timer-route"), "Should render 'timer-route' check");
    }

    @Test
    void renderShowsHeaderColumns() {
        addHealthCheck("camel", "context", "UP");

        HealthTab tab = tableTab();
        String rendered = renderToString(tab, 120, 20);

        assertTrue(rendered.contains("GROUP"), "Should show GROUP header");
        assertTrue(rendered.contains("NAME"), "Should show NAME header");
        assertTrue(rendered.contains("STATUS"), "Should show STATUS header");
        assertTrue(rendered.contains("KIND"), "Should show KIND header");
        assertTrue(rendered.contains("MESSAGE"), "Should show MESSAGE header");
    }

    @Test
    void renderShowsBorderWithTitle() {
        addHealthCheck("camel", "context", "UP");

        HealthTab tab = tableTab();
        String rendered = renderToString(tab, 120, 20);

        assertTrue(rendered.contains("Health"), "Should show 'Health' in the block title");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        HealthTab tab = tableTab();
        String rendered = renderToString(tab, 100, 10);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderReadinessLivenessKindColumn() {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = "camel";
        hc.name = "context";
        hc.state = "UP";
        hc.readiness = true;
        hc.liveness = true;
        info.healthChecks.add(hc);

        HealthTab tab = tableTab();
        String rendered = renderToString(tab, 120, 20);

        assertTrue(rendered.contains("R/L"), "Should show R/L for readiness+liveness");
    }

    @Test
    void toggleDownOnlyFilterChangesTitle() {
        addHealthCheck("camel", "context", "UP");
        addHealthCheck("routes", "failing", "DOWN");

        HealthTab tab = tableTab();

        // Press 'd' to toggle DOWN-only filter
        tab.handleKeyEvent(KeyEvent.ofChar('d', KeyModifiers.NONE));
        assertTrue(tab.isShowOnlyDown());

        String rendered = renderToString(tab, 120, 20);
        assertTrue(rendered.contains("DOWN only"), "Title should indicate DOWN-only filter");

        // "context" (UP) should be filtered out
        assertFalse(rendered.contains("context"), "UP check should be filtered out");
        assertTrue(rendered.contains("failing"), "DOWN check should still appear");
    }

    @Test
    void renderFooterHintsContainExpectedKeys() {
        HealthTab tab = tableTab();
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
        assertTrue(footer.contains("DOWN"), "Footer should contain DOWN toggle hint");
    }

    @Test
    void sortCycleChangesHeaderAppearance() {
        addHealthCheck("camel", "context", "UP");

        HealthTab tab = tableTab();

        // Default sort is "name" — header should have a sort indicator on NAME
        String rendered1 = renderToString(tab, 120, 20);
        assertTrue(rendered1.contains("NAME▼") || rendered1.contains("NAME▲"),
                "Default sort should show indicator on NAME column");

        // Press 's' to cycle to next sort column
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered2 = renderToString(tab, 120, 20);
        assertTrue(rendered2.contains("STATUS▼") || rendered2.contains("STATUS▲"),
                "After one sort cycle, indicator should move to STATUS");
    }

    @Test
    void allGoodIsAPageNotATableOfUp() {
        info.healthReady = true;
        info.healthLive = true;
        long now = System.currentTimeMillis();
        for (String name : List.of("context", "route-controller", "security-policy", "route:orders", "route:billing",
                "consumer:orders")) {
            HealthCheckInfo hc = check("camel", name, "UP");
            hc.since = now - 19 * 60_000;
            hc.invocations = 2329;
        }
        check("acme", "warehouse", "UP");

        String rendered = renderToString(new HealthTab(ctx), 160, 30);

        assertTrue(rendered.contains("All good"), rendered);
        assertTrue(rendered.contains("7 checks UP"), rendered);
        assertTrue(rendered.contains("checked 2329 times · never failed"), rendered);
        assertTrue(rendered.contains(
                "Camel checks on its own: the context, the route controller, the security policy, 2 routes and 1 consumer"),
                rendered);
        assertTrue(rendered.contains("Your checks: " + TuiIcons.HEALTH_UP + " warehouse"), rendered);
        assertFalse(rendered.contains("GROUP"), "no table: " + rendered);
    }

    @Test
    void aCheckThatIsDownComesFirstWithWhatItMeans() {
        info.healthReady = false;
        info.healthLive = true;
        long now = System.currentTimeMillis();
        check("camel", "context", "UP");
        HealthCheckInfo down = check("camel", "consumer:orders", "DOWN");
        down.message = "Connection refused: kafka:9092";
        down.since = now - 4 * 60_000;
        down.failures = 23;
        down.invocations = 251;

        String rendered = renderToString(new HealthTab(ctx), 160, 30);

        assertTrue(rendered.contains("NOT READY"), rendered);
        assertTrue(rendered.contains("consumer:orders   DOWN since"), rendered);
        assertTrue(rendered.contains("Connection refused: kafka:9092"), rendered);
        assertTrue(rendered.contains("failed 23 times, of 251 checks"), rendered);
        assertTrue(rendered.contains("Kubernetes would take it out of the service"), rendered);
        assertTrue(rendered.contains("1 other check UP"), rendered);
    }

    @Test
    void theStripShowsAFlapThePageWouldNotShow() {
        info.healthReady = true;
        info.healthLive = true;
        check("camel", "context", "UP");
        long end = System.currentTimeMillis() / 1000;
        for (long s = end - 60; s <= end; s++) {
            // ready went down for 5 seconds 30 seconds ago
            info.healthReady = s < end - 35 || s > end - 31;
            ctx.healthHistory.record(info, s);
        }
        info.healthReady = true;

        String rendered = renderToString(new HealthTab(ctx), 160, 30);

        assertTrue(rendered.contains("All good"), rendered);
        assertTrue(rendered.contains("down 5s, the last at"), rendered);
    }

    // ---- Helper methods ----

    /** The tab with the checks one by one in the table (t). */
    private HealthTab tableTab() {
        HealthTab tab = new HealthTab(ctx);
        tab.handleKeyEvent(KeyEvent.ofChar('t', KeyModifiers.NONE));
        return tab;
    }

    private HealthCheckInfo check(String group, String name, String state) {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = group;
        hc.name = name;
        hc.state = state;
        hc.readiness = true;
        info.healthChecks.add(hc);
        return hc;
    }

    private void addHealthCheck(String group, String name, String state) {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = group;
        hc.name = name;
        hc.state = state;
        hc.readiness = true;
        info.healthChecks.add(hc);
    }

    /**
     * Renders a tab into a virtual buffer and extracts the full text content.
     */
    private static String renderToString(MonitorTab tab, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
        return bufferToString(buffer);
    }

    /**
     * Extracts all text from a buffer row by row.
     */
    static String bufferToString(Buffer buffer) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                String sym = buffer.get(x, y).symbol();
                sb.append(sym.isEmpty() ? " " : sym);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
