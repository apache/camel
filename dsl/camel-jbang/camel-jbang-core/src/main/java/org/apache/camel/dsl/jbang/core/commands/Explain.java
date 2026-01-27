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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command to explain Camel routes using AI/LLM services.
 * <p>
 * Supports multiple LLM providers: Ollama, OpenAI, Azure OpenAI, vLLM, LM Studio, LocalAI, etc.
 */
@Command(name = "explain",
         description = "Explain what a Camel route does using AI/LLM",
         sortOptions = false, showDefaultValues = true)
public class Explain extends CamelCommand {

    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2";
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    private static final List<String> COMMON_COMPONENTS = Arrays.asList(
            "kafka", "http", "https", "file", "timer", "direct", "seda",
            "log", "mock", "rest", "rest-api", "sql", "jms", "activemq",
            "aws2-s3", "aws2-sqs", "aws2-sns", "aws2-kinesis",
            "azure-storage-blob", "azure-storage-queue",
            "google-pubsub", "google-storage",
            "mongodb", "couchdb", "cassandraql",
            "elasticsearch", "opensearch",
            "ftp", "sftp", "ftps",
            "smtp", "imap", "pop3",
            "websocket", "netty-http", "vertx-http",
            "bean", "process", "script");

    private static final List<String> COMMON_EIPS = Arrays.asList(
            "split", "aggregate", "filter", "choice", "when", "otherwise",
            "multicast", "recipientList", "routingSlip", "dynamicRouter",
            "circuitBreaker", "throttle", "delay", "resequence",
            "idempotentConsumer", "loadBalance", "saga",
            "onException", "doTry", "doCatch", "doFinally",
            "transform", "setBody", "setHeader", "removeHeader",
            "marshal", "unmarshal", "convertBodyTo",
            "enrich", "pollEnrich", "wireTap", "pipeline");

    enum ApiType {
        ollama((explain, prompts) -> explain.callOllama(prompts[0], prompts[1], prompts[2])),
        openai((explain, prompts) -> explain.callOpenAiCompatible(prompts[0], prompts[1], prompts[2], prompts[3]));

        private final BiFunction<Explain, String[], String> caller;

        ApiType(BiFunction<Explain, String[], String> caller) {
            this.caller = caller;
        }

        String call(Explain explain, String endpoint, String sysPrompt, String userPrompt, String apiKey) {
            return caller.apply(explain, new String[] { endpoint, sysPrompt, userPrompt, apiKey });
        }
    }

    @Parameters(description = "Route file(s) to explain", arity = "1..*")
    List<String> files;

    @Option(names = { "--url" },
            description = "LLM API endpoint URL. Auto-detected from 'camel infra' for Ollama if not specified.")
    String url;

    @Option(names = { "--api-type" },
            description = "API type: 'ollama' or 'openai' (OpenAI-compatible)",
            defaultValue = "ollama")
    ApiType apiType = ApiType.ollama;

    @Option(names = { "--api-key" },
            description = "API key for authentication. Also reads OPENAI_API_KEY or LLM_API_KEY env vars")
    String apiKey;

    @Option(names = { "--model" },
            description = "Model to use",
            defaultValue = DEFAULT_MODEL)
    String model = DEFAULT_MODEL;

    @Option(names = { "--verbose", "-v" },
            description = "Include detailed technical information")
    boolean verbose;

    @Option(names = { "--format" },
            description = "Output format: text, markdown",
            defaultValue = "text")
    String format = "text";

    @Option(names = { "--timeout" },
            description = "Timeout in seconds for LLM response",
            defaultValue = "120")
    int timeout = 120;

    @Option(names = { "--catalog-context" },
            description = "Include Camel Catalog descriptions in the prompt")
    boolean catalogContext;

    @Option(names = { "--show-prompt" },
            description = "Show the prompt sent to the LLM")
    boolean showPrompt;

    @Option(names = { "--temperature" },
            description = "Temperature for response generation (0.0-2.0)",
            defaultValue = "0.7")
    double temperature = 0.7;

    @Option(names = { "--system-prompt" },
            description = "Custom system prompt")
    String systemPrompt;

