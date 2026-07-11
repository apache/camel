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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared LLM HTTP client supporting Ollama, OpenAI-compatible, and Anthropic (including Vertex AI) APIs.
 */
public class LlmClient {

    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_ANTHROPIC_URL = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String VERTEX_ANTHROPIC_VERSION = "vertex-2023-10-16";
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-6";
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    // Pre-4.6 Claude models require a @date suffix on Vertex AI
    private static final Map<String, String> VERTEX_MODEL_MAP = Map.of(
            "claude-sonnet-4-5", "claude-sonnet-4-5@20250929",
            "claude-opus-4-5", "claude-opus-4-5@20251101",
            "claude-haiku-4-5", "claude-haiku-4-5@20251001");

    public enum ApiType {
        ollama,
        openai,
        anthropic
    }

    // -- Unified abstractions for tool-calling across API formats --

    public record ToolDef(String name, String description, JsonObject parameters) {
    }

    public record ToolCall(String id, String name, JsonObject arguments) {
    }

    public record ToolResult(String toolCallId, String content) {
    }

    public record Message(String role, String content, List<ToolCall> toolCalls, List<ToolResult> toolResults) {

        public static Message user(String text) {
            return new Message("user", text, null, null);
        }

        public static Message assistantWithToolCalls(String text, List<ToolCall> calls) {
            return new Message("assistant", text, calls, null);
        }

        public static Message toolResults(List<ToolResult> results) {
            return new Message("tool", null, null, results);
        }
    }

    public record TokenUsage(int inputTokens, int outputTokens, int totalTokens) {
        public static final TokenUsage EMPTY = new TokenUsage(0, 0, 0);

        public TokenUsage add(TokenUsage other) {
            return new TokenUsage(
                    inputTokens + other.inputTokens,
                    outputTokens + other.outputTokens,
                    totalTokens + other.totalTokens);
        }
    }

    public record ChatResponse(String text, List<ToolCall> toolCalls, String stopReason, boolean streamed,
            TokenUsage usage) {
    }

    public static String formatTokens(int tokens) {
        if (tokens >= 1000) {
            double k = tokens / 1000.0;
            if (k == (int) k) {
                return (int) k + "k";
            }
            return String.format(java.util.Locale.ROOT, "%.1fk", k);
        }
        return String.valueOf(tokens);
    }

    // -- Configuration --

    ApiType apiType;
    String url;
    String apiKey;
    String model;
    int timeout;
    double temperature;
    boolean stream;
    int maxTokens;
    boolean verbose;
    Printer printer = new Printer() {
        @Override
        public void println() {
        }

        @Override
        public void println(String line) {
        }

        @Override
        public void print(String output) {
        }

        @Override
        public void printf(String format, Object... args) {
        }
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    // Vertex AI specific
    private String vertexRegion;
    private String vertexProjectId;

    public String model() {
        return model;
    }

    public ApiType apiType() {
        return apiType;
    }

    // -- Builder --

    public static LlmClient create() {
        return new LlmClient();
    }

    public LlmClient withApiType(ApiType apiType) {
        this.apiType = apiType;
        return this;
    }

    public LlmClient withUrl(String url) {
        this.url = url;
        return this;
    }

    public LlmClient withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public LlmClient withModel(String model) {
        this.model = model;
        return this;
    }

    public LlmClient withTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public LlmClient withTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    public LlmClient withStream(boolean stream) {
        this.stream = stream;
        return this;
    }

    public LlmClient withMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public LlmClient withVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public LlmClient withPrinter(Printer printer) {
        this.printer = printer;
        return this;
    }

    // -- Auto-detection --

    public boolean detectEndpoint() {
        boolean found;
        if (tryExplicitUrl()) {
            found = true;
        } else if (apiType != null) {
            found = switch (apiType) {
                case anthropic -> tryAnthropicOrVertex();
                case openai -> tryOpenAi();
                case ollama -> tryInfraOllama() || tryDefaultOllama();
            };
        } else {
            // auto-detect priority: anthropic → vertex → openai → ollama
            found = tryAnthropicApiKey()
                    || tryVertexAi()
                    || tryOpenAi()
                    || tryInfraOllama()
                    || tryDefaultOllama();
        }
        if (found && apiType == ApiType.ollama && "llama3.2".equals(model)) {
            resolveOllamaModel();
        }
        return found;
    }

    String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        if (apiType == ApiType.anthropic) {
            String key = System.getenv("ANTHROPIC_API_KEY");
            if (key != null && !key.isBlank()) {
                apiKey = key;
                return key;
            }
            // Vertex AI uses gcloud token, not API key
            return null;
        }
        return Stream.of("OPENAI_API_KEY", "LLM_API_KEY")
                .map(System::getenv)
                .filter(k -> k != null && !k.isBlank())
                .findFirst()
                .map(k -> {
                    apiKey = k;
                    return k;
                })
                .orElse(null);
    }

    // -- Simple generate (for explain) --

    String generate(String systemPrompt, String userPrompt) {
        return switch (apiType) {
            case ollama -> generateOllama(systemPrompt, userPrompt);
            case openai -> generateOpenAi(systemPrompt, userPrompt);
            case anthropic -> generateAnthropic(systemPrompt, userPrompt);
        };
    }

