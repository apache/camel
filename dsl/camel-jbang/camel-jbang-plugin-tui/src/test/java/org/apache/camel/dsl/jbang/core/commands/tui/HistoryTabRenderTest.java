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
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering tests for {@link HistoryTab}. These tests render the tab into a virtual terminal buffer and inspect the
 * rendered cell content.
 */
class HistoryTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;
    private AtomicReference<List<TraceEntry>> traces;

    @BeforeEach
    void setUp() {
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
        traces = new AtomicReference<>(new ArrayList<>());
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsBlockTitle() {
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("History") || rendered.contains("Trace"),
                "Should show History or Trace in the block title");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Select a history entry") || rendered.contains("History of last completed"),
                "Should show placeholder or title when traces are empty");
    }

    @Test
    void renderShowsTraceEntry() {
        TraceEntry te = createTrace("EX-001", "route1", "Done", true, true);
        traces.set(List.of(te));
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        String rendered = TuiTestHelper.renderToString(tab, 120, 30);
        assertTrue(rendered.contains("EX-001"), "Should show the exchange ID in the rendered output");
    }

    @Test
    void renderDoneStatusInGreen() {
        TraceEntry te = createTrace("EX-002", "route1", "Done", true, true);
        traces.set(List.of(te));
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());

        Rect area = new Rect(0, 0, 120, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "D", Color.GREEN),
                "Done status should contain a cell rendered in GREEN");
    }

    @Test
    void renderFailedStatusInRed() {
        TraceEntry te = createTrace("EX-003", "route1", "Failed", true, true);
        te.failed = true;
        traces.set(List.of(te));
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());

        Rect area = new Rect(0, 0, 120, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "F", Color.LIGHT_RED),
                "Failed status should contain a cell rendered in LIGHT_RED");
    }

    @Test
    void renderRouteIdInCyan() {
        TraceEntry te = createTrace("EX-004", "myRoute", "Done", true, true);
        traces.set(List.of(te));
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());

        Rect area = new Rect(0, 0, 120, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "m", Color.CYAN),
                "Route ID should contain a cell rendered in CYAN");
    }

    @Test
    void renderMultipleTracesAllAppear() {
        TraceEntry te1 = createTrace("EX-010", "route1", "Done", true, true);
        TraceEntry te2 = createTrace("EX-020", "route2", "Done", true, true);
        traces.set(List.of(te1, te2));
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);
        assertTrue(rendered.contains("EX-010"), "First trace entry should appear");
        assertTrue(rendered.contains("EX-020"), "Second trace entry should appear");
    }

    @Test
    void renderFooterHints() {
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        String footer = footerSpans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(footer.contains("Esc") || footer.contains("back"),
                "Footer should contain Esc or back hint");
    }

    @Test
    void renderShowsTableHeaders() {
        TraceEntry te = createTrace("EX-005", "route1", "Done", true, true);
        traces.set(List.of(te));
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);
        assertTrue(rendered.contains("TIME"), "Should show TIME header");
        assertTrue(rendered.contains("ROUTE"), "Should show ROUTE header");
        assertTrue(rendered.contains("STATUS"), "Should show STATUS header");
    }

    @Test
    void clickingATraceRowSelectsItInTheTraceList() {
        traces.set(List.of(
                createTrace("EX-100", "route1", "Done", true, true),
                createTrace("EX-200", "route2", "Done", true, true)));
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());

        Rect area = new Rect(0, 0, 140, 30);
        renderOnce(tab, area);

        // The trace list is the active table when the tracer is active. Border + header put the first data row
        // at tableArea.y() + 2, so the second row is one below.
        Rect tableArea = tab.getTableArea();
        assertNotNull(tableArea, "the trace list must expose a clickable table area");
        int secondRowY = tableArea.y() + 3;
        assertTrue(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, tableArea.x() + 2, secondRowY), area),
                "a click on a trace row is consumed");
        assertEquals(Integer.valueOf(1), tab.getTableState().selected(), "clicking the second trace row selects it");
    }

    @Test
    void clickingAHistoryRowSelectsItWhenTracerIsInactive() {
        // With no live traces the Inspect tab shows the history list, backed by a different table state and area
        // than the trace list. This is the regression: the mouse handler must target the history table here.
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        tab.historyEntries = List.of(historyEntry("route1", "n1"), historyEntry("route2", "n2"));

        Rect area = new Rect(0, 0, 140, 30);
        renderOnce(tab, area);

        Rect tableArea = tab.getTableArea();
        assertNotNull(tableArea, "the history list must expose a clickable table area when the tracer is inactive");
        int secondRowY = tableArea.y() + 3;
        assertTrue(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, tableArea.x() + 2, secondRowY), area),
                "a click on a history row is consumed");
        assertEquals(Integer.valueOf(1), tab.getTableState().selected(), "clicking the second history row selects it");
    }

    // ---- Helper methods ----

    private void renderOnce(HistoryTab tab, Rect area) {
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
    }

    private HistoryEntry historyEntry(String routeId, String nodeId) {
        HistoryEntry he = new HistoryEntry();
        he.pid = "1234";
        he.exchangeId = "EX-" + nodeId;
        he.timestamp = "12:00:00.000";
        he.epochMs = System.currentTimeMillis();
        he.routeId = routeId;
        he.nodeId = nodeId;
        he.processor = "log";
        he.direction = "in";
        he.first = true;
        he.last = true;
        he.elapsed = 10;
        return he;
    }

    private TraceEntry createTrace(String exchangeId, String routeId, String status, boolean first, boolean last) {
        TraceEntry te = new TraceEntry();
        te.pid = "1234";
        te.uid = exchangeId + "-1";
        te.exchangeId = exchangeId;
        te.routeId = routeId;
        te.timestamp = "12:00:00.000";
        te.epochMs = System.currentTimeMillis();
        te.status = status;
        te.direction = "in";
        te.first = first;
        te.last = last;
        te.processor = "log";
        te.nodeId = "log1";
        te.elapsed = 100;
        return te;
    }

}
