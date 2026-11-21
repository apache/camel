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
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.mcp.GenAiTraceReport.Conversation;
import org.apache.camel.dsl.jbang.core.commands.mcp.GenAiTraceReport.GenAi;
import org.apache.camel.dsl.jbang.core.commands.mcp.GenAiTraceReport.Message;
import org.apache.camel.dsl.jbang.core.commands.mcp.GenAiTraceReport.Run;
import org.apache.camel.dsl.jbang.core.commands.mcp.GenAiTraceReport.RunStep;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * CAMEL-27312: the GenAI part of camel_runtime_ai_trace and camel_runtime_ai_conversation, from answers shaped as the
 * genai dev console of camel-ai-observability gives them.
 */
class GenAiTraceReportTest {

    @Test
    void summaryComesFirstAndFitsInWhatAChatHistoryKeeps() {
        String summary = GenAiTraceReport.summary(console("full", run("7", "support")), "*");

        assertThat(summary).isEqualTo("GenAI level=full: 1 agent run, 2 model calls (granite4:3b), 1 tool call; "
                                      + "runs newest first. camel_runtime_ai_conversation with a runId shows the "
                                      + "messages of a run.");
        assertThat(summary.length()).isLessThan(300);
    }

    @Test
    void agentRunHasItsQuestionAndItsStepsInOrder() {
        GenAi genAi = GenAiTraceReport.genAi(console("full", run("7", "support")), "*");

        assertThat(genAi.level()).isEqualTo("full");
        assertThat(genAi.modelCalls()).singleElement().satisfies(model -> {
            assertThat(model.model()).isEqualTo("granite4:3b");
            assertThat(model.calls()).isEqualTo(2);
            assertThat(model.cachedTokens()).isEqualTo(270);
            assertThat(model.avgMs()).isEqualTo(130);
            assertThat(model.failed()).isNull();
        });
        Run run = genAi.agentRuns().get(0);
        assertThat(run.runId()).isEqualTo("7");
        assertThat(run.question()).isEqualTo("Is the Camel cap in stock?");
        assertThat(run.answer()).isEqualTo("No, it is sold out.");
        assertThat(run.agent()).isEqualTo("support");
        assertThat(run.start()).isEqualTo("2026-11-21T10:00:00Z");
        assertThat(run.running()).isNull();
        assertThat(run.inputTokens()).isEqualTo(700);
        assertThat(run.cachedTokens()).isEqualTo(270);
        assertThat(run.steps()).extracting(RunStep::step).containsExactly("model call", "tool call", "model call");
        RunStep tool = run.steps().get(1);
        assertThat(tool.name()).isEqualTo("execute_tool lookupStock");
        assertThat(tool.arguments()).isEqualTo("{\"sku\":\"CAMEL-CAP\"}");
        assertThat(tool.result()).hasSize(GenAiTraceReport.MAX_STEP_TEXT).endsWith("...");
        assertThat(run.steps().get(0).finishReason()).isEqualTo("tool_calls");
        assertThat(run.steps().get(2).cachedTokens()).isNull();
        assertThat(genAi.moreRuns()).isNull();
    }

    @Test
    void newestFiveRunsAreShown() {
        JsonObject[] runs = new JsonObject[7];
        for (int i = 0; i < runs.length; i++) {
            runs[i] = detailed(run(String.valueOf(100 - i), "support"));
        }

        GenAi genAi = GenAiTraceReport.genAi(console("detailed", runs), "*");

        assertThat(genAi.agentRuns()).extracting(Run::runId).containsExactly("100", "99", "98", "97", "96");
        assertThat(genAi.moreRuns()).isEqualTo("+2 more runs");
        // at level detailed the console records no content: no question, no answer, no tool arguments or results
        assertThat(genAi.level()).isEqualTo("detailed");
        assertThat(genAi.agentRuns().get(0).question()).isNull();
        assertThat(genAi.agentRuns().get(0).answer()).isNull();
        assertThat(genAi.agentRuns().get(0).steps().get(1).arguments()).isNull();
        assertThat(genAi.agentRuns().get(0).steps().get(1).result()).isNull();
    }

    @Test
    void summarySaysWhatToDoAtLevelBasicOrWithoutTheConsole() {
        JsonObject basic = console("basic");
        basic.put("modelCalls", 3L);
        assertThat(GenAiTraceReport.summary(basic, "*")).isEqualTo(
                "GenAI level=basic: 3 model calls (granite4:3b). Agent runs and tool calls are not traced: set "
                                                                   + "camel.aiObservability.level=detailed.");

        JsonObject absent = new JsonObject(Map.of("enabled", false));
        assertThat(GenAiTraceReport.summary(absent, "*")).isEqualTo(GenAiTraceReport.NO_CONSOLE);
        assertThat(GenAiTraceReport.summary(null, "*")).isEqualTo(GenAiTraceReport.NO_CONSOLE);
        assertThat(GenAiTraceReport.genAi(absent, "*")).isNull();

        JsonObject off = console("detailed");
        off.put("recording", false);
        off.put("modelCalls", 0L);
        off.put("agentRuns", 0L);
        assertThat(GenAiTraceReport.summary(off, "*")).isEqualTo(GenAiTraceReport.NOT_RECORDING);
        assertThat(GenAiTraceReport.genAi(off, "*")).isNull();
    }

