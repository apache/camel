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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.ai.ChaosExplorePlaybook;
import org.apache.camel.dsl.jbang.core.commands.process.ListChaos;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.ChaosExperimentRun;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Let there be chaos (CAMEL-26787): the AI of camel ask explores chaos on a running integration by itself, for the
 * budget of the user, and reports at the end.
 */
@Command(name = "chaos-explore",
         description = "Let there be chaos: an AI explores by itself how a running integration copes with faults, for a"
                       + " time budget, and reports what held and what broke",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nThis command starts a chaos exploration with the budget (camel cmd chaos-explore-start), and hands",
                 "it to the AI of camel ask with a playbook: read the suggestions, run one experiment per call",
                 "(camel cmd chaos-experiment), read the verdicts, and go on while there is time. The AI asks",
                 "nothing on the way: starting this command is the consent to the exploration, and camel-chaos",
                 "removes every rule at the deadline. The AI never changes the routes. When it is done, the",
                 "exploration is finished, and its report and the journal of the experiments are printed. The exit",
                 "code is 1 when an experiment failed.",
                 "%nThe integration needs camel-chaos switched on (camel run --chaos), and remote endpoints are best",
                 "stubbed (camel run --stub=remote), so the experiments do not reach real systems.",
                 "%nExamples:",
                 "  camel cmd chaos-explore",
                 "  camel cmd chaos-explore myApp --budget=30m --focus=refunds",
                 "  camel cmd chaos-explore --api-type=anthropic --budget=1h" })
public class ChaosExplore extends CamelCommand {

    @Parameters(description = "Name or pid of the running Camel integration (must match one); without it, the only one"
                              + " running, or the only one with chaos switched on",
                arity = "0..1")
    String name;

    @Option(names = { "--budget" }, defaultValue = "10m",
            description = "How long the AI explores, between 10s and 2h")
    String budget;

    @Option(names = { "--focus" },
            description = "What to explore, such as a route id (default: all routes)")
    String focus;

    @Option(names = { "--url" },
            description = "LLM API endpoint URL, as for camel ask. Auto-detected if not specified")
    String url;

    @Option(names = { "--api-type" },
            description = "API type: 'ollama', 'openai', 'anthropic', or 'watsonx'")
    LlmClient.ApiType apiType;

    @Option(names = { "--api-key" },
            description = "API key. Also reads ANTHROPIC_API_KEY, OPENAI_API_KEY, WATSONX_APIKEY, or LLM_API_KEY env vars")
    String apiKey;

    @Option(names = { "--model" },
            description = "Model to use. Default: the provider's default; with Ollama the installed model best at"
                          + " tool calling")
    String model;

    @Option(names = { "--timeout" }, defaultValue = "300",
            description = "Timeout in seconds for one answer of the LLM (an experiment can take minutes)")
    int timeout = 300;

    @Option(names = { "--max-iterations" }, defaultValue = "100",
            description = "Maximum number of tool-calling rounds")
    int maxIterations = 100;

    @Option(names = { "--tools" }, defaultValue = "auto",
            description = "The tools the model gets: core (a small set, for a local model), full, or auto")
    Ask.ToolSet tools = Ask.ToolSet.auto;

    @Option(names = { "--yes" },
            description = "Let the AI send messages to endpoints the integration does not have, and run the other calls"
                          + " that camel ask asks for first, without asking")
    boolean yes;

    @Option(names = { "--show-tools" },
            description = "Show the tool calls and results as they happen")
    boolean showTools;

    @Option(names = { "--save" }, negatable = true, defaultValue = "true", fallbackValue = "true",
            description = "Save the report in test/chaos/reports (as markdown, and the journal as JSON), and each"
                          + " experiment that failed as a scenario in test/chaos, to replay it")
    boolean save = true;

    @Option(names = { "--verbose" },
            description = "Print debug information of the LLM calls")
    boolean verbose;

