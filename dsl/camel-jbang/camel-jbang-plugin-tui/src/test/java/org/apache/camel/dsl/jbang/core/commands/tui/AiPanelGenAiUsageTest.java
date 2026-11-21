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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPanelGenAiUsageTest {

    private AtomicReference<GenAiConsoleUsage> genAi;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        genAi = new AtomicReference<>(GenAiConsoleUsage.NONE);
    }

    @Test
    void combinedUsageIncludesTheGenAiCallsOfTheIntegration() {
        AiPanel panel = new AiPanel();
        panel.setGenAiUsage(genAi);
        panel.recordUsageForTesting(new AiUsageEntry(
                "gpt-4o-mini", "openai", 10, 5, 15, 1000, "stop", Instant.now()));

        genAi.set(console("detailed", chat("route-chat", "gpt-4o", 100, 20, 0)));

        assertThat(panel.combinedUsageEntriesForTesting()).hasSize(2);
        assertThat(panel.combinedUsageEntriesForTesting())
                .anyMatch(entry -> entry.source() == AiUsageSource.ROUTE
                        && "route-chat".equals(entry.routeId()));
    }

    @Test
    void statsViewRendersRouteAndTuiBreakdown() {
        AiPanel panel = new AiPanel();
        panel.setGenAiUsage(genAi);
        panel.open();
        panel.recordUsageForTesting(new AiUsageEntry(
                "gpt-4o-mini", "openai", 10, 5, 15, 1000, "stop", Instant.now()));
        genAi.set(console("detailed", chat("orders", "gpt-4o", 50, 10, 0)));
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
        panel.setGenAiUsage(genAi);
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
    void togglingStatsViewRequestsUsageRefresh() {
        AiPanel panel = new AiPanel();
        assertThat(panel.usageRefreshRequested).isFalse();

        panel.toggleStatsViewForTesting();
        assertThat(panel.isStatsView()).isTrue();
        assertThat(panel.usageRefreshRequested).isTrue();

        panel.toggleStatsViewForTesting();
        assertThat(panel.isStatsView()).isFalse();
        assertThat(panel.usageRefreshRequested).isTrue();
    }

    @Test
    void statsViewShowsGuidanceWhenNoDataAvailable() {
        AiPanel panel = new AiPanel();
        panel.setGenAiUsage(genAi);
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
        panel.setGenAiUsage(genAi);
        panel.open();
        genAi.set(console("detailed", agentRun()));
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
        panel.setGenAiUsage(genAi);
        panel.open();
        genAi.set(console("basic", chat("orders", "gpt-4o", 50, 10, 0)));
        panel.toggleStatsViewForTesting();

        assertThat(render(panel, new Rect(0, 0, 140, 24)))
                .contains("GenAI level: basic")
                .contains("agent runs and tool calls are not traced");
    }

    @Test
    void statsViewSaysWhenTheIntegrationDoesNotRecord() {
        AiPanel panel = new AiPanel();
        panel.setGenAiUsage(genAi);
        panel.open();
        panel.recordUsageForTesting(new AiUsageEntry(
                "gpt-4o-mini", "openai", 10, 5, 15, 1000, "stop", Instant.now()));
        JsonObject off = answer("detailed");
        off.put("recording", false);
        genAi.set(GenAiConsoleUsage.from(off));
        panel.toggleStatsViewForTesting();

        assertThat(render(panel, new Rect(0, 0, 160, 24)))
                .contains("not recorded: the dev console of the integration is off");
    }

    @Test
    void usageSummaryListsAgentRunsAndCachedTokens() {
        genAi.set(console("detailed", agentRun()));

        AiUsageStats stats = new AiUsageStats();
        stats.setGenAiUsage(genAi);
        assertThat(stats.summary())
                .contains("2 request(s) (0 from this panel, 2 from the integration)")
                .contains("cached 800")
                .contains("**Agent runs:** 1 (2 model call(s), 1 tool call(s)), **GenAI level:** detailed")
                .contains("| Model | Reqs | In | Cached | Out | Total | Avg/req |");
    }

    /** The answer of the genai action of the integration, as the CLI connector gives it. */
    private static GenAiConsoleUsage console(String level, JsonObject... calls) {
        return GenAiConsoleUsage.from(answer(level, calls));
    }

    private static JsonObject answer(String level, JsonObject... calls) {
        JsonObject answer = new JsonObject();
        answer.put("recording", true);
        answer.put("level", level);
        answer.put("calls", new JsonArray(List.of((Object[]) calls)));
        return answer;
    }

    /** An agent run at level detailed: two model calls and a tool call. */
    private static JsonObject agentRun() {
        JsonObject run = call("invoke_agent", "invoke_agent support", "support");
        run.put("agentName", "support");
        JsonObject tool = call("execute_tool", "execute_tool lookupOrder", null);
        tool.put("toolName", "lookupOrder");
        run.put("steps", new JsonArray(
                List.of(
                        chat(null, "gpt-4.1", 1000, 100, 0), tool, chat("support", "gpt-4.1", 1200, 100, 800))));
        return run;
    }

    private static JsonObject chat(String routeId, String model, long input, long output, long cached) {
        JsonObject chat = call("chat", "chat " + model, routeId);
        chat.put("provider", "openai");
        chat.put("model", model);
        chat.put("inputTokens", input);
        chat.put("outputTokens", output);
        if (cached > 0) {
            chat.put("cachedInputTokens", cached);
        }
        chat.put("finishReason", "stop");
        return chat;
    }

    private static JsonObject call(String operation, String name, String routeId) {
        JsonObject call = new JsonObject();
        call.put("operation", operation);
        call.put("name", name);
        if (routeId != null) {
            call.put("routeId", routeId);
        }
        call.put("timestamp", Instant.now().toEpochMilli());
        call.put("running", false);
        call.put("durationMs", 1000L);
        return call;
    }
}
