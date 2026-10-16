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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.layout.Alignment;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.barchart.Bar;
import dev.tamboui.widgets.barchart.BarChart;
import dev.tamboui.widgets.barchart.BarGroup;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.tui.AiPanel.AiUsageEntry;
import org.apache.camel.dsl.jbang.core.commands.tui.AiPanel.AiUsageSource;

import static org.apache.camel.dsl.jbang.core.commands.tui.AiPanel.formatSeconds;

/**
 * The AI usage of the session, for the AI panel: the panel's own requests (tokens, latency and the question each was
 * for), the time per question spent waiting for the model and in tool calls, and the GenAI calls of the monitored
 * integration, from its OpenTelemetry spans. Ctrl+U shows it as a view ({@link #render(Frame, Rect)}), {@code /usage}
 * as text ({@link #summary()}).
 */
final class AiUsageStats {

    private final List<AiUsageEntry> usageHistory = new CopyOnWriteArrayList<>();
    /** Per answered question: [aiMs, toolMs, toolCalls], in order, for the time chart in the usage view. */
    private final List<long[]> turnTimings = new CopyOnWriteArrayList<>();
    private volatile long sessionToolTimeMs;
    private volatile int sessionToolCalls;
    /** Route usage (GenAI spans) recorded before this instant is left out after a {@code /usage reset}. */
    private volatile Instant usageResetAt = Instant.EPOCH;
    private AtomicReference<List<SpanEntry>> otelSpans = new AtomicReference<>(List.of());
    private final TableState statsTableState = new TableState();
    private int statsScrollOffset;

    void setOtelSpans(AtomicReference<List<SpanEntry>> otelSpans) {
        this.otelSpans = otelSpans != null ? otelSpans : new AtomicReference<>(List.of());
    }

    /** A request of the panel, or of an ACP agent. */
    void add(AiUsageEntry entry) {
        usageHistory.add(entry);
    }

    /** A tool call of the panel and the time it took. */
    void addToolCall(long elapsedMs) {
        sessionToolTimeMs += elapsedMs;
        sessionToolCalls++;
    }

    /** An answered question: the time waiting for the model, the time in tool calls, and how many calls. */
    void addTurn(long aiMs, long toolMs, long toolCalls) {
        turnTimings.add(new long[] { aiMs, toolMs, toolCalls });
    }

    /**
     * Forgets the usage recorded so far: the panel's own requests are dropped, and route usage from spans that were
     * exported before now is hidden (the spans themselves stay, as the Spans tab owns them).
     */
    void reset() {
        usageHistory.clear();
        turnTimings.clear();
        usageResetAt = Instant.now();
        statsScrollOffset = 0;
    }

    /** Forgets everything of the session, as a new conversation does. */
    void clear() {
        usageHistory.clear();
        turnTimings.clear();
        statsScrollOffset = 0;
        sessionToolTimeMs = 0;
        sessionToolCalls = 0;
    }

    /** PgUp and PgDn scroll the model table of the view. */
    void scroll(int delta) {
        statsScrollOffset = Math.max(0, statsScrollOffset + delta);
    }

    void resetScroll() {
        statsScrollOffset = 0;
    }

