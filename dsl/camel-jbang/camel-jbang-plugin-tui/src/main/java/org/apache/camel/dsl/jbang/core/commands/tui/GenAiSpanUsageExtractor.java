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
import org.apache.camel.dsl.jbang.core.common.genai.GenAiSpans;
import org.apache.camel.dsl.jbang.core.common.genai.SpanEntry;

/**
 * Converts the GenAI model calls among the OTel spans of a monitored Camel integration into AI usage entries for the
 * TUI stats view. Which span is a model call is decided by {@link GenAiSpans}, shared with the MCP server.
 */
final class GenAiSpanUsageExtractor {

    private GenAiSpanUsageExtractor() {
    }

    static List<AiUsageEntry> extract(List<SpanEntry> spans) {
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }
        List<AiUsageEntry> entries = new ArrayList<>();
        for (SpanEntry span : spans) {
            if (GenAiSpans.isModelCall(span)) {
                entries.add(toUsageEntry(span));
            }
        }
        return List.copyOf(entries);
    }

    private static AiUsageEntry toUsageEntry(SpanEntry span) {
        Map<String, Object> attrs = span.attributes();
        String model = GenAiSpans.model(attrs);
        if (model == null) {
            model = span.name() != null ? span.name() : "unknown";
        }
        String provider = GenAiSpans.provider(attrs);
        if (provider == null) {
            provider = "unknown";
        }
        int inputTokens = (int) GenAiSpans.number(attrs, GenAiAttributes.INPUT_TOKENS);
        int outputTokens = (int) GenAiSpans.number(attrs, GenAiAttributes.OUTPUT_TOKENS);
        int cachedInputTokens = (int) GenAiSpans.number(attrs, GenAiAttributes.CACHE_READ_INPUT_TOKENS);
        long latencyMs = Math.max(0, span.durationMs());
        Instant timestamp = span.startEpochNanos() > 0 ? Instant.ofEpochSecond(0, span.startEpochNanos()) : Instant.EPOCH;
        return new AiUsageEntry(
                model, provider, inputTokens, outputTokens, inputTokens + outputTokens, latencyMs,
                GenAiSpans.text(attrs, GenAiAttributes.FINISH_REASONS), timestamp, AiUsageSource.ROUTE, span.routeId(),
                0, cachedInputTokens);
    }
}
