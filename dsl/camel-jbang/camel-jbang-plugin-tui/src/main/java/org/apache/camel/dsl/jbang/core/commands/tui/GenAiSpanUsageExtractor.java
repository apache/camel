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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.apache.camel.component.ai.observability.GenAiOperationName;

/**
 * Converts OTel spans exported from monitored Camel integrations into AI usage entries for the TUI stats view.
 */
final class GenAiSpanUsageExtractor {

    /**
     * The GenAI operations that are not a model call: an agent run and a tool call (observability level detailed and
     * above), and {@code create_agent} of the OpenTelemetry conventions, which AI frameworks may record.
     */
    private static final Set<String> NON_MODEL_OPERATIONS = Set.of(
            GenAiOperationName.INVOKE_AGENT.value(), GenAiOperationName.EXECUTE_TOOL.value(), "create_agent");

    /** The attributes only recorded at observability level detailed and above. */
    private static final List<String> DETAILED_ATTRIBUTES = List.of(
            GenAiAttributes.PROVIDER_NAME, GenAiAttributes.AGENT_NAME, GenAiAttributes.TOOL_NAME,
            GenAiAttributes.CONVERSATION_ID, GenAiAttributes.CACHE_READ_INPUT_TOKENS);

    /** The attributes only recorded at observability level full. */
    private static final List<String> FULL_ATTRIBUTES = List.of(
            GenAiAttributes.INPUT_MESSAGES, GenAiAttributes.OUTPUT_MESSAGES, GenAiAttributes.SYSTEM_INSTRUCTIONS,
            GenAiAttributes.TOOL_CALL_ARGUMENTS, GenAiAttributes.TOOL_CALL_RESULT);

    private static final int MAX_PARENT_HOPS = 50;

    /**
     * The agent runs and tool calls among the GenAI spans, and the observability level they were recorded at.
     *
     * @param agentRuns       the {@code invoke_agent} spans
     * @param agentModelCalls the model calls made inside an agent run
     * @param toolCalls       the {@code execute_tool} spans
     * @param level           the level the Camel GenAI spans show, or {@code null} when there are none
     */
    record GenAiActivity(int agentRuns, int agentModelCalls, int toolCalls, GenAiObservabilityLevel level) {

        static final GenAiActivity NONE = new GenAiActivity(0, 0, 0, null);
    }

    private GenAiSpanUsageExtractor() {
    }

    /** Any GenAI span: a model call, an agent run or a tool call. */
    static boolean isGenAiSpan(SpanEntry span) {
        if (span == null || span.attributes() == null) {
            return false;
        }
        return span.attributes().containsKey(GenAiAttributes.OPERATION_NAME);
    }

    /**
     * A GenAI span of one model call (chat, embeddings, generate_content, ...), not an agent run or a tool call. An
     * operation the Camel observability does not know, such as one recorded by an AI framework, counts as a model call.
     */
    static boolean isModelCall(SpanEntry span) {
        return isGenAiSpan(span) && !NON_MODEL_OPERATIONS.contains(operation(span));
    }

    /** The provider of a GenAI span: {@code gen_ai.system}, else the newer {@code gen_ai.provider.name}. */
    static String provider(Map<String, Object> attrs) {
        String provider = stringAttr(attrs, GenAiAttributes.SYSTEM);
        if (isBlank(provider) || "unknown".equalsIgnoreCase(provider)) {
            String name = stringAttr(attrs, GenAiAttributes.PROVIDER_NAME);
            if (!isBlank(name)) {
                return name;
            }
        }
        return provider;
    }