    // -- Chat with tools (for ask) --

    public ChatResponse chatWithTools(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
        return switch (apiType) {
            case ollama -> chatOllamaFormat(systemPrompt, messages, tools);
            case openai -> chatOpenAiFormat(systemPrompt, messages, tools);
            case anthropic -> chatAnthropicFormat(systemPrompt, messages, tools);
        };
    }

    // -- Model listing --

    /**
     * Lists model identifiers available from the configured provider: Ollama's {@code /api/tags}, or the
     * OpenAI-compatible/Anthropic {@code /v1/models}. Vertex AI has no equivalent listing endpoint (models come from
     * the Vertex AI model garden, not the Anthropic API surface), so it always returns an empty list. Best-effort: any
     * failure (unreachable endpoint, non-200 response, unexpected JSON shape) yields an empty list rather than
     * throwing, since this only feeds the informational {@code /model} listing in the AI panel.
     */
    public List<String> listModels() {
        if (apiType == null || url == null || url.isBlank()) {
            return List.of();
        }
        return switch (apiType) {
            case ollama -> listOllamaModels();
            case openai -> listOpenAiModels();
            case anthropic -> isVertexAi() ? List.of() : listAnthropicModels();
        };
    }

    private List<String> listOllamaModels() {
        JsonObject response = sendGetRequest(url + "/api/tags", Map.of());
        return extractStringList(response, "models", "name");
    }

    private List<String> listOpenAiModels() {
        Map<String, String> headers = new HashMap<>();
        String key = resolveApiKey();
        if (key != null) {
            headers.put("Authorization", "Bearer " + key);
        }
        JsonObject response = sendGetRequest(normalizeOpenAiModelsUrl(url), headers);
        return extractStringList(response, "data", "id");
    }

    private List<String> listAnthropicModels() {
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        JsonObject response = sendGetRequest(base + "/v1/models", buildAnthropicHeaders());
        return extractStringList(response, "data", "id");
    }

