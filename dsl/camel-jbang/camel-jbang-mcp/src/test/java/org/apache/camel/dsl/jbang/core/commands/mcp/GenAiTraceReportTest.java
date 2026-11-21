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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiMessages;
import org.apache.camel.dsl.jbang.core.common.genai.SpanEntry;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiTraceReportTest {

    private static final String QUESTION
            = "Order ORD-1003 has a Camel cap in it: is the order paid, and is the cap in stock?";

    @Test
    void summaryComesFirstAndFitsInWhatAChatHistoryKeeps() {
        String summary = GenAiTraceReport.summary(run("run1", 1000, true), "*");

        assertThat(summary).isEqualTo(
                "GenAI level=full: 1 agent run, 2 model calls (granite4:3b), 2 tool calls; runs newest first. "
                                      + "camel_runtime_ai_conversation with a runId shows the messages of a run.");
        assertThat(summary.length()).isLessThan(400);
    }

    @Test
    void agentRunHasItsQuestionAndItsStepsInOrder() {
        List<SpanEntry> spans = run("run1", 1000, true);

        GenAiTraceReport.GenAi genAi = GenAiTraceReport.genAi(spans, null);

        assertThat(genAi.level()).isEqualTo("full");
        assertThat(genAi.modelCalls()).singleElement().satisfies(m -> {
            assertThat(m.model()).isEqualTo("granite4:3b");
            assertThat(m.calls()).isEqualTo(2);
            assertThat(m.inputTokens()).isEqualTo(694);
            assertThat(m.cachedTokens()).isEqualTo(585);
            assertThat(m.finishReasons()).isEqualTo("tool_calls,stop");
        });
        GenAiTraceReport.Run run = genAi.agentRuns().get(0);
        assertThat(run.runId()).isEqualTo("run1");
        assertThat(run.question()).isEqualTo(QUESTION);
        assertThat(run.exchangeId()).isEqualTo("EX-run1");
        assertThat(run.routeId()).isEqualTo("customer-questions");
        assertThat(run.steps()).extracting(GenAiTraceReport.RunStep::step)
                .containsExactly("model call", "tool call", "tool call", "model call");
        assertThat(run.steps().get(1)).satisfies(step -> {
            assertThat(step.name()).isEqualTo("order_status");
            assertThat(step.arguments()).isEqualTo("{\"orderId\":\"ORD-1003\"}");
            assertThat(step.result()).isEqualTo("Order ORD-1003 for customer C-134 in US is pending with 3 line(s)");
            assertThat(step.inputTokens()).isNull();
        });
        assertThat(genAi.moreRuns()).isNull();
    }

    @Test
    void newestFiveRunsAreShown() {
        List<SpanEntry> spans = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            spans.addAll(run("run" + i, i * 10_000, false));
        }

        GenAiTraceReport.GenAi genAi = GenAiTraceReport.genAi(spans, "*");

        assertThat(genAi.level()).isEqualTo("detailed");
        assertThat(genAi.agentRuns()).extracting(GenAiTraceReport.Run::runId)
                .containsExactly("run7", "run6", "run5", "run4", "run3");
        assertThat(genAi.moreRuns()).isEqualTo("+2 more runs");
        // level detailed records no messages: no question, no tool arguments
        assertThat(genAi.agentRuns().get(0).question()).isNull();
        assertThat(genAi.agentRuns().get(0).steps().get(1).arguments()).isNull();
        assertThat(GenAiTraceReport.summary(spans, "*"))
                .startsWith("GenAI level=detailed: 7 agent runs, 14 model calls (granite4:3b), 14 tool calls")
                .endsWith("The messages need camel.aiObservability.level=full.");
    }

    @Test
    void summarySaysWhatToDoAtLevelBasicOrWithoutSpans() {
        SpanEntry chat = span("chat", null, "orders", 0, 100,
                attrs("chat", GenAiAttributes.SYSTEM, "openai", GenAiAttributes.REQUEST_MODEL, "gpt-4.1"));

        assertThat(GenAiTraceReport.summary(List.of(chat), "*"))
                .isEqualTo("GenAI level=basic: 1 model call (gpt-4.1). Agent runs and tool calls are not traced: "
                           + "set camel.aiObservability.level=detailed.");
        assertThat(GenAiTraceReport.summary(List.of(), "*")).isEqualTo(GenAiTraceReport.NO_SPANS);
        assertThat(GenAiTraceReport.genAi(List.of(), "*")).isNull();
    }

    @Test
    void routeFilterKeepsTheRunsOfThatRoute() {
        assertThat(GenAiTraceReport.genAi(run("run1", 1000, true), "other-route").agentRuns()).isNull();
        assertThat(GenAiTraceReport.genAi(run("run1", 1000, true), "customer-questions").agentRuns()).hasSize(1);
    }

    @Test
    void spansAreReadFromTheDump() {
        JsonObject span = new JsonObject();
        span.put("traceId", "t1");
        span.put("spanId", "s1");
        span.put("name", "chat");
        JsonObject attributes = new JsonObject();
        attributes.put(GenAiAttributes.OPERATION_NAME, "chat");
        span.put("attributes", attributes);
        JsonObject dump = new JsonObject();
        dump.put("enabled", true);
        dump.put("spans", new JsonArray(List.of(span)));

        assertThat(GenAiTraceReport.spans(dump)).singleElement()
                .satisfies(s -> assertThat(s.attributes()).containsEntry(GenAiAttributes.OPERATION_NAME, "chat"));
        dump.put("enabled", false);
        assertThat(GenAiTraceReport.spans(dump)).isEmpty();
        assertThat(GenAiTraceReport.spans(null)).isEmpty();
    }

    @Test
    void conversationListsTheMessagesOfTheRunInOrder() {
        GenAiTraceReport.Conversation conversation = GenAiTraceReport.conversation(run("run1", 1000, true), "run1");

        assertThat(conversation.level()).isEqualTo("full");
        assertThat(conversation.summary()).startsWith("GenAI level=full: 7 message parts of run run1.");
        assertThat(conversation.messages())
                .extracting(m -> m.role() + ":" + m.type() + (m.tool() != null ? ":" + m.tool() : ""))
                .containsExactly("system:text", "user:text", "assistant:tool call:order_status",
                        "assistant:tool call:stock_level", "tool:tool result:order_status",
                        "tool:tool result:stock_level", "assistant:text");
        assertThat(conversation.messages().get(6).text())
                .isEqualTo("Your order ORD-1003 is still pending and the Camel cap is currently out of stock.");
        assertThat(conversation.cut()).isNull();
    }

    @Test
    void conversationIsCapped() {
        String longAnswer = "x".repeat(GenAiTraceReport.MAX_MESSAGE * 2);
        List<SpanEntry> spans = new ArrayList<>();
        SpanEntry agent = span("run1", "route1", "customer-questions", 0, 1000,
                attrs("invoke_agent", GenAiAttributes.REQUEST_MODEL, "granite4:3b"));
        GenAiMessages input = new GenAiMessages();
        for (int i = 0; i < 10; i++) {
            input.text(GenAiMessages.USER, "question " + i + " " + longAnswer);
        }
        spans.add(agent);
        spans.add(span("chat1", "run1", "customer-questions", 10, 100,
                attrs("chat", GenAiAttributes.REQUEST_MODEL, "granite4:3b",
                        GenAiAttributes.INPUT_MESSAGES, input.toJson(8192))));

        GenAiTraceReport.Conversation conversation = GenAiTraceReport.conversation(spans, "run1");

        assertThat(conversation.messages()).allSatisfy(m -> assertThat(m.text().length())
                .isLessThanOrEqualTo(GenAiTraceReport.MAX_MESSAGE));
        assertThat(conversation.messages().stream().mapToInt(m -> m.text().length()).sum())
                .isLessThanOrEqualTo(GenAiTraceReport.MAX_CONVERSATION);
        assertThat(conversation.messages().get(0).text()).endsWith("...");
        assertThat(conversation.cut()).isEqualTo("4 more message parts not shown (6000 characters in all)");
    }

    @Test
    void conversationOfARunWithoutMessagesSaysTheLevel() {
        GenAiTraceReport.Conversation conversation = GenAiTraceReport.conversation(run("run1", 1000, false), "run1");

        assertThat(conversation.messages()).isNull();
        assertThat(conversation.summary()).isEqualTo("GenAI level=detailed: no messages for run run1. "
                                                     + "The messages are recorded at camel.aiObservability.level=full only.");
        assertThat(GenAiTraceReport.conversation(run("run1", 1000, false), "nope")).isNull();
    }

    /**
     * The agent run of question 3 of the ai/agent-tools example: a route span, the agent run, a model call asking for
     * two tools, the two tool calls, and the model call that answers. {@code full} adds the messages.
     */
    private static List<SpanEntry> run(String id, long startMs, boolean full) {
        List<SpanEntry> spans = new ArrayList<>();
        spans.add(new SpanEntry(
                "trace-" + id, "route-" + id, null, "file://questions", "INTERNAL", "OK",
                startMs * 1_000_000L, (startMs + 1010) * 1_000_000L, 1010, "customer-questions", null, "camel",
                Map.of("exchangeId", "EX-" + id)));
        spans.add(span(id, "route-" + id, "customer-questions", startMs + 2, 1002,
                attrs("invoke_agent", GenAiAttributes.AGENT_NAME, "customer-questions",
                        GenAiAttributes.REQUEST_MODEL, "granite4:3b", GenAiAttributes.PROVIDER_NAME, "openai")));
        Map<String, Object> chat1 = attrs("chat", GenAiAttributes.REQUEST_MODEL, "granite4:3b",
                GenAiAttributes.PROVIDER_NAME, "openai", GenAiAttributes.FINISH_REASONS, "tool_calls");
        chat1.put(GenAiAttributes.INPUT_TOKENS, 300L);
        chat1.put(GenAiAttributes.OUTPUT_TOKENS, 47L);
        chat1.put(GenAiAttributes.CACHE_READ_INPUT_TOKENS, 271L);
        Map<String, Object> chat2 = attrs("chat", GenAiAttributes.REQUEST_MODEL, "granite4:3b",
                GenAiAttributes.PROVIDER_NAME, "openai", GenAiAttributes.FINISH_REASONS, "stop");
        chat2.put(GenAiAttributes.INPUT_TOKENS, 394L);
        chat2.put(GenAiAttributes.OUTPUT_TOKENS, 20L);
        chat2.put(GenAiAttributes.CACHE_READ_INPUT_TOKENS, 314L);
        Map<String, Object> tool1 = attrs("execute_tool", GenAiAttributes.TOOL_NAME, "order_status");
        Map<String, Object> tool2 = attrs("execute_tool", GenAiAttributes.TOOL_NAME, "stock_level");
        if (full) {
            chat2.put(GenAiAttributes.INPUT_MESSAGES, new GenAiMessages()
                    .text(GenAiMessages.SYSTEM, "You answer the customers of a web shop.")
                    .text(GenAiMessages.USER, QUESTION)
                    .text(GenAiMessages.ASSISTANT, null)
                    .toolCall("c1", "order_status", "{\"orderId\":\"ORD-1003\"}")
                    .toolCall("c2", "stock_level", "{\"sku\":\"CAMEL-CAP\"}")
                    .toolResult("c1", "Order ORD-1003 for customer C-134 in US is pending with 3 line(s)")
                    .toolResult("c2", "0 CAMEL-CAP in stock")
                    .toJson(8192));
            chat2.put(GenAiAttributes.OUTPUT_MESSAGES, new GenAiMessages()
                    .text(GenAiMessages.ASSISTANT,
                            "Your order ORD-1003 is still pending and the Camel cap is currently out of stock.")
                    .finishReason("stop")
                    .toJson(8192));
            tool1.put(GenAiAttributes.TOOL_CALL_ARGUMENTS, "{\"orderId\":\"ORD-1003\"}");
            tool1.put(GenAiAttributes.TOOL_CALL_RESULT, "Order ORD-1003 for customer C-134 in US is pending with 3 line(s)");
            tool2.put(GenAiAttributes.TOOL_CALL_ARGUMENTS, "{\"sku\":\"CAMEL-CAP\"}");
            tool2.put(GenAiAttributes.TOOL_CALL_RESULT, "0 CAMEL-CAP in stock");
        }
        spans.add(span(id + "-chat1", id, "customer-questions", startMs + 3, 615, chat1));
        spans.add(span(id + "-tool1", id, "customer-questions", startMs + 620, 4, tool1));
        spans.add(span(id + "-tool2", id, "customer-questions", startMs + 625, 2, tool2));
        spans.add(span(id + "-chat2", id, "customer-questions", startMs + 630, 378, chat2));
        return spans;
    }

    private static Map<String, Object> attrs(String operation, String... keyValues) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(GenAiAttributes.OPERATION_NAME, operation);
        for (int i = 0; i < keyValues.length; i += 2) {
            attrs.put(keyValues[i], keyValues[i + 1]);
        }
        return attrs;
    }

    private static SpanEntry span(
            String spanId, String parentSpanId, String routeId, long startMs, long ms, Map<String, Object> attributes) {
        return new SpanEntry(
                "trace-1", spanId, parentSpanId, spanId, "INTERNAL", "OK", startMs * 1_000_000L,
                (startMs + ms) * 1_000_000L, ms, routeId, null, "camel", attributes);
    }
}
