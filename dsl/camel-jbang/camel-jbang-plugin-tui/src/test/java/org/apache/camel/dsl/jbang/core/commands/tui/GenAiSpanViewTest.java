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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.dsl.jbang.core.commands.tui.GenAiSpanView.AgentRun;
import org.apache.camel.dsl.jbang.core.commands.tui.GenAiSpanView.Step;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiSpanViewTest {

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void labelOfAnAgentRun() {
        assertThat(GenAiSpanView.label(agent())).isEqualTo("◆ agent support-agent · llama3.2");
    }

    @Test
    void labelOfAModelCallHasItsTokensAndFinishReason() {
        assertThat(GenAiSpanView.label(chat("chat1", "agent", 100, 236, 1660, 25, 0, "tool_calls")))
                .isEqualTo("● chat llama3.2 · 1.7k→25 · tool_calls");
        SpanEntry embeddings = span("e", "", 0, 10, attrs("embeddings", GenAiAttributes.REQUEST_MODEL, "unknown"));
        assertThat(GenAiSpanView.label(embeddings)).isEqualTo("● embeddings");
    }

    @Test
    void labelOfAToolCallHasItsArgumentsOnOneLine() {
        assertThat(GenAiSpanView.label(tool("tool", "agent", 340, 1, "{\n  \"orderId\": \"A-17\"\n}")))
                .isEqualTo("⚙ tool lookupOrder { \"orderId\": \"A-17\" }");
        String prefix = "⚙ tool lookupOrder ";
        assertThat(GenAiSpanView.label(tool("tool", "agent", 340, 1, "{\"note\":\"" + "x".repeat(60) + "\"}")))
                .satisfies(label -> assertThat(CharWidth.of(label)).isEqualTo(CharWidth.of(prefix) + 40))
                .endsWith("…");
        // cut by display width, never inside a wide character, a flag or a ZWJ emoji
        String wide = "{\"city\":\"東京\",\"flag\":\"\uD83C\uDDEB\uD83C\uDDF7\",\"who\":\"\uD83D\uDC69\u200D\uD83D\uDCBB\","
                      + "\"note\":\"東京東京東京東京東京東京\"}";
        String label = GenAiSpanView.label(tool("tool", "agent", 340, 1, wide));
        assertThat(CharWidth.of(label)).isLessThanOrEqualTo(CharWidth.of(prefix) + 40);
        assertThat(label).startsWith(prefix + "{\"city\":\"東京\",\"flag\":\"\uD83C\uDDEB\uD83C\uDDF7\"").endsWith("…");
        assertThat(GenAiSpanView.label(tool("tool", "agent", 340, 1, null))).isEqualTo("⚙ tool lookupOrder");
    }

    @Test
    void agentRunHasItsStepsInOrder() {
        AgentRun run = GenAiSpanView.agentRun(agent(), trace());

        assertThat(run.agentName()).isEqualTo("support-agent");
        assertThat(run.model()).isEqualTo("llama3.2");
        assertThat(run.provider()).isEqualTo("openai");
        assertThat(run.conversationId()).isEqualTo("conv-1");
        assertThat(run.steps()).extracting(Step::name).containsExactly("chat llama3.2", "lookupOrder", "chat llama3.2");
        assertThat(run.modelCalls()).isEqualTo(2);
        assertThat(run.toolCalls()).isEqualTo(1);
        assertThat(run.inputTokens()).isEqualTo(1660 + 1700);
        assertThat(run.outputTokens()).isEqualTo(25 + 12);
        assertThat(run.cachedTokens()).isEqualTo(1600);
        assertThat(run.modelMs()).isEqualTo(236 + 146);
        assertThat(run.toolMs()).isEqualTo(1);
    }

    @Test
    void agentRunLeavesOutTheStepsOfAnotherRun() {
        SpanEntry inner = span("inner", "tool", 350, 50, attrs("invoke_agent", GenAiAttributes.AGENT_NAME, "helper"));
        SpanEntry innerChat = chat("innerChat", "inner", 360, 30, 10, 5, 0, "stop");
        List<SpanEntry> spans = new ArrayList<>(trace());
        spans.add(inner);
        spans.add(innerChat);

        assertThat(GenAiSpanView.agentRun(agent(), spans).steps()).hasSize(3);
        assertThat(GenAiSpanView.agentRun(inner, spans).steps()).extracting(Step::span).containsExactly(innerChat);
    }

    @Test
    void agentRunLinesShowStepsTokensAndTime() {
        String text = text(GenAiSpanView.agentRunLines(GenAiSpanView.agentRun(agent(), trace())));

        assertThat(text)
                .contains("Agent:  support-agent  Model: llama3.2 (openai)  Conversation id: conv-1")
                .contains("Steps:  2 model calls, 1 tool call  Tokens: 3.4k in / 37 out / 1.6k cached")
                .contains("Time:   385ms = model 382ms (99%) + tools 1ms (0%) + other 2ms")
                .contains(" 1. ● chat llama3.2")
                .contains(" 2. ⚙ lookupOrder")
                .contains("1.7k→12 (1.6k cached)  stop");
    }

    /** An agent run: a model call, a tool call (whose route spans nest under it), a model call under a Camel span. */
    private static List<SpanEntry> trace() {
        SpanEntry toolRoute = span("toolRoute", "tool", 341, 1, Map.of("camel.uri", "ai-tool://lookupOrder"));
        SpanEntry step = span("step", "agent", 380, 150, Map.of("camel.uri", "openai:chat-completion"));
        return List.of(agent(),
                chat("chat1", "agent", 100, 236, 1660, 25, 0, "tool_calls"),
                tool("tool", "agent", 340, 1, "{\"orderId\":\"A-17\"}"),
                toolRoute,
                step,
                chat("chat2", "step", 382, 146, 1700, 12, 1600, "stop"));
    }

    private static SpanEntry agent() {
        return span("agent", "route", 99, 385, attrs("invoke_agent",
                GenAiAttributes.AGENT_NAME, "support-agent",
                GenAiAttributes.REQUEST_MODEL, "llama3.2",
                GenAiAttributes.PROVIDER_NAME, "openai",
                GenAiAttributes.CONVERSATION_ID, "conv-1"));
    }

    private static SpanEntry chat(
            String id, String parent, long start, long ms, long in, long out, long cached, String finish) {
        Map<String, Object> attrs = attrs("chat", GenAiAttributes.REQUEST_MODEL, "llama3.2",
                GenAiAttributes.FINISH_REASONS, finish);
        attrs.put(GenAiAttributes.INPUT_TOKENS, in);
        attrs.put(GenAiAttributes.OUTPUT_TOKENS, out);
        if (cached > 0) {
            attrs.put(GenAiAttributes.CACHE_READ_INPUT_TOKENS, cached);
        }
        return span(id, parent, start, ms, attrs);
    }

    private static SpanEntry tool(String id, String parent, long start, long ms, String arguments) {
        Map<String, Object> attrs = attrs("execute_tool", GenAiAttributes.TOOL_NAME, "lookupOrder");
        if (arguments != null) {
            attrs.put(GenAiAttributes.TOOL_CALL_ARGUMENTS, arguments);
        }
        return span(id, parent, start, ms, attrs);
    }

    private static Map<String, Object> attrs(String operation, String... keyValues) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(GenAiAttributes.OPERATION_NAME, operation);
        for (int i = 0; i < keyValues.length; i += 2) {
            attrs.put(keyValues[i], keyValues[i + 1]);
        }
        return attrs;
    }

    private static SpanEntry span(String id, String parent, long startMs, long ms, Map<String, Object> attributes) {
        return new SpanEntry(
                "trace-1", id, parent, id, "INTERNAL", "OK", startMs * 1_000_000L,
                (startMs + ms) * 1_000_000L, ms, "support-agent", null, "camel", attributes);
    }

    private static String text(List<Line> lines) {
        return lines.stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(Collectors.joining("\n"));
    }
}