    private static List<String> extractStringList(JsonObject response, String arrayField, String nameField) {
        if (response == null) {
            return List.of();
        }
        if (!(response.get(arrayField) instanceof JsonArray items)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object obj : items) {
            if (obj instanceof JsonObject item) {
                String name = item.getString(nameField);
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    String normalizeOpenAiModelsUrl(String endpoint) {
        String u = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (!u.endsWith("/v1/models")) {
            u = u.endsWith("/v1") ? u : u + "/v1";
            u = u + "/models";
        }
        return u;
    }

    private JsonObject sendGetRequest(String requestUrl, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT_SECONDS))
                    .GET();
            for (Map.Entry<String, String> h : headers.entrySet()) {
                builder.header(h.getKey(), h.getValue());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return (JsonObject) Jsoner.deserialize(response.body());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Ollama generate ----

    private String generateOllama(String systemPrompt, String userPrompt) {
        JsonObject request = new JsonObject();
        request.put("model", model);
        request.put("prompt", userPrompt);
        request.put("system", systemPrompt);
        request.put("stream", stream);

        JsonObject options = new JsonObject();
        options.put("temperature", temperature);
        request.put("options", options);

        if (stream) {
            return sendStreamingRequest(url + "/api/generate", request, null, "response");
        }
        JsonObject response = sendRequest(url + "/api/generate", request, null);
        return response != null ? response.getString("response") : null;
    }

    // ---- OpenAI-compatible generate ----

    private String generateOpenAi(String systemPrompt, String userPrompt) {
        JsonArray messages = new JsonArray();
        messages.add(createOpenAiMessage("system", systemPrompt));
        messages.add(createOpenAiMessage("user", userPrompt));

        JsonObject request = new JsonObject();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", temperature);

        String resolvedKey = resolveApiKey();
        String apiUrl = normalizeOpenAiUrl(url);

        JsonObject response = sendRequest(apiUrl, request, resolvedKey);
        return extractOpenAiContent(response);
    }

    // ---- Anthropic generate ----

    private String generateAnthropic(String systemPrompt, String userPrompt) {
        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.put("role", "user");
        JsonArray content = new JsonArray();
        JsonObject textBlock = new JsonObject();
        textBlock.put("type", "text");
        textBlock.put("text", userPrompt);
        content.add(textBlock);
        userMsg.put("content", content);
        messages.add(userMsg);

        JsonObject request = buildAnthropicRequest(systemPrompt, messages, null);

        String apiUrl = resolveAnthropicUrl();
        Map<String, String> headers = buildAnthropicHeaders();

        if (stream) {
            return sendAnthropicStreamingRequest(apiUrl, request, headers);
        }
        JsonObject response = sendRequestWithHeaders(apiUrl, request, headers);
        return extractAnthropicTextContent(response);
    }

    // ---- OpenAI/Ollama chat with tools ----

    private ChatResponse chatOpenAiFormat(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
        JsonArray jsonMessages = buildChatMessages(systemPrompt, messages, false);
        JsonArray jsonTools = buildOpenAiStyleTools(tools);

        JsonObject request = new JsonObject();
        request.put("model", model);
        request.put("messages", jsonMessages);
        request.put("temperature", temperature);
        if (jsonTools != null) {
            request.put("tools", jsonTools);
        }

        String apiUrl = normalizeOpenAiUrl(url);
        String resolvedKey = resolveApiKey();

        JsonObject response = sendRequest(apiUrl, request, resolvedKey);
        return parseOpenAiChatResponse(response);
    }

    // ---- Ollama native chat with tools ----

    private ChatResponse chatOllamaFormat(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
        JsonArray jsonMessages = buildChatMessages(systemPrompt, messages, true);
        JsonArray jsonTools = buildOpenAiStyleTools(tools);

        JsonObject request = new JsonObject();
        request.put("model", model);
        request.put("messages", jsonMessages);
        request.put("think", false);
        if (jsonTools != null) {
            request.put("tools", jsonTools);
        }

        JsonObject options = new JsonObject();
        options.put("temperature", temperature);
        request.put("options", options);

        if (stream) {
            request.put("stream", true);
            return streamOllamaChatRequest(url + "/api/chat", request);
        }
        request.put("stream", false);
        JsonObject response = sendRequest(url + "/api/chat", request, null);
        return parseOllamaChatResponse(response);
    }

    private ChatResponse streamOllamaChatRequest(String requestUrl, JsonObject body) {
        try {
            String jsonBody = body.toJson();
            if (verbose) {
                printer.println("[verbose] POST (streaming) " + requestUrl);
                printer.println("[verbose] Request: " + truncateVerbose(jsonBody));
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<Stream<String>> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                handleErrorStatus(response.statusCode(), errorBody);
                return new ChatResponse(null, List.of(), "error", false, TokenUsage.EMPTY);
            }

            StringBuilder fullText = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();
            String[] doneReasonHolder = { null };
            int[] tokenHolder = { 0, 0 };

            response.body().forEach(line -> {
                if (line.isBlank()) {
                    return;
                }
                if (verbose) {
                    printer.println("[verbose] Chunk: " + truncateVerbose(line));
                }
                try {
                    JsonObject chunk = (JsonObject) Jsoner.deserialize(line);
                    JsonObject message = (JsonObject) chunk.get("message");
                    if (message == null) {
                        return;
                    }

                    String content = message.getString("content");
                    if (content != null && !content.isEmpty()) {
                        printer.print(content);
                        fullText.append(content);
                    }

                    JsonArray rawToolCalls = (JsonArray) message.get("tool_calls");
                    if (rawToolCalls != null) {
                        int idx = toolCalls.size();
                        for (Object obj : rawToolCalls) {
                            JsonObject tc = (JsonObject) obj;
                            JsonObject function = (JsonObject) tc.get("function");
                            String id = tc.containsKey("id") ? tc.getString("id") : "call_" + idx;
                            String name = function.getString("name");
                            JsonObject args;
                            Object argsObj = function.get("arguments");
                            if (argsObj instanceof JsonObject jo) {
                                args = jo;
                            } else if (argsObj instanceof String s) {
                                try {
                                    args = (JsonObject) Jsoner.deserialize(s);
                                } catch (Exception e) {
                                    args = new JsonObject();
                                }
                            } else {
                                args = new JsonObject();
                            }
                            toolCalls.add(new ToolCall(id, name, args));
                            idx++;
                        }
                    }

                    if (Boolean.TRUE.equals(chunk.get("done"))) {
                        doneReasonHolder[0] = chunk.getString("done_reason");
                        tokenHolder[0] = getIntValue(chunk, "prompt_eval_count");
                        tokenHolder[1] = getIntValue(chunk, "eval_count");
                    }
                } catch (Exception e) {
                    // skip malformed chunks
                }
            });

            if (fullText.length() > 0) {
                printer.println();
            }

            String text = fullText.length() > 0 ? fullText.toString() : null;
            String stopReason
                    = !toolCalls.isEmpty() ? "tool_calls" : (doneReasonHolder[0] != null ? doneReasonHolder[0] : "stop");

            TokenUsage usage = new TokenUsage(tokenHolder[0], tokenHolder[1], tokenHolder[0] + tokenHolder[1]);
            if (verbose) {
                printer.println("[verbose] Streamed Ollama: text=" + (text != null ? truncateVerbose(text) : "null")
                                + ", toolCalls=" + toolCalls.size() + ", doneReason=" + doneReasonHolder[0]
                                + ", tokens=" + usage.totalTokens());
            }
            return new ChatResponse(text, toolCalls, stopReason, true, usage);
        } catch (HttpTimeoutException e) {
            printer.println("\nRequest timed out after " + timeout + " seconds.");
            return new ChatResponse(null, List.of(), "error", false, TokenUsage.EMPTY);
        } catch (Exception e) {
            printer.println("\nError during streaming: " + e.getMessage());
            return new ChatResponse(null, List.of(), "error", false, TokenUsage.EMPTY);
        }
    }

    // ---- Shared message/tool builders ----

    private JsonArray buildChatMessages(String systemPrompt, List<Message> messages, boolean ollamaNative) {
        JsonArray jsonMessages = new JsonArray();
        jsonMessages.add(createOpenAiMessage("system", systemPrompt));

        for (Message msg : messages) {
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                JsonObject assistantMsg = new JsonObject();
                assistantMsg.put("role", "assistant");
                if (msg.content() != null) {
                    assistantMsg.put("content", msg.content());
                }
                JsonArray toolCalls = new JsonArray();
                for (ToolCall tc : msg.toolCalls()) {
                    JsonObject call = new JsonObject();
                    if (!ollamaNative) {
                        call.put("id", tc.id());
                        call.put("type", "function");
                    }
                    JsonObject function = new JsonObject();
                    function.put("name", tc.name());
                    function.put("arguments", ollamaNative ? tc.arguments() : tc.arguments().toJson());
                    call.put("function", function);
                    toolCalls.add(call);
                }
                assistantMsg.put("tool_calls", toolCalls);
                jsonMessages.add(assistantMsg);
            } else if (msg.toolResults() != null && !msg.toolResults().isEmpty()) {
                for (ToolResult tr : msg.toolResults()) {
                    JsonObject toolMsg = new JsonObject();
                    toolMsg.put("role", "tool");
                    if (!ollamaNative) {
                        toolMsg.put("tool_call_id", tr.toolCallId());
                    }
                    toolMsg.put("content", tr.content());
                    jsonMessages.add(toolMsg);
                }
            } else {
                jsonMessages.add(createOpenAiMessage(msg.role(), msg.content()));
            }
        }
        return jsonMessages;
    }

    private JsonArray buildOpenAiStyleTools(List<ToolDef> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        JsonArray jsonTools = new JsonArray();
        for (ToolDef tool : tools) {
            JsonObject toolObj = new JsonObject();
            toolObj.put("type", "function");
            JsonObject function = new JsonObject();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameters());
            toolObj.put("function", function);
            jsonTools.add(toolObj);
        }
        return jsonTools;
    }

    // ---- Anthropic chat with tools ----

    private ChatResponse chatAnthropicFormat(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
        JsonArray jsonMessages = new JsonArray();

        for (Message msg : messages) {
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                // assistant message with tool_use blocks
                JsonObject assistantMsg = new JsonObject();
                assistantMsg.put("role", "assistant");
                JsonArray content = new JsonArray();
                if (msg.content() != null) {
                    JsonObject textBlock = new JsonObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", msg.content());
                    content.add(textBlock);
                }
                for (ToolCall tc : msg.toolCalls()) {
                    JsonObject toolUse = new JsonObject();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", tc.id());
                    toolUse.put("name", tc.name());
                    toolUse.put("input", tc.arguments());
                    content.add(toolUse);
                }
                assistantMsg.put("content", content);
                jsonMessages.add(assistantMsg);
            } else if (msg.toolResults() != null && !msg.toolResults().isEmpty()) {
                // tool results as user message with tool_result blocks
                JsonObject userMsg = new JsonObject();
                userMsg.put("role", "user");
                JsonArray content = new JsonArray();
                for (ToolResult tr : msg.toolResults()) {
                    JsonObject toolResult = new JsonObject();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", tr.toolCallId());
                    toolResult.put("content", tr.content());
                    content.add(toolResult);
                }
                userMsg.put("content", content);
                jsonMessages.add(userMsg);
            } else {
                JsonObject m = new JsonObject();
                m.put("role", msg.role());
                JsonArray content = new JsonArray();
                JsonObject textBlock = new JsonObject();
                textBlock.put("type", "text");
                textBlock.put("text", msg.content());
                content.add(textBlock);
                m.put("content", content);
                jsonMessages.add(m);
            }
        }

        JsonArray jsonTools = null;
        if (tools != null && !tools.isEmpty()) {
            jsonTools = new JsonArray();
            for (ToolDef tool : tools) {
                JsonObject toolObj = new JsonObject();
                toolObj.put("name", tool.name());
                toolObj.put("description", tool.description());
                toolObj.put("input_schema", tool.parameters());
                jsonTools.add(toolObj);
            }
        }

        JsonObject request = buildAnthropicRequest(systemPrompt, jsonMessages, jsonTools);
        String apiUrl = resolveAnthropicUrl();
        Map<String, String> headers = buildAnthropicHeaders();

        JsonObject response = sendRequestWithHeaders(apiUrl, request, headers);
        return parseAnthropicChatResponse(response);
    }

