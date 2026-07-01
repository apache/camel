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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link OverviewTab}. These tests render the tab into a virtual terminal buffer via
 * {@link Frame#forTesting(Buffer)} and inspect the rendered cell content.
 */
class OverviewTabRenderTest {

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
        info.state = 5;

        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });
        String rendered = TuiTestHelper.renderToString(tab, 160, 30);

        assertTrue(rendered.contains("PID"), "Should show PID header");
        assertTrue(rendered.contains("NAME"), "Should show NAME header");
        assertTrue(rendered.contains("STATUS"), "Should show STATUS header");
        assertTrue(rendered.contains("TOTAL"), "Should show TOTAL header");
    }

    @Test
    void renderShowsIntegrationData() {
        info.state = 5;

        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });
        String rendered = TuiTestHelper.renderToString(tab, 160, 30);

        assertTrue(rendered.contains("1234"), "Should render PID");
        assertTrue(rendered.contains("test-app"), "Should render integration name");
    }

    @Test
    void renderNameInCyan() {
        info.state = 5;

        // Ensure no row is selected (highlighted), otherwise the highlight style
        // overrides fg colors to WHITE.
        ctx.selectedPid = null;
        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundCyan = TuiTestHelper.findCellWithColor(buffer, "t", Color.CYAN);
        assertTrue(foundCyan, "Name should be rendered in CYAN");
    }

    @Test
    void renderStartedStatusInGreen() {
        info.state = 5;

        // Ensure no row is selected (highlighted), otherwise the highlight style
        // overrides fg colors to WHITE.
        ctx.selectedPid = null;
        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundGreen = TuiTestHelper.findCellWithColor(buffer, "R", Color.LIGHT_GREEN);
        assertTrue(foundGreen, "Running status should be rendered in LIGHT_GREEN");
    }

    @Test
    void renderStoppedStatusInRed() {
        info.state = 9;

        // Ensure no row is selected (highlighted), otherwise the highlight style
        // overrides fg colors to WHITE.
        ctx.selectedPid = null;
        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRed = TuiTestHelper.findCellWithColor(buffer, "S", Color.LIGHT_RED);
        assertTrue(foundRed, "Stopped status should be rendered in LIGHT_RED");
    }

    @Test
    void renderMultipleIntegrationsAllAppear() {
        IntegrationInfo info2 = new IntegrationInfo();
        info2.pid = "5678";
        info2.name = "other-app";
        info2.state = 5;

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info, info2));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        MonitorContext ctx2 = new MonitorContext(data, infraData);

        OverviewTab tab = new OverviewTab(ctx2, new MetricsCollector(), new HashSet<>(), () -> {
        });
        String rendered = TuiTestHelper.renderToString(tab, 160, 30);

        assertTrue(rendered.contains("test-app"), "Should render first integration");
        assertTrue(rendered.contains("other-app"), "Should render second integration");
    }

    @Test
    void renderShowsSortColumn() {
        info.state = 5;

        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });
        String rendered = TuiTestHelper.renderToString(tab, 160, 30);

        assertTrue(rendered.contains("NAME▼") || rendered.contains("NAME▲"),
                "Default sort should show indicator on NAME column");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        info.state = 5;

        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });

        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 160, 30);
        assertTrue(rendered.contains("VERSION▼") || rendered.contains("VERSION▲"),
                "After one sort cycle, indicator should move to VERSION");
    }

    @Test
    void renderFooterHints() {
        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
        assertTrue(footer.contains("navigate"), "Footer should contain navigate hint");
    }

    @Test
    void clickingAnIntegrationRowSelectsItAndFiresPidChange() {
        info.state = 5;
        IntegrationInfo info2 = new IntegrationInfo();
        info2.pid = "5678";
        info2.name = "other-app";
        info2.state = 5;

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info, info2));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        MonitorContext ctx2 = new MonitorContext(data, infraData);
        // The default sort is by name, so the rows are ordered other-app (5678), test-app (1234).
        // Start with test-app selected so that clicking the first row (other-app) changes the selection.
        ctx2.selectedPid = "1234";

        boolean[] pidChanged = { false };
        OverviewTab tab = new OverviewTab(ctx2, new MetricsCollector(), new HashSet<>(), () -> pidChanged[0] = true);

        Rect area = new Rect(0, 0, 160, 30);
        renderOnce(tab, area);

        // The border and header put the first data row at tableArea.y() + 2.
        Rect tableArea = tab.getTableArea();
        int firstRowY = tableArea.y() + 2;
        assertTrue(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, tableArea.x() + 2, firstRowY), area),
                "a click on an integration row is consumed");
        assertEquals("5678", ctx2.selectedPid, "clicking the first row (other-app) selects that integration");
        assertTrue(pidChanged[0], "selecting a different integration fires the pid-changed callback");
    }

    @Test
    void clickingTheDividerRowSelectsNothing() {
        info.state = 5;
        InfraInfo infra = new InfraInfo();
        infra.pid = "9999";
        infra.alias = "kafka";
        infra.alive = true;

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of(infra));
        MonitorContext ctx2 = new MonitorContext(data, infraData);
        ctx2.selectedPid = "1234";

        boolean[] pidChanged = { false };
        OverviewTab tab = new OverviewTab(ctx2, new MetricsCollector(), new HashSet<>(), () -> pidChanged[0] = true);

        Rect area = new Rect(0, 0, 160, 30);
        renderOnce(tab, area);

        // Rows: 0 = the integration, 1 = the "Dev/Infra Services" divider, 2 = the infra service.
        Rect tableArea = tab.getTableArea();
        int dividerRowY = tableArea.y() + 3;
        assertTrue(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, tableArea.x() + 2, dividerRowY), area),
                "a click on the divider row is consumed");
        assertEquals("1234", ctx2.selectedPid, "the divider row is not selectable, so the selection is unchanged");
        assertFalse(pidChanged[0], "clicking the divider does not fire the pid-changed callback");
    }

    @Test
    void renderFailedCountInRed() {
        info.state = 5;
        info.failed = 42;

        // Ensure no row is selected (highlighted), otherwise the highlight style
        // overrides fg colors to WHITE.
        ctx.selectedPid = null;
        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRed = TuiTestHelper.findCellWithColor(buffer, "4", Color.LIGHT_RED);
        assertTrue(foundRed, "Failed count should be rendered in LIGHT_RED");
    }

    // ---- Helper methods ----

    private void renderOnce(OverviewTab tab, Rect area) {
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
    }

}
