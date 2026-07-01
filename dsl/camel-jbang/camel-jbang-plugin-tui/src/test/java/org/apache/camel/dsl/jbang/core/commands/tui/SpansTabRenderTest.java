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
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link SpansTab}. Renders the spans tab into a virtual terminal buffer and inspects
 * the rendered output.
 */
class SpansTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;
    private AtomicReference<List<SpanEntry>> spans;

    @BeforeEach
    void setUp() {
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
        spans = new AtomicReference<>(new ArrayList<>());
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        SpansTab tab = new SpansTab(ctx, spans);
        String rendered = TuiTestHelper.renderToString(tab, 100, 10);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsBlockTitle() {
        SpansTab tab = new SpansTab(ctx, spans);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("Spans") || rendered.contains("Traces"),
                "Should show 'Spans' or 'Traces' in the block title");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        SpansTab tab = new SpansTab(ctx, spans);
        String rendered = TuiTestHelper.renderToString(tab, 140, 10);

        assertTrue(rendered.contains("No OTel") || rendered.contains("No spans") || rendered.contains("--observe"),
                "Should show placeholder when no spans exist");
    }

    @Test
    void renderShowsSpanData() {
        spans.get().add(createSpan("abcd1234efgh5678", "span001", "timer:orders", "OK", 42));

        SpansTab tab = new SpansTab(ctx, spans);
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("abcd1234"),
                "Should render the trace ID (or its short form)");
    }

    @Test
    void renderErrorSpanInRed() {
        // Add an OK trace first (which will be auto-selected/highlighted),
        // then an ERROR trace which will NOT be highlighted, preserving its LIGHT_RED color.
        spans.get().add(createSpan("aaaa0001", "span000", "timer:ok", "OK", 50));
        spans.get().add(createSpan("trace0001", "span001", "direct:fail", "ERROR", 100));

        SpansTab tab = new SpansTab(ctx, spans);

        Rect area = new Rect(0, 0, 140, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRed = TuiTestHelper.findCellWithColor(buffer, "E", Color.LIGHT_RED);
        assertTrue(foundRed, "ERROR status should be rendered in LIGHT_RED");
    }

    @Test
    void renderShowsTableHeaders() {
        spans.get().add(createSpan("trace0001", "span001", "timer:tick", "OK", 10));

        SpansTab tab = new SpansTab(ctx, spans);
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("TRACE-ID"), "Should show TRACE-ID header");
        assertTrue(rendered.contains("STATUS"), "Should show STATUS header");
        assertTrue(rendered.contains("DURATION"), "Should show DURATION header");
    }

    @Test
    void renderFooterHints() {
        SpansTab tab = new SpansTab(ctx, spans);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("F5"), "Footer should contain F5 refresh hint");
    }

    @Test
    void renderMultipleSpansAllAppear() {
        spans.get().add(createSpan("aaaa1111bbbb2222", "span001", "timer:a", "OK", 10));
        spans.get().add(createSpan("cccc3333dddd4444", "span002", "timer:b", "OK", 20));

        SpansTab tab = new SpansTab(ctx, spans);
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("aaaa1111"), "Should render first trace ID");
        assertTrue(rendered.contains("cccc3333"), "Should render second trace ID");
    }

    @Test
    void clickingATraceRowSelectsItAndSurvivesReRender() {
        // Two traces so the clicked (second) row differs from the auto-selected first row.
        spans.get().add(createSpan("aaaa1111bbbb2222", "span001", "timer:a", "OK", 10));
        spans.get().add(createSpan("cccc3333dddd4444", "span002", "timer:b", "OK", 20));

        SpansTab tab = new SpansTab(ctx, spans);
        Rect area = new Rect(0, 0, 140, 20);

        // First render captures the table geometry and auto-selects the first trace.
        renderOnce(tab, area);
        assertEquals(Integer.valueOf(0), tab.getTableState().selected(), "the first trace is auto-selected initially");

        // Click the second data row. The border and header put the first data row at y = 2.
        assertTrue(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 5, 3), area),
                "a click on a trace row is consumed");
        assertEquals(Integer.valueOf(1), tab.getTableState().selected(), "clicking the second row selects it");

        // The next render restores the selection from selectedListTraceId; because the click synced that
        // field (as keyboard navigation does), the clicked row must survive instead of snapping back to row 0.
        renderOnce(tab, area);
        assertEquals(Integer.valueOf(1), tab.getTableState().selected(),
                "the clicked selection survives the next render");
    }

    // ---- Helper methods ----

    private void renderOnce(SpansTab tab, Rect area) {
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
    }

    private SpanEntry createSpan(String traceId, String spanId, String name, String status, long durationMs) {
        return new SpanEntry(
                traceId, spanId, null, name, "INTERNAL", status,
                0L, durationMs * 1_000_000L, durationMs,
                "route1", null, "camel", Map.of());
    }

}
