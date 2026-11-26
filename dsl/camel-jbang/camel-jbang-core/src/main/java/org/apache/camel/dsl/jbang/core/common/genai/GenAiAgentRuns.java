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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiMessages;

/**
 * The agent runs among the GenAI spans: an {@code invoke_agent} span with the model calls and tool calls whose nearest
 * agent run it is (observability level detailed and above).
 */
public final class GenAiAgentRuns {

    /**
     * A model call or a tool call of an agent run.
     *
     * @param arguments the tool arguments, recorded at level full only
     * @param result    the tool result, recorded at level full only
     */
    public record Step(SpanEntry span, boolean tool, String name, long durationMs, long inputTokens, long outputTokens,
            long cachedTokens, String finishReason, String arguments, String result) {
    }

    /** An agent run with its steps in the order they started. */
    public record AgentRun(SpanEntry span, String agentName, String model, String provider, String conversationId,
            long durationMs, List<Step> steps) {

        public long modelCalls() {
            return steps.stream().filter(s -> !s.tool()).count();
        }

        public long toolCalls() {
            return steps.stream().filter(Step::tool).count();
        }

        public long inputTokens() {
            return steps.stream().mapToLong(Step::inputTokens).sum();
        }

        public long outputTokens() {
            return steps.stream().mapToLong(Step::outputTokens).sum();
        }

        public long cachedTokens() {
            return steps.stream().mapToLong(Step::cachedTokens).sum();
        }

        public long modelMs() {
            return steps.stream().filter(s -> !s.tool()).mapToLong(Step::durationMs).sum();
        }

        public long toolMs() {
            return steps.stream().filter(Step::tool).mapToLong(Step::durationMs).sum();
        }

        /** The time of the run outside its model and tool calls. */
        public long otherMs() {
            return Math.max(0, durationMs - modelMs() - toolMs());
        }
    }

    private GenAiAgentRuns() {
    }

    /** The agent runs among the spans, the newest first. */
    public static List<AgentRun> agentRuns(List<SpanEntry> spans) {
        if (spans == null) {
            return List.of();
        }
        return spans.stream()
                .filter(GenAiSpans::isAgentRun)
                .sorted(Comparator.comparingLong(SpanEntry::startEpochNanos).reversed())
                .map(agent -> agentRun(agent, spans))
                .toList();
    }

    /** The agent run with its model and tool calls: the GenAI spans whose nearest agent run is this one. */
    public static AgentRun agentRun(SpanEntry agent, List<SpanEntry> spans) {
        Map<String, SpanEntry> byId = GenAiSpans.byId(spans);
        List<Step> steps = spans.stream()
                .filter(s -> s != agent && GenAiSpans.isGenAiSpan(s) && !GenAiSpans.isAgentRun(s))
                .filter(s -> {
                    SpanEntry run = GenAiSpans.nearestAgentRun(s, byId);
                    return run != null && agent.spanId() != null && agent.spanId().equals(run.spanId());
                })
                .sorted(Comparator.comparingLong(SpanEntry::startEpochNanos))
                .map(GenAiAgentRuns::step)
                .toList();
        Map<String, Object> attrs = agent.attributes();
        String agentName = GenAiSpans.text(attrs, GenAiAttributes.AGENT_NAME);
        String provider = GenAiSpans.text(attrs, GenAiAttributes.PROVIDER_NAME);
        return new AgentRun(
                agent, agentName != null ? agentName : agent.routeId(), GenAiSpans.model(attrs),
                provider != null ? provider : GenAiSpans.text(attrs, GenAiAttributes.SYSTEM),
                GenAiSpans.text(attrs, GenAiAttributes.CONVERSATION_ID), agent.durationMs(), steps);
    }

    private static Step step(SpanEntry span) {
        Map<String, Object> attrs = span.attributes();
        if (GenAiSpans.isToolCall(span)) {
            String name = GenAiSpans.text(attrs, GenAiAttributes.TOOL_NAME);
            return new Step(
                    span, true, name != null ? name : span.name(), span.durationMs(), 0, 0, 0, null,
                    GenAiSpans.text(attrs, GenAiAttributes.TOOL_CALL_ARGUMENTS),
                    GenAiSpans.text(attrs, GenAiAttributes.TOOL_CALL_RESULT));
        }
        String model = GenAiSpans.model(attrs);
        return new Step(
                span, false, GenAiSpans.operation(span) + (model != null ? " " + model : ""),
                span.durationMs(), GenAiSpans.number(attrs, GenAiAttributes.INPUT_TOKENS),
                GenAiSpans.number(attrs, GenAiAttributes.OUTPUT_TOKENS),
                GenAiSpans.number(attrs, GenAiAttributes.CACHE_READ_INPUT_TOKENS),
                GenAiSpans.text(attrs, GenAiAttributes.FINISH_REASONS), null, null);
    }

    /** The exchange id of the Camel span nearest above the span, or {@code null}. */
    public static String exchangeId(SpanEntry span, List<SpanEntry> spans) {
        Map<String, SpanEntry> byId = GenAiSpans.byId(spans);
        SpanEntry current = span;
        for (int hops = 0; hops < GenAiSpans.MAX_PARENT_HOPS && current != null; hops++) {
            String exchangeId = GenAiSpans.text(current.attributes(), "exchangeId");
            if (exchangeId != null) {
                return exchangeId;
            }
            current = current.isRoot() ? null : byId.get(current.parentSpanId());
        }
        return null;
    }

    /** The first user message of the run (recorded at level full), or {@code null}. */
    public static String firstUserMessage(AgentRun run, List<SpanEntry> spans) {
        for (GenAiTranscript.Turn turn : GenAiTranscript.of(run.span(), spans).turns()) {
            if (GenAiMessages.USER.equals(turn.role())) {
                for (GenAiTranscript.Part part : turn.parts()) {
                    if (part.kind() == GenAiTranscript.Kind.TEXT && part.text() != null) {
                        return part.text();
                    }
                }
            }
        }
        return null;
    }
}
