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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects what the Ollama tab shows: the Ollama server and its loaded models over the REST API, the live state of the
 * llama-server runner Ollama spawns on this machine, the load on the host, and a log of requests with the timings
 * Ollama reports per request.
 * <p/>
 * {@link #poll()} is called from the TUI's background refresh while the tab is showing and throttles each source on its
 * own interval. Requests arrive from two sides: the TUI's own AI panel calls {@link #recordRequest} with the usage of
 * each Ollama reply, and calls made by Camel routes come in through {@link #ingestSpans} from the GenAI observability
 * spans of the selected integration. The runner and host probes only run when the Ollama host is this machine; a remote
 * or containerised Ollama still gets the API and per-request data.
 * <p/>
 * All I/O happens outside the lock; the {@code update*} methods apply results under it and are also what the tests
 * feed.
 */
final class OllamaMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaMonitor.class);

    static final int MAX_REQUESTS = 200;
    static final int HISTORY_POINTS = 120;

    private static final long DETECT_INTERVAL_MS = 10_000;
    private static final long VERSION_INTERVAL_MS = 30_000;
    private static final long RECONNECT_INTERVAL_MS = 5_000;
    private static final long PS_INTERVAL_MS = 1_000;
    private static final long TAGS_INTERVAL_MS = 15_000;
    private static final long RUNNER_SCAN_INTERVAL_MS = 5_000;
    private static final long HOST_INTERVAL_MS = 1_000;
    /** The runner's slot is read twice a second while it generates, and every two seconds while it sits idle. */
    private static final long SLOT_BUSY_INTERVAL_MS = 500;
    private static final long SLOT_IDLE_INTERVAL_MS = 2_000;
    private static final long RATE_WINDOW_MS = 1_500;
    private static final int MAX_SEEN_SPANS = 4_000;
    private static final long SPAN_INTERVAL_MS = 5_000;

    // ---- data ----

    record ServerInfo(String baseUrl, String version, boolean local) {
    }

    record ModelShape(String architecture, int layers, int experts, int expertsUsed, long maxContext,
            int embeddingLength, long parameters, List<String> capabilities) {
    }

    record LoadedModel(String name, String family, String parameterSize, String quantization, long sizeBytes,
            long sizeVram, long contextLength, Instant expiresAt, ModelShape shape) {

        LoadedModel withShape(ModelShape newShape) {
            return new LoadedModel(
                    name, family, parameterSize, quantization, sizeBytes, sizeVram, contextLength,
                    expiresAt, newShape);
        }

        /** Share of the model held in GPU memory; 100 means fully offloaded. */
        int gpuPercent() {
            if (sizeBytes <= 0) {
                return 0;
            }
            return (int) Math.min(100, sizeVram * 100 / sizeBytes);
        }
    }

    /** Folded state of the runner's slots; {@code decoded} counts tokens of the current or last request. */
    record SlotState(boolean processing, long promptTokens, long promptProcessed, long cacheTokens, long decoded,
            long contextSize, String speculative, int slots, Instant sampledAt) {

        long contextUsed() {
            return Math.max(promptTokens, cacheTokens) + decoded;
        }

        int cacheHitPercent() {
            if (promptTokens <= 0) {
                return 0;
            }
            return (int) Math.min(100, cacheTokens * 100 / promptTokens);
        }
    }

    record RunnerInfo(long pid, int port, long contextSize, int parallel, String modelPath, String executable) {
    }

    record GpuStats(String name, int utilizationPercent, long memoryUsedBytes, long memoryTotalBytes, int count) {
    }

    record ProcessStats(long pid, String label, double cpuPercent, long rssBytes) {
    }

    record HostStats(GpuStats gpu, ProcessStats server, ProcessStats runner, Instant sampledAt) {
    }

    enum RequestSource {
        TUI,
        ROUTE
    }

    /**
     * One request as Ollama reported it (TUI) or as the GenAI span of a route recorded it (no phase timings).
     * {@code contextSize} is the context window of the runner that served it, 0 when unknown.
     */
    record RequestEntry(Instant timestamp, RequestSource source, String routeId, String model, int inputTokens,
            int outputTokens, int cachedTokens, long prefillMs, long decodeMs, long loadMs, long totalMs,
            String doneReason, long contextSize, int question, String questionText) {

        RequestEntry(Instant timestamp, RequestSource source, String routeId, String model, int inputTokens,
                     int outputTokens, int cachedTokens, long prefillMs, long decodeMs, long loadMs, long totalMs,
                     String doneReason, long contextSize) {
            this(timestamp, source, routeId, model, inputTokens, outputTokens, cachedTokens, prefillMs, decodeMs,
                 loadMs, totalMs, doneReason, contextSize, 0, null);
        }

        /** Requests of the same AI panel question (its tool-call steps) share this key; a route call stands alone. */
        String groupKey() {
            if (source == RequestSource.ROUTE) {
                return "route:" + timestamp.toEpochMilli() + ":" + routeId;
            }
            return "q" + question + ":" + (questionText != null ? questionText : "");
        }

        /**
         * Everything the model had in front of it. Ollama's {@code prompt_eval_count} is the whole prompt; the cached
         * count is the part of it served from the KV cache, not an addition.
         */
        long promptTokens() {
            return inputTokens;
        }

        /** Prompt tokens the runner actually had to evaluate this time. */
        long evaluatedTokens() {
            return Math.max(0, (long) inputTokens - cachedTokens);
        }

        /** Share of the prompt Ollama served from its cache. */
        int cacheHitPercent() {
            if (inputTokens <= 0) {
                return 0;
            }
            return (int) Math.min(100, (long) cachedTokens * 100 / inputTokens);
        }

        /** Share of the context window the prompt filled, or -1 when the window is unknown. */
        int contextPercent() {
            if (contextSize <= 0) {
                return -1;
            }
            return (int) Math.min(100, promptTokens() * 100 / contextSize);
        }

        /** Prefill speed over the tokens that were not served from cache. */
        double prefillTokensPerSecond() {
            return prefillMs > 0 && evaluatedTokens() > 0 ? evaluatedTokens() * 1000.0 / prefillMs : 0;
        }

        double decodeTokensPerSecond() {
            return decodeMs > 0 ? outputTokens * 1000.0 / decodeMs : 0;
        }

        /** Time to first token: loading the model plus processing the prompt. Zero when timings are unknown. */
        long ttftMs() {
            return hasTimings() ? loadMs + prefillMs : 0;
        }

        boolean hasTimings() {
            return prefillMs > 0 || decodeMs > 0;
        }

        /** A load of a second or more means the model was not in memory when the request arrived. */
        boolean coldStart() {
            return loadMs >= 1000;
        }
    }

    /**
     * One AI panel question with all the requests it took (the model's tool calls each cost a request), oldest step
     * first, or a single route call. Aggregates are what the user experienced for the whole question.
     */
    record QuestionGroup(String key, RequestSource source, int question, String questionText, String routeId,
            List<RequestEntry> steps) {

        RequestEntry first() {
            return steps.get(0);
        }

        RequestEntry last() {
            return steps.get(steps.size() - 1);
        }

        /** The largest prompt of the question: how far the context was pushed. */
        long promptTokens() {
            long max = 0;
            for (RequestEntry e : steps) {
                max = Math.max(max, e.promptTokens());
            }
            return max;
        }

        long outputTokens() {
            long sum = 0;
            for (RequestEntry e : steps) {
                sum += e.outputTokens();
            }
            return sum;
        }

        int cacheHitPercent() {
            long prompt = 0;
            long cached = 0;
            for (RequestEntry e : steps) {
                prompt += e.inputTokens();
                cached += e.cachedTokens();
            }
            return prompt > 0 ? (int) Math.min(100, cached * 100 / prompt) : 0;
        }

        int contextPercent() {
            int max = -1;
            for (RequestEntry e : steps) {
                max = Math.max(max, e.contextPercent());
            }
            return max;
        }

        double prefillTokensPerSecond() {
            long evaluated = 0;
            long ms = 0;
            for (RequestEntry e : steps) {
                evaluated += e.evaluatedTokens();
                ms += e.prefillMs();
            }
            return ms > 0 && evaluated > 0 ? evaluated * 1000.0 / ms : 0;
        }

        double decodeTokensPerSecond() {
            long out = 0;
            long ms = 0;
            for (RequestEntry e : steps) {
                out += e.outputTokens();
                ms += e.decodeMs();
            }
            return ms > 0 && out > 0 ? out * 1000.0 / ms : 0;
        }

        /** Time to the first token of the first step: the wait before anything happened. */
        long ttftMs() {
            return first().ttftMs();
        }

        boolean hasTimings() {
            return first().hasTimings();
        }

        boolean coldStart() {
            for (RequestEntry e : steps) {
                if (e.coldStart()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * From the first request starting to the last one finishing. A request is stamped when its reply arrives, so
         * its start is that stamp less its own total.
         */
        long wallMs() {
            long start = first().timestamp().toEpochMilli() - first().totalMs();
            long end = last().timestamp().toEpochMilli();
            return Math.max(end - start, last().totalMs());
        }

        String doneReason() {
            return last().doneReason();
        }
    }

    /**
     * Groups requests (newest first) into questions: consecutive AI panel requests with the same question form one
     * group with their steps oldest first; every route call is its own group. Groups come back newest first.
     */
    static List<QuestionGroup> groupByQuestion(List<RequestEntry> newestFirst) {
        List<QuestionGroup> groups = new ArrayList<>();
        int i = 0;
        while (i < newestFirst.size()) {
            RequestEntry head = newestFirst.get(i);
            String key = head.groupKey();
            List<RequestEntry> steps = new ArrayList<>();
            int j = i;
            while (j < newestFirst.size() && newestFirst.get(j).groupKey().equals(key)
                    && (head.source() == RequestSource.TUI || j == i)) {
                steps.add(0, newestFirst.get(j));
                j++;
            }
            groups.add(new QuestionGroup(
                    key, head.source(), head.question(), head.questionText(), head.routeId(),
                    List.copyOf(steps)));
            i = j;
        }
        return groups;
    }

    record SessionTotals(int requests, long inputTokens, long outputTokens, long cachedTokens, long prefillMs,
            long decodeMs, long loadMs, int coldStarts, int peakContextPercent, int compactions) {

        static final SessionTotals EMPTY = new SessionTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        SessionTotals plus(RequestEntry e, boolean compaction) {
            return new SessionTotals(
                    requests + 1, inputTokens + e.inputTokens(), outputTokens + e.outputTokens(),
                    cachedTokens + e.cachedTokens(), prefillMs + e.prefillMs(), decodeMs + e.decodeMs(),
                    loadMs + e.loadMs(), coldStarts + (e.coldStart() ? 1 : 0),
                    Math.max(peakContextPercent, e.contextPercent()), compactions + (compaction ? 1 : 0));
        }

        double avgDecodeTokensPerSecond() {
            return decodeMs > 0 ? outputTokens * 1000.0 / decodeMs : 0;
        }

        double avgPrefillTokensPerSecond() {
            long evaluated = Math.max(0, inputTokens - cachedTokens);
            return prefillMs > 0 && evaluated > 0 ? evaluated * 1000.0 / prefillMs : 0;
        }
    }

    /**
     * The AI panel question being answered right now, so the tab can list it from the moment it was asked: before the
     * first request returns there is no {@link RequestEntry} for it, and while tools run its last step says
     * {@code tool_calls} like a finished question's would.
     */
    record ActiveQuestion(int question, String text, Instant startedAt) {

        long elapsedMs() {
            return Math.max(0, System.currentTimeMillis() - startedAt.toEpochMilli());
        }

        boolean matches(QuestionGroup g) {
            return g.source() == RequestSource.TUI && g.question() == question;
        }
    }

    /**
     * The AI panel's questions of the session in one line, the footer of the requests table: averages per question
     * where a question row shows a figure per question, pooled rates and cache hit, the peak context fill and how many
     * questions ended at the tool-call limit. Route calls are not questions and are left out.
     */
    record QuestionSummary(int questions, int requests, long totalWallMs, long avgPromptTokens,
            long avgOutputTokens, int cacheHitPercent, int peakContextPercent, double prefillTokensPerSecond,
            double decodeTokensPerSecond, long avgTtftMs, long avgWallMs, int limitHits) {

        static final QuestionSummary EMPTY = new QuestionSummary(0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0);

        static QuestionSummary of(List<QuestionGroup> groups) {
            int questions = 0;
            int requests = 0;
            long wall = 0;
            long prompt = 0;
            long output = 0;
            long promptAll = 0;
            long cachedAll = 0;
            long evaluated = 0;
            long prefillMs = 0;
            long decodeMs = 0;
            int peak = -1;
            long ttft = 0;
            int timed = 0;
            int limits = 0;
            for (QuestionGroup g : groups) {
                if (g.source() != RequestSource.TUI) {
                    continue;
                }
                questions++;
                requests += g.steps().size();
                wall += g.wallMs();
                prompt += g.promptTokens();
                output += g.outputTokens();
                peak = Math.max(peak, g.contextPercent());
                if (g.hasTimings()) {
                    ttft += g.ttftMs();
                    timed++;
                }
                if ("limit".equals(g.doneReason())) {
                    limits++;
                }
                for (RequestEntry e : g.steps()) {
                    promptAll += e.inputTokens();
                    cachedAll += e.cachedTokens();
                    evaluated += e.evaluatedTokens();
                    prefillMs += e.prefillMs();
                    decodeMs += e.decodeMs();
                }
            }
            if (questions == 0) {
                return EMPTY;
            }
            return new QuestionSummary(
                    questions, requests, wall, prompt / questions, output / questions,
                    promptAll > 0 ? (int) Math.min(100, cachedAll * 100 / promptAll) : 0, peak,
                    prefillMs > 0 && evaluated > 0 ? evaluated * 1000.0 / prefillMs : 0,
                    decodeMs > 0 && output > 0 ? output * 1000.0 / decodeMs : 0,
                    timed > 0 ? ttft / timed : 0, wall / questions, limits);
        }
    }

    /** Immutable view for rendering and for the MCP tool. */
    record Snapshot(ServerInfo server, List<LoadedModel> models, List<String> installed, SlotState slot,
            RunnerInfo runner, HostStats host, List<RequestEntry> requests, double liveDecodeRate,
            double livePrefillRate, long[] decodeHistory, SessionTotals totals, String lastError, Instant lastPoll,
            String probedUrl, int panelWindow, int panelBudget, ActiveQuestion activeQuestion) {

        boolean connected() {
            return server != null;
        }

        boolean local() {
            return server != null && server.local();
        }

        RequestEntry lastRequest() {
            return requests.isEmpty() ? null : requests.get(0);
        }
    }

    // ---- state ----

    private final Object lock = new Object();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build();

    private volatile String baseUrl;
    private ServerInfo server;
    private List<LoadedModel> models = List.of();
    private List<String> installed = List.of();
    private final Map<String, ModelShape> shapes = new HashMap<>();
    private final Set<String> shapeAttempted = new HashSet<>();
    private SlotState slot;
    private RunnerInfo runner;
    private long serverPid;
    private HostStats host;
    private final Deque<RequestEntry> requests = new ArrayDeque<>();
    private final LinkedHashSet<String> seenSpanIds = new LinkedHashSet<>();
    private SessionTotals totals = SessionTotals.EMPTY;
    private long lastTuiPromptTokens;
    private int lastTuiQuestion;
    private final TokenRateWindow decodeWindow = new TokenRateWindow(RATE_WINDOW_MS);
    private final TokenRateWindow prefillWindow = new TokenRateWindow(RATE_WINDOW_MS);
    private final long[] decodeHistory = new long[HISTORY_POINTS];
    private String lastError;
    private Instant lastPoll;
    private int panelWindow;
    private int panelBudget;
    private ActiveQuestion activeQuestion;

    private long lastProbe;
    private long lastVersion;
    private long lastPs;
    private long lastTags;
    private long lastRunnerScan;
    private long lastHost;
    private long lastSlotPoll;
    private int psFailures;
    private long lastSpanIngest;
    private Boolean nvidiaSmiAvailable;
    private final Map<Long, long[]> cpuSamples = new HashMap<>();

    // ---- input from the rest of the TUI ----

    /** Whether an Ollama server currently answers; the More menu lists the tab only then. */
    boolean isAvailable() {
        synchronized (lock) {
            return server != null;
        }
    }

    /**
     * Cheap background check while the tab is not showing, so the More menu can list it as soon as Ollama comes up and
     * drop it when Ollama goes away: endpoint detection and one version request every ten seconds. Full polling happens
     * in {@link #poll()} while the tab is active.
     */
    void probe() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            if (now - lastProbe >= DETECT_INTERVAL_MS) {
                lastProbe = now;
                lastVersion = now;
                checkServer();
            }
        } catch (Exception e) {
            LOG.debug("Ollama probe failed", e);
        } finally {
            polling.set(false);
        }
    }

    /** Uses the Ollama endpoint another part of the TUI already resolved (the AI panel's client, an explicit URL). */
    void adoptEndpoint(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String normalized = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        // the URL switch and the reset of what was known about the old server happen under one lock, so a
        // concurrent poll never sees the new URL together with the old server still flagged as connected
        synchronized (lock) {
            if (!normalized.equals(baseUrl)) {
                baseUrl = normalized;
                server = null;
                lastVersion = 0;
                runner = null;
                slot = null;
            }
        }
    }

    /** As {@link #recordRequest(String, LlmClient.TokenUsage, long, String, int, String)} without a question. */
    void recordRequest(String model, LlmClient.TokenUsage usage, long latencyMs, String doneReason) {
        recordRequest(model, usage, latencyMs, doneReason, 0, null);
    }

    /**
     * Records a request the TUI itself made to Ollama, with the usage and timings Ollama returned. {@code question} and
     * {@code questionText} identify the AI panel turn so the tool-call steps of one question group together.
     */
    void recordRequest(
            String model, LlmClient.TokenUsage usage, long latencyMs, String doneReason, int question,
            String questionText) {
        if (usage == null) {
            return;
        }
        long total = usage.totalMillis() > 0 ? usage.totalMillis() : Math.max(0, latencyMs);
        RequestEntry entry = new RequestEntry(
                Instant.now(), RequestSource.TUI, null, model != null ? model : "unknown",
                usage.inputTokens(), usage.outputTokens(), usage.cachedTokens(),
                usage.prefillMillis(), usage.generationMillis(), usage.loadMillis(), total, doneReason,
                contextSizeForNewRequest(), question, questionText != null ? questionText.strip() : null);
        synchronized (lock) {
            addRequest(entry);
        }
    }

    /**
     * The context window that served a request that just finished: the runner's slot size when known, else the
     * allocated context of the loaded model, fetched on the spot when the tab has not polled yet. A request with a
     * different {@code num_ctx} makes Ollama reload before answering, so what is loaded after the reply is what served
     * it.
     */
    private long contextSizeForNewRequest() {
        long known = knownContextSize();
        if (known > 0) {
            return known;
        }
        String base = baseUrl;
        if (base != null) {
            JsonObject ps = getJsonObject(base + "/api/ps");
            if (ps != null) {
                List<LoadedModel> loaded = OllamaParsers.parsePs(ps);
                if (!loaded.isEmpty()) {
                    synchronized (lock) {
                        List<LoadedModel> withShapes = new ArrayList<>();
                        for (LoadedModel m : loaded) {
                            withShapes.add(m.withShape(shapes.get(m.name())));
                        }
                        models = List.copyOf(withShapes);
                    }
                    return loaded.get(0).contextLength();
                }
            }
        }
        return 0;
    }

    private long knownContextSize() {
        synchronized (lock) {
            if (slot != null && slot.contextSize() > 0) {
                return slot.contextSize();
            }
            return models.isEmpty() ? 0 : models.get(0).contextLength();
        }
    }

    /**
     * Adds the Ollama calls Camel routes made, from the GenAI observability spans of the selected integration. Spans
     * are deduplicated by id, so the same list can be handed over on every refresh.
     */
    void ingestSpans(List<SpanEntry> spans) {
        if (spans == null || spans.isEmpty()) {
            return;
        }
        List<RequestEntry> fresh = new ArrayList<>();
        synchronized (lock) {
            for (SpanEntry span : spans) {
                if (!GenAiSpanUsageExtractor.isGenAiSpan(span) || span.spanId() == null) {
                    continue;
                }
                Object system = span.attributes().get(GenAiAttributes.SYSTEM);
                if (system == null || !system.toString().toLowerCase(Locale.ROOT).contains("ollama")) {
                    continue;
                }
                if (!seenSpanIds.add(span.spanId())) {
                    continue;
                }
                Map<String, Object> attrs = span.attributes();
                String model = OllamaParsers.str(attrs, GenAiAttributes.RESPONSE_MODEL);
                if (model == null) {
                    model = OllamaParsers.str(attrs, GenAiAttributes.REQUEST_MODEL);
                }
                if (model == null) {
                    model = span.name() != null ? span.name() : "unknown";
                }
                Instant ts = span.startEpochNanos() > 0 ? Instant.ofEpochSecond(0, span.startEpochNanos()) : Instant.now();
                fresh.add(new RequestEntry(
                        ts, RequestSource.ROUTE, span.routeId(), model,
                        (int) OllamaParsers.num(attrs, GenAiAttributes.INPUT_TOKENS),
                        (int) OllamaParsers.num(attrs, GenAiAttributes.OUTPUT_TOKENS),
                        0, 0, 0, 0, Math.max(0, span.durationMs()),
                        OllamaParsers.str(attrs, GenAiAttributes.FINISH_REASONS), knownContextSizeLocked()));
            }
            // spans arrive oldest first; keep the log newest first
            fresh.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
            for (RequestEntry e : fresh) {
                addRequest(e);
            }
            while (seenSpanIds.size() > MAX_SEEN_SPANS) {
                seenSpanIds.remove(seenSpanIds.iterator().next());
            }
        }
    }

    /** True once every few seconds: the GenAI spans of a route are worth re-reading. */
    boolean wantsSpans() {
        long now = System.currentTimeMillis();
        if (now - lastSpanIngest >= SPAN_INTERVAL_MS) {
            lastSpanIngest = now;
            return true;
        }
        return false;
    }

    /** What the AI panel asks Ollama for and where it compacts, shown in the header next to what Ollama allocated. */
    void setPanelContext(int window, int budget) {
        synchronized (lock) {
            panelWindow = window;
            panelBudget = budget;
        }
    }

    /** The AI panel started working on a question; it is listed as in progress until {@link #questionFinished()}. */
    void questionStarted(int question, String text) {
        synchronized (lock) {
            activeQuestion = new ActiveQuestion(question, text, Instant.now());
        }
    }

    /** The AI panel's turn ended: answered, failed or cancelled. */
    void questionFinished() {
        synchronized (lock) {
            activeQuestion = null;
        }
    }

    /** Clears the request log, the session totals and the rate history. */
    void reset() {
        synchronized (lock) {
            requests.clear();
            totals = SessionTotals.EMPTY;
            lastTuiPromptTokens = 0;
            lastTuiQuestion = 0;
            Arrays.fill(decodeHistory, 0);
            decodeWindow.clear();
            prefillWindow.clear();
        }
    }

    /** As {@link #knownContextSize()} for callers already holding the lock. */
    private long knownContextSizeLocked() {
        if (slot != null && slot.contextSize() > 0) {
            return slot.contextSize();
        }
        return models.isEmpty() ? 0 : models.get(0).contextLength();
    }

    private void addRequest(RequestEntry entry) {
        // the first prompt of a question that is a fifth or more smaller than the last prompt of the previous
        // question means the history was compacted between the two (or a new conversation started); either way the
        // context was freed. Within a question prompts only grow, so a smaller step (the wrap-up after the tool call
        // limit) is not a compaction.
        boolean compaction = false;
        if (entry.source() == RequestSource.TUI) {
            boolean newQuestion = entry.question() == 0 || entry.question() != lastTuiQuestion;
            if (newQuestion && lastTuiPromptTokens > 0 && entry.promptTokens() < lastTuiPromptTokens * 0.8) {
                compaction = true;
            }
            lastTuiQuestion = entry.question();
            lastTuiPromptTokens = entry.promptTokens();
        }
        requests.addFirst(entry);
        while (requests.size() > MAX_REQUESTS) {
            requests.removeLast();
        }
        totals = totals.plus(entry, compaction);
    }

    // ---- state updates (also the test seam) ----

    void updateServer(ServerInfo info) {
        synchronized (lock) {
            server = info;
            if (info != null) {
                baseUrl = info.baseUrl();
                lastError = null;
            }
        }
    }

    void updateModels(List<LoadedModel> loaded) {
        synchronized (lock) {
            models = loaded != null ? List.copyOf(loaded) : List.of();
        }
    }

    void updateInstalled(List<String> names) {
        synchronized (lock) {
            installed = names != null ? List.copyOf(names) : List.of();
        }
    }

    void updateRunner(RunnerInfo info) {
        synchronized (lock) {
            runner = info;
            if (info == null) {
                slot = null;
            }
        }
    }

    /** Applies a runner slot sample; the decode and prefill rate windows advance from its counters. */
    void updateSlot(SlotState state) {
        synchronized (lock) {
            slot = state;
            if (state != null) {
                long now = state.sampledAt() != null ? state.sampledAt().toEpochMilli() : System.currentTimeMillis();
                decodeWindow.sample(now, state.decoded());
                prefillWindow.sample(now, state.promptProcessed());
            }
        }
    }

    void updateHost(HostStats stats) {
        synchronized (lock) {
            host = stats;
        }
    }

    /** Ends one poll: appends the current live decode rate to the history and stamps the poll time. */
    void tick(long nowMillis) {
        synchronized (lock) {
            long rate = Math.round(decodeWindow.ratePerSecond(nowMillis));
            System.arraycopy(decodeHistory, 1, decodeHistory, 0, decodeHistory.length - 1);
            decodeHistory[decodeHistory.length - 1] = rate;
            lastPoll = Instant.ofEpochMilli(nowMillis);
        }
    }

    Snapshot snapshot() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            return new Snapshot(
                    server, models, installed, slot, runner, host, List.copyOf(requests),
                    decodeWindow.ratePerSecond(now), prefillWindow.ratePerSecond(now),
                    decodeHistory.clone(), totals, lastError, lastPoll, baseUrl, panelWindow, panelBudget,
                    activeQuestion);
        }
    }

    /**
     * Releases the HTTP client at TUI shutdown. {@code HttpClient} is closeable from Java 21 on, while this module
     * compiles for Java 17, so the close happens when the running JVM offers it and is a no-op otherwise; the client's
     * threads are daemons either way.
     */
    void close() {
        if (http instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.debug("Closing the Ollama HTTP client failed", e);
            }
        }
    }

    // ---- polling ----

    /** One refresh cycle; safe to call every few hundred milliseconds, each source keeps its own interval. */
    void poll() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            doPoll(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.debug("Ollama poll failed", e);
        } finally {
            polling.set(false);
        }
    }

    private void doPoll(long now) {
        boolean connected;
        synchronized (lock) {
            connected = server != null;
        }
        long versionInterval = connected ? VERSION_INTERVAL_MS : RECONNECT_INTERVAL_MS;
        if (now - lastVersion >= versionInterval) {
            lastVersion = now;
            lastProbe = now;
            connected = checkServer();
        }
        if (!connected) {
            tick(now);
            return;
        }
        String base = baseUrl;

        if (now - lastPs >= PS_INTERVAL_MS) {
            lastPs = now;
            JsonObject ps = getJsonObject(base + "/api/ps");
            if (ps != null) {
                psFailures = 0;
                List<LoadedModel> loaded = new ArrayList<>();
                for (LoadedModel m : OllamaParsers.parsePs(ps)) {
                    loaded.add(m.withShape(shapeFor(base, m.name())));
                }
                updateModels(loaded);
            } else if (++psFailures >= 3) {
                synchronized (lock) {
                    server = null;
                    lastError = "Ollama stopped answering at " + OllamaParsers.displayHost(base);
                }
                lastVersion = 0;
                tick(now);
                return;
            }
        }

        if (now - lastTags >= TAGS_INTERVAL_MS) {
            lastTags = now;
            JsonObject tags = getJsonObject(base + "/api/tags");
            if (tags != null) {
                List<String> names = new ArrayList<>();
                Collection<?> list = tags.getCollection("models");
                if (list != null) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            String n = OllamaParsers.str(m, "name");
                            if (n != null) {
                                names.add(n);
                            }
                        }
                    }
                }
                updateInstalled(names);
            }
        }

        if (OllamaParsers.isLoopbackUrl(base)) {
            pollLocal(now);
        }
        tick(now);
    }

    private void pollLocal(long now) {
        RunnerInfo current;
        boolean haveModels;
        synchronized (lock) {
            current = runner;
            haveModels = !models.isEmpty();
        }
        // scan often while a model is loaded but no runner is known yet, rarely otherwise
        long scanInterval = current == null && haveModels ? PS_INTERVAL_MS : RUNNER_SCAN_INTERVAL_MS;
        if (now - lastRunnerScan >= scanInterval) {
            lastRunnerScan = now;
            RunnerInfo found = findRunner();
            if (found == null || current == null || found.pid() != current.pid() || found.port() != current.port()) {
                updateRunner(found);
                current = found;
            }
        }
        if (current != null) {
            // llama-server logs every request at the verbosity Ollama starts it with, so the slot is read only as
            // often as the live view needs: fast while it generates, slowly while idle
            boolean busy;
            synchronized (lock) {
                busy = slot != null && slot.processing();
            }
            long slotInterval = busy ? SLOT_BUSY_INTERVAL_MS : SLOT_IDLE_INTERVAL_MS;
            if (now - lastSlotPoll >= slotInterval) {
                lastSlotPoll = now;
                JsonArray slots = getJsonArray("http://127.0.0.1:" + current.port() + "/slots");
                if (slots != null) {
                    updateSlot(OllamaParsers.parseSlots(slots, Instant.ofEpochMilli(now)));
                } else {
                    updateRunner(null);
                    current = null;
                }
            }
        }
        if (now - lastHost >= HOST_INTERVAL_MS) {
            lastHost = now;
            updateHost(probeHost(now, current));
        }
    }

    private ModelShape shapeFor(String base, String name) {
        synchronized (lock) {
            ModelShape cached = shapes.get(name);
            if (cached != null || shapeAttempted.contains(name)) {
                return cached;
            }
            shapeAttempted.add(name);
        }
        JsonObject body = new JsonObject();
        body.put("model", name);
        JsonObject show = postJsonObject(base + "/api/show", body.toJson());
        ModelShape shape = OllamaParsers.parseShow(show);
        if (shape != null) {
            synchronized (lock) {
                shapes.put(name, shape);
            }
        }
        return shape;
    }

    /** Detects the endpoint when none is known yet and confirms the server answers; true when connected. */
    private boolean checkServer() {
        String base = baseUrl;
        if (base == null) {
            base = detectEndpoint();
            if (base == null) {
                return false;
            }
            adoptEndpoint(base);
            base = baseUrl;
        }
        JsonObject version = getJsonObject(base + "/api/version");
        if (version == null) {
            synchronized (lock) {
                server = null;
                models = List.of();
                slot = null;
                lastError = "Ollama not reachable at " + OllamaParsers.displayHost(base);
            }
            return false;
        }
        updateServer(new ServerInfo(base, OllamaParsers.str(version, "version"), OllamaParsers.isLoopbackUrl(base)));
        return true;
    }

    private String detectEndpoint() {
        try {
            LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama);
            if (client.detectEndpoint()) {
                return client.endpointUrl();
            }
        } catch (Exception e) {
            LOG.debug("Ollama endpoint detection failed", e);
        }
        return null;
    }

    // ---- runner and host probes ----

    private RunnerInfo findRunner() {
        try {
            RunnerInfo[] found = new RunnerInfo[1];
            long[] parent = new long[1];
            ProcessHandle.allProcesses().forEach(ph -> {
                if (found[0] != null) {
                    return;
                }
                Optional<String> cmd = ph.info().commandLine();
                if (cmd.isPresent() && OllamaParsers.isRunnerCommandLine(cmd.get())) {
                    RunnerInfo ri = OllamaParsers.parseRunnerCommandLine(ph.pid(), cmd.get());
                    if (ri != null) {
                        found[0] = ri;
                        parent[0] = ph.parent().map(ProcessHandle::pid).orElse(0L);
                    }
                }
            });
            if (found[0] != null) {
                serverPid = parent[0];
            } else {
                serverPid = findServerPid();
            }
            return found[0];
        } catch (Exception e) {
            LOG.debug("Ollama runner scan failed", e);
            return null;
        }
    }

    private long findServerPid() {
        try {
            return ProcessHandle.allProcesses()
                    .filter(ph -> ph.info().commandLine().map(c -> c.trim().endsWith("ollama serve")).orElse(false))
                    .mapToLong(ProcessHandle::pid)
                    .findFirst().orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    private HostStats probeHost(long now, RunnerInfo current) {
        GpuStats gpu = probeGpu();
        ProcessStats serverStats = serverPid > 0 ? processStats(serverPid, "ollama serve", now) : null;
        ProcessStats runnerStats = current != null ? processStats(current.pid(), current.executable(), now) : null;
        if (gpu == null && serverStats == null && runnerStats == null) {
            return null;
        }
        return new HostStats(gpu, serverStats, runnerStats, Instant.ofEpochMilli(now));
    }

    private GpuStats probeGpu() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return OllamaParsers.parseIoreg(runCommand(List.of("ioreg", "-r", "-d", "1", "-c", "IOAccelerator")));
        }
        if (Boolean.FALSE.equals(nvidiaSmiAvailable)) {
            return null;
        }
        String out = runCommand(List.of("nvidia-smi", "--query-gpu=name,utilization.gpu,memory.used,memory.total",
                "--format=csv,noheader,nounits"));
        if (out == null) {
            nvidiaSmiAvailable = Boolean.FALSE;
            return null;
        }
        nvidiaSmiAvailable = Boolean.TRUE;
        return OllamaParsers.parseNvidiaSmi(out);
    }

    /**
     * CPU percent from the growth of the process's CPU time between two polls, plus resident memory. Read through
     * {@code ps} on macOS and Linux (the JDK exposes no CPU time for other processes on macOS), from
     * {@link ProcessHandle} elsewhere.
     */
    private ProcessStats processStats(long pid, String label, long now) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            cpuSamples.remove(pid);
            return null;
        }
        long cpuMs = -1;
        long rss = 0;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            long[] ps = OllamaParsers.parsePsCpuAndRss(
                    runCommand(List.of("ps", "-o", "cputime=,rss=", "-p", Long.toString(pid))));
            if (ps != null) {
                cpuMs = ps[0];
                rss = ps[1];
            }
        }
        if (cpuMs < 0) {
            cpuMs = handle.get().info().totalCpuDuration().map(Duration::toMillis).orElse(-1L);
        }
        double cpu = 0;
        if (cpuMs >= 0) {
            long[] prev = cpuSamples.put(pid, new long[] { now, cpuMs });
            if (prev != null && now > prev[0]) {
                cpu = (cpuMs - prev[1]) * 100.0 / (now - prev[0]);
            }
        }
        return new ProcessStats(pid, label, Math.max(0, cpu), rss);
    }

    // ---- I/O helpers ----

    private JsonObject getJsonObject(String url) {
        Object parsed = getJson(url, null);
        return parsed instanceof JsonObject jo ? jo : null;
    }

    private JsonArray getJsonArray(String url) {
        Object parsed = getJson(url, null);
        return parsed instanceof JsonArray ja ? ja : null;
    }

    private JsonObject postJsonObject(String url, String body) {
        Object parsed = getJson(url, body);
        return parsed instanceof JsonObject jo ? jo : null;
    }

    private Object getJson(String url, String postBody) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(2500));
            if (postBody != null) {
                b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(postBody));
            } else {
                b.GET();
            }
            HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank()) {
                return null;
            }
            return Jsoner.deserialize(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    private static String runCommand(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] out;
            try (InputStream in = process.getInputStream()) {
                out = in.readAllBytes();
            }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return null;
        }
    }

    // ---- JSON for the MCP tool ----

    JsonObject toJson(int requestLimit) {
        Snapshot s = snapshot();
        JsonObject root = new JsonObject();
        root.put("connected", s.connected());
        if (s.server() != null) {
            JsonObject server = new JsonObject();
            server.put("url", s.server().baseUrl());
            server.put("host", OllamaParsers.displayHost(s.server().baseUrl()));
            server.put("version", s.server().version());
            server.put("local", s.server().local());
            root.put("server", server);
        } else if (s.probedUrl() != null) {
            root.put("url", s.probedUrl());
        }
        if (s.lastError() != null) {
            root.put("error", s.lastError());
        }
        JsonArray models = new JsonArray();
        for (LoadedModel m : s.models()) {
            JsonObject jm = new JsonObject();
            jm.put("name", m.name());
            jm.put("family", m.family());
            jm.put("parameterSize", m.parameterSize());
            jm.put("quantization", m.quantization());
            jm.put("sizeBytes", m.sizeBytes());
            jm.put("sizeVramBytes", m.sizeVram());
            jm.put("gpuPercent", m.gpuPercent());
            jm.put("contextLength", m.contextLength());
            if (m.expiresAt() != null) {
                jm.put("expiresAt", m.expiresAt().toString());
            }
            if (m.shape() != null) {
                JsonObject shape = new JsonObject();
                shape.put("architecture", m.shape().architecture());
                shape.put("layers", m.shape().layers());
                shape.put("experts", m.shape().experts());
                shape.put("expertsUsed", m.shape().expertsUsed());
                shape.put("maxContext", m.shape().maxContext());
                shape.put("parameters", m.shape().parameters());
                shape.put("capabilities", new JsonArray(m.shape().capabilities()));
                jm.put("shape", shape);
            }
            models.add(jm);
        }
        root.put("loadedModels", models);
        root.put("installedModels", new JsonArray(s.installed()));

        JsonObject live = new JsonObject();
        live.put("decodeTokensPerSecond", round1(s.liveDecodeRate()));
        live.put("prefillTokensPerSecond", round1(s.livePrefillRate()));
        JsonArray hist = new JsonArray();
        for (long v : s.decodeHistory()) {
            hist.add(v);
        }
        live.put("decodeHistory", hist);
        if (s.slot() != null) {
            JsonObject slot = new JsonObject();
            slot.put("processing", s.slot().processing());
            slot.put("promptTokens", s.slot().promptTokens());
            slot.put("promptTokensProcessed", s.slot().promptProcessed());
            slot.put("cacheTokens", s.slot().cacheTokens());
            slot.put("decodedTokens", s.slot().decoded());
            slot.put("contextUsed", s.slot().contextUsed());
            slot.put("contextSize", s.slot().contextSize());
            slot.put("cacheHitPercent", s.slot().cacheHitPercent());
            slot.put("speculative", s.slot().speculative());
            slot.put("slots", s.slot().slots());
            live.put("slot", slot);
        }
        root.put("live", live);

        if (s.runner() != null) {
            JsonObject runner = new JsonObject();
            runner.put("pid", s.runner().pid());
            runner.put("port", s.runner().port());
            runner.put("executable", s.runner().executable());
            runner.put("contextSize", s.runner().contextSize());
            runner.put("parallel", s.runner().parallel());
            root.put("runner", runner);
        }
        if (s.host() != null) {
            JsonObject hostJson = new JsonObject();
            if (s.host().gpu() != null) {
                JsonObject gpu = new JsonObject();
                gpu.put("name", s.host().gpu().name());
                gpu.put("utilizationPercent", s.host().gpu().utilizationPercent());
                gpu.put("memoryUsedBytes", s.host().gpu().memoryUsedBytes());
                gpu.put("memoryTotalBytes", s.host().gpu().memoryTotalBytes());
                gpu.put("count", s.host().gpu().count());
                hostJson.put("gpu", gpu);
            }
            if (s.host().server() != null) {
                hostJson.put("server", processJson(s.host().server()));
            }
            if (s.host().runner() != null) {
                hostJson.put("runner", processJson(s.host().runner()));
            }
            root.put("host", hostJson);
        }

        JsonObject session = new JsonObject();
        session.put("requests", s.totals().requests());
        session.put("inputTokens", s.totals().inputTokens());
        session.put("outputTokens", s.totals().outputTokens());
        session.put("cachedTokens", s.totals().cachedTokens());
        session.put("avgDecodeTokensPerSecond", round1(s.totals().avgDecodeTokensPerSecond()));
        session.put("avgPrefillTokensPerSecond", round1(s.totals().avgPrefillTokensPerSecond()));
        session.put("coldStarts", s.totals().coldStarts());
        session.put("peakContextPercent", s.totals().peakContextPercent());
        session.put("compactions", s.totals().compactions());
        root.put("session", session);
        if (s.panelWindow() > 0) {
            JsonObject panel = new JsonObject();
            panel.put("contextWindow", s.panelWindow());
            panel.put("compactsAbove", s.panelBudget());
            root.put("aiPanel", panel);
        }

        JsonArray reqs = new JsonArray();
        int n = 0;
        for (RequestEntry e : s.requests()) {
            if (n++ >= requestLimit) {
                break;
            }
            reqs.add(requestJson(e));
        }
        root.put("requests", reqs);
        JsonArray questions = new JsonArray();
        ActiveQuestion active = s.activeQuestion();
        boolean activeListed = false;
        int q = 0;
        List<QuestionGroup> groups = groupByQuestion(s.requests());
        QuestionSummary summary = QuestionSummary.of(groups);
        if (summary.questions() > 0) {
            JsonObject js = new JsonObject();
            js.put("questions", summary.questions());
            js.put("requests", summary.requests());
            js.put("totalWallMs", summary.totalWallMs());
            js.put("avgWallMs", summary.avgWallMs());
            js.put("avgTtftMs", summary.avgTtftMs());
            js.put("avgPromptTokens", summary.avgPromptTokens());
            js.put("avgOutputTokens", summary.avgOutputTokens());
            js.put("cacheHitPercent", summary.cacheHitPercent());
            js.put("peakContextPercent", summary.peakContextPercent());
            js.put("prefillTokensPerSecond", round1(summary.prefillTokensPerSecond()));
            js.put("decodeTokensPerSecond", round1(summary.decodeTokensPerSecond()));
            js.put("limitHits", summary.limitHits());
            root.put("questionSummary", js);
        }
        for (QuestionGroup g : groups) {
            if (q++ >= requestLimit) {
                break;
            }
            JsonObject jg = new JsonObject();
            if (active != null && active.matches(g)) {
                activeListed = true;
                jg.put("inProgress", true);
                jg.put("elapsedMs", active.elapsedMs());
            }
            jg.put("time", g.first().timestamp().toString());
            jg.put("source", g.source().name().toLowerCase(Locale.ROOT));
            if (g.routeId() != null) {
                jg.put("routeId", g.routeId());
            }
            if (g.question() > 0) {
                jg.put("question", g.question());
            }
            if (g.questionText() != null) {
                jg.put("questionText", g.questionText());
            }
            jg.put("model", g.last().model());
            jg.put("steps", g.steps().size());
            jg.put("promptTokens", g.promptTokens());
            jg.put("outputTokens", g.outputTokens());
            jg.put("cacheHitPercent", g.cacheHitPercent());
            jg.put("contextPercent", g.contextPercent());
            jg.put("prefillTokensPerSecond", round1(g.prefillTokensPerSecond()));
            jg.put("decodeTokensPerSecond", round1(g.decodeTokensPerSecond()));
            jg.put("ttftMs", g.ttftMs());
            jg.put("wallMs", g.wallMs());
            jg.put("coldStart", g.coldStart());
            if (g.doneReason() != null) {
                jg.put("doneReason", g.doneReason());
            }
            questions.add(jg);
        }
        if (active != null && !activeListed) {
            // asked, but no request has returned yet
            JsonObject jq = new JsonObject();
            jq.put("time", active.startedAt().toString());
            jq.put("source", "tui");
            jq.put("question", active.question());
            if (active.text() != null) {
                jq.put("questionText", active.text());
            }
            jq.put("steps", 0);
            jq.put("inProgress", true);
            jq.put("elapsedMs", active.elapsedMs());
            questions.add(0, jq);
        }
        root.put("questions", questions);
        if (s.lastPoll() != null) {
            root.put("lastPoll", s.lastPoll().toString());
        }
        return root;
    }

    static JsonObject requestJson(RequestEntry e) {
        JsonObject r = new JsonObject();
        r.put("time", e.timestamp().toString());
        r.put("source", e.source().name().toLowerCase(Locale.ROOT));
        if (e.routeId() != null) {
            r.put("routeId", e.routeId());
        }
        r.put("model", e.model());
        r.put("inputTokens", e.inputTokens());
        r.put("outputTokens", e.outputTokens());
        r.put("cachedTokens", e.cachedTokens());
        r.put("prefillMs", e.prefillMs());
        r.put("decodeMs", e.decodeMs());
        r.put("loadMs", e.loadMs());
        r.put("totalMs", e.totalMs());
        r.put("prefillTokensPerSecond", round1(e.prefillTokensPerSecond()));
        r.put("decodeTokensPerSecond", round1(e.decodeTokensPerSecond()));
        r.put("ttftMs", e.ttftMs());
        r.put("coldStart", e.coldStart());
        r.put("promptTokens", e.promptTokens());
        r.put("evaluatedTokens", e.evaluatedTokens());
        if (e.question() > 0) {
            r.put("question", e.question());
        }
        if (e.questionText() != null) {
            r.put("questionText", e.questionText());
        }
        r.put("contextSize", e.contextSize());
        r.put("contextPercent", e.contextPercent());
        if (e.doneReason() != null) {
            r.put("doneReason", e.doneReason());
        }
        return r;
    }

    private static JsonObject processJson(ProcessStats p) {
        JsonObject j = new JsonObject();
        j.put("pid", p.pid());
        j.put("label", p.label());
        j.put("cpuPercent", round1(p.cpuPercent()));
        j.put("rssBytes", p.rssBytes());
        return j;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