    @Test
    void routeFilterKeepsTheRunsOfThatRoute() {
        JsonObject console = console("detailed", run("9", "billing"), run("8", "support"));

        assertThat(GenAiTraceReport.genAi(console, "support").agentRuns()).extracting(Run::runId).containsExactly("8");
        assertThat(GenAiTraceReport.summary(console, "support"))
                .contains("(all routes; the runs below are of route support)");
    }

    @Test
    void conversationListsTheMessagesOfTheRunInOrder() {
        Conversation conversation = GenAiTraceReport.conversation(answer(run("7", "support")), "7");

        assertThat(conversation.level()).isEqualTo("full");
        assertThat(conversation.agent()).isEqualTo("support");
        assertThat(conversation.messages()).extracting(Message::role, Message::type).containsExactly(
                tuple("system", "text"), tuple("user", "text"), tuple("assistant", "tool call"),
                tuple("tool", "tool result"), tuple("assistant", "text"));
        assertThat(conversation.messages().get(2).tool()).isEqualTo("lookupStock");
        assertThat(conversation.messages().get(2).text()).isEqualTo("{\"sku\":\"CAMEL-CAP\"}");
        assertThat(conversation.messages().get(4).finishReason()).isEqualTo("stop");
        assertThat(conversation.summary()).startsWith("GenAI level=full: 5 message parts of run 7.");
    }

    @Test
    void conversationOfARunWithItsInputOnTheRun() {
        // as camel-openai records it: the input of the run on the run itself
        JsonObject run = run("7", "support");
        content(run).put("inputMessages", messages(
                message("system", text("You answer the customers.")), message("user", text("Where is my order?"))));
        content(steps(run).get(0)).remove("inputMessages");

        Conversation conversation = GenAiTraceReport.conversation(answer(run), "7");

        assertThat(conversation.messages()).extracting(Message::text)
                .startsWith("You answer the customers.", "Where is my order?");
    }

    @Test
    void conversationIsCapped() {
        JsonObject run = run("7", "support");
        JsonArray output = new JsonArray();
        for (int i = 0; i < 10; i++) {
            output.add(message("assistant", text("x".repeat(2000))));
        }
        content(steps(run).get(2)).put("outputMessages", output);

        Conversation conversation = GenAiTraceReport.conversation(answer(run), "7");

        int total = conversation.messages().stream().mapToInt(m -> m.text() != null ? m.text().length() : 0).sum();
        assertThat(total).isLessThanOrEqualTo(GenAiTraceReport.MAX_CONVERSATION);
        assertThat(conversation.messages())
                .allSatisfy(m -> assertThat(m.text()).hasSizeLessThanOrEqualTo(GenAiTraceReport.MAX_MESSAGE));
        assertThat(conversation.cut()).isEqualTo("4 more message parts not shown (6000 characters in all)");
    }

    @Test
    void conversationOfARunWithoutMessagesSaysTheLevel() {
        JsonObject run = run("7", "support");
        run.remove("content");
        steps(run).forEach(step -> step.remove("content"));
        JsonObject answer = answer(run);
        answer.put("level", "detailed");

        Conversation conversation = GenAiTraceReport.conversation(answer, "7");

        assertThat(conversation.messages()).isNull();
        assertThat(conversation.summary()).isEqualTo("GenAI level=detailed: no messages for run 7. The messages are "
                                                     + "recorded at camel.aiObservability.level=full only.");
        assertThat(GenAiTraceReport.conversation(new JsonObject(Map.of("level", "full", "error", "No call")), "1"))
                .isNull();
    }

    @Test
    void theAnswerOfTheConsoleIsReadAsTheGenAiActionWritesIt() throws Exception {
        // the action output file is JSON text, read back into maps and lists
        JsonObject console = (JsonObject) Jsoner.deserialize(console("full", run("7", "support")).toJson());
        JsonObject answer = (JsonObject) Jsoner.deserialize(answer(run("7", "support")).toJson());

        assertThat(GenAiTraceReport.genAi(console, "*").agentRuns().get(0).steps()).hasSize(3);
        assertThat(GenAiTraceReport.conversation(answer, "7").messages()).hasSize(5);
    }

    // ---- the answers of the genai console ----

