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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPanelGenAiUsageTest {

    private AtomicReference<List<SpanEntry>> spans;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        spans = new AtomicReference<>(List.of());
    }

    @Test
    void combinedUsageIncludesRouteGenAiSpans() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.recordUsageForTesting(new AiUsageEntry(
                "gpt-4o-mini", "openai", 10, 5, 15, 1000, "stop", Instant.now()));

        spans.set(List.of(routeSpan("route-chat", "gpt-4o", 100, 20)));

        assertThat(panel.combinedUsageEntriesForTesting()).hasSize(2);
        assertThat(panel.combinedUsageEntriesForTesting())
                .anyMatch(entry -> entry.source() == AiUsageSource.ROUTE
                        && "route-chat".equals(entry.routeId()));
    }

    @Test
    void statsViewRendersRouteAndTuiBreakdown() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.open();
        panel.recordUsageForTesting(new AiUsageEntry(
                "gpt-4o-mini", "openai", 10, 5, 15, 1000, "stop", Instant.now()));
        spans.set(List.of(routeSpan("orders", "gpt-4o", 50, 10)));
        panel.toggleStatsViewForTesting();

        Rect area = new Rect(0, 0, 120, 24);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);

        String rendered = TuiTestHelper.bufferToString(buffer);
        assertThat(rendered).contains("AI Usage");
        assertThat(rendered).contains("TUI:");
        assertThat(rendered).contains("integration:");
        assertThat(rendered).contains("[tui]");
        assertThat(rendered).contains("[route:orders]");
    }

    @Test
    void pageDownScrollsTheModelsOfTheStatsView() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.open();
        for (int i = 0; i < 12; i++) {
            panel.recordUsageForTesting(new AiUsageEntry(
                    "model-" + (char) ('a' + i), "openai", 10, 5, 15, 1000, "stop", Instant.now()));
        }
        panel.toggleStatsViewForTesting();
        Rect area = new Rect(0, 0, 120, 14);

        assertThat(render(panel, area)).contains("model-a").doesNotContain("model-l");

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        assertThat(render(panel, area)).contains("model-l").doesNotContain("model-a ");

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_UP));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_UP));
        assertThat(render(panel, area)).contains("model-a");
    }

    private static String render(AiPanel panel, Rect area) {
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        return TuiTestHelper.bufferToString(buffer);
    }

    @Test
    void togglingStatsViewRequestsSpanRefresh() {
        AiPanel panel = new AiPanel();
        assertThat(panel.spanRefreshRequested).isFalse();

        panel.toggleStatsViewForTesting();
        assertThat(panel.isStatsView()).isTrue();
        assertThat(panel.spanRefreshRequested).isTrue();

        panel.toggleStatsViewForTesting();
        assertThat(panel.isStatsView()).isFalse();
        assertThat(panel.spanRefreshRequested).isTrue();
    }

    @Test
    void statsViewShowsGuidanceWhenNoDataAvailable() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.open();
        panel.toggleStatsViewForTesting();

        Rect area = new Rect(0, 0, 100, 12);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);

        assertThat(TuiTestHelper.bufferToString(buffer))
                .contains("No AI usage data yet")
                .contains("GenAI observability");
    }

    @Test
    void statsViewCountsOnlyTheModelCallsOfAnAgentRun() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.open();
        spans.set(agentRunSpans());
        panel.toggleStatsViewForTesting();

        assertThat(panel.combinedUsageEntriesForTesting()).hasSize(2);
        String rendered = render(panel, new Rect(0, 0, 140, 24));
        assertThat(rendered)
                .contains("integration: 2")
                .contains("cached: 800")
                .contains("CACHED")
                .contains("Agent runs: 1")
                .contains("model calls: 2")
                .contains("tool calls: 1")
                .contains("GenAI level: detailed")
                .doesNotContain("execute_tool")
                // the panel itself called no tools: its tool time would read as the integration's
                .doesNotContain("Tool time");
    }

    @Test
    void statsViewExplainsLevelBasic() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.open();
        spans.set(List.of(routeSpan("orders", "gpt-4o", 50, 10)));
        panel.toggleStatsViewForTesting();

        assertThat(render(panel, new Rect(0, 0, 140, 24)))
                .contains("GenAI level: basic")
                .contains("agent runs and tool calls are not traced");
    }

    @Test
    void usageSummaryListsAgentRunsAndCachedTokens() {
        spans.set(agentRunSpans());

        AiUsageStats stats = new AiUsageStats();
        stats.setOtelSpans(spans);
        assertThat(stats.summary())
                .contains("2 request(s) (0 from this panel, 2 from the integration)")
                .contains("cached 800")
                .contains("**Agent runs:** 1 (2 model call(s), 1 tool call(s)), **GenAI level:** detailed")
                .contains("| Model | Reqs | In | Cached | Out | Total | Avg/req |");
    }

    /** An agent run at level detailed: invoke_agent with two chat calls and a tool call under it. */
    private static List<SpanEntry> agentRunSpans() {
        long start = Instant.now().getEpochSecond() * 1_000_000_000L;
        SpanEntry agent = new SpanEntry(
                "trace-1", "agent", "", "invoke_agent support", "INTERNAL", "OK", start, start + 3_000_000_000L, 3000,
                "support", null, "camel",
                Map.of(GenAiAttributes.OPERATION_NAME, "invoke_agent",
                        GenAiAttributes.SYSTEM, "openai",
                        GenAiAttributes.PROVIDER_NAME, "openai",
                        GenAiAttributes.REQUEST_MODEL, "gpt-4.1",
                        GenAiAttributes.AGENT_NAME, "support"));
        SpanEntry tool = new SpanEntry(
                "trace-1", "tool", "agent", "execute_tool lookupOrder", "INTERNAL", "OK", start, start + 200_000_000L,
                200, "support", null, "camel",
                Map.of(GenAiAttributes.OPERATION_NAME, "execute_tool", GenAiAttributes.TOOL_NAME, "lookupOrder"));
        return List.of(agent,
                chatSpan("chat1", start, 1000, 100, 0),
                tool,
                chatSpan("chat2", start, 1200, 100, 800));
    }

    private static SpanEntry chatSpan(String spanId, long start, int input, int output, long cached) {
        Map<String, Object> attrs = new HashMap<>(
                Map.of(
                        GenAiAttributes.OPERATION_NAME, "chat",
                        GenAiAttributes.SYSTEM, "openai",
                        GenAiAttributes.PROVIDER_NAME, "openai",
                        GenAiAttributes.REQUEST_MODEL, "gpt-4.1",
                        GenAiAttributes.INPUT_TOKENS, input,
                        GenAiAttributes.OUTPUT_TOKENS, output));
        if (cached > 0) {
            attrs.put(GenAiAttributes.CACHE_READ_INPUT_TOKENS, cached);
        }
        return new SpanEntry(
                "trace-1", spanId, "agent", "chat gpt-4.1", "CLIENT", "OK", start, start + 1_000_000_000L, 1000,
                "support", null, "camel", attrs);
    }

    private static SpanEntry routeSpan(String routeId, String model, int input, int output) {
        return new SpanEntry(
                "trace-1",
                "span-1",
                "",
                "chat " + model,
                "CLIENT",
                "OK",
                Instant.now().getEpochSecond() * 1_000_000_000L,
                Instant.now().getEpochSecond() * 1_000_000_000L + 500_000_000L,
                500,
                routeId,
                null,
                "camel",
                Map.of(
                        GenAiAttributes.OPERATION_NAME, "chat",
                        GenAiAttributes.SYSTEM, "openai",
                        GenAiAttributes.REQUEST_MODEL, model,
                        GenAiAttributes.RESPONSE_MODEL, model,
                        GenAiAttributes.INPUT_TOKENS, input,
                        GenAiAttributes.OUTPUT_TOKENS, output,
                        GenAiAttributes.FINISH_REASONS, "stop"));
    }
}
