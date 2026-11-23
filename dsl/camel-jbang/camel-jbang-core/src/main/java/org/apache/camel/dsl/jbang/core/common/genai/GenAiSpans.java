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
package org.apache.camel.dsl.jbang.core.common.genai;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.apache.camel.component.ai.observability.GenAiOperationName;

/**
 * Reads the GenAI spans the Camel AI producers record (camel-ai-observability): which span is a model call, an agent
 * run or a tool call, and at which observability level they were recorded. The Camel TUI and the MCP server read the
 * spans the same way.
 */
public final class GenAiSpans {

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

    static final int MAX_PARENT_HOPS = 50;

    /**
     * The agent runs and tool calls among the GenAI spans, and the observability level they were recorded at.
     *
     * @param agentRuns       the {@code invoke_agent} spans
     * @param agentModelCalls the model calls made inside an agent run
     * @param toolCalls       the {@code execute_tool} spans
     * @param level           the level the Camel GenAI spans show, or {@code null} when there are none
     */
    public record GenAiActivity(int agentRuns, int agentModelCalls, int toolCalls, GenAiObservabilityLevel level) {

        public static final GenAiActivity NONE = new GenAiActivity(0, 0, 0, null);
    }

    private GenAiSpans() {
    }

    /** Any GenAI span: a model call, an agent run or a tool call. */
    public static boolean isGenAiSpan(SpanEntry span) {
        return span != null && span.attributes() != null
                && span.attributes().containsKey(GenAiAttributes.OPERATION_NAME);
    }

    /**
     * A GenAI span of one model call (chat, embeddings, generate_content, ...), not an agent run or a tool call. An
     * operation the Camel observability does not know, such as one recorded by an AI framework, counts as a model call.
     */
    public static boolean isModelCall(SpanEntry span) {
        return isGenAiSpan(span) && !NON_MODEL_OPERATIONS.contains(operation(span));
    }

    /** An agent run ({@code invoke_agent}). */
    public static boolean isAgentRun(SpanEntry span) {
        return GenAiOperationName.INVOKE_AGENT.value().equals(operation(span));
    }

    /** A tool call ({@code execute_tool}). */
    public static boolean isToolCall(SpanEntry span) {
        return GenAiOperationName.EXECUTE_TOOL.value().equals(operation(span));
    }

    /** The GenAI operation of a span ({@code gen_ai.operation.name}), or {@code null}. */
    public static String operation(SpanEntry span) {
        return span != null ? text(span.attributes(), GenAiAttributes.OPERATION_NAME) : null;
    }

    /** The provider of a GenAI span: {@code gen_ai.system}, else the newer {@code gen_ai.provider.name}. */
    public static String provider(Map<String, Object> attrs) {
        String provider = text(attrs, GenAiAttributes.SYSTEM);
        if (provider == null || "unknown".equalsIgnoreCase(provider)) {
            String name = text(attrs, GenAiAttributes.PROVIDER_NAME);
            if (name != null) {
                return name;
            }
        }
        return provider;
    }

    /** The model of a GenAI span: the response model, else the request model; {@code null} when not known. */
    public static String model(Map<String, Object> attrs) {
        String model = text(attrs, GenAiAttributes.RESPONSE_MODEL);
        if (model == null) {
            model = text(attrs, GenAiAttributes.REQUEST_MODEL);
        }
        return "unknown".equalsIgnoreCase(model) ? null : model;
    }

    /** A text attribute, or {@code null} when absent or blank. */
    public static String text(Map<String, Object> attrs, String key) {
        Object value = attrs != null ? attrs.get(key) : null;
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    /** A number attribute (a number or a numeric text), or 0. */
    public static long number(Map<String, Object> attrs, String key) {
        Object value = attrs != null ? attrs.get(key) : null;
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value != null ? Long.parseLong(value.toString().trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Counts the agent runs, their model calls and the tool calls among the spans started at or after {@code since},
     * and tells the observability level from the attributes of the Camel GenAI spans: the level is not exported with
     * the spans, but each level records attributes the level below does not.
     */
    public static GenAiActivity activity(List<SpanEntry> spans, Instant since) {
        if (spans == null || spans.isEmpty()) {
            return GenAiActivity.NONE;
        }
        long sinceNanos = since != null && since.isAfter(Instant.EPOCH)
                ? since.getEpochSecond() * 1_000_000_000L + since.getNano() : 0;
        Map<String, SpanEntry> byId = byId(spans);
        int agentRuns = 0;
        int agentModelCalls = 0;
        int toolCalls = 0;
        GenAiObservabilityLevel level = null;
        for (SpanEntry span : spans) {
            if (!isGenAiSpan(span) || span.startEpochNanos() < sinceNanos) {
                continue;
            }
            if (isAgentRun(span)) {
                agentRuns++;
            } else if (isToolCall(span)) {
                toolCalls++;
            } else if (isModelCall(span) && nearestAgentRun(span, byId) != null) {
                agentModelCalls++;
            }
            if (span.isCamelSpan()) {
                GenAiObservabilityLevel spanLevel = levelOf(span);
                if (level == null || spanLevel.includes(level)) {
                    level = spanLevel;
                }
            }
        }
        return new GenAiActivity(agentRuns, agentModelCalls, toolCalls, level);
    }

    /** The level the Camel GenAI spans show, or {@code null} when there are none. */
    public static GenAiObservabilityLevel level(List<SpanEntry> spans) {
        return activity(spans, null).level();
    }

    private static GenAiObservabilityLevel levelOf(SpanEntry span) {
        Map<String, Object> attrs = span.attributes();
        for (String key : FULL_ATTRIBUTES) {
            if (attrs.containsKey(key)) {
                return GenAiObservabilityLevel.FULL;
            }
        }
        if (NON_MODEL_OPERATIONS.contains(operation(span))) {
            return GenAiObservabilityLevel.DETAILED;
        }
        for (String key : DETAILED_ATTRIBUTES) {
            if (attrs.containsKey(key)) {
                return GenAiObservabilityLevel.DETAILED;
            }
        }
        return GenAiObservabilityLevel.BASIC;
    }

    /** The spans by span id. */
    static Map<String, SpanEntry> byId(List<SpanEntry> spans) {
        Map<String, SpanEntry> byId = new HashMap<>();
        for (SpanEntry span : spans) {
            if (span != null && span.spanId() != null) {
                byId.put(span.spanId(), span);
            }
        }
        return byId;
    }

    /** The nearest agent run above a span, or {@code null}. */
    static SpanEntry nearestAgentRun(SpanEntry span, Map<String, SpanEntry> byId) {
        SpanEntry current = span;
        for (int hops = 0; hops < MAX_PARENT_HOPS && current != null && !current.isRoot(); hops++) {
            current = byId.get(current.parentSpanId());
            if (current != null && isAgentRun(current)) {
                return current;
            }
        }
        return null;
    }
}