    private static JsonObject console(String level, JsonObject... calls) {
        JsonObject console = new JsonObject();
        console.put("recording", true);
        console.put("level", level);
        console.put("modelCalls", 2L * calls.length);
        console.put("agentRuns", (long) calls.length);
        console.put("toolCalls", (long) calls.length);
        console.put("errors", 0L);
        JsonObject model = new JsonObject();
        model.put("provider", "ollama");
        model.put("model", "granite4:3b");
        model.put("calls", 2L);
        model.put("errors", 0L);
        model.put("inputTokens", 700L);
        model.put("outputTokens", 60L);
        model.put("cachedInputTokens", 270L);
        model.put("durationMs", 260L);
        console.put("models", new JsonArray(List.of(model)));
        console.put("calls", new JsonArray(List.of((Object[]) calls)));
        console.put("moreCalls", 0L);
        return console;
    }

    private static JsonObject answer(JsonObject call) {
        JsonObject answer = new JsonObject();
        answer.put("level", "full");
        answer.put("call", call);
        return answer;
    }

    private static JsonObject run(String id, String routeId) {
        JsonObject run = call(id, null, "invoke_agent", "invoke_agent support", 300);
        run.put("agentName", "support");
        run.put("model", "granite4:3b");
        run.put("routeId", routeId);
        run.put("exchangeId", "EX-" + id);
        run.put("timestamp", 1795255200000L);
        run.put("modelCalls", 2);
        run.put("toolCalls", 1);
        run.put("inputTokens", 700L);
        run.put("outputTokens", 60L);
        run.put("cachedInputTokens", 270L);
        run.put("question", "Is the Camel cap in stock?");
        run.put("answer", "No, it is sold out.");

        JsonObject first = call(id + "1", id, "chat", "chat granite4:3b", 120);
        first.put("inputTokens", 300L);
        first.put("cachedInputTokens", 270L);
        first.put("finishReason", "tool_calls");
        content(first).put("systemInstructions", new JsonArray(List.of(text("You answer the customers."))));
        content(first).put("inputMessages", messages(message("user", text("Is the Camel cap in stock?"))));
        JsonObject toolCall = new JsonObject();
        toolCall.put("type", "tool_call");
        toolCall.put("name", "lookupStock");
        toolCall.put("arguments", new JsonObject(Map.of("sku", "CAMEL-CAP")));
        // a model gives an empty text beside its tool calls
        JsonObject toolCallMessage = message("assistant", text(""));
        JsonArray parts = toolCallMessage.getCollection("parts");
        parts.add(toolCall);
        content(first).put("outputMessages", messages(toolCallMessage));

        JsonObject tool = call(id + "2", id, "execute_tool", "execute_tool lookupStock", 5);
        tool.put("toolName", "lookupStock");
        content(tool).put("toolArguments", "{\"sku\":\"CAMEL-CAP\"}");
        content(tool).put("toolResult", "0 CAMEL-CAP in stock " + "x".repeat(300));

        JsonObject second = call(id + "3", id, "chat", "chat granite4:3b", 140);
        second.put("inputTokens", 400L);
        second.put("finishReason", "stop");
        JsonObject reply = message("assistant", text("No, it is sold out."));
        reply.put("finish_reason", "stop");
        content(second).put("inputMessages", messages(message("user", text("Is the Camel cap in stock?"))));
        content(second).put("outputMessages", messages(reply));

        run.put("steps", new JsonArray(List.of(first, tool, second)));
        return run;
    }

    /** A run as the console answers at level detailed: without its question, answer and content. */
    private static JsonObject detailed(JsonObject run) {
        run.remove("question");
        run.remove("answer");
        run.remove("content");
        steps(run).forEach(step -> step.remove("content"));
        return run;
    }

    private static JsonObject call(String id, String parentId, String operation, String name, long ms) {
        JsonObject call = new JsonObject();
        call.put("id", id);
        if (parentId != null) {
            call.put("parentId", parentId);
        }
        call.put("operation", operation);
        call.put("name", name);
        call.put("running", false);
        call.put("durationMs", ms);
        call.put("content", new JsonObject());
        return call;
    }

    private static List<JsonObject> steps(JsonObject run) {
        List<JsonObject> steps = new ArrayList<>();
        JsonArray array = run.getCollection("steps");
        for (Object step : array) {
            steps.add((JsonObject) step);
        }
        return steps;
    }

    private static JsonObject content(JsonObject call) {
        return call.getMap("content");
    }

    private static JsonArray messages(JsonObject... messages) {
        return new JsonArray(List.of((Object[]) messages));
    }

    private static JsonObject message(String role, JsonObject part) {
        JsonObject message = new JsonObject();
        message.put("role", role);
        message.put("parts", new JsonArray(List.of(part)));
        return message;
    }

    private static JsonObject text(String text) {
        JsonObject part = new JsonObject();
        part.put("type", "text");
        part.put("content", text);
        return part;
    }
}
