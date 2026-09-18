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
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiAttributes;
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

        List<AiPanel.AiUsageEntry> entries = GenAiSpanUsageExtractor.extract(List.of(span));

        assertThat(entries).hasSize(1);
        AiPanel.AiUsageEntry entry = entries.get(0);
        assertThat(entry.source()).isEqualTo(AiPanel.AiUsageSource.ROUTE);
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

        List<AiPanel.AiUsageEntry> entries = GenAiSpanUsageExtractor.extract(List.of(camelSpan, genAi));

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
    void extractReturnsEmptyListForNullOrEmptyInput() {
        assertThat(GenAiSpanUsageExtractor.extract(null)).isEmpty();
        assertThat(GenAiSpanUsageExtractor.extract(List.of())).isEmpty();
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
