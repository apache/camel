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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sends the AI panel's real payload (system prompt, tool schemas, a user question) to a local Ollama and prints how
 * long the model spends on prompt processing and generation, cold and warm, in core and full tool mode. Skipped unless
 * {@code CAMEL_TUI_OLLAMA_BENCH} names the model to use, for example:
 *
 * <pre>
 * CAMEL_TUI_OLLAMA_BENCH=qwen3.6:35b-a3b mvn test -Dtest=AiPanelOllamaBenchmarkTest
 * </pre>
 *
 * {@code OLLAMA_HOST} overrides the server URL (default {@code http://localhost:11434}). The request mirrors what
 * {@code LlmClient} sends: {@code think=false}, {@code keep_alive=30m}, {@code num_ctx} as the client would set it. See
 * the module README for prerequisites and how to read the output.
 */
class AiPanelOllamaBenchmarkTest {

    private static final String[] QUESTIONS = {
            "[Monitoring timer-log (PID 74824)]\nwhat model is this",
            "[Monitoring timer-log (PID 74824)]\nwhat routes are running?",
            "[Monitoring kafka-demo (PID 80011)]\nany errors?" };

    record Sample(String label, long promptTokens, double promptEvalSeconds, double loadSeconds, long genTokens,
            double genSeconds, double wallSeconds, String toolCall) {

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "  %-24s prompt=%5d tok  prompt_eval=%5.1fs (%5.0f tok/s)  load=%4.1fs  gen=%3d tok in %4.1fs  wall=%5.1fs%s",
                    label, promptTokens, promptEvalSeconds, promptTokens / Math.max(promptEvalSeconds, 0.001),
                    loadSeconds, genTokens, genSeconds, wallSeconds, toolCall != null ? "  tool=" + toolCall : "");
        }
    }

    @Test
    void benchmarkRealPromptAgainstLocalOllama() throws Exception {
        String model = System.getenv("CAMEL_TUI_OLLAMA_BENCH");
        assumeTrue(model != null && !model.isBlank(), "set CAMEL_TUI_OLLAMA_BENCH=<model> to run the benchmark");
        String host = System.getenv("OLLAMA_HOST");
        String url = (host != null && !host.isBlank() ? host : "http://localhost:11434");
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        System.out.println("AI panel benchmark against " + url + " with " + model);
        for (String mode : List.of(AiPanel.TOOL_MODE_FULL, AiPanel.TOOL_MODE_CORE)) {
            AiPanel panel = new AiPanel();
            panel.setToolRegistryForTesting(new TuiToolRegistry(null));
            panel.setToolModeForTesting(mode);
            String system = panel.systemPromptForTesting();
            JsonArray tools = wireTools(panel.toolDefinitionsForTesting());
            System.out.println("== " + mode + ": " + tools.size() + " tools ==");
            String[] labels = { "1st (cold prefix)", "2nd (warm prefix)", "3rd (switched integr.)" };
            for (int i = 0; i < QUESTIONS.length; i++) {
                Sample sample = chat(http, url, model, system, tools, QUESTIONS[i], labels[i]);
                assertNotNull(sample);
                System.out.println(sample);
            }
        }
    }

    private static JsonArray wireTools(List<LlmClient.ToolDef> defs) {
        JsonArray tools = new JsonArray();
        for (LlmClient.ToolDef def : defs) {
            JsonObject function = new JsonObject();
            function.put("name", def.name());
            function.put("description", def.description());
            function.put("parameters", def.parameters());
            JsonObject tool = new JsonObject();
            tool.put("type", "function");
            tool.put("function", function);
            tools.add(tool);
        }
        return tools;
    }

    private static Sample chat(
            HttpClient http, String url, String model, String system, JsonArray tools, String question, String label)
            throws Exception {
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", question));
        JsonObject options = new JsonObject();
        options.put("temperature", 0.3);
        options.put("num_ctx", 32768);
        JsonObject request = new JsonObject();
        request.put("model", model);
        request.put("messages", messages);
        request.put("tools", tools);
        request.put("stream", false);
        request.put("think", false);
        request.put("keep_alive", "30m");
        request.put("options", options);

        long start = System.nanoTime();
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url + "/api/chat"))
                        .timeout(Duration.ofMinutes(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(request.toJson(), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        double wall = (System.nanoTime() - start) / 1e9;
        JsonObject body = (JsonObject) Jsoner.deserialize(response.body());
        String toolCall = null;
        JsonObject message = (JsonObject) body.get("message");
        if (message != null && message.get("tool_calls") instanceof JsonArray calls && !calls.isEmpty()) {
            JsonObject first = (JsonObject) calls.get(0);
            toolCall = (String) ((JsonObject) first.get("function")).get("name");
        }
        return new Sample(
                label,
                number(body, "prompt_eval_count"),
                number(body, "prompt_eval_duration") / 1e9,
                number(body, "load_duration") / 1e9,
                number(body, "eval_count"),
                number(body, "eval_duration") / 1e9,
                wall, toolCall);
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static long number(JsonObject body, String key) {
        return body.get(key) instanceof Number n ? n.longValue() : 0L;
    }
}