    public ChaosExplore(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = ProcessFinder.requireOneForChaos(name);
        // the exploration has the budget of the user, not one the AI picks
        ActionResult started = ActionClient.execute(process, "chaos", root -> {
            root.put("command", "explore-start");
            root.put("budget", budget);
            if (focus != null && !focus.isBlank()) {
                root.put("focus", focus);
            }
        }, 10000);
        if (!started.isOk()) {
            printer().printErr(started.error());
            return started.exitCode();
        }
        String integration = displayName(process);
        printer().println("Let there be chaos: " + integration + " (pid " + process.pid() + "), "
                          + TimeUtils.printDuration(started.reply().getLongOrDefault("budget", 0))
                          + (focus != null && !focus.isBlank() ? ", focus: " + focus : ""));
        int answer;
        Ask ask = ask(process, integration);
        try {
            answer = ask.doCall();
        } catch (Exception e) {
            // the LLM fails with an exit code; anything else must not cost the report of what was explored so far
            // an exception without a message is known by its type
            printer().printErr("camel ask failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            answer = CliExitCode.FAILURE;
        } finally {
            // the AI may have finished it already; else its rules go now
            ActionClient.execute(process, "chaos", root -> root.put("command", "explore-finish"), 10000);
        }
        JsonObject status = RuntimeHelper.readStatus(process.pid());
        JsonObject exploration = ChaosExperimentRun.exploration(status);
        List<String> lines = journal(exploration);
        printer().println();
        lines.forEach(printer()::println);
        if (save && exploration != null) {
            try {
                Saved saved = save(Path.of("test", "chaos"), LocalDateTime.now(),
                        new Run(integration, budget, focus, ask.usedModel, seed(status)), exploration, ask.lastAnswer,
                        lines);
                printer().println();
                printer().println("Report: " + saved.report());
                saved.scenarios()
                        .forEach(s -> printer().println("Scenario: " + s + " (replay: camel run * --chaos=" + s + ")"));
            } catch (IOException e) {
                // the journal is printed already: say why it is not saved, rather than a stack trace
                printer().printErr("Could not save the report in test/chaos: " + e.getMessage());
            }
        }
        if (failed(exploration)) {
            return CliExitCode.FAILURE;
        }
        return answer;
    }

    /** The name of the integration as camel get chaos has it: its CamelContext, else the name of the process. */
    static String displayName(CamelProcess process) {
        JsonObject status = process.status();
        if (status != null && status.get("context") instanceof JsonObject context) {
            String name = context.getString("name");
            if (name != null && !name.isBlank() && !"CamelJBang".equals(name)) {
                return name;
            }
        }
        return process.name();
    }

    /** camel ask, with the playbook as its question, for the integration. */
    Ask ask(CamelProcess process, String integration) {
        Ask ask = new Ask(getMain());
        ask.question = List.of(ChaosExplorePlaybook.text(ChaosExplorePlaybook.ASK, integration, budget, focus, true));
        ask.nameOrPid = Long.toString(process.pid());
        ask.url = url;
        ask.apiType = apiType;
        ask.apiKey = apiKey;
        ask.model = model;
        ask.timeout = timeout;
        ask.maxIterations = maxIterations;
        ask.tools = tools;
        ask.yes = yes;
        ask.showTools = showTools;
        ask.showStats = true;
        ask.verbose = verbose;
        // the exploration runs with the budget of the user: the AI does not start another one, not even with a camel
        // command, and the chaos tools are all it needs
        ask.hiddenTools = Set.of("chaos_explore_start", "cli_exec");
        return ask;
    }

    /** What the exploration ran with, for the report. */
    record Run(String integration, String budget, String focus, String model, Long seed) {
    }

    /** The files written: the report (markdown, with the journal as JSON next to it), and a scenario per failure. */
    record Saved(Path report, Path journal, List<Path> scenarios) {
    }

    private static Long seed(JsonObject status) {
        if (status != null && status.get("chaos") instanceof JsonObject chaos
                && chaos.get("report") instanceof JsonObject report && report.get("seed") instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /**
     * Saves what the exploration found (CAMEL-26788): the report of the AI with the journal of the experiments as
     * markdown in reports/, the journal as JSON next to it, and each experiment that failed as a scenario, so camel run
     * --chaos or camel cmd chaos-load replays its verdict.
     *
     * @param dir the chaos folder of the project, test/chaos, which camel run and export leave alone
     */
    static Saved save(
            Path dir, LocalDateTime when, Run run, JsonObject exploration, String report, List<String> journal)
            throws IOException {
        String stamp = when.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String date = when.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        Path reports = Files.createDirectories(dir.resolve("reports"));
        List<Path> scenarios = new ArrayList<>();
        List<Object> experiments = exploration.getCollection("experiments");
        if (experiments != null) {
            for (Object o : experiments) {
                if (!(o instanceof JsonObject e) || !"FAIL".equals(e.getString("verdict"))) {
                    continue;
                }
                Path scenario = dir.resolve("explore-" + stamp + "-" + e.getIntegerOrDefault("number", 0) + ".chaos");
                Files.writeString(scenario, scenario(e, run, date, scenario));
                scenarios.add(scenario);
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Chaos exploration: ").append(run.integration()).append("\n\n");
        md.append(date).append(" · budget ").append(run.budget());
        if (run.focus() != null && !run.focus().isBlank()) {
            md.append(" · focus ").append(run.focus());
        }
        if (run.model() != null) {
            md.append(" · ").append(run.model());
        }
        if (run.seed() != null) {
            md.append(" · seed ").append(run.seed());
        }
        md.append("\n\n## Report\n\n").append(report != null && !report.isBlank()
                ? report.strip() : "(the AI wrote no report)").append("\n\n## Experiments\n\n```text\n");
        journal.forEach(line -> md.append(line).append('\n'));
        md.append("```\n");
        if (!scenarios.isEmpty()) {
            md.append("\n## Scenarios\n\nEach experiment that failed, to replay its verdict:\n\n");
            for (Path s : scenarios) {
                md.append("- `").append(s).append("`: `camel run * --chaos=").append(s).append("`\n");
            }
        }
        Path markdown = reports.resolve("explore-" + stamp + ".md");
        Files.writeString(markdown, md.toString());

        JsonObject json = new JsonObject();
        json.put("integration", run.integration());
        json.put("budget", run.budget());
        if (run.focus() != null) {
            json.put("focus", run.focus());
        }
        if (run.model() != null) {
            json.put("model", run.model());
        }
        if (run.seed() != null) {
            json.put("seed", run.seed());
        }
        json.put("report", report);
        json.put("exploration", exploration);
        json.put("scenarios", scenarios.stream().map(Path::toString).toList());
        Path journalFile = reports.resolve("explore-" + stamp + ".json");
        Files.writeString(journalFile, Jsoner.prettyPrint(json.toJson()));
        return new Saved(markdown, journalFile, scenarios);
    }

    /** A failed experiment as a scenario: what it tested and found as comments, the seed, and its rules. */
    static String scenario(JsonObject experiment, Run run, String date, Path file) {
        StringBuilder sb = new StringBuilder();
        sb.append("# From the chaos exploration of ").append(run.integration()).append(" on ").append(date)
                .append(", experiment ").append(experiment.getIntegerOrDefault("number", 0)).append(": ")
                .append(experiment.getString("verdict")).append('\n');
        String hypothesis = experiment.getString("hypothesis");
        if (hypothesis != null) {
            sb.append("# hypothesis: ").append(hypothesis).append('\n');
        }
        List<Object> expectations = experiment.getCollection("expectations");
        if (expectations != null) {
            for (Object o : expectations) {
                if (!(o instanceof JsonObject x)) {
                    continue;
                }
                sb.append("# expect ").append(x.getString("expect")).append(": ").append(x.getString("state"))
                        .append(", ").append(x.getString("value")).append('\n');
            }
        }
        String note = experiment.getString("note");
        if (note != null) {
            sb.append("# note: ").append(note).append('\n');
        }
        sb.append("# replay: camel run * --chaos=").append(file)
                .append(" (and send it messages as the experiment did, when its routes do not feed themselves)\n");
        if (run.seed() != null) {
            sb.append("seed=").append(run.seed()).append('\n');
        }
        List<Object> rules = experiment.getCollection("ruleTexts");
        if (rules != null) {
            rules.forEach(r -> sb.append(r).append('\n'));
        }
        return sb.toString();
    }

    /** The journal of the exploration: how it ended, and a line per experiment with its expectations. */
    static List<String> journal(JsonObject exploration) {
        List<String> lines = new ArrayList<>();
        if (exploration == null) {
            lines.add("No journal of the exploration in the status of the integration");
            return lines;
        }
        List<Object> experiments = exploration.getCollection("experiments");
        int count = experiments != null ? experiments.size() : 0;
        long failed = experiments == null
                ? 0 : experiments.stream()
                        .filter(o -> o instanceof JsonObject jo && "FAIL".equals(jo.getString("verdict"))).count();
        long ran = exploration.getLongOrDefault("finishedAt", 0) - exploration.getLongOrDefault("startedAt", 0);
        lines.add("Chaos exploration " + ("deadline".equals(exploration.getString("finishedBy"))
                ? "finished at its deadline" : "finished")
                  + (ran > 0 ? " after " + TimeUtils.printDuration(ran) : "") + ": " + count
                  + (count == 1 ? " experiment" : " experiments") + ", " + failed + " failed");
        if (experiments != null) {
            for (Object o : experiments) {
                if (o instanceof JsonObject e) {
                    lines.addAll(ListChaos.experimentLines(e, "  "));
                }
            }
        }
        return lines;
    }

    private static boolean failed(JsonObject exploration) {
        List<Object> experiments = exploration != null ? exploration.getCollection("experiments") : null;
        return experiments != null
                && experiments.stream().anyMatch(o -> o instanceof JsonObject jo && "FAIL".equals(jo.getString("verdict")));
    }
}
