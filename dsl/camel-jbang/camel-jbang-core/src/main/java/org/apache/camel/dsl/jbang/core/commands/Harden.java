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
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command to suggest security hardening improvements for Camel routes using AI/LLM services.
 * <p>
 * Analyzes routes for security vulnerabilities, authentication issues, encryption gaps, secrets management, input
 * validation, and other security best practices. Supports multiple LLM providers: Ollama, OpenAI, Azure OpenAI, vLLM,
 * LM Studio, LocalAI, etc.
 */
@Command(name = "harden",
         description = "Suggest security hardening for Camel routes using AI/LLM",
         sortOptions = false, showDefaultValues = true)
public class Harden extends CamelCommand {

    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2";
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    // Components with significant security considerations
    private static final List<String> SECURITY_SENSITIVE_COMPONENTS = Arrays.asList(
            // Network/API components - need TLS, authentication
            "http", "https", "netty-http", "vertx-http", "websocket",
            "rest", "rest-api", "platform-http", "servlet", "undertow", "jetty",
            // Messaging - need authentication, encryption
            "kafka", "jms", "activemq", "amqp", "rabbitmq", "pulsar",
            "aws2-sqs", "aws2-sns", "aws2-kinesis",
            "azure-servicebus", "azure-eventhubs",
            "google-pubsub",
            // File/Storage - need access control, path validation
            "file", "ftp", "sftp", "ftps",
            "aws2-s3", "azure-storage-blob", "azure-storage-queue", "azure-files",
            "google-storage", "minio",
            // Database - need authentication, SQL injection prevention
            "sql", "jdbc", "mongodb", "couchdb", "cassandraql",
            "elasticsearch", "opensearch", "redis",
            // Email - need authentication, TLS
            "smtp", "smtps", "imap", "imaps", "pop3", "pop3s",
            // Remote execution - high risk, need strict validation
            "exec", "ssh", "docker",
            // Directory services - need secure binding
            "ldap", "ldaps",
            // Secrets management
            "hashicorp-vault", "aws2-secrets-manager", "azure-key-vault", "google-secret-manager");

    // Security-related categories
    private static final List<String> OWASP_CATEGORIES = Arrays.asList(
            "Injection (SQL, Command, LDAP, XPath)",
            "Broken Authentication",
            "Sensitive Data Exposure",
            "XML External Entities (XXE)",
            "Broken Access Control",
            "Security Misconfiguration",
            "Cross-Site Scripting (XSS)",
            "Insecure Deserialization",
            "Using Components with Known Vulnerabilities",
            "Insufficient Logging & Monitoring");

    enum ApiType {
        ollama((harden, prompts) -> harden.callOllama(prompts[0], prompts[1], prompts[2])),
        openai((harden, prompts) -> harden.callOpenAiCompatible(prompts[0], prompts[1], prompts[2], prompts[3]));

        private final BiFunction<Harden, String[], String> caller;

        ApiType(BiFunction<Harden, String[], String> caller) {
            this.caller = caller;
        }

        String call(Harden harden, String endpoint, String sysPrompt, String userPrompt, String apiKey) {
            return caller.apply(harden, new String[] { endpoint, sysPrompt, userPrompt, apiKey });
        }
    }

