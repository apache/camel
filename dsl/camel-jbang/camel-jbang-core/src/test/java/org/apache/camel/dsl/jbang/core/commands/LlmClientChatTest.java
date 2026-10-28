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

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.LlmClient.ApiType;
import org.apache.camel.dsl.jbang.core.commands.LlmClient.ChatResponse;
import org.apache.camel.dsl.jbang.core.commands.LlmClient.Message;
import org.apache.camel.dsl.jbang.core.commands.LlmClient.ToolCall;
import org.apache.camel.dsl.jbang.core.commands.LlmClient.ToolDef;
import org.apache.camel.dsl.jbang.core.commands.LlmClient.ToolResult;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chat with tools of camel ask, against a local server that plays each provider: the request in the format of the
 * provider (Ollama, OpenAI-compatible, Anthropic), the answer with its tool calls and token usage, and the errors.
 */
class LlmClientChatTest {

    private static final List<ToolDef> TOOLS = List.of(new ToolDef(
            "get_routes", "The routes of the integration",
            new JsonObject(Map.of("type", "object", "properties", new JsonObject()))));

    private final StringPrinter printer = new StringPrinter();
    private FakeLlmServer server;

    @BeforeEach
    void start() throws IOException {
        server = new FakeLlmServer("unused");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void ollamaChatWithToolCalls() {
        server.reply("/api/chat", 200, """
                {"message":{"role":"assistant","content":"Let me look",
                 "tool_calls":[{"function":{"name":"get_routes","arguments":{"name":"orders"}}}]},
                 "done":true,"done_reason":"stop","prompt_eval_count":100,"eval_count":20,
                 "prompt_eval_cached_count":80,"eval_duration":2000000000}""");

        ChatResponse response = client(ApiType.ollama).chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        assertThat(response.text()).isEqualTo("Let me look");
        assertThat(response.stopReason()).isEqualTo("tool_calls");
        assertThat(response.streamed()).isFalse();
        ToolCall call = response.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call_0");
        assertThat(call.name()).isEqualTo("get_routes");
        assertThat(call.arguments().getString("name")).isEqualTo("orders");
        assertThat(response.usage().inputTokens()).isEqualTo(100);
        assertThat(response.usage().outputTokens()).isEqualTo(20);
        assertThat(response.usage().totalTokens()).isEqualTo(120);
        assertThat(response.usage().cachedTokens()).isEqualTo(80);
        assertThat(response.usage().generationMillis()).isEqualTo(2000);

        JsonObject request = server.lastRequest();
        assertThat(request.getString("model")).isEqualTo("test");
        assertThat(request.getBoolean("stream")).isFalse();
        assertThat(request.getBoolean("think")).isFalse();
        assertThat(request.getString("keep_alive")).isEqualTo("30m");
        JsonObject options = request.getMap("options");
        assertThat(options.getInteger("num_ctx")).isPositive();
        JsonArray tools = (JsonArray) request.get("tools");
        JsonObject function = ((JsonObject) tools.get(0)).getMap("function");
        assertThat(function.getString("name")).isEqualTo("get_routes");
    }

    @Test
    void ollamaChatStreamed() {
        server.reply("/api/chat", 200, String.join("\n",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"The routes \"},\"done\":false}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"are fine\"},\"done\":false}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":"
                                                                                                  + "{\"name\":\"get_routes\",\"arguments\":\"{\\\"name\\\":\\\"orders\\\"}\"}}]},"
                                                                                                  + "\"done\":false}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
                                                                                                                       + "\"prompt_eval_count\":10,\"eval_count\":5}",
                ""));

        ChatResponse response = client(ApiType.ollama).withStream(true)
                .chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        // the answer is printed as it comes
        assertThat(printer.getStdout()).isEqualTo("The routes are fine");
        assertThat(response.text()).isEqualTo("The routes are fine");
        assertThat(response.streamed()).isTrue();
        assertThat(response.stopReason()).isEqualTo("tool_calls");
        // the arguments as text are read as JSON
        assertThat(response.toolCalls().get(0).arguments().getString("name")).isEqualTo("orders");
        assertThat(response.usage().totalTokens()).isEqualTo(15);
        assertThat(server.lastRequest().getBoolean("stream")).isTrue();
    }

    @Test
    void openAiChatWithToolCalls() {
        server.reply("/v1/chat/completions", 200, """
                {"choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                 "tool_calls":[{"id":"call_abc","type":"function",
                  "function":{"name":"get_routes","arguments":"{\\"name\\":\\"orders\\"}"}}]}}],
                 "usage":{"prompt_tokens":50,"completion_tokens":10,"total_tokens":60,
                  "prompt_tokens_details":{"cached_tokens":40}}}""");

        ChatResponse response = client(ApiType.openai).withApiKey("sk-secret")
                .chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        assertThat(response.stopReason()).isEqualTo("tool_calls");
        ToolCall call = response.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call_abc");
        assertThat(call.arguments().getString("name")).isEqualTo("orders");
        assertThat(response.usage().totalTokens()).isEqualTo(60);
        assertThat(response.usage().cachedTokens()).isEqualTo(40);
        assertThat(server.lastHeaders().getFirst("Authorization")).isEqualTo("Bearer sk-secret");
        assertThat(server.lastRequest().getString("model")).isEqualTo("test");
    }

    @Test
    void openAiToolCallArgumentsAsAnObject() {
        // some OpenAI-compatible servers send the arguments as an object, as Ollama does
        server.reply("/v1/chat/completions", 200, """
                {"choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant",
                 "tool_calls":[{"id":"call_abc","type":"function",
                  "function":{"name":"get_routes","arguments":{"name":"orders"}}}]}}]}""");

        ChatResponse response = client(ApiType.openai).chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        assertThat(response.toolCalls().get(0).arguments().getString("name")).isEqualTo("orders");
    }

    @Test
    void openAiReasoningWhenThereIsNoContent() {
        server.reply("/v1/chat/completions", 200, """
                {"choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"",
                 "reasoning_content":"The routes are fine"}}]}""");

        ChatResponse response = client(ApiType.openai).chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        assertThat(response.text()).isEqualTo("The routes are fine");
        assertThat(response.stopReason()).isEqualTo("stop");
    }

    @Test
    void anthropicChatWithToolUse() {
        server.reply("/v1/messages", 200, """
                {"stop_reason":"tool_use","content":[{"type":"text","text":"Let me look"},
                 {"type":"tool_use","id":"toolu_1","name":"get_routes","input":{"name":"orders"}}],
                 "usage":{"input_tokens":30,"output_tokens":7,"cache_read_input_tokens":20}}""");

        ChatResponse response = client(ApiType.anthropic).withApiKey("sk-ant")
                .chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        assertThat(response.text()).isEqualTo("Let me look");
        assertThat(response.stopReason()).isEqualTo("tool_use");
        ToolCall call = response.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("toolu_1");
        assertThat(call.arguments().getString("name")).isEqualTo("orders");
        assertThat(response.usage().totalTokens()).isEqualTo(37);
        assertThat(response.usage().cachedTokens()).isEqualTo(20);

        assertThat(server.lastHeaders().getFirst("x-api-key")).isEqualTo("sk-ant");
        assertThat(server.lastHeaders().getFirst("anthropic-version")).isEqualTo("2023-06-01");
        JsonObject request = server.lastRequest();
        // the system prompt apart from the messages, and the tools with their input schema
        assertThat(request.getString("system")).isEqualTo("You help");
        assertThat(request.getInteger("max_tokens")).isEqualTo(4096);
        JsonObject tool = (JsonObject) ((JsonArray) request.get("tools")).get(0);
        assertThat(tool.getString("name")).isEqualTo("get_routes");
        JsonObject schema = tool.getMap("input_schema");
        assertThat(schema).containsEntry("type", "object");
    }

    @Test
    void theConversationInTheFormatOfOpenAi() {
        server.reply("/v1/chat/completions", 200, "{\"choices\":[{\"message\":{\"content\":\"Done\"}}]}");

        client(ApiType.openai).chatWithTools("You help", conversation(), TOOLS);

        JsonArray messages = (JsonArray) server.lastRequest().get("messages");
        assertThat(messages).hasSize(4);
        assertThat(((JsonObject) messages.get(0)).getString("role")).isEqualTo("system");
        JsonObject call = (JsonObject) ((JsonArray) ((JsonObject) messages.get(2)).get("tool_calls")).get(0);
        assertThat(call.getString("id")).isEqualTo("call_1");
        assertThat(call.getString("type")).isEqualTo("function");
        // the arguments as text
        JsonObject function = call.getMap("function");
        assertThat(function.getString("arguments")).isEqualTo("{\"name\":\"orders\"}");
        JsonObject result = (JsonObject) messages.get(3);
        assertThat(result.getString("role")).isEqualTo("tool");
        assertThat(result.getString("tool_call_id")).isEqualTo("call_1");
        assertThat(result.getString("content")).isEqualTo("orders: Started");
    }

    @Test
    void theConversationInTheFormatOfOllama() {
        server.reply("/api/chat", 200, "{\"message\":{\"content\":\"Done\"},\"done\":true}");

        client(ApiType.ollama).chatWithTools("You help", conversation(), TOOLS);

        JsonArray messages = (JsonArray) server.lastRequest().get("messages");
        JsonObject call = (JsonObject) ((JsonArray) ((JsonObject) messages.get(2)).get("tool_calls")).get(0);
        assertThat(call.containsKey("id")).isFalse();
        // the arguments as an object
        JsonObject function = call.getMap("function");
        JsonObject arguments = function.getMap("arguments");
        assertThat(arguments).containsEntry("name", "orders");
        JsonObject result = (JsonObject) messages.get(3);
        assertThat(result.containsKey("tool_call_id")).isFalse();
        assertThat(result.getString("content")).isEqualTo("orders: Started");
    }

    @Test
    void theConversationInTheFormatOfAnthropic() {
        server.reply("/v1/messages", 200, "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"Done\"}]}");

        client(ApiType.anthropic).chatWithTools("You help", conversation(), TOOLS);

        JsonArray messages = (JsonArray) server.lastRequest().get("messages");
        // no system message: it is apart
        assertThat(messages).hasSize(3);
        JsonArray assistant = (JsonArray) ((JsonObject) messages.get(1)).get("content");
        JsonObject toolUse = (JsonObject) assistant.get(1);
        assertThat(toolUse.getString("type")).isEqualTo("tool_use");
        assertThat(toolUse.getString("id")).isEqualTo("call_1");
        JsonObject input = toolUse.getMap("input");
        assertThat(input).containsEntry("name", "orders");
        JsonObject user = (JsonObject) messages.get(2);
        assertThat(user.getString("role")).isEqualTo("user");
        JsonObject toolResult = (JsonObject) ((JsonArray) user.get("content")).get(0);
        assertThat(toolResult.getString("type")).isEqualTo("tool_result");
        assertThat(toolResult.getString("tool_use_id")).isEqualTo("call_1");
    }

    @Test
    void anErrorStatusIsReportedOnStderr() {
        server.reply("/v1/chat/completions", 401, "{\"error\":{\"message\":\"Incorrect API key provided\"}}");

        ChatResponse response = client(ApiType.openai).chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        assertThat(response.stopReason()).isEqualTo("error");
        assertThat(response.text()).isNull();
        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("LLM returned status: 401", "Authentication failed. Check your API key.",
                "Incorrect API key provided");
    }

    @Test
    void aStreamedErrorStatusIsReportedOnStderr() {
        server.reply("/api/chat", 404, "{\"error\":\"model 'test' not found\"}");

        ChatResponse response = client(ApiType.ollama).withStream(true)
                .chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        assertThat(response.stopReason()).isEqualTo("error");
        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("LLM returned status: 404", "Endpoint not found.",
                "model 'test' not found");
    }

    @Test
    void aStreamThatCannotConnectIsReportedOnStderr() throws IOException {
        String closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = "http://127.0.0.1:" + socket.getLocalPort();
        }

        ChatResponse response = client(ApiType.ollama).withUrl(closed).withStream(true)
                .chatWithTools("You help", List.of(Message.user("Hi")), TOOLS);

        assertThat(response.stopReason()).isEqualTo("error");
        // nothing that looks like an answer
        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).startsWith("ERROR: Calling the LLM failed: ").doesNotContain("null");
    }

    @Test
    void aGenerateStreamThatCannotConnectIsReportedOnStderr() throws IOException {
        String closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = "http://127.0.0.1:" + socket.getLocalPort();
        }

        String answer = client(ApiType.ollama).withUrl(closed).withStream(true).generate("You help", "Hi");

        assertThat(answer).isNull();
        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).startsWith("ERROR: Calling the LLM failed: ").doesNotContain("null");
    }

    @Test
    void theMessageOfAnError() {
        assertThat(LlmClient.extractErrorMessage("{\"error\":{\"message\":\"Bad model\"}}")).isEqualTo("Bad model");
        assertThat(LlmClient.extractErrorMessage("{\"error\":\"model not found\"}")).isEqualTo("model not found");
        assertThat(LlmClient.extractErrorMessage("{\"detail\":\"Too many\"}")).isEqualTo("{\"detail\":\"Too many\"}");
        // an HTML page is not shown
        assertThat(LlmClient.extractErrorMessage("<html><body>Bad Gateway</body></html>")).isNull();
    }

    private LlmClient client(ApiType apiType) {
        return LlmClient.create().withApiType(apiType).withUrl(server.url()).withModel("test").withTimeout(10)
                .withPrinter(printer);
    }

    // the question, the tool the model called, and its result
    private static List<Message> conversation() {
        ToolCall call = new ToolCall("call_1", "get_routes", new JsonObject(Map.of("name", "orders")));
        return List.of(Message.user("How are the routes?"),
                Message.assistantWithToolCalls("Let me look", List.of(call)),
                Message.toolResults(List.of(new ToolResult("call_1", "orders: Started"))));
    }
}
