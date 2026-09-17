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
        assertEquals(4_500, first.promptTokens());
        assertEquals(13, first.contextPercent());

        // the runner's slot wins over the model list when present
        monitor.updateSlot(slot(false, 0, System.currentTimeMillis()));
        monitor.recordRequest("m", new LlmClient.TokenUsage(20_000, 60, 20_060, 0, 900, 800, 0, 1800), 0, "stop");
        assertEquals(262144, monitor.snapshot().lastRequest().contextSize());
        assertEquals(7, monitor.snapshot().lastRequest().contextPercent());
        assertEquals(13, monitor.snapshot().totals().peakContextPercent());
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