    @Parameters(description = "Route file(s) to analyze for security hardening", arity = "1..*")
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
            description = "Include detailed security recommendations with code examples")
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

    public Harden(CamelJBangMain main) {
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
            int result = hardenRoute(file, endpoint, resolvedApiKey);
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
        printer().printErr("  2. camel harden my-route.yaml --url=http://localhost:11434");
        printer().printErr("  3. camel harden my-route.yaml --url=https://api.openai.com --api-type=openai --api-key=sk-...");
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

    private int hardenRoute(String file, String endpoint, String resolvedApiKey) throws Exception {
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

        String suggestions = apiType.call(this, endpoint, sysPrompt, userPrompt, resolvedApiKey);

        return handleHardeningResult(suggestions);
    }

    private void printFileHeader(String file) {
        printer().println("=".repeat(70));
        printer().println("Security Hardening Analysis: " + file);
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

    private int handleHardeningResult(String suggestions) {
        if (suggestions == null) {
            printer().printErr("Failed to get security hardening suggestions from LLM");
            return 1;
        }
        // With streaming, response was already printed during generation
        // Without streaming, we need to print it now
        if (!stream) {
            printer().println(suggestions);
        }
        printer().println();
        return 0;
    }

    private String buildSystemPrompt() {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an Apache Camel security expert specializing in integration security. ");
        prompt.append("Your task is to analyze Camel routes and provide security hardening recommendations.\n\n");

        prompt.append("Focus on these security areas:\n");
        prompt.append("1. AUTHENTICATION & AUTHORIZATION\n");
        prompt.append("   - Missing or weak authentication mechanisms\n");
        prompt.append("   - Lack of authorization checks\n");
        prompt.append("   - API key and credential exposure\n\n");

        prompt.append("2. ENCRYPTION & DATA PROTECTION\n");
        prompt.append("   - Data in transit (TLS/SSL configuration)\n");
        prompt.append("   - Data at rest encryption\n");
        prompt.append("   - Certificate validation\n\n");

        prompt.append("3. SECRETS MANAGEMENT\n");
        prompt.append("   - Hardcoded credentials or secrets\n");
        prompt.append("   - Secrets in configuration files\n");
        prompt.append("   - Recommend vault integration (HashiCorp Vault, AWS Secrets Manager, etc.)\n\n");

        prompt.append("4. INPUT VALIDATION & INJECTION PREVENTION\n");
        prompt.append("   - SQL injection vulnerabilities\n");
        prompt.append("   - Command injection risks\n");
        prompt.append("   - XML/JSON injection\n");
        prompt.append("   - Path traversal vulnerabilities\n\n");

        prompt.append("5. SECURE COMPONENT CONFIGURATION\n");
        prompt.append("   - Insecure default settings\n");
        prompt.append("   - Missing security headers\n");
        prompt.append("   - Overly permissive configurations\n\n");

        prompt.append("6. LOGGING & MONITORING\n");
        prompt.append("   - Sensitive data in logs\n");
        prompt.append("   - Missing audit trails\n");
        prompt.append("   - Security event monitoring\n\n");

        prompt.append("Guidelines:\n");
        prompt.append("- Start with an executive summary of the security posture\n");
        prompt.append("- Prioritize findings by severity: Critical, High, Medium, Low\n");
        prompt.append("- For each finding, explain the risk and provide a specific remediation\n");
        prompt.append("- Reference OWASP guidelines where applicable\n");

        if ("markdown".equals(format)) {
            prompt.append("- Format output as Markdown with clear sections and code blocks\n");
        }
        if (verbose) {
            prompt.append("- Include specific code examples showing secure implementations\n");
            prompt.append("- Provide configuration snippets for recommended security settings\n");
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
        prompt.append("Please perform a security analysis of this Camel route and provide hardening recommendations:");

        return prompt.toString();
    }

    private void appendCatalogContext(StringBuilder prompt, String routeContent) {
        String catalogInfo = buildCatalogContext(routeContent);
        if (catalogInfo.isEmpty()) {
            return;
        }
        prompt.append("Security-relevant component information:\n").append(catalogInfo).append("\n");
    }

    private String buildCatalogContext(String routeContent) {
        StringBuilder context = new StringBuilder();
        CamelCatalog catalog = new DefaultCamelCatalog();
        String lowerContent = routeContent.toLowerCase();

        SECURITY_SENSITIVE_COMPONENTS.stream()
                .filter(comp -> containsComponent(lowerContent, comp))
                .forEach(comp -> {
                    ComponentModel model = catalog.componentModel(comp);
                    if (model != null) {
                        context.append("- ").append(comp).append(": ").append(model.getDescription());
                        String securityNote = getSecurityNote(comp);
                        if (securityNote != null) {
                            context.append(" [Security: ").append(securityNote).append("]");
                        }
                        context.append("\n");
                    }
                });

        return context.toString();
    }

    private String getSecurityNote(String component) {
        return switch (component) {
            case "http" -> "Prefer HTTPS; validate certificates; configure timeouts";
            case "https" -> "Verify TLS version >= 1.2; validate certificates";
            case "kafka" -> "Enable SASL authentication; use SSL; configure ACLs";
            case "sql", "jdbc" -> "Use parameterized queries to prevent SQL injection";
            case "file" -> "Validate file paths; prevent path traversal";
            case "ftp" -> "Prefer SFTP/FTPS over plain FTP";
            case "exec" -> "High risk - validate all inputs to prevent command injection";
            case "ssh" -> "Use key-based authentication; validate host keys";
            case "rest", "rest-api", "platform-http" ->
                "Implement authentication; validate input; set security headers";
            case "ldap" -> "Use LDAPS; prevent LDAP injection";
            case "mongodb", "redis" -> "Enable authentication; use TLS";
            case "jms", "activemq", "amqp" -> "Enable authentication; use SSL/TLS";
            case "aws2-s3", "aws2-sqs", "aws2-sns" -> "Use IAM roles; enable server-side encryption";
            case "azure-storage-blob" -> "Use managed identities; enable encryption";
            case "smtp", "imap" -> "Use TLS (SMTPS/IMAPS); authenticate securely";
            default -> null;
        };
    }

    private boolean containsComponent(String content, String comp) {
        return content.contains(comp + ":")
                || content.contains("\"" + comp + "\"")
                || content.contains("'" + comp + "'");
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

        printer().println("Performing security analysis with " + model + " (Ollama)...");
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

        printer().println("Performing security analysis with " + model + " (OpenAI-compatible)...");
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
        String normalizedUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (!normalizedUrl.endsWith("/v1/chat/completions")) {
            normalizedUrl = normalizedUrl.endsWith("/v1") ? normalizedUrl : normalizedUrl + "/v1";
            normalizedUrl = normalizedUrl + "/chat/completions";
        }
        return normalizedUrl;
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

    private String sendStreamingRequest(String requestUrl, JsonObject body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
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

    private JsonObject sendRequest(String requestUrl, JsonObject body, String authKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
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
