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
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiSpanUsageExtractorTest {

    @Test
    void isGenAiSpanRequiresOperationNameAttribute() {
        SpanEntry genAi = span(Map.of(GenAiAttributes.OPERATION_NAME, "chat"));
        SpanEntry other = span(Map.of("http.method", "GET"));

        assertThat(GenAiSpanUsageExtractor.isGenAiSpan(genAi)).isTrue();
        assertThat(GenAiSpanUsageExtractor.isGenAiSpan(other)).isFalse();
        assertThat(GenAiSpanUsageExtractor.isGenAiSpan(null)).isFalse();
    }

    @Test
    void extractMapsGenAiSemanticConventionAttributes() {
        SpanEntry span = span(Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.SYSTEM, "openai",
                GenAiAttributes.REQUEST_MODEL, "gpt-4o-mini",
                GenAiAttributes.RESPONSE_MODEL, "gpt-4o-mini",
                GenAiAttributes.INPUT_TOKENS, 120,
                GenAiAttributes.OUTPUT_TOKENS, 45,
                GenAiAttributes.FINISH_REASONS, "stop"));

        List<AiUsageEntry> entries = GenAiSpanUsageExtractor.extract(List.of(span));

        assertThat(entries).hasSize(1);
        AiUsageEntry entry = entries.get(0);
        assertThat(entry.source()).isEqualTo(AiUsageSource.ROUTE);
        assertThat(entry.model()).isEqualTo("gpt-4o-mini");
        assertThat(entry.provider()).isEqualTo("openai");
        assertThat(entry.inputTokens()).isEqualTo(120);
        assertThat(entry.outputTokens()).isEqualTo(45);
        assertThat(entry.totalTokens()).isEqualTo(165);
        assertThat(entry.latencyMs()).isEqualTo(250);
        assertThat(entry.stopReason()).isEqualTo("stop");
        assertThat(entry.routeId()).isEqualTo("route-a");
    }

    @Test
    void extractIgnoresNonGenAiSpans() {
        SpanEntry camelSpan = span(Map.of("camel.routeId", "r1"));
        SpanEntry genAi = span(Map.of(
                GenAiAttributes.OPERATION_NAME, "embeddings",
                GenAiAttributes.SYSTEM, "openai",
                GenAiAttributes.REQUEST_MODEL, "text-embedding-3-small",
                GenAiAttributes.INPUT_TOKENS, "10",
                GenAiAttributes.OUTPUT_TOKENS, "0"));

        List<AiUsageEntry> entries = GenAiSpanUsageExtractor.extract(List.of(camelSpan, genAi));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).model()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void extractFallsBackToRequestModelAndSpanName() {
        SpanEntry requestOnly = span("chat gpt-4", Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.REQUEST_MODEL, "gpt-4",
                GenAiAttributes.SYSTEM, "anthropic"));

        SpanEntry nameFallback = span("embeddings unknown-model", Map.of(
                GenAiAttributes.OPERATION_NAME, "embeddings",
                GenAiAttributes.REQUEST_MODEL, "unknown",
                GenAiAttributes.SYSTEM, "openai"));

        assertThat(GenAiSpanUsageExtractor.extract(List.of(requestOnly)).get(0).model()).isEqualTo("gpt-4");
        assertThat(GenAiSpanUsageExtractor.extract(List.of(nameFallback)).get(0).model())
                .isEqualTo("embeddings unknown-model");
    }

    @Test
    void isModelCallLeavesOutAgentRunsAndToolCalls() {
        assertThat(GenAiSpanUsageExtractor.isModelCall(span(Map.of(GenAiAttributes.OPERATION_NAME, "chat")))).isTrue();
        assertThat(GenAiSpanUsageExtractor.isModelCall(span(Map.of(GenAiAttributes.OPERATION_NAME, "embeddings"))))
                .isTrue();
        // an operation recorded by an AI framework, unknown to the Camel observability, still is a model call
        assertThat(GenAiSpanUsageExtractor.isModelCall(span(Map.of(GenAiAttributes.OPERATION_NAME, "text_completion"))))
                .isTrue();
        assertThat(GenAiSpanUsageExtractor.isModelCall(span(Map.of(GenAiAttributes.OPERATION_NAME, "invoke_agent"))))
                .isFalse();
        assertThat(GenAiSpanUsageExtractor.isModelCall(span(Map.of(GenAiAttributes.OPERATION_NAME, "execute_tool"))))
                .isFalse();
        assertThat(GenAiSpanUsageExtractor.isModelCall(span(Map.of(GenAiAttributes.OPERATION_NAME, "create_agent"))))
                .isFalse();
        assertThat(GenAiSpanUsageExtractor.isModelCall(span(Map.of("http.method", "GET")))).isFalse();
    }

    @Test
    void extractCountsOnlyTheModelCallsOfAnAgentRun() {
        List<SpanEntry> run = agentRun();

        List<AiUsageEntry> entries = GenAiSpanUsageExtractor.extract(run);

        assertThat(entries).hasSize(2);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.model()).isEqualTo("gpt-4.1");
            assertThat(entry.provider()).isEqualTo("openai");
        });
        assertThat(entries).extracting(AiUsageEntry::totalTokens).containsExactly(1100, 1300);
        assertThat(entries).extracting(AiUsageEntry::cachedInputTokens).containsExactly(0, 800);
    }

    @Test
    void extractFallsBackToProviderName() {
        SpanEntry span = span(Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.PROVIDER_NAME, "anthropic",
                GenAiAttributes.REQUEST_MODEL, "claude-sonnet-5-5"));
        SpanEntry unknownSystem = span(Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.SYSTEM, "unknown",
                GenAiAttributes.PROVIDER_NAME, "ollama",
                GenAiAttributes.REQUEST_MODEL, "qwen3:8b"));

        assertThat(GenAiSpanUsageExtractor.extract(List.of(span)).get(0).provider()).isEqualTo("anthropic");
        assertThat(GenAiSpanUsageExtractor.extract(List.of(unknownSystem)).get(0).provider()).isEqualTo("ollama");
    }

    @Test
    void activityCountsAgentRunsAndToolCalls() {
        List<SpanEntry> spans = new ArrayList<>(agentRun());
        // a model call outside any agent run
        spans.add(span("s9", "", "chat gpt-4.1", Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.SYSTEM, "openai",
                GenAiAttributes.PROVIDER_NAME, "openai",
                GenAiAttributes.REQUEST_MODEL, "gpt-4.1")));

        GenAiSpanUsageExtractor.GenAiActivity activity = GenAiSpanUsageExtractor.activity(spans, Instant.EPOCH);

        assertThat(activity.agentRuns()).isEqualTo(1);
        assertThat(activity.agentModelCalls()).isEqualTo(2);
        assertThat(activity.toolCalls()).isEqualTo(1);
        assertThat(activity.level()).isEqualTo(GenAiObservabilityLevel.DETAILED);
    }

    @Test
    void activityTellsTheObservabilityLevel() {
        SpanEntry basic = span(Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.SYSTEM, "openai",
                GenAiAttributes.REQUEST_MODEL, "gpt-4.1"));
        SpanEntry full = span("s2", "", "chat gpt-4.1", Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.SYSTEM, "openai",
                GenAiAttributes.PROVIDER_NAME, "openai",
                GenAiAttributes.INPUT_MESSAGES, "[{\"role\":\"user\",\"parts\":[]}]"));
        SpanEntry framework = new SpanEntry(
                "trace-1", "s3", "", "chat", "CLIENT", "OK", 0, 0, 10, null, null, "dev.langchain4j",
                Map.of(GenAiAttributes.OPERATION_NAME, "chat", GenAiAttributes.PROVIDER_NAME, "openai"));

        assertThat(GenAiSpanUsageExtractor.activity(List.of(basic), Instant.EPOCH).level())
                .isEqualTo(GenAiObservabilityLevel.BASIC);
        assertThat(GenAiSpanUsageExtractor.activity(List.of(basic, full), Instant.EPOCH).level())
                .isEqualTo(GenAiObservabilityLevel.FULL);
        // the spans of an AI framework do not tell the level of the Camel observability
        assertThat(GenAiSpanUsageExtractor.activity(List.of(framework), Instant.EPOCH).level()).isNull();
        assertThat(GenAiSpanUsageExtractor.activity(List.of(), Instant.EPOCH).level()).isNull();
    }

    @Test
    void activityLeavesOutSpansBeforeTheReset() {
        Instant afterTheRun = Instant.parse("2026-08-13T12:00:01Z");

        GenAiSpanUsageExtractor.GenAiActivity activity = GenAiSpanUsageExtractor.activity(agentRun(), afterTheRun);

        assertThat(activity.agentRuns()).isZero();
        assertThat(activity.toolCalls()).isZero();
        assertThat(activity.level()).isNull();
    }

    @Test
    void extractReturnsEmptyListForNullOrEmptyInput() {
        assertThat(GenAiSpanUsageExtractor.extract(null)).isEmpty();
        assertThat(GenAiSpanUsageExtractor.extract(List.of())).isEmpty();
    }

    /** An agent run at level detailed: invoke_agent, then chat, execute_tool and chat nested under it. */
    private static List<SpanEntry> agentRun() {
        SpanEntry agent = span("agent", "", "invoke_agent support", Map.of(
                GenAiAttributes.OPERATION_NAME, "invoke_agent",
                GenAiAttributes.SYSTEM, "openai",
                GenAiAttributes.PROVIDER_NAME, "openai",
                GenAiAttributes.REQUEST_MODEL, "gpt-4.1",
                GenAiAttributes.AGENT_NAME, "support",
                GenAiAttributes.FINISH_REASONS, "stop"));
        SpanEntry chat1 = span("chat1", "agent", "chat gpt-4.1", Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.SYSTEM, "openai",
                GenAiAttributes.PROVIDER_NAME, "openai",
                GenAiAttributes.REQUEST_MODEL, "gpt-4.1",
                GenAiAttributes.INPUT_TOKENS, 1000,
                GenAiAttributes.OUTPUT_TOKENS, 100,
                GenAiAttributes.FINISH_REASONS, "tool_calls"));
        // the execute_tool span has no system nor model
        SpanEntry tool = span("tool", "agent", "execute_tool lookupOrder", Map.of(
                GenAiAttributes.OPERATION_NAME, "execute_tool",
                GenAiAttributes.TOOL_NAME, "lookupOrder"));
        SpanEntry chat2 = span("chat2", "agent", "chat gpt-4.1", Map.of(
                GenAiAttributes.OPERATION_NAME, "chat",
                GenAiAttributes.SYSTEM, "openai",
                GenAiAttributes.PROVIDER_NAME, "openai",
                GenAiAttributes.REQUEST_MODEL, "gpt-4.1",
                GenAiAttributes.INPUT_TOKENS, 1200,
                GenAiAttributes.OUTPUT_TOKENS, 100,
                GenAiAttributes.CACHE_READ_INPUT_TOKENS, 800L,
                GenAiAttributes.FINISH_REASONS, "stop"));
        return List.of(agent, chat1, tool, chat2);
    }

    private static SpanEntry span(String spanId, String parentSpanId, String name, Map<String, Object> attributes) {
        return new SpanEntry(
                "trace-1",
                spanId,
                parentSpanId,
                name,
                "CLIENT",
                "OK",
                Instant.parse("2026-08-13T12:00:00Z").getEpochSecond() * 1_000_000_000L,
                Instant.parse("2026-08-13T12:00:00.250Z").getEpochSecond() * 1_000_000_000L,
                250,
                "route-a",
                null,
                "camel",
                attributes);
    }

    private static SpanEntry span(Map<String, Object> attributes) {
        return span("chat gpt-4o-mini", attributes);
    }

    private static SpanEntry span(String name, Map<String, Object> attributes) {
        return new SpanEntry(
                "trace-1",
                "span-1",
                "",
                name,
                "CLIENT",
                "OK",
                Instant.parse("2026-08-13T12:00:00Z").getEpochSecond() * 1_000_000_000L,
                Instant.parse("2026-08-13T12:00:00.250Z").getEpochSecond() * 1_000_000_000L,
                250,
                "route-a",
                null,
                "camel",
                attributes);
    }
}
