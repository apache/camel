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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiAgentRuns;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiAgentRuns.AgentRun;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiAgentRuns.Step;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiSpans;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiTranscript;
import org.apache.camel.dsl.jbang.core.common.genai.SpanEntry;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The GenAI part of the AI trace and the conversation of a run, from the GenAI spans of the span dump (CAMEL-27312).
 * Written for a model that reads the answer: the summary comes first and fits in the few hundred characters a chat
 * history keeps of a tool result, the runs are the newest first and shallow, and the long texts are cut with a note.
 * The words are those of the Camel TUI: agent run, model call, tool call.
 */
final class GenAiTraceReport {

    static final String CONVERSATION_TOOL = "camel_runtime_ai_conversation";
    static final int MAX_RUNS = 5;
    static final int MAX_STEP_TEXT = 200;
    static final int MAX_QUESTION = 200;
    static final int MAX_MESSAGE = 1000;
    static final int MAX_CONVERSATION = 6000;
    private static final int MAX_MODELS_IN_SUMMARY = 3;

    static final String NO_SPANS = "No GenAI spans. Run the integration with camel run --observe (it adds "
                                   + "camel-ai-observability when a GenAI component is used) and set "
                                   + "camel.aiObservability.level=detailed to see the agent runs and tool calls.";

    /** The model calls of one model. */
    record ModelCalls(String model, String provider, int calls, long inputTokens, long outputTokens, Long cachedTokens,
            long avgMs, String finishReasons) {
    }

    /** A model call or tool call of a run; the arguments and result are recorded at level full only. */
    record RunStep(String step, String name, long durationMs, Long inputTokens, Long outputTokens, Long cachedTokens,
            String finishReason, String arguments, String result) {
    }

    /** An agent run; the question is its first user message, recorded at level full only. */
    record Run(String runId, String question, String agent, String model, String routeId, String exchangeId,
            String start, long durationMs, long modelCalls, long toolCalls, long inputTokens, long outputTokens,
            Long cachedTokens, List<RunStep> steps) {
    }

    /** The GenAI part of the AI trace. */
    record GenAi(String level, List<ModelCalls> modelCalls, List<Run> agentRuns, String moreRuns) {
    }

    /** A part of a message of a conversation. */
    record Message(String role, String type, String tool, String text, String finishReason) {
    }

    /** The conversation of a run. */
    record Conversation(String summary, String level, String runId, String agent, String model, List<Message> messages,
            String cut) {
    }

    private GenAiTraceReport() {
    }

    /** The spans of a {@code get_spans} answer, or an empty list when tracing is off. */
    static List<SpanEntry> spans(JsonObject dump) {
        if (dump == null || Boolean.FALSE.equals(dump.getBoolean("enabled"))) {
            return List.of();
        }
        JsonArray array = dump.getCollection("spans");
        if (array == null) {
            return List.of();
        }
        List<SpanEntry> spans = new ArrayList<>(array.size());
        for (Object o : array) {
            if (o instanceof JsonObject jo) {
                spans.add(SpanEntry.fromJson(jo));
            }
        }
        return spans;
    }

    /** The one-line summary: the level, the counts and what to do next. */
    static String summary(List<SpanEntry> spans, String routeFilter) {
        List<SpanEntry> modelCalls = modelCallSpans(spans, routeFilter);
        GenAiObservabilityLevel level = GenAiSpans.level(spans);
        if (level == null && modelCalls.isEmpty()) {
            return NO_SPANS;
        }
        String models = String.join(", ", models(modelCalls));
        String calls = modelCalls.size() + " model call" + (modelCalls.size() == 1 ? "" : "s")
                       + (models.isEmpty() ? "" : " (" + models + ")");
        if (level == null || level == GenAiObservabilityLevel.BASIC) {
            return "GenAI level=basic: " + calls + ". Agent runs and tool calls are not traced: set "
                   + "camel.aiObservability.level=detailed.";
        }
        List<AgentRun> runs = runs(spans, routeFilter);
        long toolCalls = runs.stream().mapToLong(AgentRun::toolCalls).sum();
        StringBuilder sb = new StringBuilder("GenAI level=").append(level(level)).append(": ")
                .append(runs.size()).append(" agent run").append(runs.size() == 1 ? "" : "s").append(", ")
                .append(calls).append(", ").append(toolCalls).append(" tool call").append(toolCalls == 1 ? "" : "s");
        if (!runs.isEmpty()) {
            sb.append("; runs newest first. ");
            sb.append(level == GenAiObservabilityLevel.FULL
                    ? CONVERSATION_TOOL + " with a runId shows the messages of a run."
                    : "The messages need camel.aiObservability.level=full.");
        }
        return sb.toString();
    }

