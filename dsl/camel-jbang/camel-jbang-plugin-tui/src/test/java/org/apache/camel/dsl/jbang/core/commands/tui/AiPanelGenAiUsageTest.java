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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPanelGenAiUsageTest {

    private AtomicReference<List<SpanEntry>> spans;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        spans = new AtomicReference<>(List.of());
    }

    @Test
    void combinedUsageIncludesRouteGenAiSpans() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.recordUsageForTesting(new AiPanel.AiUsageEntry(
                "gpt-4o-mini", "openai", 10, 5, 15, 1000, "stop", Instant.now()));

        spans.set(List.of(routeSpan("route-chat", "gpt-4o", 100, 20)));

        assertThat(panel.combinedUsageEntriesForTesting()).hasSize(2);
        assertThat(panel.combinedUsageEntriesForTesting())
                .anyMatch(entry -> entry.source() == AiPanel.AiUsageSource.ROUTE
                        && "route-chat".equals(entry.routeId()));
    }

    @Test
    void statsViewRendersRouteAndTuiBreakdown() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.open();
        panel.recordUsageForTesting(new AiPanel.AiUsageEntry(
                "gpt-4o-mini", "openai", 10, 5, 15, 1000, "stop", Instant.now()));
        spans.set(List.of(routeSpan("orders", "gpt-4o", 50, 10)));
        panel.toggleStatsViewForTesting();

        Rect area = new Rect(0, 0, 120, 24);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);

        String rendered = TuiTestHelper.bufferToString(buffer);
        assertThat(rendered).contains("AI Usage");
        assertThat(rendered).contains("TUI:");
        assertThat(rendered).contains("routes:");
        assertThat(rendered).contains("[tui]");
        assertThat(rendered).contains("[route:orders]");
    }

    @Test
    void togglingStatsViewRequestsSpanRefresh() {
        AiPanel panel = new AiPanel();
        assertThat(panel.spanRefreshRequested).isFalse();

        panel.toggleStatsViewForTesting();
        assertThat(panel.isStatsView()).isTrue();
        assertThat(panel.spanRefreshRequested).isTrue();

        panel.toggleStatsViewForTesting();
        assertThat(panel.isStatsView()).isFalse();
        assertThat(panel.spanRefreshRequested).isTrue();
    }

    @Test
    void statsViewShowsGuidanceWhenNoDataAvailable() {
        AiPanel panel = new AiPanel();
        panel.setOtelSpans(spans);
        panel.open();
        panel.toggleStatsViewForTesting();

        Rect area = new Rect(0, 0, 100, 12);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);

        assertThat(TuiTestHelper.bufferToString(buffer))
                .contains("No AI usage data yet")
                .contains("GenAI observability");
    }

    private static SpanEntry routeSpan(String routeId, String model, int input, int output) {
        return new SpanEntry(
                "trace-1",
                "span-1",
                "",
                "chat " + model,
                "CLIENT",
                "OK",
                Instant.now().getEpochSecond() * 1_000_000_000L,
                Instant.now().getEpochSecond() * 1_000_000_000L + 500_000_000L,
                500,
                routeId,
                null,
                "camel",
                Map.of(
                        GenAiAttributes.OPERATION_NAME, "chat",
                        GenAiAttributes.SYSTEM, "openai",
                        GenAiAttributes.REQUEST_MODEL, model,
                        GenAiAttributes.RESPONSE_MODEL, model,
                        GenAiAttributes.INPUT_TOKENS, input,
                        GenAiAttributes.OUTPUT_TOKENS, output,
                        GenAiAttributes.FINISH_REASONS, "stop"));
    }
}
