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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteDescriptions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * A high-level overview of the integrations in a project directory, from the route sources: routes, entry points, how
 * the routes connect, external systems and findings. With {@code --ai} an LLM writes an overview, the business
 * capabilities and descriptions of the routes that have none into the project's integration summary file, marked as
 * AI-assisted.
 */
@Command(name = "overview",
         description = "High-level overview of the integrations in a project, optionally explained by AI/LLM",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel overview",
                 "  camel overview src/main/resources/camel --format=json",
                 "  camel overview --ai",
                 "  camel overview --ai --api-type=anthropic",
                 "  camel overview --apply-descriptions" })
public class Overview extends CamelCommand {

    public static class FormatCompletionCandidates implements Iterable<String> {

        public FormatCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("markdown", "json").iterator();
        }
    }

    private static final String DEFAULT_MODEL = "llama3.2";

    @Parameters(description = "Project directory (default: the current directory)", arity = "0..1")
    String directory;

    @Option(names = { "--format" },
            completionCandidates = FormatCompletionCandidates.class,
            description = "Output format (${COMPLETION-CANDIDATES})",
            defaultValue = "markdown")
    String format = "markdown";

    @Option(names = { "--save" },
            description = "Save the overview as camel-summary.md in the project directory, keeping its AI-assisted sections")
    boolean save;

    @Option(names = { "--ai" },
            description = "Ask an LLM to explain the project (an overview, the capabilities, labels and notes of routes"
                          + " that have none) and save it in camel-summary.md, marked as AI-assisted")
    boolean ai;

    @Option(names = { "--apply-descriptions" },
            description = "Put the AI-assisted route descriptions and notes of camel-summary.md into the route sources, so they become part of the routes")
    boolean applyDescriptions;

    @Option(names = { "--url" },
            description = "LLM API endpoint URL. Auto-detected from 'camel infra' for Ollama if not specified. Also reads AZURE_OPENAI_ENDPOINT or WATSONX_URL env vars")
    String url;

    @Option(names = { "--api-type" },
            description = "API type: 'ollama', 'openai' (OpenAI-compatible), 'anthropic' (Anthropic/Vertex AI), or 'watsonx' (IBM watsonx.ai)")
    LlmClient.ApiType apiType;

    @Option(names = { "--api-key" },
            description = "API key for authentication. Also reads ANTHROPIC_API_KEY, OPENAI_API_KEY, WATSONX_APIKEY, or LLM_API_KEY env vars")
    String apiKey;

    @Option(names = { "--model" },
            description = "Model to use",
            defaultValue = DEFAULT_MODEL)
    String model = DEFAULT_MODEL;

    @Option(names = { "--timeout" },
            description = "Timeout in seconds for LLM response",
            defaultValue = "300")
    int timeout = 300;

    @Option(names = { "--temperature" },
            description = "Temperature for response generation (0.0-2.0)",
            defaultValue = "0.3")
    double temperature = 0.3;

    @Option(names = { "--show-prompt" },
            description = "Show the prompt sent to the LLM")
    boolean showPrompt;

    public Overview(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Path dir = Path.of(directory != null ? directory : ".").toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            printer().printErr("Not a directory: " + dir);
            return 1;
        }
        ProjectOverview.Overview overview = ProjectOverview.analyze(dir, new DefaultCamelCatalog());
        if (overview.routes().isEmpty()) {
            printer().printErr("No routes found in " + dir);
            return 1;
        }
        IntegrationSummary.Summary summary = IntegrationSummary.read(dir);

        if (ai) {
            Integer result = explain(overview, summary);
            if (result != 0) {
                return result;
            }
            summary = IntegrationSummary.read(dir);
        } else if (save) {
            IntegrationSummary.write(overview, summary != null ? summary.ai() : null,
                    summary != null ? summary.fingerprint() : null, summary != null ? summary.model() : null);
            printer().println("Saved " + IntegrationSummary.FILE_NAME);
        }

        if (applyDescriptions) {
            return applyDescriptions(overview, summary);
        }

        if ("json".equals(format)) {
            printer().println(ProjectOverview.toJson(overview, summary).toJson());
        } else if (!ai) {
            printer().println(IntegrationSummary.render(overview, summary != null ? summary.ai() : null,
                    summary != null ? summary.fingerprint() : null, summary != null ? summary.model() : null,
                    summary != null ? summary.date() : null));
        }
        return 0;
    }

    private Integer explain(ProjectOverview.Overview overview, IntegrationSummary.Summary previous) throws Exception {
        LlmClient client = LlmClient.create()
                .withUrl(url)
                .withApiType(apiType)
                .withApiKey(apiKey)
                .withModel(model)
                .withTimeout(timeout)
                .withTemperature(temperature)
                .withStream(false)
                .withPrinter(printer());
        if (!client.detectEndpoint()) {
            printer().printErr("LLM service is not running or not reachable.");
            printer().printErr("Start one with: camel infra run ollama, or pass --url, --api-type and --api-key");
            return 1;
        }
        String system = IntegrationSummary.systemPrompt();
        String user = IntegrationSummary.userPrompt(overview, null);
        if (showPrompt) {
            printer().println("--- SYSTEM PROMPT ---");
            printer().println(system);
            printer().println("--- USER PROMPT ---");
            printer().println(user);
            printer().println("--- END PROMPTS ---");
            printer().println();
        }
        printer().println("Explaining " + overview.flows().size() + " routes with " + client.model() + " ("
                          + client.apiType() + ")...");
        String answer = client.generate(system, user);
        if (answer == null || answer.isBlank()) {
            printer().printErr("Failed to get an answer from the LLM");
            return 1;
        }
        IntegrationSummary.AiContent fresh = IntegrationSummary.parseAnswer(answer, overview);
        if (fresh.isEmpty()) {
            printer().printErr("The LLM answer did not follow the expected format; nothing was saved:");
            printer().printErr(answer);
            return 1;
        }
        IntegrationSummary.AiContent merged
                = IntegrationSummary.merge(overview, previous != null ? previous.ai() : null, fresh);
        Path file = IntegrationSummary.write(overview, merged, overview.fingerprint(), client.model());
        printer().println(Files.readString(file, StandardCharsets.UTF_8));
        printer().println("Saved " + IntegrationSummary.FILE_NAME + ": " + fresh.capabilities().size()
                          + " capabilities, " + fresh.descriptions().size() + " route descriptions, "
                          + fresh.notes().size() + " notes (AI-assisted, review"
                          + " before relying on them)");
        return 0;
    }

    private Integer applyDescriptions(ProjectOverview.Overview overview, IntegrationSummary.Summary summary)
            throws Exception {
        if (summary == null || summary.descriptions().isEmpty() && summary.notes().isEmpty()) {
            printer().printErr("No AI-assisted route descriptions in " + IntegrationSummary.FILE_NAME
                               + "; run camel overview --ai first");
            return 1;
        }
        RouteDescriptions.Plan plan = RouteDescriptions.plan(overview, summary.descriptions(), summary.notes());
        for (RouteDescriptions.Change change : plan.changes()) {
            Files.writeString(overview.directory().resolve(change.file()), change.newContent(), StandardCharsets.UTF_8);
            printer().println("Updated " + change.file() + ": " + String.join(", ", change.routes()));
        }
        for (RouteDescriptions.Skipped skipped : plan.skipped()) {
            printer().println("Skipped " + skipped.route() + ": " + skipped.reason());
        }
        if (!plan.changes().isEmpty()) {
            // the descriptions are part of the routes now; the summary no longer marks them as AI-assisted
            ProjectOverview.Overview after = ProjectOverview.analyze(overview.directory(), new DefaultCamelCatalog());
            IntegrationSummary.AiContent rest
                    = IntegrationSummary.merge(after, summary.ai(), new IntegrationSummary.AiContent(
                            null, List.of(),
                            Map.of()));
            IntegrationSummary.write(after, rest, after.fingerprint(), summary.model());
            printer().println("Review the changes (git diff) before committing them");
        }
        return 0;
    }
}