    /** The model calls per model, the newest five agent runs and the level; {@code null} without GenAI spans. */
    static GenAi genAi(List<SpanEntry> spans, String routeFilter) {
        List<SpanEntry> modelCalls = modelCallSpans(spans, routeFilter);
        GenAiObservabilityLevel level = GenAiSpans.level(spans);
        if (level == null && modelCalls.isEmpty()) {
            return null;
        }
        List<AgentRun> runs = runs(spans, routeFilter);
        List<Run> shown = runs.stream().limit(MAX_RUNS).map(run -> run(run, spans)).toList();
        return new GenAi(
                level(level), modelCalls(modelCalls), shown.isEmpty() ? null : shown,
                runs.size() > MAX_RUNS ? "+" + (runs.size() - MAX_RUNS) + " more runs" : null);
    }

    /**
     * The messages of a run (or of a model call or tool call) by its span id, cut at {@value #MAX_MESSAGE} characters a
     * message and {@value #MAX_CONVERSATION} in all; {@code null} when there is no GenAI span with that id.
     */
    static Conversation conversation(List<SpanEntry> spans, String runId) {
        SpanEntry span = spans.stream()
                .filter(s -> runId != null && runId.equals(s.spanId()) && GenAiSpans.isGenAiSpan(s))
                .findFirst().orElse(null);
        if (span == null) {
            return null;
        }
        String level = level(GenAiSpans.level(spans));
        String agent = GenAiSpans.text(span.attributes(), GenAiAttributes.AGENT_NAME);
        String model = GenAiSpans.model(span.attributes());
        GenAiTranscript transcript = GenAiTranscript.of(span, spans);
        if (transcript.isEmpty()) {
            return new Conversation(
                    "GenAI level=" + level + ": no messages for run " + runId
                                    + ". The messages are recorded at camel.aiObservability.level=full only.",
                    level, runId, agent, model, null, null);
        }
        List<Message> messages = new ArrayList<>();
        int total = 0;
        int left = 0;
        for (GenAiTranscript.Turn turn : transcript.turns()) {
            for (GenAiTranscript.Part part : turn.parts()) {
                if (total >= MAX_CONVERSATION) {
                    left++;
                    continue;
                }
                String text = cut(part.text(), Math.min(MAX_MESSAGE, MAX_CONVERSATION - total));
                total += text != null ? text.length() : 0;
                messages.add(new Message(turn.role(), type(part.kind()), part.toolName(), text, turn.finishReason()));
            }
        }
        String cut = left > 0
                ? left + " more message parts not shown (" + MAX_CONVERSATION + " characters in all)"
                : null;
        return new Conversation(
                "GenAI level=" + level + ": " + messages.size() + " message parts of run " + runId
                                + (left > 0 ? ", " + left + " more cut" : "")
                                + ". Texts over " + MAX_MESSAGE + " characters are cut and end with '...'.",
                level, runId, agent, model, messages, cut);
    }

    private static List<SpanEntry> modelCallSpans(List<SpanEntry> spans, String routeFilter) {
        return spans.stream().filter(GenAiSpans::isModelCall).filter(s -> matches(s, routeFilter)).toList();
    }

    private static List<AgentRun> runs(List<SpanEntry> spans, String routeFilter) {
        return GenAiAgentRuns.agentRuns(spans).stream().filter(run -> matches(run.span(), routeFilter)).toList();
    }

    private static boolean matches(SpanEntry span, String routeFilter) {
        return routeFilter == null || routeFilter.isBlank() || "*".equals(routeFilter)
                || routeFilter.equals(span.routeId());
    }

