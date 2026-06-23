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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command to explain Camel routes using AI/LLM services.
 * <p>
 * Supports multiple LLM providers: Ollama, OpenAI, Azure OpenAI, Anthropic, Vertex AI, vLLM, LM Studio, LocalAI, etc.
 */
@Command(name = "explain",
         description = "Explain what a Camel route does using AI/LLM",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel explain hello.java",
                 "  camel explain hello.yaml --format=markdown",
                 "  camel explain hello.java --model=gpt-4",
                 "  camel explain hello.yaml --api-type=anthropic" })
public class Explain extends CamelCommand {

    public static class FormatCompletionCandidates implements Iterable<String> {

        public FormatCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("text", "markdown").iterator();
        }
    }

    private static final String DEFAULT_MODEL = "llama3.2";

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

    @Parameters(description = "Route file(s) to explain", arity = "1..*")
    List<String> files;

    @Option(names = { "--url" },
            description = "LLM API endpoint URL. Auto-detected from 'camel infra' for Ollama if not specified.")
    String url;

    @Option(names = { "--api-type" },
            description = "API type: 'ollama', 'openai' (OpenAI-compatible), or 'anthropic' (Anthropic/Vertex AI)")
    LlmClient.ApiType apiType;

    @Option(names = { "--api-key" },
            description = "API key for authentication. Also reads ANTHROPIC_API_KEY, OPENAI_API_KEY, or LLM_API_KEY env vars")
    String apiKey;

    @Option(names = { "--model" },
            description = "Model to use",
            defaultValue = DEFAULT_MODEL)
    String model = DEFAULT_MODEL;

    @Option(names = { "--verbose", "-v" },
            description = "Include detailed technical information")
    boolean verbose;

    @Option(names = { "--format" },
            completionCandidates = FormatCompletionCandidates.class,
            description = "Output format (${COMPLETION-CANDIDATES})",
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

    public Explain(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        LlmClient client = LlmClient.create()
                .withUrl(url)
                .withApiType(apiType)
                .withApiKey(apiKey)
                .withModel(model)
                .withTimeout(timeout)
                .withTemperature(temperature)
                .withStream(stream)
                .withPrinter(printer());

        if (!client.detectEndpoint()) {
            printUsageHelp();
            return 1;
        }

        printConfiguration(client);

        for (String file : files) {
            int result = explainRoute(file, client);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private void printConfiguration(LlmClient client) {
        printer().println("LLM Configuration:");
        printer().println("  URL: " + (client.url != null ? client.url : "(Vertex AI)"));
        printer().println("  API Type: " + client.apiType);
        printer().println("  Model: " + client.model);
        printMaskedApiKey(client.apiKey);
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
        printer().printErr("  4. camel explain my-route.yaml --api-type=anthropic  (uses ANTHROPIC_API_KEY or Vertex AI)");
    }

    private int explainRoute(String file, LlmClient client) throws Exception {
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

        printer().println("Analyzing route with " + client.model + " (" + client.apiType + ")...");
        printer().println();

        String explanation = client.generate(sysPrompt, userPrompt);

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
}
