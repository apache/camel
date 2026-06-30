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
 * Higher-level rendering tests for {@link InflightTab}. Renders the inflight table into a virtual terminal buffer and
 * inspects the rendered output.
 */
class InflightTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";
        info.inflightBrowseEnabled = true;

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void renderShowsTableHeaders() {
        addInflight("ID-001", "route1", "route1", "to1", 500, false);

        InflightTab tab = new InflightTab(ctx);
        String rendered = renderToString(tab, 160, 20);

        assertTrue(rendered.contains("STATUS"), "Should show STATUS header");
        assertTrue(rendered.contains("EXCHANGE ID"), "Should show EXCHANGE ID header");
        assertTrue(rendered.contains("DURATION"), "Should show DURATION header");
    }

    @Test
    void renderShowsInflightExchange() {
        addInflight("ID-myhost-1234", "my-route", "my-route", "to1", 500, false);

        InflightTab tab = new InflightTab(ctx);
        String rendered = renderToString(tab, 160, 20);

        assertTrue(rendered.contains("ID-myhost-1234"), "Should render the exchange ID");
        assertTrue(rendered.contains("my-route"), "Should render the route ID");
    }

    @Test
    void renderInflightStatusInGreen() {
        addInflight("ID-001", "route1", "route1", "to1", 500, false);

        InflightTab tab = new InflightTab(ctx);

        Rect area = new Rect(0, 0, 160, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundGreen = findCellWithColor(buffer, "i", Color.GREEN);
        assertTrue(foundGreen, "Inflight status should be rendered in GREEN");
    }

    @Test
    void renderBlockedStatusInRed() {
        addInflight("ID-001", "route1", "route1", "to1", 500, true);

        InflightTab tab = new InflightTab(ctx);

        Rect area = new Rect(0, 0, 160, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRed = findCellWithColor(buffer, "b", Color.LIGHT_RED);
        assertTrue(foundRed, "Blocked status should be rendered in LIGHT_RED");
    }

    @Test
    void renderExchangeIdInCyan() {
        addInflight("ID-001", "route1", "route1", "to1", 500, false);

        InflightTab tab = new InflightTab(ctx);

        Rect area = new Rect(0, 0, 160, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundCyan = findCellWithColor(buffer, "I", Color.CYAN);
        assertTrue(foundCyan, "Exchange ID should use CYAN color");
    }

    @Test
    void renderMultipleExchangesAllAppear() {
        addInflight("ID-AAA", "route-a", "route-a", "to1", 500, false);
        addInflight("ID-BBB", "route-b", "route-b", "to2", 1500, false);

        InflightTab tab = new InflightTab(ctx);
        String rendered = renderToString(tab, 160, 20);

        assertTrue(rendered.contains("ID-AAA"), "Should render first exchange");
        assertTrue(rendered.contains("ID-BBB"), "Should render second exchange");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        InflightTab tab = new InflightTab(ctx);
        String rendered = renderToString(tab, 160, 20);

        // The placeholder text is placed in the first column (width 10),
        // so the full text gets truncated. Check for the visible portion.
        assertTrue(rendered.contains("No infligh"),
                "Should show placeholder when no inflight exchanges exist");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        InflightTab tab = new InflightTab(ctx);
        String rendered = renderToString(tab, 120, 15);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderBrowseDisabledMessage() {
        info.inflightBrowseEnabled = false;

        InflightTab tab = new InflightTab(ctx);
        String rendered = renderToString(tab, 160, 20);

        assertTrue(rendered.contains("Inflight browse is not enabled"),
                "Should show browse disabled message when inflightBrowseEnabled is false");
    }

    @Test
    void renderShowsSortColumn() {
        addInflight("ID-001", "route1", "route1", "to1", 500, false);

        InflightTab tab = new InflightTab(ctx);
        String rendered = renderToString(tab, 160, 20);

        assertTrue(rendered.contains("sort:duration"), "Title should show current sort column");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addInflight("ID-001", "route1", "route1", "to1", 500, false);

        InflightTab tab = new InflightTab(ctx);

        // Press 's' to cycle sort — default is "duration" (index 3), next is "status" (index 0)
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));

        String rendered = renderToString(tab, 160, 20);
        assertTrue(rendered.contains("sort:status"), "Sort should cycle to 'status'");
    }

    @Test
    void renderFooterHints() {
        InflightTab tab = new InflightTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
    }

    // ---- Helper methods ----

    private InflightInfo addInflight(
            String exchangeId, String fromRouteId, String atRouteId,
            String nodeId, long duration, boolean blocked) {
        InflightInfo ii = new InflightInfo();
        ii.exchangeId = exchangeId;
        ii.fromRouteId = fromRouteId;
        ii.atRouteId = atRouteId;
        ii.nodeId = nodeId;
        ii.duration = duration;
        ii.blocked = blocked;
        info.inflightExchanges.add(ii);
        return ii;
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
