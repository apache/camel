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

/**
 * Converts OTel spans exported from monitored Camel integrations into AI usage entries for the TUI stats view.
 */
final class GenAiSpanUsageExtractor {

    private GenAiSpanUsageExtractor() {
    }

    static boolean isGenAiSpan(SpanEntry span) {
        if (span == null || span.attributes() == null) {
            return false;
        }
        return span.attributes().containsKey(GenAiAttributes.OPERATION_NAME);
    }

    static List<AiPanel.AiUsageEntry> extract(List<SpanEntry> spans) {
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }
        List<AiPanel.AiUsageEntry> entries = new ArrayList<>();
        for (SpanEntry span : spans) {
            if (!isGenAiSpan(span)) {
                continue;
            }
            AiPanel.AiUsageEntry entry = toUsageEntry(span);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    private static AiPanel.AiUsageEntry toUsageEntry(SpanEntry span) {
        Map<String, Object> attrs = span.attributes();
        String requestModel = stringAttr(attrs, GenAiAttributes.REQUEST_MODEL);
        String responseModel = stringAttr(attrs, GenAiAttributes.RESPONSE_MODEL);
        String model = !isBlank(responseModel) ? responseModel : requestModel;
        if (isBlank(model) || "unknown".equalsIgnoreCase(model)) {
            model = span.name() != null ? span.name() : "unknown";
        }

        String provider = stringAttr(attrs, GenAiAttributes.SYSTEM);
        if (isBlank(provider)) {
            provider = "unknown";
        }

        int inputTokens = intAttr(attrs, GenAiAttributes.INPUT_TOKENS);
        int outputTokens = intAttr(attrs, GenAiAttributes.OUTPUT_TOKENS);
        int totalTokens = inputTokens + outputTokens;

        long latencyMs = span.durationMs() > 0 ? span.durationMs() : 0;
        String stopReason = stringAttr(attrs, GenAiAttributes.FINISH_REASONS);
        Instant timestamp = span.startEpochNanos() > 0
                ? Instant.ofEpochSecond(0, span.startEpochNanos())
                : Instant.EPOCH;

        return new AiPanel.AiUsageEntry(
                model,
                provider,
                inputTokens,
                outputTokens,
                totalTokens,
                latencyMs,
                stopReason,
                timestamp,
                AiPanel.AiUsageSource.ROUTE,
                span.routeId());
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