    // ---- Anthropic helpers ----

    private JsonObject buildAnthropicRequest(String systemPrompt, JsonArray messages, JsonArray tools) {
        JsonObject request = new JsonObject();
        if (isVertexAi()) {
            // Vertex AI: model is in the URL, version goes in body
            request.put("anthropic_version", VERTEX_ANTHROPIC_VERSION);
        } else {
            // Direct Anthropic API: model goes in body
            request.put("model", model);
        }
        request.put("max_tokens", maxTokens > 0 ? maxTokens : 4096);
        if (systemPrompt != null) {
            request.put("system", systemPrompt);
        }
        request.put("messages", messages);
        request.put("temperature", temperature);
        if (tools != null && !tools.isEmpty()) {
            request.put("tools", tools);
        }
        return request;
    }

    private String resolveAnthropicUrl() {
        if (isVertexAi()) {
            String vertexModel = resolveVertexModel(model);
            return String.format(
                    "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/anthropic/models/%s:rawPredict",
                    vertexRegion, vertexProjectId, vertexRegion, vertexModel);
        }
        String base = url != null ? url : DEFAULT_ANTHROPIC_URL;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/v1/messages";
    }

    private Map<String, String> buildAnthropicHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (isVertexAi()) {
            String token = getGcloudAccessToken();
            if (token != null) {
                headers.put("Authorization", "Bearer " + token);
            }
        } else {
            String key = resolveApiKey();
            if (key != null) {
                headers.put("x-api-key", key);
            }
            headers.put("anthropic-version", ANTHROPIC_VERSION);
        }
        return headers;
    }

    private boolean isVertexAi() {
        return vertexRegion != null && vertexProjectId != null;
    }

    static String resolveVertexModel(String model) {
        if (model == null || model.contains("@")) {
            return model;
        }
        return VERTEX_MODEL_MAP.getOrDefault(model, model);
    }

    private String getGcloudAccessToken() {
        try {
            ProcessBuilder pb = new ProcessBuilder("gcloud", "auth", "print-access-token");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String token = reader.readLine();
                int exit = p.waitFor();
                if (exit == 0 && token != null && !token.isBlank()) {
                    return token.strip();
                }
            }
        } catch (Exception e) {
            // gcloud not available
        }
        return null;
    }

    // ---- Response parsing ----

    private ChatResponse parseOpenAiChatResponse(JsonObject response) {
        if (response == null) {
            if (verbose) {
                printer.println("[verbose] parseOpenAiChatResponse: response is null");
            }
            return new ChatResponse(null, List.of(), "error", false, TokenUsage.EMPTY);
        }
        TokenUsage usage = extractOpenAiUsage(response);
        JsonArray choices = (JsonArray) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            if (verbose) {
                printer.println("[verbose] parseOpenAiChatResponse: no choices in response. Keys: " + response.keySet());
            }
            return new ChatResponse(null, List.of(), "error", false, usage);
        }
        JsonObject firstChoice = (JsonObject) choices.get(0);
        String finishReason = firstChoice.getString("finish_reason");
        JsonObject message = (JsonObject) firstChoice.get("message");
        if (message == null) {
            return new ChatResponse(null, List.of(), finishReason, false, usage);
        }

        String content = message.getString("content");
        if (content == null || content.isBlank()) {
            String reasoning = message.getString("reasoning_content");
            if (reasoning == null || reasoning.isBlank()) {
                reasoning = message.getString("reasoning");
            }
            if (reasoning != null && !reasoning.isBlank()) {
                content = reasoning;
            }
        }
        JsonArray rawToolCalls = (JsonArray) message.get("tool_calls");
        List<ToolCall> toolCalls = new ArrayList<>();
        if (rawToolCalls != null) {
            for (Object obj : rawToolCalls) {
                JsonObject tc = (JsonObject) obj;
                JsonObject function = (JsonObject) tc.get("function");
                String id = tc.getString("id");
                String name = function.getString("name");
                JsonObject args;
                try {
                    args = (JsonObject) Jsoner.deserialize(function.getString("arguments"));
                } catch (Exception e) {
                    args = new JsonObject();
                }
                toolCalls.add(new ToolCall(id, name, args));
            }
        }
        if (verbose) {
            printer.println("[verbose] Parsed: text=" + (content != null ? truncateVerbose(content) : "null")
                            + ", toolCalls=" + toolCalls.size() + ", finishReason=" + finishReason);
        }
        return new ChatResponse(content, toolCalls, finishReason, false, usage);
    }

    private ChatResponse parseOllamaChatResponse(JsonObject response) {
        if (response == null) {
            if (verbose) {
                printer.println("[verbose] parseOllamaChatResponse: response is null");
            }
            return new ChatResponse(null, List.of(), "error", false, TokenUsage.EMPTY);
        }
        JsonObject message = (JsonObject) response.get("message");
        if (message == null) {
            if (verbose) {
                printer.println("[verbose] parseOllamaChatResponse: no message in response. Keys: " + response.keySet());
            }
            return new ChatResponse(null, List.of(), "error", false, TokenUsage.EMPTY);
        }

        String content = message.getString("content");
        String doneReason = response.getString("done_reason");

        JsonArray rawToolCalls = (JsonArray) message.get("tool_calls");
        List<ToolCall> toolCalls = new ArrayList<>();
        if (rawToolCalls != null) {
            int idx = 0;
            for (Object obj : rawToolCalls) {
                JsonObject tc = (JsonObject) obj;
                JsonObject function = (JsonObject) tc.get("function");
                String id = tc.containsKey("id") ? tc.getString("id") : "call_" + idx;
                String name = function.getString("name");
                JsonObject args;
                Object argsObj = function.get("arguments");
                if (argsObj instanceof JsonObject jo) {
                    args = jo;
                } else if (argsObj instanceof String s) {
                    try {
                        args = (JsonObject) Jsoner.deserialize(s);
                    } catch (Exception e) {
                        args = new JsonObject();
                    }
                } else {
                    args = new JsonObject();
                }
                toolCalls.add(new ToolCall(id, name, args));
                idx++;
            }
        }

        String stopReason = !toolCalls.isEmpty() ? "tool_calls" : (doneReason != null ? doneReason : "stop");

        int inputTokens = getIntValue(response, "prompt_eval_count");
        int outputTokens = getIntValue(response, "eval_count");
        TokenUsage usage = new TokenUsage(inputTokens, outputTokens, inputTokens + outputTokens);

        if (verbose) {
            printer.println("[verbose] Parsed Ollama: text=" + (content != null ? truncateVerbose(content) : "null")
                            + ", toolCalls=" + toolCalls.size() + ", doneReason=" + doneReason
                            + ", tokens=" + usage.totalTokens());
        }
        return new ChatResponse(content, toolCalls, stopReason, false, usage);
    }

    private ChatResponse parseAnthropicChatResponse(JsonObject response) {
        if (response == null) {
            return new ChatResponse(null, List.of(), "error", false, TokenUsage.EMPTY);
        }
        String stopReason = response.getString("stop_reason");
        TokenUsage usage = extractAnthropicUsage(response);
        JsonArray contentBlocks = (JsonArray) response.get("content");
        if (contentBlocks == null) {
            return new ChatResponse(null, List.of(), stopReason, false, usage);
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (Object obj : contentBlocks) {
            JsonObject block = (JsonObject) obj;
            String type = block.getString("type");
            if ("text".equals(type)) {
                text.append(block.getString("text"));
            } else if ("tool_use".equals(type)) {
                String id = block.getString("id");
                String name = block.getString("name");
                JsonObject input = (JsonObject) block.get("input");
                toolCalls.add(new ToolCall(id, name, input != null ? input : new JsonObject()));
            }
        }
        String textContent = text.length() > 0 ? text.toString() : null;
        return new ChatResponse(textContent, toolCalls, stopReason, false, usage);
    }

    // ---- Token usage extraction ----

    private TokenUsage extractOpenAiUsage(JsonObject response) {
        JsonObject usage = (JsonObject) response.get("usage");
        if (usage == null) {
            return TokenUsage.EMPTY;
        }
        int prompt = getIntValue(usage, "prompt_tokens");
        int completion = getIntValue(usage, "completion_tokens");
        int total = getIntValue(usage, "total_tokens");
        if (total == 0) {
            total = prompt + completion;
        }
        return new TokenUsage(prompt, completion, total);
    }

    private TokenUsage extractAnthropicUsage(JsonObject response) {
        JsonObject usage = (JsonObject) response.get("usage");
        if (usage == null) {
            return TokenUsage.EMPTY;
        }
        int input = getIntValue(usage, "input_tokens");
        int output = getIntValue(usage, "output_tokens");
        return new TokenUsage(input, output, input + output);
    }

    private static int getIntValue(JsonObject obj, String key) {
        Object val = obj.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private String extractOpenAiContent(JsonObject response) {
        if (response == null) {
            return null;
        }
        JsonArray choices = (JsonArray) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JsonObject firstChoice = (JsonObject) choices.get(0);
        JsonObject message = (JsonObject) firstChoice.get("message");
        return message != null ? message.getString("content") : null;
    }

    private String extractAnthropicTextContent(JsonObject response) {
        if (response == null) {
            return null;
        }
        JsonArray contentBlocks = (JsonArray) response.get("content");
        if (contentBlocks == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object obj : contentBlocks) {
            JsonObject block = (JsonObject) obj;
            if ("text".equals(block.getString("type"))) {
                sb.append(block.getString("text"));
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ---- HTTP transport ----

    private JsonObject sendRequest(String requestUrl, JsonObject body, String authKey) {
        try {
            String jsonBody = body.toJson();
            if (verbose) {
                printer.println("[verbose] POST " + requestUrl);
                printer.println("[verbose] Request: " + truncateVerbose(jsonBody));
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (authKey != null && !authKey.isBlank()) {
                builder.header("Authorization", "Bearer " + authKey);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (verbose) {
                printer.println("[verbose] Response status: " + response.statusCode());
                printer.println("[verbose] Response: " + truncateVerbose(response.body()));
            }

            if (response.statusCode() == 200) {
                return (JsonObject) Jsoner.deserialize(response.body());
            }

            handleErrorStatus(response.statusCode(), response.body());
            return null;
        } catch (HttpTimeoutException e) {
            printer.println("Request timed out after " + timeout + " seconds.");
            return null;
        } catch (Exception e) {
            printer.println("Error calling LLM: " + e.getMessage());
            return null;
        }
    }

    private JsonObject sendRequestWithHeaders(String requestUrl, JsonObject body, Map<String, String> headers) {
        try {
            String jsonBody = body.toJson();
            if (verbose) {
                printer.println("[verbose] POST " + requestUrl);
                printer.println("[verbose] Request: " + truncateVerbose(jsonBody));
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            for (Map.Entry<String, String> h : headers.entrySet()) {
                builder.header(h.getKey(), h.getValue());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (verbose) {
                printer.println("[verbose] Response status: " + response.statusCode());
                printer.println("[verbose] Response: " + truncateVerbose(response.body()));
            }

            if (response.statusCode() == 200) {
                return (JsonObject) Jsoner.deserialize(response.body());
            }

            handleErrorStatus(response.statusCode(), response.body());
            return null;
        } catch (HttpTimeoutException e) {
            printer.println("Request timed out after " + timeout + " seconds.");
            return null;
        } catch (Exception e) {
            printer.println("Error calling LLM: " + e.getMessage());
            return null;
        }
    }

    String sendStreamingRequest(String requestUrl, JsonObject body, String authKey, String textField) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJson()));

            if (authKey != null && !authKey.isBlank()) {
                builder.header("Authorization", "Bearer " + authKey);
            }

            HttpResponse<Stream<String>> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                handleErrorStatus(response.statusCode(), errorBody);
                return null;
            }

            StringBuilder fullResponse = new StringBuilder();
            response.body().forEach(line -> {
                if (line.isBlank()) {
                    return;
                }
                try {
                    JsonObject chunk = (JsonObject) Jsoner.deserialize(line);
                    String text = chunk.getString(textField);
                    if (text != null) {
                        printer.print(text);
                        fullResponse.append(text);
                    }
                } catch (Exception e) {
                    // skip malformed chunks
                }
            });
            printer.println();
            return fullResponse.toString();
        } catch (HttpTimeoutException e) {
            printer.println("\nRequest timed out after " + timeout + " seconds.");
            return null;
        } catch (Exception e) {
            printer.println("\nError during streaming: " + e.getMessage());
            return null;
        }
    }

    private String sendAnthropicStreamingRequest(String requestUrl, JsonObject body, Map<String, String> headers) {
        body.put("stream", true);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJson()));

            for (Map.Entry<String, String> h : headers.entrySet()) {
                builder.header(h.getKey(), h.getValue());
            }

            HttpResponse<Stream<String>> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                handleErrorStatus(response.statusCode(), errorBody);
                return null;
            }

            StringBuilder fullResponse = new StringBuilder();
            response.body().forEach(line -> {
                if (line.isBlank() || !line.startsWith("data: ")) {
                    return;
                }
                String data = line.substring(6);
                if ("[DONE]".equals(data)) {
                    return;
                }
                try {
                    JsonObject event = (JsonObject) Jsoner.deserialize(data);
                    String type = event.getString("type");
                    if ("content_block_delta".equals(type)) {
                        JsonObject delta = (JsonObject) event.get("delta");
                        if (delta != null && "text_delta".equals(delta.getString("type"))) {
                            String text = delta.getString("text");
                            if (text != null) {
                                printer.print(text);
                                fullResponse.append(text);
                            }
                        }
                    }
                } catch (Exception e) {
                    // skip malformed events
                }
            });
            printer.println();
            return fullResponse.toString();
        } catch (HttpTimeoutException e) {
            printer.println("\nRequest timed out after " + timeout + " seconds.");
            return null;
        } catch (Exception e) {
            printer.println("\nError during streaming: " + e.getMessage());
            return null;
        }
    }

    // ---- Endpoint detection helpers ----

    // Order matters: /api/tags is checked before /v1/models, matching the original priority.
    private static final List<Map.Entry<String, ApiType>> EXPLICIT_URL_HEALTH_CHECK_SUFFIXES = List.of(
            Map.entry("/api/tags", ApiType.ollama),
            Map.entry("/v1/models", ApiType.openai));

    private boolean tryExplicitUrl() {
        if (url == null || url.isBlank()) {
            return false;
        }
        for (Map.Entry<String, ApiType> check : EXPLICIT_URL_HEALTH_CHECK_SUFFIXES) {
            if (tryHealthCheck(url + check.getKey())) {
                if (apiType == null) {
                    apiType = check.getValue();
                }
                return true;
            }
        }
        if (tryHealthCheck(url)) {
            if (apiType == null) {
                apiType = inferApiTypeFromUrlHost();
            }
            return true;
        }
        return false;
    }

    ApiType inferApiTypeFromUrlHost() {
        String lower = url.toLowerCase();
        if (lower.contains("anthropic")) {
            return ApiType.anthropic;
        }
        if (lower.contains("11434") || lower.contains("ollama")) {
            return ApiType.ollama;
        }
        return ApiType.openai;
    }

    private boolean tryAnthropicApiKey() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key != null && !key.isBlank()) {
            apiType = ApiType.anthropic;
            apiKey = key;
            url = DEFAULT_ANTHROPIC_URL;
            if (model == null || "llama3.2".equals(model)) {
                model = DEFAULT_ANTHROPIC_MODEL;
            }
            return true;
        }
        return false;
    }

    private boolean tryVertexAi() {
        String region = System.getenv("CLOUD_ML_REGION");
        String project = System.getenv("ANTHROPIC_VERTEX_PROJECT_ID");
        if (region != null && !region.isBlank() && project != null && !project.isBlank()) {
            apiType = ApiType.anthropic;
            vertexRegion = region;
            vertexProjectId = project;
            if (model == null || "llama3.2".equals(model)) {
                model = DEFAULT_ANTHROPIC_MODEL;
            }
            return true;
        }
        return false;
    }

    private boolean tryAnthropicOrVertex() {
        if (url != null && !url.isBlank()) {
            return isEndpointReachable(url);
        }
        return tryAnthropicApiKey() || tryVertexAi();
    }

    private boolean tryOpenAi() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getenv("LLM_API_KEY");
        }
        if (key != null && !key.isBlank()) {
            apiType = ApiType.openai;
            apiKey = key;
            if (url == null || url.isBlank()) {
                url = "https://api.openai.com";
            }
            return true;
        }
        return false;
    }

    private boolean tryInfraOllama() {
        try {
            Map<Long, Path> pids = findOllamaPids();
            for (Path pidFile : pids.values()) {
                String baseUrl = readBaseUrlFromPidFile(pidFile);
                if (baseUrl != null && isEndpointReachable(baseUrl)) {
                    apiType = ApiType.ollama;
                    url = baseUrl;
                    return true;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private boolean tryDefaultOllama() {
        if (isEndpointReachable(DEFAULT_OLLAMA_URL)) {
            apiType = ApiType.ollama;
            url = DEFAULT_OLLAMA_URL;
            return true;
        }
        return false;
    }

    private void resolveOllamaModel() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/tags"))
                    .timeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT_SECONDS))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }
            JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
            JsonArray models = (JsonArray) json.get("models");
            if (models == null || models.isEmpty()) {
                return;
            }

            List<String> available = new ArrayList<>();
            for (Object obj : models) {
                JsonObject m = (JsonObject) obj;
                String name = m.getString("name");
                if (name != null) {
                    available.add(name);
                }
            }

            List<String> preferred
                    = List.of("qwen3.5", "qwen3", "nemotron-3-nano", "mistral-nemo",
                            "qwen2.5", "granite4.1", "llama3.1", "llama3.3", "mistral");
            for (String pref : preferred) {
                for (String avail : available) {
                    if (avail.equals(pref) || avail.startsWith(pref + ":")) {
                        model = avail;
                        printer.println("Auto-selected model: " + model + " (better tool-calling support)");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // best-effort, keep default
        }
    }

    boolean isEndpointReachable(String endpoint) {
        return tryHealthCheck(endpoint + "/api/tags")
                || tryHealthCheck(endpoint + "/v1/models")
                || tryHealthCheck(endpoint);
    }

    private boolean tryHealthCheck(String healthUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT_SECONDS))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<Long, Path> findOllamaPids() throws Exception {
        Map<Long, Path> pids = new HashMap<>();
        Path camelDir = CommandLineHelper.getCamelDir();
        if (!Files.exists(camelDir)) {
            return pids;
        }
        try (Stream<Path> fileStream = Files.list(camelDir)) {
            fileStream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("infra-ollama-") && name.endsWith(".json");
                    })
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String pidStr = name.substring(name.lastIndexOf("-") + 1, name.lastIndexOf('.'));
                        try {
                            pids.put(Long.valueOf(pidStr), p);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    });
        }
        return pids;
    }

    private String readBaseUrlFromPidFile(Path pidFile) throws Exception {
        String json = Files.readString(pidFile);
        JsonObject jo = (JsonObject) Jsoner.deserialize(json);
        return jo.getString("baseUrl");
    }

    // ---- URL helpers ----

    String normalizeOpenAiUrl(String endpoint) {
        String u = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (!u.endsWith("/v1/chat/completions")) {
            u = u.endsWith("/v1") ? u : u + "/v1";
            u = u + "/chat/completions";
        }
        return u;
    }

    // ---- Error handling ----

    private void handleErrorStatus(int statusCode, String body) {
        printer.println("LLM returned status: " + statusCode);
        switch (statusCode) {
            case 401 -> printer.println("Authentication failed. Check your API key.");
            case 404 -> {
                if (isVertexAi()) {
                    printer.println("Model not found. Check that the model identifier is valid for Vertex AI.");
                } else {
                    printer.println("Endpoint not found.");
                }
            }
            case 429 -> printer.println("Rate limit exceeded.");
            default -> {
            }
        }
        if (body != null && !body.isBlank()) {
            String errorMessage = extractErrorMessage(body);
            if (errorMessage != null) {
                printer.println(errorMessage);
            }
        }
    }

    static String extractErrorMessage(String body) {
        String trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            try {
                JsonObject json = (JsonObject) Jsoner.deserialize(trimmed);
                Object error = json.get("error");
                if (error instanceof JsonObject) {
                    String message = ((JsonObject) error).getString("message");
                    if (message != null && !message.isBlank()) {
                        return message;
                    }
                } else if (error instanceof String && !((String) error).isBlank()) {
                    return (String) error;
                }
                return trimmed;
            } catch (Exception e) {
                return trimmed;
            }
        }
        // Non-JSON response (e.g., HTML error page) — don't dump it
        return null;
    }

    // ---- OpenAI message helpers ----

    static JsonObject createOpenAiMessage(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private static String truncateVerbose(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 500 ? s.substring(0, 500) + "... (" + s.length() + " chars)" : s;
    }
}
