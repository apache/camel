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
        printer().println("Let there be chaos: " + process.name() + " (pid " + process.pid() + "), "
                          + TimeUtils.printDuration(started.reply().getLongOrDefault("budget", 0))
                          + (focus != null && !focus.isBlank() ? ", focus: " + focus : ""));
        int answer;
        try {
            answer = ask(process).doCall();
        } catch (Exception e) {
            // the LLM fails with an exit code; anything else must not cost the report of what was explored so far
            printer().printErr("camel ask failed: " + e.getMessage());
            answer = CliExitCode.FAILURE;
        } finally {
            // the AI may have finished it already; else its rules go now
            ActionClient.execute(process, "chaos", root -> root.put("command", "explore-finish"), 10000);
        }
        JsonObject exploration = ChaosExperimentRun.exploration(RuntimeHelper.readStatus(process.pid()));
        List<String> lines = journal(exploration);
        printer().println();
        lines.forEach(printer()::println);
        if (failed(exploration)) {
            return CliExitCode.FAILURE;
        }
        return answer;
    }

    /** camel ask, with the playbook as its question, for the integration. */
    Ask ask(CamelProcess process) {
        Ask ask = new Ask(getMain());
        ask.question = List.of(ChaosExplorePlaybook.text(ChaosExplorePlaybook.ASK, process.name(), budget, focus, true));
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
        // the exploration runs with the budget of the user: the AI does not start another one
        ask.hiddenTools = Set.of("chaos_explore_start");
        return ask;
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
                lines.addAll(ListChaos.experimentLines((JsonObject) o, "  "));
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