    @Option(names = { "--stream" },
            description = "Stream the response as it's generated (shows progress)",
            defaultValue = "true")
    boolean stream = true;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    public Explain(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String endpoint = detectEndpoint();
        if (endpoint == null) {
            printUsageHelp();
            return 1;
        }

        String resolvedApiKey = resolveApiKey();
        printConfiguration(endpoint, resolvedApiKey);

        for (String file : files) {
            int result = explainRoute(file, endpoint, resolvedApiKey);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private void printConfiguration(String endpoint, String resolvedApiKey) {
        printer().println("LLM Configuration:");
        printer().println("  URL: " + endpoint);
        printer().println("  API Type: " + apiType);
        printer().println("  Model: " + model);
        printMaskedApiKey(resolvedApiKey);
        printer().println();
    }

    private void printMaskedApiKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String masked = "****" + key.substring(Math.max(0, key.length() - 4));
        printer().println("  API Key: " + masked);
    }

    private void printUsageHelp() {
        printer().printErr("LLM service is not running or not reachable.");
        printer().printErr("");
        printer().printErr("Options:");
        printer().printErr("  1. camel infra run ollama");
        printer().printErr("  2. camel explain my-route.yaml --url=http://localhost:11434");
        printer().printErr("  3. camel explain my-route.yaml --url=https://api.openai.com --api-type=openai --api-key=sk-...");
    }

    private String detectEndpoint() {
        return tryExplicitUrl()
                .or(this::tryInfraOllama)
                .or(this::tryDefaultOllama)
                .orElse(null);
    }

    private Optional<String> tryExplicitUrl() {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        if (isEndpointReachable(url)) {
            return Optional.of(url);
        }
        printer().printErr("Cannot connect to LLM service at: " + url);
        return Optional.empty();
    }

    private Optional<String> tryInfraOllama() {
        try {
            Map<Long, Path> pids = findOllamaPids();
            for (Path pidFile : pids.values()) {
                String baseUrl = readBaseUrlFromPidFile(pidFile);
                if (baseUrl != null && isEndpointReachable(baseUrl)) {
                    apiType = ApiType.ollama;
                    return Optional.of(baseUrl);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return Optional.empty();
    }

    private String readBaseUrlFromPidFile(Path pidFile) throws Exception {
        String json = Files.readString(pidFile);
        JsonObject jo = (JsonObject) Jsoner.deserialize(json);
        return jo.getString("baseUrl");
    }

    private Optional<String> tryDefaultOllama() {
        if (isEndpointReachable(DEFAULT_OLLAMA_URL)) {
            apiType = ApiType.ollama;
            return Optional.of(DEFAULT_OLLAMA_URL);
        }
        return Optional.empty();
    }

    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        return Stream.of("OPENAI_API_KEY", "LLM_API_KEY")
                .map(System::getenv)
                .filter(k -> k != null && !k.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Map<Long, Path> findOllamaPids() throws Exception {
        Map<Long, Path> pids = new HashMap<>();
        Path camelDir = CommandLineHelper.getCamelDir();

        if (!Files.exists(camelDir)) {
            return pids;
        }

        try (Stream<Path> fileStream = Files.list(camelDir)) {
            fileStream
                    .filter(this::isOllamaPidFile)
                    .forEach(p -> addPidEntry(pids, p));
        }
        return pids;
    }

    private boolean isOllamaPidFile(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith("infra-ollama-") && name.endsWith(".json");
    }

    private void addPidEntry(Map<Long, Path> pids, Path p) {
        String name = p.getFileName().toString();
        String pidStr = name.substring(name.lastIndexOf("-") + 1, name.lastIndexOf('.'));
        try {
            pids.put(Long.valueOf(pidStr), p);
        } catch (NumberFormatException e) {
            // ignore
        }
    }

    private boolean isEndpointReachable(String endpoint) {
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

    private int explainRoute(String file, String endpoint, String resolvedApiKey) throws Exception {
        Path path = Path.of(file);
        if (!Files.exists(path)) {
            printer().printErr("File not found: " + file);
            return 1;
        }

        String routeContent = Files.readString(path);
        String ext = Optional.ofNullable(FileUtil.onlyExt(file, false)).orElse("yaml");

        printFileHeader(file);

        String sysPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(routeContent, ext, file);

        printPromptsIfRequested(sysPrompt, userPrompt);

        String explanation = apiType.call(this, endpoint, sysPrompt, userPrompt, resolvedApiKey);

        return handleExplanationResult(explanation);
    }

    private void printFileHeader(String file) {
        printer().println("=".repeat(70));
        printer().println("Explaining: " + file);
        printer().println("=".repeat(70));
        printer().println();
    }

    private void printPromptsIfRequested(String sysPrompt, String userPrompt) {
        if (!showPrompt) {
            return;
        }
        printer().println("--- SYSTEM PROMPT ---");
        printer().println(sysPrompt);
        printer().println("--- USER PROMPT ---");
        printer().println(userPrompt);
        printer().println("--- END PROMPTS ---");
        printer().println();
    }

    private int handleExplanationResult(String explanation) {
        if (explanation == null) {
            printer().printErr("Failed to get explanation from LLM");
            return 1;
        }
        // With streaming, response was already printed during generation
        // Without streaming, we need to print it now
        if (!stream) {
            printer().println(explanation);
        }
        printer().println();
        return 0;
    }

    private String buildSystemPrompt() {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an Apache Camel integration expert. ");
        prompt.append("Your task is to explain Camel routes in plain English.\n\n");
        prompt.append("Guidelines:\n");
        prompt.append("- Start with a one-sentence summary\n");
        prompt.append("- Describe step by step what the route does\n");
        prompt.append("- Explain each component and EIP used\n");
        prompt.append("- Mention error handling if present\n");
        prompt.append("- Use bullet points for clarity\n");

        if ("markdown".equals(format)) {
            prompt.append("- Format output as Markdown\n");
        }
        if (verbose) {
            prompt.append("- Include technical details about options and expressions\n");
        }
        return prompt.toString();
    }

    private String buildUserPrompt(String routeContent, String fileExtension, String fileName) {
        StringBuilder prompt = new StringBuilder();

        if (catalogContext) {
            appendCatalogContext(prompt, routeContent);
        }

        prompt.append("File: ").append(fileName).append("\n");
        prompt.append("Format: ").append(fileExtension.toUpperCase()).append("\n\n");
        prompt.append("Route definition:\n```").append(fileExtension).append("\n");
        prompt.append(routeContent).append("\n```\n\n");
        prompt.append("Please explain this Camel route:");

        return prompt.toString();
    }

    private void appendCatalogContext(StringBuilder prompt, String routeContent) {
        String catalogInfo = buildCatalogContext(routeContent);
        if (catalogInfo.isEmpty()) {
            return;
        }
        prompt.append("Reference information:\n").append(catalogInfo).append("\n");
    }

    private String buildCatalogContext(String routeContent) {
        StringBuilder context = new StringBuilder();
        CamelCatalog catalog = new DefaultCamelCatalog();
        String lowerContent = routeContent.toLowerCase();

        appendComponentDescriptions(context, catalog, lowerContent);
        appendEipDescriptions(context, catalog, lowerContent);

        return context.toString();
    }

    private void appendComponentDescriptions(StringBuilder context, CamelCatalog catalog, String content) {
        COMMON_COMPONENTS.stream()
                .filter(comp -> containsComponent(content, comp))
                .forEach(comp -> appendModelDescription(context, catalog.componentModel(comp), comp, ""));
    }

    private void appendEipDescriptions(StringBuilder context, CamelCatalog catalog, String content) {
        COMMON_EIPS.stream()
                .filter(eip -> content.contains(eip.toLowerCase()) || content.contains(camelCaseToDash(eip)))
                .forEach(eip -> appendModelDescription(context, catalog.eipModel(eip), eip, " (EIP)"));
    }

    private boolean containsComponent(String content, String comp) {
        return content.contains(comp + ":")
                || content.contains("\"" + comp + "\"")
                || content.contains("'" + comp + "'");
    }

    private void appendModelDescription(StringBuilder context, Object model, String name, String suffix) {
        String description = getModelDescription(model);
        if (description != null) {
            context.append("- ").append(name).append(suffix).append(": ").append(description).append("\n");
        }
    }

    private String getModelDescription(Object model) {
        if (model instanceof ComponentModel componentModel) {
            return componentModel.getDescription();
        }
        if (model instanceof EipModel eipModel) {
            return eipModel.getDescription();
        }
        return null;
    }

    private String camelCaseToDash(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    String callOllama(String endpoint, String sysPrompt, String userPrompt) {
        JsonObject request = new JsonObject();
        request.put("model", model);
        request.put("prompt", userPrompt);
        request.put("system", sysPrompt);
        request.put("stream", stream);

        JsonObject options = new JsonObject();
        options.put("temperature", temperature);
        request.put("options", options);

        printer().println("Analyzing route with " + model + " (Ollama)...");
        printer().println();

        if (stream) {
            return sendStreamingRequest(endpoint + "/api/generate", request);
        }
        JsonObject response = sendRequest(endpoint + "/api/generate", request, null);
        return response != null ? response.getString("response") : null;
    }

    String callOpenAiCompatible(String endpoint, String sysPrompt, String userPrompt, String resolvedApiKey) {
        JsonArray messages = new JsonArray();
        messages.add(createMessage("system", sysPrompt));
        messages.add(createMessage("user", userPrompt));

        JsonObject request = new JsonObject();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", temperature);

        String apiUrl = normalizeOpenAiUrl(endpoint);

        printer().println("Analyzing route with " + model + " (OpenAI-compatible)...");
        printer().println();

        JsonObject response = sendRequest(apiUrl, request, resolvedApiKey);
        return extractOpenAiContent(response);
    }

    private JsonObject createMessage(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private String normalizeOpenAiUrl(String endpoint) {
        String url = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (!url.endsWith("/v1/chat/completions")) {
            url = url.endsWith("/v1") ? url : url + "/v1";
            url = url + "/chat/completions";
        }
        return url;
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

    private String sendStreamingRequest(String url, JsonObject body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJson()))
                    .build();

            HttpResponse<Stream<String>> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                handleErrorStatus(response.statusCode(), "Streaming request failed");
                return null;
            }

            StringBuilder fullResponse = new StringBuilder();
            response.body().forEach(line -> {
                if (line.isBlank()) {
                    return;
                }
                try {
                    JsonObject chunk = (JsonObject) Jsoner.deserialize(line);
                    String text = chunk.getString("response");
                    if (text != null) {
                        printer().print(text);
                        fullResponse.append(text);
                    }
                } catch (Exception e) {
                    // Skip malformed chunks
                }
            });

            printer().println();
            return fullResponse.toString();

        } catch (java.net.http.HttpTimeoutException e) {
            printer().printErr("\nRequest timed out after " + timeout + " seconds.");
            return null;
        } catch (Exception e) {
            printer().printErr("\nError during streaming: " + e.getMessage());
            return null;
        }
    }

    private JsonObject sendRequest(String url, JsonObject body, String authKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJson()));

            if (authKey != null && !authKey.isBlank()) {
                builder.header("Authorization", "Bearer " + authKey);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return (JsonObject) Jsoner.deserialize(response.body());
            }

            handleErrorStatus(response.statusCode(), response.body());
            return null;

        } catch (java.net.http.HttpTimeoutException e) {
            printer().printErr("Request timed out after " + timeout + " seconds.");
            return null;
        } catch (Exception e) {
            printer().printErr("Error calling LLM: " + e.getMessage());
            return null;
        }
    }

    private void handleErrorStatus(int statusCode, String body) {
        printer().printErr("LLM returned status: " + statusCode);
        switch (statusCode) {
            case 401 -> printer().printErr("Authentication failed. Check your API key.");
            case 404 -> printer().printErr("Model '" + model + "' not found.");
            case 429 -> printer().printErr("Rate limit exceeded.");
            default -> printer().printErr(body);
        }
    }
}
