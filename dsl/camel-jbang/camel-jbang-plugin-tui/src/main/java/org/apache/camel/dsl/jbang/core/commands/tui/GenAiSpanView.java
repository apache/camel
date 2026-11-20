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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;

/**
 * How the Spans tab shows GenAI spans: a label per span in the waterfall (an agent run, a model call with its tokens
 * and finish reason, a tool call with its arguments), and in the detail panel of an agent run its steps, tokens and
 * time.
 */
final class GenAiSpanView {

    static final String AGENT = "◆";
    static final String MODEL_CALL = "●";
    static final String TOOL = TuiIcons.GEAR;

    private static final int MAX_ARGUMENTS = 40;
    private static final int MAX_STEPS = 8;
    private static final int MAX_PARENT_HOPS = 50;

    /** A model call or tool call of an agent run. */
    record Step(SpanEntry span, boolean tool, String name, long durationMs, long inputTokens, long outputTokens,
            long cachedTokens, String finishReason) {
    }

    /** An agent run with its steps in the order they started. */
    record AgentRun(String agentName, String model, String provider, String conversationId, long durationMs,
            List<Step> steps) {

        long modelCalls() {
            return steps.stream().filter(s -> !s.tool()).count();
        }

        long toolCalls() {
            return steps.stream().filter(Step::tool).count();
        }

        long inputTokens() {
            return steps.stream().mapToLong(Step::inputTokens).sum();
        }

        long outputTokens() {
            return steps.stream().mapToLong(Step::outputTokens).sum();
        }

        long cachedTokens() {
            return steps.stream().mapToLong(Step::cachedTokens).sum();
        }

        long modelMs() {
            return steps.stream().filter(s -> !s.tool()).mapToLong(Step::durationMs).sum();
        }

        long toolMs() {
            return steps.stream().filter(Step::tool).mapToLong(Step::durationMs).sum();
        }
    }

    private GenAiSpanView() {
    }

    static boolean isAgentRun(SpanEntry span) {
        return GenAiOperationName.INVOKE_AGENT.value().equals(operation(span));
    }

    static boolean isToolCall(SpanEntry span) {
        return GenAiOperationName.EXECUTE_TOOL.value().equals(operation(span));
    }

    /** The waterfall label of a GenAI span. */
    static String label(SpanEntry span) {
        Map<String, Object> attrs = span.attributes();
        if (isAgentRun(span)) {
            String model = model(attrs);
            return AGENT + " agent " + orElse(text(attrs, GenAiAttributes.AGENT_NAME), span.routeId())
                   + (model != null ? " · " + model : "");
        }
        if (isToolCall(span)) {
            String arguments = text(attrs, GenAiAttributes.TOOL_CALL_ARGUMENTS);
            return TOOL + " tool " + orElse(text(attrs, GenAiAttributes.TOOL_NAME), span.name())
                   + (arguments != null ? " " + oneLine(arguments, MAX_ARGUMENTS) : "");
        }
        StringBuilder sb = new StringBuilder(MODEL_CALL).append(' ').append(operation(span));
        String model = model(attrs);
        if (model != null) {
            sb.append(' ').append(model);
        }
        long in = number(attrs, GenAiAttributes.INPUT_TOKENS);
        long out = number(attrs, GenAiAttributes.OUTPUT_TOKENS);
        if (in > 0 || out > 0) {
            sb.append(" · ").append(tokens(in)).append('→').append(tokens(out));
        }
        String finish = text(attrs, GenAiAttributes.FINISH_REASONS);
        if (finish != null) {
            sb.append(" · ").append(finish);
        }
        return sb.toString();
    }

    /** The colour of a GenAI span in the waterfall: an agent run, a model call or a tool call. */
    static Style style(SpanEntry span, boolean selected) {
        Style style = isAgentRun(span) ? Theme.label() : isToolCall(span) ? Theme.warning() : Theme.success();
        return selected ? style.bold() : style;
    }

    /** The agent run with its model and tool calls: the GenAI spans whose nearest agent run is this one. */
    static AgentRun agentRun(SpanEntry agent, List<SpanEntry> traceSpans) {
        Map<String, SpanEntry> byId = new HashMap<>();
        for (SpanEntry s : traceSpans) {
            if (s.spanId() != null) {
                byId.put(s.spanId(), s);
            }
        }
        List<Step> steps = new ArrayList<>();
        traceSpans.stream()
                .filter(s -> s != agent && GenAiTranscript.isGenAiSpan(s) && !isAgentRun(s))
                .filter(s -> agent.spanId() != null && agent.spanId().equals(nearestAgentRun(s, byId)))
                .sorted(Comparator.comparingLong(SpanEntry::startEpochNanos))
                .forEach(s -> steps.add(step(s)));
        Map<String, Object> attrs = agent.attributes();
        return new AgentRun(
                orElse(text(attrs, GenAiAttributes.AGENT_NAME), agent.routeId()), model(attrs),
                orElse(text(attrs, GenAiAttributes.PROVIDER_NAME), text(attrs, GenAiAttributes.SYSTEM)),
                text(attrs, GenAiAttributes.CONVERSATION_ID), agent.durationMs(), steps);
    }

    private static Step step(SpanEntry span) {
        Map<String, Object> attrs = span.attributes();
        if (isToolCall(span)) {
            return new Step(
                    span, true, orElse(text(attrs, GenAiAttributes.TOOL_NAME), span.name()), span.durationMs(),
                    0, 0, 0, null);
        }
        return new Step(
                span, false, operation(span) + (model(attrs) != null ? " " + model(attrs) : ""),
                span.durationMs(), number(attrs, GenAiAttributes.INPUT_TOKENS),
                number(attrs, GenAiAttributes.OUTPUT_TOKENS), number(attrs, GenAiAttributes.CACHE_READ_INPUT_TOKENS),
                text(attrs, GenAiAttributes.FINISH_REASONS));
    }