    private static Set<String> models(List<SpanEntry> modelCalls) {
        Set<String> models = new LinkedHashSet<>();
        for (SpanEntry span : modelCalls) {
            String model = GenAiSpans.model(span.attributes());
            if (model != null && models.size() < MAX_MODELS_IN_SUMMARY) {
                models.add(model);
            }
        }
        return models;
    }

    private static List<ModelCalls> modelCalls(List<SpanEntry> modelCalls) {
        Map<String, List<SpanEntry>> byModel = new LinkedHashMap<>();
        for (SpanEntry span : modelCalls) {
            String model = GenAiSpans.model(span.attributes());
            byModel.computeIfAbsent(model != null ? model : "unknown", k -> new ArrayList<>()).add(span);
        }
        List<ModelCalls> result = new ArrayList<>();
        byModel.forEach((model, calls) -> {
            long in = 0;
            long out = 0;
            long cached = 0;
            long ms = 0;
            Set<String> finishReasons = new LinkedHashSet<>();
            for (SpanEntry span : calls) {
                Map<String, Object> attrs = span.attributes();
                in += GenAiSpans.number(attrs, GenAiAttributes.INPUT_TOKENS);
                out += GenAiSpans.number(attrs, GenAiAttributes.OUTPUT_TOKENS);
                cached += GenAiSpans.number(attrs, GenAiAttributes.CACHE_READ_INPUT_TOKENS);
                ms += Math.max(0, span.durationMs());
                String finish = GenAiSpans.text(attrs, GenAiAttributes.FINISH_REASONS);
                if (finish != null) {
                    finishReasons.add(finish);
                }
            }
            result.add(new ModelCalls(
                    model, GenAiSpans.provider(calls.get(0).attributes()), calls.size(), in, out,
                    cached > 0 ? cached : null, ms / calls.size(),
                    finishReasons.isEmpty() ? null : String.join(",", finishReasons)));
        });
        return result;
    }

    private static Run run(AgentRun run, List<SpanEntry> spans) {
        SpanEntry span = run.span();
        List<RunStep> steps = run.steps().stream().map(GenAiTraceReport::step).toList();
        return new Run(
                span.spanId(), cut(GenAiAgentRuns.firstUserMessage(run, spans), MAX_QUESTION), run.agentName(),
                run.model(), span.routeId(), GenAiAgentRuns.exchangeId(span, spans),
                span.startEpochNanos() > 0 ? Instant.ofEpochSecond(0, span.startEpochNanos()).toString() : null,
                run.durationMs(), run.modelCalls(), run.toolCalls(), run.inputTokens(), run.outputTokens(),
                run.cachedTokens() > 0 ? run.cachedTokens() : null, steps);
    }

    private static RunStep step(Step step) {
        if (step.tool()) {
            return new RunStep(
                    "tool call", step.name(), step.durationMs(), null, null, null, null,
                    cut(step.arguments(), MAX_STEP_TEXT), cut(step.result(), MAX_STEP_TEXT));
        }
        return new RunStep(
                "model call", step.name(), step.durationMs(), step.inputTokens(), step.outputTokens(),
                step.cachedTokens() > 0 ? step.cachedTokens() : null, step.finishReason(), null, null);
    }

    private static String type(GenAiTranscript.Kind kind) {
        return switch (kind) {
            case TEXT -> "text";
            case REASONING -> "reasoning";
            case TOOL_CALL -> "tool call";
            case TOOL_RESULT -> "tool result";
        };
    }

    private static String level(GenAiObservabilityLevel level) {
        return level != null ? level.name().toLowerCase(Locale.ROOT) : "basic";
    }

    /** The text on one line, cut at {@code max} characters with "...". */
    static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        String line = text.replaceAll("\\s+", " ").strip();
        if (line.endsWith(GenAiTranscript.TRUNCATED)) {
            line = line.substring(0, line.length() - GenAiTranscript.TRUNCATED.length()) + "...";
        }
        return line.length() > max ? line.substring(0, Math.max(0, max - 3)) + "..." : line;
    }
}
