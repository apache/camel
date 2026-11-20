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
import java.util.List;
import java.util.Map;

import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiAgentRuns.AgentRun;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiAgentRuns.Step;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiSpans;
import org.apache.camel.dsl.jbang.core.common.genai.SpanEntry;

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

    private GenAiSpanView() {
    }

    /** The waterfall label of a GenAI span. */
    static String label(SpanEntry span) {
        Map<String, Object> attrs = span.attributes();
        if (GenAiSpans.isAgentRun(span)) {
            String model = GenAiSpans.model(attrs);
            return AGENT + " agent " + orElse(GenAiSpans.text(attrs, GenAiAttributes.AGENT_NAME), span.routeId())
                   + (model != null ? " · " + model : "");
        }
        if (GenAiSpans.isToolCall(span)) {
            String arguments = GenAiSpans.text(attrs, GenAiAttributes.TOOL_CALL_ARGUMENTS);
            return TOOL + " tool " + orElse(GenAiSpans.text(attrs, GenAiAttributes.TOOL_NAME), span.name())
                   + (arguments != null ? " " + oneLine(arguments, MAX_ARGUMENTS) : "");
        }
        StringBuilder sb = new StringBuilder(MODEL_CALL).append(' ').append(GenAiSpans.operation(span));
        String model = GenAiSpans.model(attrs);
        if (model != null) {
            sb.append(' ').append(model);
        }
        long in = GenAiSpans.number(attrs, GenAiAttributes.INPUT_TOKENS);
        long out = GenAiSpans.number(attrs, GenAiAttributes.OUTPUT_TOKENS);
        if (in > 0 || out > 0) {
            sb.append(" · ").append(tokens(in)).append('→').append(tokens(out));
        }
        String finish = GenAiSpans.text(attrs, GenAiAttributes.FINISH_REASONS);
        if (finish != null) {
            sb.append(" · ").append(finish);
        }
        return sb.toString();
    }

    /** The colour of a GenAI span in the waterfall: an agent run, a model call or a tool call. */
    static Style style(SpanEntry span, boolean selected) {
        Style style
                = GenAiSpans.isAgentRun(span) ? Theme.label() : GenAiSpans.isToolCall(span) ? Theme.warning() : Theme.success();
        return selected ? style.bold() : style;
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
        long other = run.otherMs();
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

    private static String orElse(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