    private static String nearestAgentRun(SpanEntry span, Map<String, SpanEntry> byId) {
        SpanEntry current = span;
        for (int hops = 0; hops < MAX_PARENT_HOPS && current != null && !current.isRoot(); hops++) {
            current = byId.get(current.parentSpanId());
            if (current != null && isAgentRun(current)) {
                return current.spanId();
            }
        }
        return null;
    }

    /** The lines of the detail panel for an agent run: model, steps, tokens, time, and the steps one by one. */
    static List<Line> agentRunLines(AgentRun run) {
        Style muted = Theme.muted();
        Style value = Style.EMPTY.fg(Theme.baseFg()).bold();
        List<Line> lines = new ArrayList<>();
        List<Span> first = new ArrayList<>(
                List.of(
                        Span.styled(" Agent:  ", muted), Span.styled(orElse(run.agentName(), "-"), Theme.label())));
        if (run.model() != null) {
            first.add(Span.styled("  Model: ", muted));
            first.add(Span.styled(run.model() + (run.provider() != null ? " (" + run.provider() + ")" : ""), value));
        }
        if (run.conversationId() != null) {
            first.add(Span.styled("  Conversation id: ", muted));
            first.add(Span.raw(run.conversationId()));
        }
        lines.add(Line.from(first));
        lines.add(Line.from(
                Span.styled(" Steps:  ", muted),
                Span.styled(run.modelCalls() + " model call" + (run.modelCalls() == 1 ? "" : "s") + ", "
                            + run.toolCalls() + " tool call" + (run.toolCalls() == 1 ? "" : "s"),
                        value),
                Span.styled("  Tokens: ", muted),
                Span.styled(tokens(run.inputTokens()) + " in / " + tokens(run.outputTokens()) + " out / "
                            + tokens(run.cachedTokens()) + " cached",
                        value)));
        long other = Math.max(0, run.durationMs() - run.modelMs() - run.toolMs());
        lines.add(Line.from(
                Span.styled(" Time:   ", muted),
                Span.styled(duration(run.durationMs()), value),
                Span.styled(" = model " + duration(run.modelMs()) + percent(run.modelMs(), run.durationMs())
                            + " + tools " + duration(run.toolMs()) + percent(run.toolMs(), run.durationMs())
                            + " + other " + duration(other),
                        muted)));
        int shown = Math.min(MAX_STEPS, run.steps().size());
        for (int i = 0; i < shown; i++) {
            lines.add(stepLine(i + 1, run.steps().get(i)));
        }
        if (run.steps().size() > shown) {
            lines.add(Line.from(Span.styled("   … " + (run.steps().size() - shown) + " more steps", muted)));
        }
        return lines;
    }

    private static Line stepLine(int number, Step step) {
        String symbol = step.tool() ? TOOL : MODEL_CALL;
        Style style = step.tool() ? Theme.warning() : Theme.success();
        String name = symbol + " " + step.name();
        name += " ".repeat(Math.max(1, 28 - CharWidth.of(name)));
        List<Span> spans = new ArrayList<>(
                List.of(
                        Span.styled(String.format("  %2d. ", number), Theme.muted()),
                        Span.styled(name, style),
                        Span.styled(String.format("%8s", duration(step.durationMs())), Style.EMPTY.fg(Theme.baseFg()))));
        if (!step.tool()) {
            spans.add(Span.styled("  " + tokens(step.inputTokens()) + "→" + tokens(step.outputTokens()), Theme.muted()));
            if (step.cachedTokens() > 0) {
                spans.add(Span.styled(" (" + tokens(step.cachedTokens()) + " cached)", Theme.muted()));
            }
            if (step.finishReason() != null) {
                spans.add(Span.styled("  " + step.finishReason(), Theme.muted()));
            }
        }
        return Line.from(spans);
    }

    static String duration(long ms) {
        return ms < 1000 ? ms + "ms" : AiPanel.formatSeconds(ms);
    }

    private static String percent(long part, long whole) {
        return whole > 0 ? " (" + (part * 100 / whole) + "%)" : "";
    }

    private static String tokens(long tokens) {
        return LlmClient.formatTokens((int) Math.min(Integer.MAX_VALUE, tokens));
    }

    /** The text on one line, cut by display width (a value may hold CJK text or emoji). */
    private static String oneLine(String text, int max) {
        String line = text.replaceAll("\\s+", " ").strip();
        return CharWidth.of(line) > max ? CharWidth.substringByWidth(line, max - 1) + "…" : line;
    }

    private static String operation(SpanEntry span) {
        return span != null ? text(span.attributes(), GenAiAttributes.OPERATION_NAME) : null;
    }

    private static String model(Map<String, Object> attrs) {
        String model = orElse(text(attrs, GenAiAttributes.RESPONSE_MODEL), text(attrs, GenAiAttributes.REQUEST_MODEL));
        return "unknown".equals(model) ? null : model;
    }

    private static String orElse(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String text(Map<String, Object> attrs, String key) {
        Object value = attrs != null ? attrs.get(key) : null;
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static long number(Map<String, Object> attrs, String key) {
        Object value = attrs != null ? attrs.get(key) : null;
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value != null ? Long.parseLong(value.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
