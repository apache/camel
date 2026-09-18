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

import dev.tamboui.style.Style;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.GpuStats;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.HostStats;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.LoadedModel;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.ModelShape;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.ProcessStats;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RequestEntry;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RequestSource;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RunnerInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.ServerInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.SlotState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders {@link OllamaTab} into a virtual terminal from a monitor fed by hand, so the layout is checked without an
 * Ollama server.
 */
class OllamaTabRenderTest {

    private MonitorContext ctx;
    private OllamaMonitor monitor;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        monitor = new OllamaMonitor();
        ctx.ollamaMonitor = monitor;
    }

    @Test
    void showsHowToStartOllamaWhenNotDetected() {
        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 160, 30);
        assertTrue(rendered.contains("Ollama not detected at localhost:11434"), rendered);
        assertTrue(rendered.contains("ollama serve"), rendered);
        assertTrue(rendered.contains("camel infra run ollama"), rendered);
    }

    @Test
    void rendersLocalServerWithModelPanelsAndRequests() {
        localServerWithModel();
        monitor.updateRunner(new RunnerInfo(23629, 58237, 262144, 1, "/blob", "llama-server"));
        monitor.updateSlot(new SlotState(false, 25, 25, 9, 6, 262144, "draft-mtp", 1, Instant.now()));
        monitor.updateHost(new HostStats(
                new GpuStats("Apple GPU", 25, 31463079936L, 34170552320L, 1),
                new ProcessStats(70566, "ollama serve", 0.1, 82176L * 1024),
                new ProcessStats(23629, "llama-server", 12.5, 28273856L * 1024),
                Instant.now()));
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(16, 6, 22, 0, 188, 81, 12, 300), 0, "stop");

        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 180, 40);

        // header
        assertTrue(rendered.contains("Ollama 0.33.3"), rendered);
        assertTrue(rendered.contains("localhost:11434"), rendered);
        assertTrue(rendered.contains("runner llama-server :58237"), rendered);
        assertTrue(rendered.contains("qwen3.6:35b-a3b"), rendered);
        assertTrue(rendered.contains("qwen35moe"), rendered);
        assertTrue(rendered.contains("41 layers"), rendered);
        assertTrue(rendered.contains("256 experts (8 active)"), rendered);
        assertTrue(rendered.contains("max ctx 256k"), rendered);
        assertTrue(rendered.contains("100% GPU"), rendered);
        assertTrue(rendered.contains("· ctx 256k ·"), rendered);
        assertFalse(rendered.contains("of 256k"), rendered);
        // panels
        assertTrue(rendered.contains("Throughput"), rendered);
        assertTrue(rendered.contains("decode"), rendered);
        assertTrue(rendered.contains("74"), rendered); // 6 tokens in 81 ms
        assertTrue(rendered.contains("prefill"), rendered);
        assertTrue(rendered.contains("TTFT 0.20s"), rendered);
        assertTrue(rendered.contains("Context"), rendered);
        // idle runner: the cache figure comes from the last request (none cached there), not the cleared slot
        assertTrue(rendered.contains("cache hit 0% (last request)"), rendered);
        assertTrue(rendered.contains("idle"), rendered);
        assertTrue(rendered.contains("speculative draft-mtp"), rendered);
        assertTrue(rendered.contains("Host"), rendered);
        assertTrue(rendered.contains("GPU"), rendered);
        assertTrue(rendered.contains("25%"), rendered);
        assertTrue(rendered.contains("llama-server"), rendered);
        assertTrue(rendered.contains("ollama serve"), rendered);
        // request log
        assertTrue(rendered.contains("Requests (1 question, 1 request)"), rendered);
        assertTrue(rendered.contains("CTX"), rendered);
        assertTrue(rendered.contains("PREFILL"), rendered);
        assertTrue(rendered.contains("DECODE"), rendered);
        assertTrue(rendered.contains("tui"), rendered);
        assertTrue(rendered.contains("stop"), rendered);
    }

    @Test
    void workingRunnerShowsTheSlotCacheFigure() {
        localServerWithModel();
        monitor.updateRunner(new RunnerInfo(23629, 58237, 262144, 1, "/blob", "llama-server"));
        monitor.updateSlot(new SlotState(true, 25, 25, 9, 6, 262144, "draft-mtp", 1, Instant.now()));
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(16, 6, 22, 0, 188, 81, 12, 300), 0, "stop");

        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 180, 40);
        assertTrue(rendered.contains("cache hit 36%"), rendered);
        assertTrue(rendered.contains("working"), rendered);
    }

    @Test
    void remoteServerHidesHostPanelAndExplainsMissingLiveState() {
        monitor.updateServer(new ServerInfo("http://gpu-box.lan:11434", "0.33.3", false));
        monitor.updateModels(List.of(model()));

        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 160, 30);

        assertTrue(rendered.contains("gpu-box.lan:11434"), rendered);
        assertTrue(rendered.contains("remote"), rendered);
        assertFalse(rendered.contains(" Host "), rendered);
        assertTrue(rendered.contains("live state needs the runner on this machine"), rendered);
        assertTrue(rendered.contains("No requests yet"), rendered);
    }

    @Test
    void noModelLoadedListsInstalledModels() {
        monitor.updateServer(new ServerInfo("http://localhost:11434", "0.33.3", true));
        monitor.updateInstalled(List.of("qwen3.6:35b-a3b", "llama3.2:latest"));

        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 160, 30);

        assertTrue(rendered.contains("No model loaded"), rendered);
        assertTrue(rendered.contains("2 installed: qwen3.6:35b-a3b, llama3.2:latest"), rendered);
    }

    @Test
    void requestsAreGroupedPerQuestionAndUnfoldOnEnter() {
        localServerWithModel();
        String question = "how many messages have camel done\nand are any failing?";
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(4_500, 33, 4_533, 0, 620, 700, 0, 1_320), 0,
                "tool_calls", 7, question);
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(4_700, 78, 4_778, 4_500, 50, 1_600, 0, 1_700), 0,
                "tool_calls", 7, question);
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(7_000, 106, 7_106, 4_700, 360, 1_900, 0, 2_300), 0,
                "stop", 7, question);

        OllamaTab tab = new OllamaTab(ctx, monitor);
        String rendered = TuiTestHelper.renderToString(tab, 200, 40);
        assertTrue(rendered.contains("Requests (1 question, 3 requests)"), rendered);
        assertTrue(rendered.contains("#7 ×3"), rendered);
        assertTrue(rendered.contains("how many messages have camel done …"), rendered);
        assertTrue(rendered.contains("QUESTION"), rendered);
        assertFalse(rendered.contains("step 1/3"), rendered);

        // Enter on the question unfolds its steps
        tab.navigateDown();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        rendered = TuiTestHelper.renderToString(tab, 200, 40);
        assertTrue(rendered.contains("step 1/3"), rendered);
        assertTrue(rendered.contains("step 3/3"), rendered);

        // and folds them again
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        rendered = TuiTestHelper.renderToString(tab, 200, 40);
        assertFalse(rendered.contains("step 1/3"), rendered);
    }

    @Test
    void aQuestionThatCostManyRequestsOrRanLongIsColoured() {
        // the request count: plain for a few, yellow from ten, red when the tool-call limit ended the question
        assertEquals(Theme.info(), OllamaTab.requestCountStyle(3, "stop"));
        assertEquals(Theme.warning(), OllamaTab.requestCountStyle(OllamaTab.MANY_REQUESTS, "stop"));
        assertEquals(Theme.error().bold(), OllamaTab.requestCountStyle(26, "limit"));
        assertEquals(Theme.error().bold(), OllamaTab.requestCountStyle(2, "limit"));

        // the total: plain under half a minute, yellow from there, orange from a minute
        assertEquals(Style.EMPTY, OllamaTab.totalTimeStyle(12_000));
        assertEquals(Theme.warning(), OllamaTab.totalTimeStyle(OllamaTab.SLOW_QUESTION_MS));
        assertEquals(Style.EMPTY.fg(Theme.accent()).bold(), OllamaTab.totalTimeStyle(75_000));

        // and the row still reads the same
        localServerWithModel();
        String question = "what's the name of the source file that has the route";
        for (int i = 0; i < 25; i++) {
            monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(
                    9_000 + i * 200, 40, 9_040, 8_800, 300,
                    1_500, 0, 2_000), 0, "tool_calls", 9, question);
        }
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(
                14_000, 120, 14_120, 13_800, 300, 3_000, 0,
                3_500), 0, "limit", 9, question);
        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 200, 40);
        assertTrue(rendered.contains("#9 ×26"), rendered);
        assertTrue(rendered.contains("limit"), rendered);
    }

    @Test
    void theQuestionBeingAnsweredShowsAsWorkingFromTheMomentItIsAsked() {
        localServerWithModel();
        String question = "what's the name of the source file that has the route";
        monitor.questionStarted(9, question);

        // before the first request returns: a row with the question, a running clock and no figures
        OllamaTab tab = new OllamaTab(ctx, monitor);
        String rendered = TuiTestHelper.renderToString(tab, 200, 40);
        assertTrue(rendered.contains("Requests (0 questions, 0 requests, 1 in progress)"), rendered);
        assertTrue(rendered.contains("#9"), rendered);
        assertTrue(rendered.contains(question), rendered);
        assertTrue(rendered.contains("working"), rendered);
        assertFalse(rendered.contains("No requests yet"), rendered);

        // a tool call came back: the normal row, still working rather than tool_calls
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(9_000, 40, 9_040, 0, 5_000, 700, 0, 5_800),
                0, "tool_calls", 9, question);
        rendered = TuiTestHelper.renderToString(tab, 200, 40);
        assertTrue(rendered.contains("Requests (1 question, 1 request, 1 in progress)"), rendered);
        assertTrue(rendered.contains("working"), rendered);
        assertFalse(rendered.contains("tool_calls"), rendered);

        // the answer landed
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(
                9_400, 120, 9_520, 9_000, 300, 2_000, 0,
                2_400), 0, "stop", 9, question);
        monitor.questionFinished();
        rendered = TuiTestHelper.renderToString(tab, 200, 40);
        assertTrue(rendered.contains("Requests (1 question, 2 requests)"), rendered);
        assertTrue(rendered.contains("#9 ×2"), rendered);
        assertFalse(rendered.contains("working"), rendered);
        assertTrue(rendered.contains("stop"), rendered);
    }

    @Test
    void aFooterAveragesTheQuestionsOnceThereAreTwo() {
        localServerWithModel();
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(
                5_000, 100, 5_100, 4_000, 500, 1_500, 0,
                2_000), 0, "stop", 1, "how many routes");
        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 200, 40);
        assertFalse(rendered.contains("avg/question"), rendered);

        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(
                5_200, 40, 5_240, 5_000, 500, 500, 0,
                1_000), 0, "tool_calls", 2, "how much memory");
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(
                5_400, 300, 5_700, 5_200, 200, 4_800, 0,
                5_000), 0, "limit", 2, "how much memory");
        rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 200, 40);
        assertTrue(rendered.contains("avg/question"), rendered);
        assertTrue(rendered.contains("2 questions · 3 requests"), rendered);
        assertTrue(rendered.contains("1 limit"), rendered);
    }

    @Test
    void routeRequestsShowTheirRouteId() {
        localServerWithModel();
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(16, 6, 22, 0, 188, 81, 12, 300), 0, "stop");
        // a route call comes without phase timings: prefill/decode show as dashes, total from the span
        monitor.ingestSpans(List.of(routeSpan()));

        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 180, 40);
        assertTrue(rendered.contains("route:chat-route"), rendered);
        assertTrue(rendered.contains("Requests (2 questions, 2 requests)"), rendered);
    }

    @Test
    void tableDataJsonCarriesRequestsAndSummary() {
        localServerWithModel();
        monitor.recordRequest("qwen3.6:35b-a3b", new LlmClient.TokenUsage(16, 6, 22, 0, 188, 81, 12, 300), 0, "stop");

        JsonObject json = new OllamaTab(ctx, monitor).getTableDataAsJson();
        assertEquals("Ollama", json.get("tab"));
        assertEquals(1, json.get("totalRows"));
        JsonArray rows = (JsonArray) json.get("rows");
        assertEquals(1, rows.size());
        assertEquals("qwen3.6:35b-a3b", ((JsonObject) rows.get(0)).get("model"));
        JsonObject summary = (JsonObject) json.get("summary");
        assertEquals(true, summary.get("connected"));
        assertEquals(1, ((JsonArray) summary.get("loadedModels")).size());
    }

    @Test
    void headerShowsWhatThePanelAsksForAndWhereItCompacts() {
        localServerWithModel();
        // the model in this fixture is loaded at 256k while the panel asked for 64k: both are named
        monitor.setPanelContext(65_536, 32_768);
        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 200, 40);
        assertTrue(rendered.contains("AI panel asks 64k · AI panel compacts above 32k"), rendered);
        // when the panel adopted the loaded window only the compaction point is worth a mention
        monitor.setPanelContext(262_144, 32_768);
        rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 200, 40);
        assertFalse(rendered.contains("AI panel asks"), rendered);
        assertTrue(rendered.contains("ctx 256k · unloads in"), rendered);
        assertTrue(rendered.contains("AI panel compacts above 32k"), rendered);
    }

    @Test
    void contextTrendShowsTurnsPeakAndCompactions() {
        localServerWithModel(); // ctx 262,144
        // four turns: the prompt grows, then a compaction frees most of it
        monitor.recordRequest("m", usage(26_000), 0, "stop");
        monitor.recordRequest("m", usage(52_000), 0, "stop");
        monitor.recordRequest("m", usage(131_000), 0, "stop");
        monitor.recordRequest("m", usage(39_000), 0, "stop");

        String rendered = TuiTestHelper.renderToString(new OllamaTab(ctx, monitor), 200, 40);
        assertTrue(rendered.contains("turns"), rendered);
        assertTrue(rendered.contains("14% (peak 49%) · 1 compaction"), rendered);
        // the CTX column of the biggest turn
        assertTrue(rendered.contains("49%"), rendered);
        assertEquals(49, monitor.snapshot().totals().peakContextPercent());
        assertEquals(1, monitor.snapshot().totals().compactions());
    }

    @Test
    void helpTextExplainsThePhasesAndColumns() {
        String help = new OllamaTab(ctx, monitor).getHelpText();
        assertTrue(help != null && help.contains("TTFT"), help);
        for (String term : List.of("Prefill", "Decode", "cold", "cache hit", "speculative", "CACHE", "QUESTION",
                "REASON")) {
            assertTrue(help.contains(term), "help should explain " + term);
        }
    }

    @Test
    void formattingHelpers() {
        assertEquals("54", OllamaTab.formatRate(54.04));
        assertEquals("7.5", OllamaTab.formatRate(7.49));
        assertEquals("0", OllamaTab.formatRate(0));
        assertEquals("999", OllamaTab.formatTokens(999));
        assertEquals("1.2k", OllamaTab.formatTokens(1203));
        assertEquals("256k", OllamaTab.formatTokens(262144));
        assertEquals("64k", OllamaTab.formatTokens(65536));
        assertEquals("32k", OllamaTab.formatTokens(32768));
        assertEquals("250k", OllamaTab.formatTokens(250_000));
        assertEquals("1.5M", OllamaTab.formatTokens(1_500_000));
        assertEquals("0.14s", OllamaTab.formatSeconds(138));
        assertEquals("2.8s", OllamaTab.formatSeconds(2829));
        assertEquals("11s", OllamaTab.formatSeconds(11378));
        assertEquals("1m5s", OllamaTab.formatSeconds(65_000));
        assertEquals("▓▓▓▓▓░░░░░", OllamaTab.gaugeBar(50, 10));
        assertEquals("░░░░", OllamaTab.gaugeBar(0, 4));
        assertEquals("▓▓▓▓", OllamaTab.gaugeBar(140, 4));
        assertEquals("  ▁▄█", OllamaTab.sparkline(new long[] { 10, 40, 80 }, 5));
        assertEquals("    ", OllamaTab.sparkline(new long[] { 0, 0 }, 4));
        assertEquals("▁█", OllamaTab.sparkline(new long[] { 1, 2, 3, 20 }, 2));
        assertEquals("▁▄█", OllamaTab.sparkline(new long[] { 10, 50, 100 }, 3, 100));
        Instant now = Instant.parse("2026-09-17T09:18:12Z");
        assertEquals("unloads in 4m32s", OllamaTab.formatCountdown(Instant.parse("2026-09-17T09:22:44Z"), now));
        assertEquals("unloads in 2h5m", OllamaTab.formatCountdown(now.plusSeconds(2 * 3600 + 300), now));
        assertEquals("unloading", OllamaTab.formatCountdown(now.minusSeconds(1), now));
        assertEquals("stays loaded", OllamaTab.formatCountdown(now.plusSeconds(400L * 24 * 3600), now));
    }

    @Test
    void requestHistoryIsOldestFirstAndCappedToWidth() {
        List<RequestEntry> newestFirst = List.of(
                request(30), request(20), request(10));
        long[] history = OllamaTab.requestHistory(newestFirst, 2);
        assertEquals(2, history.length);
        assertEquals(20, history[0]);
        assertEquals(30, history[1]);
    }

    private void localServerWithModel() {
        monitor.updateServer(new ServerInfo("http://localhost:11434", "0.33.3", true));
        monitor.updateModels(List.of(model()));
    }

    private static LlmClient.TokenUsage usage(int promptTokens) {
        return new LlmClient.TokenUsage(promptTokens, 50, promptTokens + 50, 0, 900, 800, 10, 1800);
    }

    private static LoadedModel model() {
        return new LoadedModel(
                "qwen3.6:35b-a3b", "qwen35moe", "35.5B", "Q4_K_M", 23567972432L, 23567972432L, 262144,
                Instant.now().plusSeconds(272),
                new ModelShape(
                        "qwen35moe", 41, 256, 8, 262144, 2048, 35505251456L,
                        List.of("completion", "vision", "tools", "thinking")));
    }

    private static RequestEntry request(int decodeTokensPerSecond) {
        return new RequestEntry(
                Instant.now(), RequestSource.TUI, null, "m", 10, decodeTokensPerSecond, 0, 100, 1000,
                0, 1100, "stop", 0);
    }

    private static SpanEntry routeSpan() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("gen_ai.operation.name", "chat");
        attrs.put("gen_ai.system", "ollama");
        attrs.put("gen_ai.request.model", "qwen3.6:35b-a3b");
        attrs.put("gen_ai.usage.input_tokens", "412");
        attrs.put("gen_ai.usage.output_tokens", "180");
        long start = Instant.now().toEpochMilli() * 1_000_000L;
        return new SpanEntry(
                "trace-1", "span-1", null, "chat qwen3.6:35b-a3b", "CLIENT", "OK", start,
                start + 4100 * 1_000_000L, 4100, "chat-route", null, null, attrs);
    }
}
