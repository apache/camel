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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27313: camel get genai shows the answer of the genai dev console of an integration.
 */
class CamelGenAiActionTest extends CamelCommandBaseTestSupport {

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        useTestHomeDir();
    }

    @Test
    void theModelsAndTheRunsWithTheirSteps() throws Exception {
        JsonObject answer = console("detailed", run(false));
        answer.put("moreCalls", 3L);
        Testable command = command(answer);

        assertThat(command.doWatchCall()).isEqualTo(CliExitCode.OK);

        String output = printer.getOutput();
        assertThat(output)
                .contains("GenAI level: detailed   Model calls: 2   Agent runs: 1   Tool calls: 1   Failed: 0")
                .contains("Tokens: in 338 (cached 195), out 28")
                .contains("PROVIDER", "openai", "llama3.2")
                .contains("invoke_agent support-agent", "  chat llama3.2", "  execute_tool lookupOrder", "tool_calls")
                .contains("+3 older calls")
                .doesNotContain("QUESTION");
        assertThat(output.indexOf("execute_tool")).isBetween(output.indexOf("chat llama3.2"),
                output.lastIndexOf("chat llama3.2"));
        assertThat(command.sent).containsEntry("action", "genai").containsEntry("limit", "20").doesNotContainKey("content");
    }

    @Test
    void contentAddsTheQuestionOfEachRun() throws Exception {
        Testable command = command(console("full", run(true)));
        command.content = true;

        command.doWatchCall();

        assertThat(printer.getOutput()).contains("QUESTION", "What is the status of order A-17?");
        assertThat(command.sent).containsEntry("content", "true");
    }

    @Test
    void oneCallWithItsMessages() throws Exception {
        JsonObject answer = new JsonObject();
        answer.put("level", "full");
        answer.put("call", run(true));
        Testable command = command(answer);
        command.id = "5";

        assertThat(command.doWatchCall()).isEqualTo(CliExitCode.OK);

        assertThat(printer.getOutput())
                .contains("#5 invoke_agent support-agent   route: support-agent")
                .contains("system: You are a support agent.")
                .contains("question: What is the status of order A-17?")
                .contains("#6 chat llama3.2", "(tool_calls): tool call lookupOrder {\"orderId\":\"A-17\"}")
                .contains("#7 execute_tool lookupOrder", ": {\"orderId\":\"A-17\"} -> DELIVERED")
                .contains("answer: The order A-17 is delivered.")
                .doesNotContain("no messages");
        assertThat(command.sent).containsEntry("id", "5").doesNotContainKey("limit");
    }

    @Test
    void theSystemMessageMayBeInTheInputOfTheRun() throws Exception {
        // as camel-openai records it
        JsonObject run = run(true);
        JsonObject first = (JsonObject) run.<JsonArray> getCollection("steps").get(0);
        first.getMap("content").remove("systemInstructions");
        run.put("content", new JsonObject(
                Map.of("inputMessages", Jsoner.deserialize(
                        "[{\"role\":\"system\",\"parts\":[{\"type\":\"text\",\"content\":\"You answer orders.\"}]}]",
                        (JsonArray) null))));
        JsonObject answer = new JsonObject();
        answer.put("level", "full");
        answer.put("call", run);
        Testable command = command(answer);
        command.id = "5";

        command.doWatchCall();

        assertThat(printer.getOutput()).contains("system: You answer orders.");
    }

    @Test
    void oneCallWithoutMessagesSaysTheLevel() throws Exception {
        JsonObject answer = new JsonObject();
        answer.put("level", "detailed");
        answer.put("call", run(false));
        Testable command = command(answer);
        command.id = "5";

        command.doWatchCall();

        assertThat(printer.getOutput()).contains("no messages: they are recorded at camel.aiObservability.level=full");
    }

    @Test
    void aCallThatIsNoLongerKeptIsNotFound() throws Exception {
        JsonObject answer = new JsonObject();
        answer.put("level", "full");
        answer.put("error", "No GenAI call with id 99: it may no longer be kept");
        Testable command = command(answer);
        command.id = "99";

        assertThat(command.doWatchCall()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getOutput()).contains("No GenAI call with id 99");
    }

    @Test
    void anIntegrationWithoutTheConsoleSaysHowToGetIt() throws Exception {
        Testable command = command(new JsonObject(Map.of("enabled", false)));

        assertThat(command.doWatchCall()).isEqualTo(CliExitCode.OK);
        assertThat(printer.getOutput()).contains("test " + CamelGenAiAction.NO_CONSOLE);
    }

    @Test
    void jsonIsTheAnswerOfTheConsole() throws Exception {
        Testable command = command(console("detailed", run(false)));
        command.jsonOutput = true;

        command.doWatchCall();

        JsonObject json = Jsoner.deserialize(printer.getOutput(), (JsonObject) null);
        assertThat(json.getString("level")).isEqualTo("detailed");
        JsonArray calls = json.getCollection("calls");
        assertThat(calls).hasSize(1);
    }

    private Testable command(JsonObject answer) {
        return new CamelJBangMain().withPrinter(printer).command(new Testable(answer));
    }

    private static JsonObject console(String level, JsonObject... calls) {
        JsonObject model = new JsonObject();
        model.put("provider", "openai");
        model.put("model", "llama3.2");
        model.put("calls", 2L);
        model.put("errors", 0L);
        model.put("inputTokens", 338L);
        model.put("outputTokens", 28L);
        model.put("cachedInputTokens", 195L);
        model.put("durationMs", 380L);
        JsonObject answer = new JsonObject();
        answer.put("recording", true);
        answer.put("level", level);
        answer.put("modelCalls", 2L);
        answer.put("agentRuns", 1L);
        answer.put("toolCalls", 1L);
        answer.put("errors", 0L);
        answer.put("inputTokens", 338L);
        answer.put("outputTokens", 28L);
        answer.put("cachedInputTokens", 195L);
        answer.put("models", new JsonArray(List.of(model)));
        answer.put("calls", new JsonArray(List.of((Object[]) calls)));
        answer.put("moreCalls", 0L);
        return answer;
    }

    private static JsonObject run(boolean withContent) {
        JsonObject run = call("5", "invoke_agent", "invoke_agent support-agent", 384);
        run.put("routeId", "support-agent");
        run.put("timestamp", System.currentTimeMillis() - 5000);
        run.put("inputTokens", 338L);
        run.put("outputTokens", 28L);
        JsonObject first = call("6", "chat", "chat llama3.2", 230);
        first.put("inputTokens", 196L);
        first.put("outputTokens", 15L);
        first.put("finishReason", "tool_calls");
        JsonObject tool = call("7", "execute_tool", "execute_tool lookupOrder", 1);
        JsonObject second = call("8", "chat", "chat llama3.2", 151);
        second.put("inputTokens", 142L);
        second.put("outputTokens", 13L);
        second.put("finishReason", "stop");
        if (withContent) {
            run.put("question", "What is the status of order A-17?");
            run.put("answer", "The order A-17 is delivered.");
            first.put("content", new JsonObject(
                    Map.of(
                            "systemInstructions", "[{\"type\":\"text\",\"content\":\"You are a support agent.\"}]",
                            "outputMessages", Jsoner.deserialize(
                                    "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"\"},"
                                                                 + "{\"type\":\"tool_call\",\"name\":\"lookupOrder\","
                                                                 + "\"arguments\":{\"orderId\":\"A-17\"}}]}]",
                                    (JsonArray) null))));
            tool.put("content", new JsonObject(Map.of("toolArguments", "{\"orderId\":\"A-17\"}", "toolResult", "DELIVERED")));
        }
        run.put("steps", new JsonArray(List.of(first, tool, second)));
        return run;
    }

    private static JsonObject call(String id, String operation, String name, long ms) {
        JsonObject call = new JsonObject();
        call.put("id", id);
        call.put("operation", operation);
        call.put("name", name);
        call.put("running", false);
        call.put("durationMs", ms);
        return call;
    }

    /** Bypasses process discovery and the action file, and keeps the options sent with the action. */
    private static class Testable extends CamelGenAiAction {
        private final JsonObject answer;
        private final JsonObject sent = new JsonObject();

        Testable(JsonObject answer) {
            this.answer = answer;
        }

        @Override
        protected CamelProcess findOne(String name) {
            return new CamelProcess(12345L, "test", new JsonObject());
        }

        @Override
        protected JsonObject executeAction(CamelProcess process, String action, Consumer<JsonObject> options) {
            sent.put("action", action);
            if (options != null) {
                options.accept(sent);
            }
            return answer;
        }
    }
}