    /** The Ctrl+U view: totals, a table per model, and the tokens and time per question as bar charts. */
    void render(Frame frame, Rect area) {
        if (area.height() < 3) {
            return;
        }

        List<AiUsageEntry> entries = combinedUsageEntries();
        if (entries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled(
                            "No AI usage data yet. Ask a question, or run an integration with GenAI observability and --observe.",
                            Style.EMPTY.dim()))),
                    area);
            return;
        }

        // Compute aggregates
        int totalInput = 0;
        int totalOutput = 0;
        int totalTokens = 0;
        long totalLatency = 0;
        long tuiLatency = 0;
        int tuiRequests = 0;
        int routeRequests = 0;
        for (AiUsageEntry e : entries) {
            totalInput += e.inputTokens();
            totalOutput += e.outputTokens();
            totalTokens += e.totalTokens();
            totalLatency += e.latencyMs();
            if (e.source() == AiUsageSource.ROUTE) {
                routeRequests++;
            } else {
                tuiRequests++;
                tuiLatency += e.latencyMs();
            }
        }
        int requestCount = entries.size();
        int questionCount = countQuestions(entries);

        // Per-model aggregation
        Map<String, long[]> perModel = new LinkedHashMap<>();
        for (AiUsageEntry e : entries) {
            String key = modelTableKey(e);
            long[] stats = perModel.computeIfAbsent(key, k -> new long[5]);
            stats[0]++; // requests
            stats[1] += e.inputTokens();
            stats[2] += e.outputTokens();
            stats[3] += e.totalTokens();
            stats[4] += e.latencyMs();
        }

        // Tokens per question (panel requests only): the round trips made for one question share its sequence
        // number. Grouping by that, rather than by a pause between requests, keeps a follow-up typed right after the
        // previous answer as its own bar.
        List<Integer> turnTokens = new ArrayList<>();
        int lastQuestion = -1;
        for (AiUsageEntry e : usageHistory) {
            if (e.question() != lastQuestion) {
                turnTokens.add(0);
                lastQuestion = e.question();
            }
            int last = turnTokens.size() - 1;
            turnTokens.set(last, turnTokens.get(last) + e.totalTokens());
        }

        // Layout: summary (2 rows) + model table (header + models + 1 blank) + chart (fill)
        int tableRows = perModel.size() + 1;
        int summaryRows = 2;
        int chartMinRows = 4;
        boolean hasChart = area.height() > summaryRows + tableRows + chartMinRows + 1;

        List<Constraint> constraints = new ArrayList<>();
        constraints.add(Constraint.length(summaryRows));
        constraints.add(Constraint.length(tableRows + 1));
        if (hasChart) {
            constraints.add(Constraint.fill());
        }
        List<Rect> sections = Layout.vertical()
                .constraints(constraints)
                .split(area);

        // --- Summary ---
        Rect summaryArea = sections.get(0);
        Style dimStyle = Theme.muted();
        Style cyanStyle = Style.EMPTY.fg(Theme.accent());
        List<Line> summaryLines = new ArrayList<>();
        summaryLines.add(Line.from(
                Span.styled("Requests: ", dimStyle),
                Span.styled(String.valueOf(requestCount), cyanStyle),
                Span.styled(" (TUI: ", dimStyle),
                Span.styled(String.valueOf(tuiRequests), cyanStyle),
                Span.styled(" / integration: ", dimStyle),
                Span.styled(String.valueOf(routeRequests), cyanStyle),
                Span.styled(")   Total tokens: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalTokens), cyanStyle),
                Span.styled(" (in: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalInput), Theme.success()),
                Span.styled(" / out: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalOutput), Theme.label()),
                Span.styled(")", dimStyle)));
        summaryLines.add(Line.from(
                Span.styled("Avg per question: ", dimStyle),
                Span.styled(formatSeconds(questionCount > 0 ? tuiLatency / questionCount : totalLatency / requestCount),
                        cyanStyle),
                Span.styled(" (" + formatSeconds(totalLatency / requestCount) + " per request)", dimStyle),
                Span.styled("   AI time: ", dimStyle),
                Span.styled(formatSeconds(totalLatency), cyanStyle),
                Span.styled("   Tool time: ", dimStyle),
                Span.styled(formatSeconds(sessionToolTimeMs), cyanStyle),
                Span.styled(" (" + sessionToolCalls + " calls)", dimStyle)));
        frame.renderWidget(
                Paragraph.from(new Text(summaryLines, Alignment.LEFT)),
                summaryArea);

        // --- Per-model table ---
        Rect tableArea = sections.get(1);
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : perModel.entrySet()) {
            long[] s = entry.getValue();
            rows.add(Row.from(
                    Cell.from(Span.styled(entry.getKey(), cyanStyle)),
                    Cell.from(String.valueOf(s[0])),
                    Cell.from(LlmClient.formatTokens((int) s[1])),
                    Cell.from(LlmClient.formatTokens((int) s[2])),
                    Cell.from(LlmClient.formatTokens((int) s[3])),
                    Cell.from(formatSeconds(s[4] / s[0]))));
        }
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("MODEL", Style.EMPTY.bold())),
                        Cell.from(Span.styled("REQS", Style.EMPTY.bold())),
                        Cell.from(Span.styled("INPUT", Style.EMPTY.bold())),
                        Cell.from(Span.styled("OUTPUT", Style.EMPTY.bold())),
                        Cell.from(Span.styled("TOTAL", Style.EMPTY.bold())),
                        Cell.from(Span.styled("AVG/REQ", Style.EMPTY.bold()))))
                .widths(
                        Constraint.fill(),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8))
                .build();
        // PgUp/PgDn scroll the models that do not fit below the header
        int visibleModels = Math.max(0, tableArea.height() - 1);
        statsScrollOffset = Math.max(0, Math.min(statsScrollOffset, rows.size() - visibleModels));
        statsTableState.setOffset(statsScrollOffset);
        frame.renderStatefulWidget(table, tableArea, statsTableState);

        // --- Charts per question: tokens on the left, AI vs tool time on the right ---
        if (hasChart && (turnTokens.size() > 1 || turnTimings.size() > 1)) {
            Rect chartsArea = sections.get(2);
            List<long[]> timings = new ArrayList<>(turnTimings);
            boolean showTime = timings.size() > 1 && chartsArea.width() >= 40;
            List<Rect> halves = showTime
                    ? Layout.horizontal().constraints(Constraint.fill(), Constraint.length(1), Constraint.fill())
                            .split(chartsArea)
                    : List.of(chartsArea);
            if (turnTokens.size() > 1) {
                renderTokensPerQuestion(frame, halves.get(0), turnTokens);
            }
            if (showTime) {
                renderTimePerQuestion(frame, halves.get(2), timings);
            }
        }
    }

    private void renderTokensPerQuestion(Frame frame, Rect chartArea, List<Integer> turnTokens) {
        {

            // Title row + chart
            List<Rect> chartParts = Layout.vertical()
                    .constraints(Constraint.length(1), Constraint.fill())
                    .split(chartArea);
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled("Tokens per question:", Style.EMPTY.bold()))),
                    chartParts.get(0));

            Rect barArea = chartParts.get(1);
            int maxTokensInTurn = turnTokens.stream().mapToInt(Integer::intValue).max().orElse(1);

            // Limit bars to available width
            int maxBars = Math.max(1, barArea.width() / 2);
            int startIdx = Math.max(0, turnTokens.size() - maxBars);
            List<BarGroup> groups = new ArrayList<>();
            for (int i = startIdx; i < turnTokens.size(); i++) {
                groups.add(BarGroup.of(
                        Bar.builder()
                                .value(turnTokens.get(i))
                                .textValue("")
                                .style(Style.EMPTY.fg(Theme.accent()))
                                .build()));
            }

            BarChart barChart = BarChart.builder()
                    .data(groups)
                    .max(maxTokensInTurn + 2)
                    .barWidth(1)
                    .barGap(1)
                    .groupGap(0)
                    .build();
            frame.renderWidget(barChart, barArea);
        }
    }

    /**
     * One group of two bars per answered question: time waiting for the model next to time spent in tool calls.
     */
    private void renderTimePerQuestion(Frame frame, Rect chartArea, List<long[]> timings) {
        List<Rect> chartParts = Layout.vertical()
                .constraints(Constraint.length(1), Constraint.fill())
                .split(chartArea);
        Style aiStyle = Style.EMPTY.fg(Theme.accent());
        Style toolStyle = Theme.warning();
        frame.renderWidget(
                Paragraph.from(Line.from(
                        Span.styled("Time per question: ", Style.EMPTY.bold()),
                        Span.styled("\u25a0 ai ", aiStyle),
                        Span.styled("\u25a0 tools", toolStyle))),
                chartParts.get(0));

        Rect barArea = chartParts.get(1);
        long maxMs = 1;
        for (long[] t : timings) {
            maxMs = Math.max(maxMs, Math.max(t[0], t[1]));
        }
        // each question takes two 1-wide bars plus a gap
        int maxGroups = Math.max(1, barArea.width() / 3);
        int startIdx = Math.max(0, timings.size() - maxGroups);
        List<BarGroup> groups = new ArrayList<>();
        for (int i = startIdx; i < timings.size(); i++) {
            long[] t = timings.get(i);
            groups.add(BarGroup.of(
                    Bar.builder().value(t[0]).textValue("").style(aiStyle).build(),
                    Bar.builder().value(t[1]).textValue("").style(toolStyle).build()));
        }
        BarChart barChart = BarChart.builder()
                .data(groups)
                .max(maxMs + maxMs / 20)
                .barWidth(1)
                .barGap(0)
                .groupGap(1)
                .build();
        frame.renderWidget(barChart, barArea);
    }

    /** The panel's own requests, then the GenAI calls of the integration since the last reset. */
    List<AiUsageEntry> combinedUsageEntries() {
        List<AiUsageEntry> combined = new ArrayList<>(usageHistory.size() + 8);
        combined.addAll(usageHistory);
        List<SpanEntry> spans = otelSpans.get();
        if (spans != null && !spans.isEmpty()) {
            Instant since = usageResetAt;
            for (AiUsageEntry entry : GenAiSpanUsageExtractor.extract(spans)) {
                if (!entry.timestamp().isBefore(since)) {
                    combined.add(entry);
                }
            }
        }
        return combined;
    }

    private static String modelTableKey(AiUsageEntry entry) {
        String modelProvider = entry.model() + " (" + entry.provider() + ")";
        if (entry.source() == AiUsageSource.ROUTE) {
            String route = entry.routeId() != null && !entry.routeId().isBlank() ? entry.routeId() : "route";
            return "[route:" + route + "] " + modelProvider;
        }
        return "[tui] " + modelProvider;
    }

    /**
     * One model round trip. {@code question} is the sequence number of the panel question the request was made for (a
     * question takes several round trips when the model calls tools), so usage can be grouped per question; it is 0 for
     * route requests, which belong to no question.
     */
    /**
     * How many questions the panel's own requests were made for: a question takes one request per model round trip, and
     * consecutive requests with the same question number belong to one question. Route calls do not count.
     */
    static int countQuestions(List<AiUsageEntry> entries) {
        int questions = 0;
        int lastQuestion = -1;
        for (AiUsageEntry e : entries) {
            if (e.source() == AiUsageSource.ROUTE) {
                continue;
            }
            if (questions == 0 || e.question() != lastQuestion) {
                questions++;
                lastQuestion = e.question();
            }
        }
        return questions;
    }

    /**
     * The usage figures of the Ctrl+U view as text for the chat: totals, then one line per model (and per route for
     * GenAI spans from the monitored integration).
     */
    String summary() {
        List<AiUsageEntry> entries = combinedUsageEntries();
        if (entries.isEmpty()) {
            return "No AI usage yet. Ask a question, or run an integration with GenAI observability and --observe to see "
                   + "their usage here.";
        }
        int totalInput = 0;
        int totalOutput = 0;
        int totalTokens = 0;
        long totalLatency = 0;
        long tuiLatency = 0;
        int tuiRequests = 0;
        int routeRequests = 0;
        Map<String, long[]> perModel = new LinkedHashMap<>();
        for (AiUsageEntry e : entries) {
            totalInput += e.inputTokens();
            totalOutput += e.outputTokens();
            totalTokens += e.totalTokens();
            totalLatency += e.latencyMs();
            if (e.source() == AiUsageSource.ROUTE) {
                routeRequests++;
            } else {
                tuiRequests++;
                tuiLatency += e.latencyMs();
            }
            long[] stats = perModel.computeIfAbsent(modelTableKey(e), k -> new long[5]);
            stats[0]++;
            stats[1] += e.inputTokens();
            stats[2] += e.outputTokens();
            stats[3] += e.totalTokens();
            stats[4] += e.latencyMs();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**AI usage:** ").append(entries.size()).append(" request(s)");
        if (routeRequests > 0) {
            sb.append(" (").append(tuiRequests).append(" from this panel, ").append(routeRequests)
                    .append(" from the integration)");
        }
        sb.append("\n\n");
        sb.append("- **Tokens:** ").append(LlmClient.formatTokens(totalTokens))
                .append(" (in ").append(LlmClient.formatTokens(totalInput))
                .append(", out ").append(LlmClient.formatTokens(totalOutput)).append(")\n");
        int questionCount = countQuestions(entries);
        sb.append("- **Avg per question:** ")
                .append(formatSeconds(questionCount > 0 ? tuiLatency / questionCount : totalLatency / entries.size()))
                .append(" (").append(formatSeconds(totalLatency / entries.size())).append(" per request)\n");
        if (sessionToolCalls > 0) {
            sb.append("- **AI time:** ").append(formatSeconds(totalLatency))
                    .append(", **Tool time:** ").append(formatSeconds(sessionToolTimeMs))
                    .append(" in ").append(sessionToolCalls).append(" tool call(s)\n");
        }
        AiUsageEntry last = usageHistory.isEmpty() ? null : usageHistory.get(usageHistory.size() - 1);
        if (last != null) {
            sb.append("- **Last request:** ").append(LlmClient.formatTokens(last.totalTokens()))
                    .append(" tokens in ").append(formatSeconds(last.latencyMs())).append("\n");
        }
        sb.append("\n| Model | Reqs | In | Out | Total | Avg/req |\n|---|---|---|---|---|---|\n");
        for (Map.Entry<String, long[]> entry : perModel.entrySet()) {
            long[] stats = entry.getValue();
            sb.append("| ").append(entry.getKey())
                    .append(" | ").append(stats[0])
                    .append(" | ").append(LlmClient.formatTokens((int) stats[1]))
                    .append(" | ").append(LlmClient.formatTokens((int) stats[2]))
                    .append(" | ").append(LlmClient.formatTokens((int) stats[3]))
                    .append(" | ").append(formatSeconds(stats[4] / stats[0]))
                    .append(" |\n");
        }
        sb.append("\n_Ctrl+U opens the full usage view with the per-question charts._");
        return sb.toString().strip();
    }
}
