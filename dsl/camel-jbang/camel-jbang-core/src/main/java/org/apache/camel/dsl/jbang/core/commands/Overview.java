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
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
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
    public boolean isJsonOutput() {
        return "json".equals(format);
    }

    @Override
    public Integer doCall() throws Exception {
        if (!"markdown".equals(format) && !"json".equals(format)) {
            throw CliException.usage("--format must be markdown or json, was: " + format);
        }
        Path dir = Path.of(directory != null ? directory : ".").toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            throw CliException.notFound("Directory does not exist: " + dir);
        }
        if (!Files.isDirectory(dir)) {
            throw CliException.usage("Not a directory: " + dir);
        }
        ProjectOverview.Overview overview = ProjectOverview.analyze(dir, new DefaultCamelCatalog());
        if (overview.routes().isEmpty()) {
            throw CliException.failure("No routes found in " + dir);
        }
        IntegrationSummary.Summary summary = IntegrationSummary.read(dir);

        if (ai) {
            explain(overview, summary);
            summary = IntegrationSummary.read(dir);
        } else if (save) {
            IntegrationSummary.write(overview, summary != null ? summary.ai() : null,
                    summary != null ? summary.fingerprint() : null, summary != null ? summary.model() : null);
            // not part of the output, which can be JSON
            printer().printDiagnostic("Saved " + IntegrationSummary.FILE_NAME);
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

    private void explain(ProjectOverview.Overview overview, IntegrationSummary.Summary previous) throws Exception {
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
            throw CliException.failure("LLM service is not running or not reachable. Start one with: camel infra run"
                                       + " ollama, or pass --url, --api-type and --api-key");
        }
        String system = IntegrationSummary.systemPrompt();
        String user = IntegrationSummary.userPrompt(overview, null);
        // the prompts and the progress go to the diagnostics channel, as the output can be JSON
        if (showPrompt) {
            printer().printDiagnostic("--- SYSTEM PROMPT ---");
            printer().printDiagnostic(system);
            printer().printDiagnostic("--- USER PROMPT ---");
            printer().printDiagnostic(user);
            printer().printDiagnostic("--- END PROMPTS ---");
            printer().printDiagnostic("");
        }
        printer().printDiagnostic("Explaining " + overview.flows().size() + " routes with " + client.model() + " ("
                                  + client.apiType() + ")...");
        String answer = client.generate(system, user);
        if (answer == null || answer.isBlank()) {
            throw CliException.failure("Failed to get an answer from the LLM");
        }
        IntegrationSummary.AiContent fresh = IntegrationSummary.parseAnswer(answer, overview);
        if (fresh.isEmpty()) {
            printer().printDiagnostic(answer);
            throw CliException.failure("The LLM answer (above) did not follow the expected format; nothing was saved");
        }
        IntegrationSummary.AiContent merged
                = IntegrationSummary.merge(overview, previous != null ? previous.ai() : null, fresh);
        Path file = IntegrationSummary.write(overview, merged, overview.fingerprint(), client.model());
        if (!isJsonOutput()) {
            printer().println(Files.readString(file, StandardCharsets.UTF_8));
        }
        printer().printDiagnostic("Saved " + IntegrationSummary.FILE_NAME + ": " + fresh.capabilities().size()
                                  + " capabilities, " + fresh.descriptions().size() + " route descriptions, "
                                  + fresh.notes().size() + " notes (AI-assisted, review"
                                  + " before relying on them)");
    }

    private Integer applyDescriptions(ProjectOverview.Overview overview, IntegrationSummary.Summary summary)
            throws Exception {
        if (summary == null || summary.descriptions().isEmpty() && summary.notes().isEmpty()) {
            throw CliException.failure("No AI-assisted route descriptions in " + IntegrationSummary.FILE_NAME
                                       + "; run camel overview --ai first");
        }
        RouteDescriptions.Plan plan = RouteDescriptions.plan(overview, summary.descriptions(), summary.notes());
        JsonArray updated = new JsonArray();
        for (RouteDescriptions.Change change : plan.changes()) {
            Files.writeString(overview.directory().resolve(change.file()), change.newContent(), StandardCharsets.UTF_8);
            if (isJsonOutput()) {
                JsonObject jo = new JsonObject();
                jo.put("file", change.file());
                jo.put("routes", change.routes());
                updated.add(jo);
            } else {
                printer().println("Updated " + change.file() + ": " + String.join(", ", change.routes()));
            }
        }
        JsonArray skippedRoutes = new JsonArray();
        for (RouteDescriptions.Skipped skipped : plan.skipped()) {
            if (isJsonOutput()) {
                JsonObject jo = new JsonObject();
                jo.put("route", skipped.route());
                jo.put("reason", skipped.reason());
                skippedRoutes.add(jo);
            } else {
                printer().println("Skipped " + skipped.route() + ": " + skipped.reason());
            }
        }
        if (isJsonOutput()) {
            JsonObject jo = new JsonObject();
            jo.put("updated", updated);
            jo.put("skipped", skippedRoutes);
            printer().println(jo.toJson());
        }
        if (!plan.changes().isEmpty()) {
            // the descriptions are part of the routes now; the summary no longer marks them as AI-assisted
            ProjectOverview.Overview after = ProjectOverview.analyze(overview.directory(), new DefaultCamelCatalog());
            IntegrationSummary.AiContent rest
                    = IntegrationSummary.merge(after, summary.ai(), new IntegrationSummary.AiContent(
                            null, List.of(),
                            Map.of()));
            IntegrationSummary.write(after, rest, after.fingerprint(), summary.model());
            printer().printTip("review the changes (git diff) before committing them");
        }
        return 0;
    }
}
