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
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiMessages;
import org.apache.camel.dsl.jbang.core.common.genai.SpanEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
        Theme.resetForTesting();
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

        boolean foundRed = TuiTestHelper.findCellWithColor(buffer, "E", Theme.error().fg().orElse(Color.LIGHT_RED));
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
    void tOpensTheConversationOfAGenAiSpan() {
        String input = new GenAiMessages()
                .text(GenAiMessages.USER, "What is the status of order A-17?")
                .toJson(8192);
        String output = new GenAiMessages()
                .text(GenAiMessages.ASSISTANT, "Order A-17 was delivered.")
                .finishReason("stop")
                .toJson(8192);
        spans.get().add(new SpanEntry(
                "trace0001", "span001", null, "chat llama3.2", "CLIENT", "OK", 0L, 500_000_000L, 500, "route1", null,
                "camel", Map.of(GenAiAttributes.OPERATION_NAME, "chat",
                        GenAiAttributes.INPUT_MESSAGES, input,
                        GenAiAttributes.OUTPUT_MESSAGES, output)));
        SpansTab tab = new SpansTab(ctx, spans);
        TuiTestHelper.renderToString(tab, 140, 30);
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(TuiTestHelper.renderToString(tab, 140, 30)).contains("Conversation: t shows the messages");
        assertThat(footer(tab)).contains("conversation");

        tab.handleKeyEvent(KeyEvent.ofChar('t', KeyModifiers.NONE));

        assertThat(tab.transcriptForTesting().isVisible()).isTrue();
        assertThat(TuiTestHelper.renderToString(tab, 140, 30))
                .contains("Conversation · ")
                .contains("What is the status of order A-17?")
                .contains("Order A-17 was delivered.");
        assertThat(footer(tab)).contains("close").doesNotContain("refresh");

        assertThat(tab.handleEscape()).isTrue();
        assertThat(tab.transcriptForTesting().isVisible()).isFalse();
    }

    @Test
    void conversationOfASpanWithoutMessagesSaysTheLevel() {
        spans.get().add(new SpanEntry(
                "trace0001", "span001", null, "chat llama3.2", "CLIENT", "OK", 0L, 500_000_000L, 500, "route1", null,
                "camel", Map.of(GenAiAttributes.OPERATION_NAME, "chat", GenAiAttributes.REQUEST_MODEL, "llama3.2")));
        SpansTab tab = new SpansTab(ctx, spans);
        TuiTestHelper.renderToString(tab, 140, 30);
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(TuiTestHelper.renderToString(tab, 140, 30))
                .contains("Conversation: recorded at camel.aiObservability.level=full");

        tab.handleKeyEvent(KeyEvent.ofChar('t', KeyModifiers.NONE));

        assertThat(tab.transcriptForTesting().markdownForTesting()).contains(TranscriptOverlay.NO_CONTENT);
    }

    @Test
    void waterfallLabelsGenAiSpansAndSummarizesTheAgentRun() {
        spans.get().add(new SpanEntry(
                "trace0001", "agent", null, "invoke_agent", "INTERNAL", "OK", 0L, 400_000_000L, 400, "route1", null,
                "camel", Map.of(GenAiAttributes.OPERATION_NAME, "invoke_agent",
                        GenAiAttributes.AGENT_NAME, "support-agent",
                        GenAiAttributes.REQUEST_MODEL, "llama3.2")));
        spans.get().add(new SpanEntry(
                "trace0001", "chat", "agent", "chat", "CLIENT", "OK", 0L, 300_000_000L, 300, "route1", null,
                "camel", Map.of(GenAiAttributes.OPERATION_NAME, "chat",
                        GenAiAttributes.REQUEST_MODEL, "llama3.2",
                        GenAiAttributes.INPUT_TOKENS, 166L,
                        GenAiAttributes.OUTPUT_TOKENS, 25L,
                        GenAiAttributes.FINISH_REASONS, "stop")));
        spans.get().add(new SpanEntry(
                "trace0001", "tool", "agent", "execute_tool", "INTERNAL", "OK", 300_000_000L, 310_000_000L, 10,
                "route1", null, "camel", Map.of(GenAiAttributes.OPERATION_NAME, "execute_tool",
                        GenAiAttributes.TOOL_NAME, "lookupOrder")));
        SpansTab tab = new SpansTab(ctx, spans);
        TuiTestHelper.renderToString(tab, 160, 34);
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(TuiTestHelper.renderToString(tab, 160, 34))
                .contains("◆ agent support-agent · llama3.2")
                .contains("● chat llama3.2 · 166→25 · stop")
                .contains("⚙ tool lookupOrder")
                .contains("Steps:  1 model call, 1 tool call")
                .contains("1. ● chat llama3.2");
    }

    @Test
    void waterfallBarsLineUpWhateverTheWidthOfTheLabels() {
        // GenAI symbols, a CJK route, a flag and a ZWJ emoji: all spans start at 0, so all bars start in one column
        spans.get().add(new SpanEntry(
                "trace0001", "root", null, "root", "INTERNAL", "OK", 0L, 400_000_000L, 400,
                "注文ルート", null, "camel", Map.of("camel.uri", "timer://\uD83C\uDDEB\uD83C\uDDF7")));
        spans.get().add(new SpanEntry(
                "trace0001", "agent", "root", "agent", "INTERNAL", "OK", 0L, 300_000_000L, 300,
                "route1", null, "camel", Map.of(GenAiAttributes.OPERATION_NAME, "invoke_agent",
                        GenAiAttributes.AGENT_NAME, "\uD83D\uDC69\u200D\uD83D\uDCBB")));
        spans.get().add(new SpanEntry(
                "trace0001", "tool", "agent", "tool", "INTERNAL", "OK", 0L, 200_000_000L, 200,
                "route1", null, "camel", Map.of(GenAiAttributes.OPERATION_NAME, "execute_tool",
                        GenAiAttributes.TOOL_NAME, "lookupOrder")));
        SpansTab tab = new SpansTab(ctx, spans);
        TuiTestHelper.renderToString(tab, 140, 30);
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        tab.render(Frame.forTesting(buffer), area);

        List<Integer> barColumns = new ArrayList<>();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                if ("█".equals(buffer.get(x, y).symbol())) {
                    barColumns.add(x);
                    break;
                }
            }
        }
        assertThat(barColumns).hasSize(3).containsOnly(barColumns.get(0));
    }

    private static String footer(SpansTab tab) {
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        return footerSpans.stream().map(Span::content).reduce("", String::concat);
    }

    // ---- Helper methods ----

    private SpanEntry createSpan(String traceId, String spanId, String name, String status, long durationMs) {
        return new SpanEntry(
                traceId, spanId, null, name, "INTERNAL", status,
                0L, durationMs * 1_000_000L, durationMs,
                "route1", null, "camel", Map.of());
    }

    @Test
    void remoteComponentsComeFromTheCatalog() {
        // as the catalog marks them; a scheme it does not know counts as remote
        assertThat(SpansTab.isRemoteScheme("kafka")).isTrue();
        assertThat(SpansTab.isRemoteScheme("acme")).isTrue();
        assertThat(SpansTab.isRemoteScheme("log")).isFalse();
        // not in the list the check used to keep: a timer or an xslt step is not a remote call
        assertThat(SpansTab.isRemoteScheme("timer")).isFalse();
        assertThat(SpansTab.isRemoteScheme("xslt")).isFalse();
    }
}
