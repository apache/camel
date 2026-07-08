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
 * Higher-level rendering tests for {@link ConsumersTab}. These tests render the tab into a virtual terminal buffer via
 * {@link Frame#forTesting(Buffer)} and inspect the rendered cell content.
 */
class ConsumersTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
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
        addConsumer("route1", "timer://hello", "Started", "org.apache.camel.component.timer.TimerConsumer");

        ConsumersTab tab = new ConsumersTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("ROUTE"), "Should show ROUTE header");
        assertTrue(rendered.contains("STATUS"), "Should show STATUS header");
        assertTrue(rendered.contains("TYPE"), "Should show TYPE header");
        assertTrue(rendered.contains("URI"), "Should show URI header");
    }

    @Test
    void renderShowsConsumerData() {
        addConsumer("timer-route", "timer://hello?period=2000", "Started",
                "org.apache.camel.component.timer.TimerConsumer");

        ConsumersTab tab = new ConsumersTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 160, 20);

        assertTrue(rendered.contains("timer-route"), "Should render consumer route id");
        assertTrue(rendered.contains("timer://hello"), "Should render consumer URI");
    }

    @Test
    void renderRouteIdInCyan() {
        addConsumer("my-route", "timer://tick", "Started",
                "org.apache.camel.component.timer.TimerConsumer");

        ConsumersTab tab = new ConsumersTab(ctx);

        Rect area = new Rect(0, 0, 120, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "m", Color.CYAN),
                "Route id should be rendered in CYAN");
    }

    @Test
    void renderStartedStatusInGreen() {
        addConsumer("route1", "timer://hello", "Started",
                "org.apache.camel.component.timer.TimerConsumer");

        ConsumersTab tab = new ConsumersTab(ctx);

        Rect area = new Rect(0, 0, 120, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "S", Theme.success().fg().orElse(Color.GREEN)),
                "Started status should be rendered in GREEN");
    }

    @Test
    void renderStoppedStatusInRed() {
        addConsumer("route1", "timer://hello", "Stopped",
                "org.apache.camel.component.timer.TimerConsumer");

        ConsumersTab tab = new ConsumersTab(ctx);

        Rect area = new Rect(0, 0, 120, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "S", Theme.error().fg().orElse(Color.LIGHT_RED)),
                "Stopped status should be rendered in LIGHT_RED");
    }

    @Test
    void renderMultipleConsumersAllAppear() {
        addConsumer("route-alpha", "timer://alpha", "Started",
                "org.apache.camel.component.timer.TimerConsumer");
        addConsumer("route-beta", "seda://beta", "Started",
                "org.apache.camel.component.seda.SedaConsumer");

        ConsumersTab tab = new ConsumersTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("route-alpha"), "Should render first consumer");
        assertTrue(rendered.contains("route-beta"), "Should render second consumer");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        // No consumers added
        ConsumersTab tab = new ConsumersTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 10);

        assertTrue(rendered.contains("No consumers"),
                "Should show placeholder when no consumers exist");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        ConsumersTab tab = new ConsumersTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 10);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsSortColumn() {
        addConsumer("route1", "timer://hello", "Started",
                "org.apache.camel.component.timer.TimerConsumer");

        ConsumersTab tab = new ConsumersTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("ROUTE▼"), "Column header should show sort indicator on default sort column");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addConsumer("route1", "timer://hello", "Started",
                "org.apache.camel.component.timer.TimerConsumer");

        ConsumersTab tab = new ConsumersTab(ctx);

        // Default sort is "id" — header should have a sort indicator on ROUTE
        String rendered1 = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered1.contains("ROUTE▼") || rendered1.contains("ROUTE▲"),
                "Default sort should show indicator on ROUTE column");

        // Press 's' to cycle to next sort column (status)
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered2 = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered2.contains("STATUS▼") || rendered2.contains("STATUS▲"),
                "After one sort cycle, indicator should move to STATUS");
    }

    @Test
    void renderFooterHints() {
        ConsumersTab tab = new ConsumersTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
    }

    // ---- Helper methods ----

    private ConsumerInfo addConsumer(String id, String uri, String state, String className) {
        ConsumerInfo ci = new ConsumerInfo();
        ci.id = id;
        ci.uri = uri;
        ci.state = state;
        ci.className = className;
        info.consumers.add(ci);
        return ci;
    }
}
