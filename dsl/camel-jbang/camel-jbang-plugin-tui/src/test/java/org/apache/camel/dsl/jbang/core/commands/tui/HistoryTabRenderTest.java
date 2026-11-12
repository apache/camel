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
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void fFollowsTheLastExchange() {
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        assertTrue(footer(tab).contains("follow [off]"), footer(tab));

        assertTrue(tab.handleKeyEvent(KeyEvent.ofChar('f')));
        assertTrue(tab.isHistoryFollow());
        assertTrue(footer(tab).contains("follow [on]"), footer(tab));

        assertTrue(tab.handleKeyEvent(KeyEvent.ofChar('f')));
        assertTrue(!tab.isHistoryFollow());
    }

    private static String footer(HistoryTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFooter(spans);
        return spans.stream().map(Span::content).reduce("", String::concat);
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
    void theTableDataNamesTheInspectTabAndReadingStepsKeepsTheView() {
        traces.set(List.of(createTrace("EX-010", "route1", "Done", true, true)));
        HistoryTab tab = new HistoryTab(ctx, traces, new HashMap<>());
        TuiTestHelper.renderToString(tab, 120, 30);

        JsonObject table = tab.getTableDataAsJson();
        assertEquals("Inspect", table.getString("tab"), "the name tui_get_options lists");
        assertEquals("Traces", table.getString("view"));

        // tui_get_history with an exchangeId: the steps of that exchange, while the tab still lists the traces
        JsonObject steps = tab.traceStepsAsJson("EX-010");
        assertEquals("Trace Steps", steps.getString("view"));
        assertEquals("EX-010", steps.getString("exchangeId"));
        assertEquals(1, steps.getInteger("totalRows"));
        assertEquals("Traces", tab.getTableDataAsJson().getString("view"));
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

    @Test
    void theStepsOfACircuitBreakerAreUnderIt() {
        // as the history of the circuit breaker example has it: the breaker runs its steps on a copy (131) of the
        // exchange (130), and the copy calls the supplier route over direct
        List<HistoryEntry> entries = List.of(
                step("130", "stock-check", "from1", "from", "timer:stock?period=1000", true, false),
                step("130", "stock-check", "circuitBreaker1", "circuitBreaker", "circuitBreaker", false, false),
                step("131", "stock-check", "to1", "to", "to[direct:supplier]", false, false),
                step("131", "supplier", "from2", "from", "direct:supplier", true, false),
                step("131", "supplier", "setBody3", "setBody", "setBody[simple{in stock}]", false, false),
                step("131", "supplier", "from2", "from", "direct:supplier", false, true),
                step("130", "stock-check", "log1", "log", "log[Stock check]", false, false),
                step("130", "stock-check", "from1", "from", "timer:stock?period=1000", false, true));

        List<HistoryEntry> ordered = new HistoryTab(ctx, traces, new HashMap<>()).reorderHistoryDepthFirst(entries);

        assertEquals(List.of("from1", "circuitBreaker1", "to1", "from2", "setBody3", "from2", "log1", "from1"),
                ordered.stream().map(e -> e.nodeId).toList());
        assertEquals(List.of(0, 0, 1, 1, 1, 1, 0, 0), ordered.stream().map(e -> e.inlineDepth).toList());
    }

    private static HistoryEntry step(
            String exchangeId, String routeId, String nodeId, String shortName, String label, boolean first,
            boolean last) {
        HistoryEntry e = new HistoryEntry();
        e.exchangeId = exchangeId;
        e.routeId = routeId;
        e.nodeId = nodeId;
        e.nodeShortName = shortName;
        e.nodeLabel = label;
        e.first = first;
        e.last = last;
        return e;
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

    @Test
    void theDiagramIsDrawnAgainForAnotherExchange() {
        List<HistoryEntry> shown = List.of(historyEntry("EX-1"), historyEntry("EX-1"));

        // the history was read again (tab opened again, F5) and starts with another exchange
        assertTrue(HistoryTab.isOtherExchange(shown, List.of(historyEntry("EX-2"))));
        // the same exchange: the diagram already shows it
        assertFalse(HistoryTab.isOtherExchange(shown, List.of(historyEntry("EX-1"), historyEntry("EX-1"))));
        // nothing drawn yet, or no history: nothing to draw again
        assertFalse(HistoryTab.isOtherExchange(List.of(), List.of(historyEntry("EX-2"))));
        assertFalse(HistoryTab.isOtherExchange(shown, List.of()));
    }

    private static HistoryEntry historyEntry(String exchangeId) {
        HistoryEntry e = new HistoryEntry();
        e.exchangeId = exchangeId;
        return e;
    }

    private static HistoryTab.WaterfallStep step(String id, boolean first, boolean last, long elapsed, long start) {
        return new HistoryTab.WaterfallStep(id, id, "-->", first, last, 0, elapsed, 0, start);
    }

    @Test
    void theInfoPanelSaysWhatAStepIsAndWhatItChangedInWords() {
        // CAMEL-26864: it showed the marker of the table ("Step: 1/6 *->") and an empty "Changed:"
        assertEquals("message in", HistoryTab.stepKind(true, false, false, false));
        assertEquals("message in (remote)", HistoryTab.stepKind(true, false, true, false));
        assertEquals("message out", HistoryTab.stepKind(false, true, false, false));
        assertEquals("to a remote endpoint", HistoryTab.stepKind(false, false, true, false));
        assertEquals("to a stub", HistoryTab.stepKind(false, false, true, true));
        assertEquals("", HistoryTab.stepKind(false, false, false, false));

        assertEquals("(first step)", HistoryTab.changedWords("", true).content());
        assertEquals("nothing", HistoryTab.changedWords("    ", false).content());
        assertEquals("body, properties", HistoryTab.changedWords("B P ", false).content());
    }
}
