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

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RequestEntry;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RequestSource;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.ServerInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.SlotState;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.Snapshot;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaMonitorTest {

    /** The usage Ollama returned for a real warm request: 16 prompt tokens in 188 ms, 6 tokens in 81 ms. */
    private static final LlmClient.TokenUsage WARM = new LlmClient.TokenUsage(16, 6, 22, 0, 188, 81, 12, 300);
    /** And a cold one: 11.1 s spent loading the model first. */
    private static final LlmClient.TokenUsage COLD = new LlmClient.TokenUsage(18, 7, 25, 0, 138, 130, 11108, 11378);

    @Test
    void tuiRequestsCarryOllamaTimings() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.recordRequest("qwen3.6:35b-a3b", WARM, 350, "stop");

        Snapshot s = monitor.snapshot();
        assertEquals(1, s.requests().size());
        RequestEntry e = s.lastRequest();
        assertEquals(RequestSource.TUI, e.source());
        assertEquals("qwen3.6:35b-a3b", e.model());
        assertEquals(16, e.inputTokens());
        assertEquals(6, e.outputTokens());
        assertEquals(188, e.prefillMs());
        assertEquals(81, e.decodeMs());
        assertEquals(12, e.loadMs());
        // Ollama's own total wins over the client-side latency
        assertEquals(300, e.totalMs());
        assertEquals(85.1, e.prefillTokensPerSecond(), 0.1);
        assertEquals(74.1, e.decodeTokensPerSecond(), 0.1);
        assertEquals(200, e.ttftMs());
        assertFalse(e.coldStart());
        assertEquals("stop", e.doneReason());
    }

    @Test
    void prefillRateCountsOnlyTokensNotServedFromCache() {
        // a tool-loop step observed live: 12,774 prompt tokens of which 12,624 came from the cache, 532 ms prefill
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.recordRequest("m", new LlmClient.TokenUsage(12_774, 161, 12_935, 12_624, 532, 2_990, 0, 3_588), 0,
                "stop");
        RequestEntry e = monitor.snapshot().lastRequest();
        assertEquals(150, e.evaluatedTokens());
        assertEquals(98, e.cacheHitPercent());
        assertEquals(150 * 1000.0 / 532, e.prefillTokensPerSecond(), 0.1);
        assertEquals(12_774, e.promptTokens());
        assertEquals(150 * 1000.0 / 532, monitor.snapshot().totals().avgPrefillTokensPerSecond(), 0.1);
    }

    @Test
    void coldStartIsDetectedFromLoadTime() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.recordRequest("qwen3.6:35b-a3b", COLD, 11400, "stop");
        RequestEntry e = monitor.snapshot().lastRequest();
        assertTrue(e.coldStart());
        assertEquals(11108 + 138, e.ttftMs());
        assertEquals(1, monitor.snapshot().totals().coldStarts());
    }

    @Test
    void clientLatencyFillsInWhenOllamaReportsNoTotal() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.recordRequest("m", new LlmClient.TokenUsage(10, 5, 15), 1234, "stop");
        assertEquals(1234, monitor.snapshot().lastRequest().totalMs());
        assertFalse(monitor.snapshot().lastRequest().hasTimings());
    }

    @Test
    void sessionTotalsAggregateAcrossRequests() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.recordRequest("m", WARM, 0, "stop");
        monitor.recordRequest("m", COLD, 0, "stop");
        var t = monitor.snapshot().totals();
        assertEquals(2, t.requests());
        assertEquals(34, t.inputTokens());
        assertEquals(13, t.outputTokens());
        assertEquals(188 + 138, t.prefillMs());
        assertEquals(81 + 130, t.decodeMs());
        assertEquals(1, t.coldStarts());
        assertEquals(13 * 1000.0 / 211, t.avgDecodeTokensPerSecond(), 0.01);
    }

    @Test
    void requestLogIsCappedNewestFirst() {
        OllamaMonitor monitor = new OllamaMonitor();
        for (int i = 0; i < OllamaMonitor.MAX_REQUESTS + 5; i++) {
            monitor.recordRequest("m" + i, WARM, 0, "stop");
        }
        List<RequestEntry> requests = monitor.snapshot().requests();
        assertEquals(OllamaMonitor.MAX_REQUESTS, requests.size());
        assertEquals("m" + (OllamaMonitor.MAX_REQUESTS + 4), requests.get(0).model());
        // totals still count every request
        assertEquals(OllamaMonitor.MAX_REQUESTS + 5, monitor.snapshot().totals().requests());
    }

    @Test
    void routeSpansBecomeRouteRequestsOnlyForOllamaAndOnlyOnce() {
        OllamaMonitor monitor = new OllamaMonitor();
        SpanEntry ollama = genAiSpan("s1", "ollama", "qwen3.6:35b-a3b", "chat-route", 412, 180, 4100);
        SpanEntry openai = genAiSpan("s2", "openai", "gpt-4o", "other-route", 10, 10, 500);
        SpanEntry plain = new SpanEntry("t", "s3", null, "camel-route", "INTERNAL", "OK", 0, 0, 5, "r", null, null, Map.of());

        monitor.ingestSpans(List.of(ollama, openai, plain));
        monitor.ingestSpans(List.of(ollama));

        List<RequestEntry> requests = monitor.snapshot().requests();
        assertEquals(1, requests.size());
        RequestEntry e = requests.get(0);
        assertEquals(RequestSource.ROUTE, e.source());
        assertEquals("chat-route", e.routeId());
        assertEquals("qwen3.6:35b-a3b", e.model());
        assertEquals(412, e.inputTokens());
        assertEquals(180, e.outputTokens());
        assertEquals(4100, e.totalMs());
        assertFalse(e.hasTimings());
        assertEquals(0, e.ttftMs());
        // no runner slot and no model polled yet: the window is unknown
        assertEquals(0, e.contextSize());
        assertEquals(-1, e.contextPercent());
    }

    @Test
    void contextFillComesFromTheSlotOrTheLoadedModelAndCompactionsAreCounted() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.updateServer(new ServerInfo("http://localhost:11434", "0.33.3", true));
        monitor.updateModels(List.of(new OllamaMonitor.LoadedModel(
                "m", "f", "35.5B", "Q4_K_M", 1, 1, 32_768, null, null)));
        monitor.recordRequest("m", new LlmClient.TokenUsage(4_000, 60, 4_060, 500, 900, 800, 0, 1800), 0, "stop");
        RequestEntry first = monitor.snapshot().lastRequest();
        assertEquals(32_768, first.contextSize());
        // prompt_eval_count is the whole prompt; the cached 500 are part of it, not on top
        assertEquals(4_000, first.promptTokens());
        assertEquals(3_500, first.evaluatedTokens());
        assertEquals(12, first.contextPercent());

        // the runner's slot wins over the model list when present
        monitor.updateSlot(slot(false, 0, System.currentTimeMillis()));
        monitor.recordRequest("m", new LlmClient.TokenUsage(20_000, 60, 20_060, 0, 900, 800, 0, 1800), 0, "stop");
        assertEquals(262144, monitor.snapshot().lastRequest().contextSize());
        assertEquals(7, monitor.snapshot().lastRequest().contextPercent());
        assertEquals(12, monitor.snapshot().totals().peakContextPercent());
        assertEquals(0, monitor.snapshot().totals().compactions());

        // a prompt a fifth smaller than the previous turn counts as a compaction
        monitor.recordRequest("m", new LlmClient.TokenUsage(9_000, 60, 9_060, 0, 900, 800, 0, 1800), 0, "stop");
        assertEquals(1, monitor.snapshot().totals().compactions());
        JsonObject last = (JsonObject) ((JsonArray) monitor.toJson(1).get("requests")).get(0);
        assertEquals(9_000L, last.get("promptTokens"));
        assertEquals(3, last.get("contextPercent"));
    }

    @Test
    void slotSamplesDriveTheLiveDecodeRateAndHistory() {
        OllamaMonitor monitor = new OllamaMonitor();
        long t0 = System.currentTimeMillis();
        monitor.updateSlot(slot(true, 0, t0));
        monitor.updateSlot(slot(true, 25, t0 + 500));
        monitor.updateSlot(slot(true, 50, t0 + 1000));
        monitor.tick(t0 + 1000);

        Snapshot s = monitor.snapshot();
        assertNotNull(s.slot());
        assertTrue(s.slot().processing());
        assertTrue(s.liveDecodeRate() > 0);
        long[] history = s.decodeHistory();
        assertEquals(OllamaMonitor.HISTORY_POINTS, history.length);
        assertEquals(50, history[history.length - 1]);
        assertEquals(0, history[0]);
    }

    @Test
    void requestsGroupIntoQuestionsWithStepsOldestFirst() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.updateModels(List.of(new OllamaMonitor.LoadedModel(
                "m", "f", "35.5B", "Q4_K_M", 1, 1, 32_768, null, null)));
        // question 7: three steps (two tool calls, then the answer); the prompt grows and the cache warms
        // the first step loaded the model: 1.1 s load plus 5.1 s prefill
        monitor.recordRequest("m", new LlmClient.TokenUsage(4_500, 33, 4_533, 0, 5_100, 700, 1_100, 6_900), 0,
                "tool_calls", 7, "how many messages have camel done");
        monitor.recordRequest("m", new LlmClient.TokenUsage(4_700, 78, 4_778, 4_500, 500, 1_600, 0, 1_900), 0,
                "tool_calls", 7, "how many messages have camel done");
        monitor.recordRequest("m", new LlmClient.TokenUsage(7_000, 106, 7_106, 4_700, 3_600, 1_900, 0, 5_500), 0,
                "stop", 7, "how many messages have camel done");
        // question 8: a single request
        monitor.recordRequest("m", new LlmClient.TokenUsage(7_200, 40, 7_240, 7_000, 300, 700, 0, 1_000), 0,
                "stop", 8, "thanks");
        // and a route call
        monitor.ingestSpans(List.of(genAiSpan("s9", "ollama", "m", "chat-route", 412, 180, 4100)));

        List<OllamaMonitor.QuestionGroup> groups = OllamaMonitor.groupByQuestion(monitor.snapshot().requests());
        assertEquals(3, groups.size());
        OllamaMonitor.QuestionGroup route = groups.get(0);
        assertEquals(RequestSource.ROUTE, route.source());
        assertEquals(1, route.steps().size());
        assertEquals("chat-route", route.routeId());

        OllamaMonitor.QuestionGroup q8 = groups.get(1);
        assertEquals(8, q8.question());
        assertEquals("thanks", q8.questionText());
        assertEquals(1, q8.steps().size());

        OllamaMonitor.QuestionGroup q7 = groups.get(2);
        assertEquals(7, q7.question());
        assertEquals(3, q7.steps().size());
        assertEquals(33, q7.steps().get(0).outputTokens(), "steps are oldest first");
        assertEquals(7_000, q7.promptTokens());
        assertEquals(33 + 78 + 106, q7.outputTokens());
        // 9,200 of 16,200 prompt tokens came from the cache
        assertEquals(56, q7.cacheHitPercent());
        assertEquals(21, q7.contextPercent());
        assertEquals(6_200, q7.ttftMs());
        assertTrue(q7.coldStart());
        assertEquals("stop", q7.doneReason());
        // evaluated 4,500 + 200 + 2,300 tokens over 9.2 s of prefill
        assertEquals(7_000 * 1000.0 / 9_200, q7.prefillTokensPerSecond(), 0.5);
        // the three replies arrived within the same millisecond in this test, so the wall time is the first
        // request's own duration plus nothing: at least as long as the first step, never shorter than the last
        assertTrue(q7.wallMs() >= 6_900, "wall " + q7.wallMs());

        JsonArray questions = (JsonArray) monitor.toJson(10).get("questions");
        assertEquals(3, questions.size());
        JsonObject jq7 = (JsonObject) questions.get(2);
        assertEquals(3, jq7.get("steps"));
        assertEquals("how many messages have camel done", jq7.get("questionText"));
    }

    @Test
    void closeReleasesTheClientAndCanBeCalledTwice() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.close();
        monitor.close();
        // the in-memory state stays usable for a last snapshot
        assertFalse(monitor.snapshot().connected());
    }

    @Test
    void theQuestionBeingAnsweredIsListedUntilTheTurnEnds() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.questionStarted(9, "what's the name of the source file that has the route");

        // asked, nothing back yet: a question with no steps
        JsonArray questions = (JsonArray) monitor.toJson(10).get("questions");
        assertEquals(1, questions.size());
        JsonObject q = (JsonObject) questions.get(0);
        assertEquals(9, q.get("question"));
        assertEquals(0, q.get("steps"));
        assertEquals(true, q.get("inProgress"));
        assertNotNull(q.get("elapsedMs"));
        assertNotNull(monitor.snapshot().activeQuestion());

        // the first tool call returned: the same question, now with a step, still in progress
        monitor.recordRequest("m", new LlmClient.TokenUsage(9_000, 40, 9_040, 0, 5_000, 700, 0, 5_800), 0,
                "tool_calls", 9, "what's the name of the source file that has the route");
        questions = (JsonArray) monitor.toJson(10).get("questions");
        assertEquals(1, questions.size());
        q = (JsonObject) questions.get(0);
        assertEquals(1, q.get("steps"));
        assertEquals(true, q.get("inProgress"));

        // the answer landed
        monitor.recordRequest("m", new LlmClient.TokenUsage(9_400, 120, 9_520, 9_000, 300, 2_000, 0, 2_400), 0,
                "stop", 9, "what's the name of the source file that has the route");
        monitor.questionFinished();
        q = (JsonObject) ((JsonArray) monitor.toJson(10).get("questions")).get(0);
        assertEquals(2, q.get("steps"));
        assertNull(q.get("inProgress"));
        assertNull(monitor.snapshot().activeQuestion());
    }

    @Test
    void theQuestionSummaryAveragesPerQuestionAndLeavesRoutesOut() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.updateModels(List.of(new OllamaMonitor.LoadedModel(
                "m", "f", "35.5B", "Q4_K_M", 1, 1, 65_536, null, null)));
        // question 1: two steps, 4 s of wall time, ended at the tool-call limit
        monitor.recordRequest("m", new LlmClient.TokenUsage(5_000, 40, 5_040, 4_000, 500, 1_000, 0, 1_500), 0,
                "tool_calls", 1, "one");
        monitor.recordRequest("m", new LlmClient.TokenUsage(5_200, 160, 5_360, 5_000, 200, 2_300, 0, 2_500), 0,
                "limit", 1, "one");
        // question 2: one step of 2 s
        monitor.recordRequest("m", new LlmClient.TokenUsage(5_400, 100, 5_500, 5_200, 200, 1_800, 0, 2_000), 0,
                "stop", 2, "two");
        // a route call is not a question
        monitor.ingestSpans(List.of(genAiSpan("s1", "ollama", "m", "chat-route", 412, 180, 9_000)));

        OllamaMonitor.QuestionSummary s
                = OllamaMonitor.QuestionSummary.of(OllamaMonitor.groupByQuestion(monitor.snapshot().requests()));
        assertEquals(2, s.questions());
        assertEquals(3, s.requests());
        assertEquals(5_300, s.avgPromptTokens(), "average of each question's largest prompt");
        assertEquals(150, s.avgOutputTokens());
        assertEquals(91, s.cacheHitPercent(), "14.2k cached of 15.6k prompt tokens");
        assertEquals(8, s.peakContextPercent());
        assertEquals(1, s.limitHits());
        // the fake requests land at once, so a question's wall time is its first step's total plus the rest:
        // (1.5 s + 1 s) and 2 s, averaged
        assertTrue(s.avgWallMs() >= 2_250 && s.avgWallMs() < 2_400, "was " + s.avgWallMs());
        assertEquals(350, s.avgTtftMs());
        assertEquals(59, Math.round(s.decodeTokensPerSecond()), "300 tokens over 5.1 s of decode");
        assertEquals(1556, Math.round(s.prefillTokensPerSecond()), "1.4k evaluated tokens over 0.9 s");

        JsonObject js = (JsonObject) monitor.toJson(10).get("questionSummary");
        assertEquals(2, js.get("questions"));
        assertEquals(1, js.get("limitHits"));
        assertEquals(s.avgWallMs(), js.get("avgWallMs"));

        assertEquals(OllamaMonitor.QuestionSummary.EMPTY, OllamaMonitor.QuestionSummary.of(List.of()));
    }

    @Test
    void panelContextAppearsInTheJsonOnceKnown() {
        OllamaMonitor monitor = new OllamaMonitor();
        assertNull(monitor.toJson(1).get("aiPanel"));
        monitor.setPanelContext(32_768, 16_384);
        JsonObject panel = (JsonObject) monitor.toJson(1).get("aiPanel");
        assertEquals(32_768, panel.get("contextWindow"));
        assertEquals(16_384, panel.get("compactsAbove"));
    }

    @Test
    void aSmallerStepWithinAQuestionIsNotACompactionButASmallerNextQuestionIs() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.updateModels(List.of(new OllamaMonitor.LoadedModel(
                "m", "f", "35.5B", "Q4_K_M", 1, 1, 32_768, null, null)));
        // question 3 grows over its steps, then the wrap-up after the tool-call limit is smaller: no compaction
        monitor.recordRequest("m", new LlmClient.TokenUsage(20_000, 40, 20_040, 19_000, 300, 700, 0, 1_000), 0,
                "tool_calls", 3, "q3");
        monitor.recordRequest("m", new LlmClient.TokenUsage(24_000, 40, 24_040, 23_000, 300, 700, 0, 1_000), 0,
                "tool_calls", 3, "q3");
        monitor.recordRequest("m", new LlmClient.TokenUsage(15_000, 200, 15_200, 0, 20_000, 3_000, 0, 23_000), 0,
                "limit", 3, "q3");
        assertEquals(0, monitor.snapshot().totals().compactions());
        // the next question starts far below where the previous one ended: that is a compaction
        monitor.recordRequest("m", new LlmClient.TokenUsage(9_000, 40, 9_040, 0, 900, 700, 0, 1_600), 0,
                "stop", 4, "q4");
        assertEquals(1, monitor.snapshot().totals().compactions());
        assertEquals("limit", OllamaMonitor.groupByQuestion(monitor.snapshot().requests()).get(1).doneReason());
    }

    @Test
    void availabilityFollowsTheServer() {
        OllamaMonitor monitor = new OllamaMonitor();
        assertFalse(monitor.isAvailable());
        monitor.updateServer(new ServerInfo("http://localhost:11434", "0.33.3", true));
        assertTrue(monitor.isAvailable());
        monitor.updateServer(null);
        assertFalse(monitor.isAvailable());
    }

    @Test
    void resetClearsRequestsTotalsAndHistoryButKeepsServer() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.updateServer(new ServerInfo("http://localhost:11434", "0.33.3", true));
        monitor.recordRequest("m", WARM, 0, "stop");
        monitor.reset();
        Snapshot s = monitor.snapshot();
        assertTrue(s.requests().isEmpty());
        assertEquals(0, s.totals().requests());
        assertTrue(s.connected());
        assertEquals("0.33.3", s.server().version());
    }

    @Test
    void adoptingADifferentEndpointForgetsTheOldServer() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.updateServer(new ServerInfo("http://localhost:11434", "0.33.3", true));
        monitor.adoptEndpoint("http://gpu-box.lan:11434/");
        Snapshot s = monitor.snapshot();
        assertFalse(s.connected());
        assertEquals("http://gpu-box.lan:11434", s.probedUrl());
        // the same endpoint again changes nothing
        monitor.updateServer(new ServerInfo("http://gpu-box.lan:11434", "0.33.3", false));
        monitor.adoptEndpoint("http://gpu-box.lan:11434");
        assertTrue(monitor.snapshot().connected());
    }

    @Test
    void jsonCarriesServerModelsLiveSessionAndRequests() {
        OllamaMonitor monitor = new OllamaMonitor();
        monitor.updateServer(new ServerInfo("http://localhost:11434", "0.33.3", true));
        monitor.updateModels(List.of(new OllamaMonitor.LoadedModel(
                "qwen3.6:35b-a3b", "qwen35moe", "35.5B", "Q4_K_M", 23567972432L, 23567972432L, 262144,
                Instant.parse("2026-09-17T09:22:44Z"),
                new OllamaMonitor.ModelShape(
                        "qwen35moe", 41, 256, 8, 262144, 2048, 35505251456L,
                        List.of("completion", "tools")))));
        monitor.updateInstalled(List.of("qwen3.6:35b-a3b", "llama3.2:latest"));
        monitor.updateRunner(new OllamaMonitor.RunnerInfo(23629, 58237, 262144, 1, "/blob", "llama-server"));
        monitor.updateSlot(slot(false, 6, System.currentTimeMillis()));
        monitor.updateHost(new OllamaMonitor.HostStats(
                new OllamaMonitor.GpuStats("Apple GPU", 25, 31463079936L, 34170552320L, 1),
                new OllamaMonitor.ProcessStats(70566, "ollama serve", 0.1, 82176L * 1024),
                new OllamaMonitor.ProcessStats(23629, "llama-server", 12.5, 28273856L * 1024),
                Instant.now()));
        monitor.recordRequest("qwen3.6:35b-a3b", WARM, 0, "stop");
        monitor.recordRequest("qwen3.6:35b-a3b", COLD, 0, "stop");

        JsonObject json = monitor.toJson(1);
        assertEquals(true, json.get("connected"));
        JsonObject server = (JsonObject) json.get("server");
        assertEquals("0.33.3", server.get("version"));
        assertEquals("localhost:11434", server.get("host"));
        assertEquals(true, server.get("local"));

        JsonArray models = (JsonArray) json.get("loadedModels");
        assertEquals(1, models.size());
        JsonObject model = (JsonObject) models.get(0);
        assertEquals("qwen3.6:35b-a3b", model.get("name"));
        assertEquals(100, model.get("gpuPercent"));
        assertEquals(41, ((JsonObject) model.get("shape")).get("layers"));
        assertEquals(256, ((JsonObject) model.get("shape")).get("experts"));
        assertEquals(2, ((JsonArray) json.get("installedModels")).size());

        JsonObject live = (JsonObject) json.get("live");
        assertNotNull(live.get("slot"));
        assertEquals(OllamaMonitor.HISTORY_POINTS, ((JsonArray) live.get("decodeHistory")).size());
        assertEquals(58237, ((JsonObject) json.get("runner")).get("port"));
        JsonObject host = (JsonObject) json.get("host");
        assertEquals(25, ((JsonObject) host.get("gpu")).get("utilizationPercent"));
        assertEquals("llama-server", ((JsonObject) host.get("runner")).get("label"));

        JsonObject session = (JsonObject) json.get("session");
        assertEquals(2, session.get("requests"));
        assertEquals(1, session.get("coldStarts"));
        // limit applies to the request list only
        JsonArray requests = (JsonArray) json.get("requests");
        assertEquals(1, requests.size());
        JsonObject last = (JsonObject) requests.get(0);
        assertEquals(true, last.get("coldStart"));
        assertEquals("tui", last.get("source"));
        assertEquals(11108L + 138, last.get("ttftMs"));
    }

    @Test
    void disconnectedJsonSaysSoWithoutFailing() {
        OllamaMonitor monitor = new OllamaMonitor();
        JsonObject json = monitor.toJson(10);
        assertEquals(false, json.get("connected"));
        assertNull(json.get("server"));
        assertEquals(0, ((JsonArray) json.get("requests")).size());
    }

    private static SlotState slot(boolean processing, long decoded, long atMillis) {
        return new SlotState(processing, 25, 25, 9, decoded, 262144, "draft-mtp", 1, Instant.ofEpochMilli(atMillis));
    }

    private static SpanEntry genAiSpan(
            String spanId, String system, String model, String routeId, int in, int out, long durationMs) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(GenAiAttributes.OPERATION_NAME, "chat");
        attrs.put(GenAiAttributes.SYSTEM, system);
        attrs.put(GenAiAttributes.REQUEST_MODEL, model);
        attrs.put(GenAiAttributes.INPUT_TOKENS, String.valueOf(in));
        attrs.put(GenAiAttributes.OUTPUT_TOKENS, out);
        attrs.put(GenAiAttributes.FINISH_REASONS, "stop");
        long start = Instant.parse("2026-09-17T09:15:02Z").toEpochMilli() * 1_000_000L;
        return new SpanEntry(
                "trace-1", spanId, "parent", "chat " + model, "CLIENT", "OK", start,
                start + durationMs * 1_000_000L, durationMs, routeId, null, null, attrs);
    }
}
