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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel ask with a question: the model answers, or calls tools until it answers, against a local server that plays
 * Ollama (no running integration). The answer is the only output on stdout.
 */
class AskLoopTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path home;

    private Path previousHome;
    private FakeLlmServer server;

    @BeforeEach
    void start() throws Exception {
        // no running integrations
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        server = new FakeLlmServer("unused");
    }

    @AfterEach
    void stop() {
        server.close();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void theAnswerIsTheOutput() throws Exception {
        server.reply("/api/chat", 200, answer("The routes are fine"));

        assertThat(command().doCall()).isZero();

        assertThat(printer.getStdout()).isEqualTo("The routes are fine");
        assertThat(printer.getStderr()).isEqualTo("Using test (ollama, core tools) to answer your question...");
        JsonArray messages = (JsonArray) server.lastRequest().get("messages");
        assertThat(((JsonObject) messages.get(1)).getString("content")).isEqualTo("what routes are running?");
    }

    @Test
    void theToolsTheModelCallsAreRunAndTheirResultsGiven() throws Exception {
        server.reply("/api/chat", 200, toolCall("no_such_tool"), answer("Done"));

        assertThat(command("--show-tools").doCall()).isZero();

        assertThat(printer.getStdout()).isEqualTo("Done");
        assertThat(printer.getStderr()).contains("[tool] no_such_tool({\"name\":\"orders\"})",
                "[result] Unknown tool: no_such_tool");
        // the second question has the call and its result
        JsonArray messages = (JsonArray) server.lastRequest().get("messages");
        JsonObject result = (JsonObject) messages.get(messages.size() - 1);
        assertThat(result.getString("role")).isEqualTo("tool");
        assertThat(result.getString("content")).isEqualTo("Unknown tool: no_such_tool");
    }

    @Test
    void aFailedCallOfTheLlmIsAFailure() throws Exception {
        server.reply("/api/chat", 500, "{\"error\":\"model runner has crashed\"}");

        assertThat(command().doCall()).isEqualTo(1);

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("LLM returned status: 500", "model runner has crashed",
                "Failed to get response from LLM");
    }

    @Test
    void noAnswerWithinTheMaximumIterationsIsAFailure() throws Exception {
        server.reply("/api/chat", 200, toolCall("no_such_tool"));

        assertThat(command("--max-iterations=2").doCall()).isEqualTo(1);

        assertThat(printer.getStderr()).contains("Reached maximum iterations (2) without a final answer.");
    }

    @Test
    void theStatsOnStderr() throws Exception {
        server.reply("/api/chat", 200, answer("The routes are fine"));

        assertThat(command("--show-stats").doCall()).isZero();

        assertThat(printer.getStdout()).isEqualTo("The routes are fine");
        assertThat(printer.getStderr()).contains("10 input / 5 output / 15 total tokens");
    }

    private Ask command(String... args) {
        Ask command = new Ask(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, Stream.concat(
                Stream.of("what routes are running?", "--url=" + server.url(), "--api-type=ollama", "--model=test"),
                Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    // as Ollama streams them: the answer in two parts, and the end with the token counts
    private static String answer(String text) {
        int half = text.length() / 2;
        return String.join("\n",
                chunk(new JsonObject(Map.of("role", "assistant", "content", text.substring(0, half))), false),
                chunk(new JsonObject(Map.of("role", "assistant", "content", text.substring(half))), false),
                chunk(new JsonObject(Map.of("role", "assistant", "content", "")), true), "");
    }

    private static String toolCall(String tool) {
        JsonObject function = new JsonObject(Map.of("name", tool, "arguments", new JsonObject(Map.of("name", "orders"))));
        JsonObject message = new JsonObject(
                Map.of("role", "assistant", "content", "",
                        "tool_calls", new JsonArray(List.of(new JsonObject(Map.of("function", function))))));
        return String.join("\n", chunk(message, false),
                chunk(new JsonObject(Map.of("role", "assistant", "content", "")), true), "");
    }

    private static String chunk(JsonObject message, boolean done) {
        JsonObject chunk = new JsonObject();
        chunk.put("message", message);
        chunk.put("done", done);
        if (done) {
            chunk.put("done_reason", "stop");
            chunk.put("prompt_eval_count", 10);
            chunk.put("eval_count", 5);
        }
        return chunk.toJson();
    }
}