    static List<AiUsageEntry> extract(List<SpanEntry> spans) {
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }
        List<AiUsageEntry> entries = new ArrayList<>();
        for (SpanEntry span : spans) {
            if (!isModelCall(span)) {
                continue;
            }
            AiUsageEntry entry = toUsageEntry(span);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    private static AiUsageEntry toUsageEntry(SpanEntry span) {
        Map<String, Object> attrs = span.attributes();
        String requestModel = stringAttr(attrs, GenAiAttributes.REQUEST_MODEL);
        String responseModel = stringAttr(attrs, GenAiAttributes.RESPONSE_MODEL);
        String model = !isBlank(responseModel) ? responseModel : requestModel;
        if (isBlank(model) || "unknown".equalsIgnoreCase(model)) {
            model = span.name() != null ? span.name() : "unknown";
        }

        String provider = provider(attrs);
        if (isBlank(provider)) {
            provider = "unknown";
        }

        int inputTokens = intAttr(attrs, GenAiAttributes.INPUT_TOKENS);
        int outputTokens = intAttr(attrs, GenAiAttributes.OUTPUT_TOKENS);
        int totalTokens = inputTokens + outputTokens;
        int cachedInputTokens = intAttr(attrs, GenAiAttributes.CACHE_READ_INPUT_TOKENS);

        long latencyMs = span.durationMs() > 0 ? span.durationMs() : 0;
        String stopReason = stringAttr(attrs, GenAiAttributes.FINISH_REASONS);
        Instant timestamp = span.startEpochNanos() > 0
                ? Instant.ofEpochSecond(0, span.startEpochNanos())
                : Instant.EPOCH;

        return new AiUsageEntry(
                model,
                provider,
                inputTokens,
                outputTokens,
                totalTokens,
                latencyMs,
                stopReason,
                timestamp,
                AiUsageSource.ROUTE,
                span.routeId(),
                0,
                cachedInputTokens);
    }

    /**
     * Counts the agent runs, their model calls and the tool calls among the spans started at or after {@code since},
     * and tells the observability level from the attributes of the Camel GenAI spans: the level is not exported with
     * the spans, but each level records attributes the level below does not.
     */
    static GenAiActivity activity(List<SpanEntry> spans, Instant since) {
        if (spans == null || spans.isEmpty()) {
            return GenAiActivity.NONE;
        }
        long sinceNanos = since != null && since.isAfter(Instant.EPOCH)
                ? since.getEpochSecond() * 1_000_000_000L + since.getNano() : 0;
        Map<String, SpanEntry> byId = new HashMap<>();
        for (SpanEntry span : spans) {
            if (span != null && span.spanId() != null) {
                byId.put(span.spanId(), span);
            }
        }
        int agentRuns = 0;
        int agentModelCalls = 0;
        int toolCalls = 0;
        GenAiObservabilityLevel level = null;
        for (SpanEntry span : spans) {
            if (!isGenAiSpan(span) || span.startEpochNanos() < sinceNanos) {
                continue;
            }
            String operation = operation(span);
            if (GenAiOperationName.INVOKE_AGENT.value().equals(operation)) {
                agentRuns++;
            } else if (GenAiOperationName.EXECUTE_TOOL.value().equals(operation)) {
                toolCalls++;
            } else if (isModelCall(span) && insideAgentRun(span, byId)) {
                agentModelCalls++;
            }
            if (span.isCamelSpan()) {
                GenAiObservabilityLevel spanLevel = levelOf(span, operation);
                if (level == null || spanLevel.includes(level)) {
                    level = spanLevel;
                }
            }
        }
        return new GenAiActivity(agentRuns, agentModelCalls, toolCalls, level);
    }

    private static GenAiObservabilityLevel levelOf(SpanEntry span, String operation) {
        Map<String, Object> attrs = span.attributes();
        for (String key : FULL_ATTRIBUTES) {
            if (attrs.containsKey(key)) {
                return GenAiObservabilityLevel.FULL;
            }
        }
        if (NON_MODEL_OPERATIONS.contains(operation)) {
            return GenAiObservabilityLevel.DETAILED;
        }
        for (String key : DETAILED_ATTRIBUTES) {
            if (attrs.containsKey(key)) {
                return GenAiObservabilityLevel.DETAILED;
            }
        }
        return GenAiObservabilityLevel.BASIC;
    }

    private static boolean insideAgentRun(SpanEntry span, Map<String, SpanEntry> byId) {
        SpanEntry current = span;
        for (int hops = 0; hops < MAX_PARENT_HOPS && current != null && !current.isRoot(); hops++) {
            current = byId.get(current.parentSpanId());
            if (current != null && GenAiOperationName.INVOKE_AGENT.value().equals(operation(current))) {
                return true;
            }
        }
        return false;
    }

    private static String operation(SpanEntry span) {
        return span.attributes() != null ? stringAttr(span.attributes(), GenAiAttributes.OPERATION_NAME) : null;
    }

    private static String stringAttr(Map<String, Object> attrs, String key) {
        Object value = attrs.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static int intAttr(Map<String, Object> attrs, String key) {
        Object value = attrs.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
