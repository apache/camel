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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        Theme.resetForTesting();
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

        Color successColor = Theme.success().fg().orElse(Color.GREEN);
        assertTrue(TuiTestHelper.findCellWithColor(buffer, "D", successColor),
                "Done status should contain a cell rendered in success color");
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

        Color errorColor = Theme.error().fg().orElse(Color.LIGHT_RED);
        assertTrue(TuiTestHelper.findCellWithColor(buffer, "F", errorColor),
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

        assertTrue(TuiTestHelper.findCellWithColor(buffer, "m", Theme.accent()),
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

    // ---- Helper methods ----

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

    @Test
    void theTitleGivesTheElapsedTimeOfTheWholeExchange() {
        // checkout calls payment-provider over direct: the called route returns first, after 0ms
        HistoryEntry from = historyEntry("checkout", 0, false);
        HistoryEntry called = historyEntry("payment-provider", 0, true);
        HistoryEntry done = historyEntry("checkout", 17, true);
        String title = HistoryTab.buildHistoryTitle(List.of(from, called, done)).content().spans().stream()
                .map(Span::content).reduce("", String::concat);
        assertTrue(title.contains("elapsed:17ms"), title);
    }

    private static HistoryEntry historyEntry(String routeId, long elapsed, boolean last) {
        HistoryEntry e = new HistoryEntry();
        e.routeId = routeId;
        e.elapsed = elapsed;
        e.last = last;
        return e;
    }

    @Test
    void theWaterfallBarsStandWhereTheStepsRan() {
        // the exchange was created 2s before it was processed: the route row starts with its first step
        // the last row of a route carries the creation time too: it stands where its route started
        List<HistoryTab.WaterfallStep> steps = List.of(
                step("from1", true, false, 17, 1_000),
                step("unmarshal1", false, false, 0, 3_000),
                step("to2", false, false, 16, 3_001),
                step("from2", true, false, 12, 1_000),
                step("log3", false, false, 0, 3_005),
                step("from2", false, true, 12, 1_000),
                step("from1", false, true, 17, 1_000));
        long[] offsets = HistoryTab.waterfallOffsets(steps);
        assertTrue(Arrays.equals(new long[] { 0, 0, 1, 5, 5, 5, 0 }, offsets), Arrays.toString(offsets));

        // without the times of the steps, every bar starts at 0
        long[] none = HistoryTab.waterfallOffsets(
                List.of(step("from1", true, false, 5, 0), step("log1", false, false, 5, 0)));
        assertTrue(Arrays.equals(new long[] { 0, 0 }, none));
    }

    private static HistoryTab.WaterfallStep step(String id, boolean first, boolean last, long elapsed, long start) {
        return new HistoryTab.WaterfallStep(id, id, "-->", first, last, 0, elapsed, 0, start);
    }
}
