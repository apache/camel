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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

/**
 * The {@code camel cmd chaos-*} commands: they send the {@code chaos} action of the CLI connector, which adds, changes
 * or removes the rules of camel-chaos in a running integration. Chaos must be switched on in the integration (the
 * camel-chaos dependency with profile dev, or camel.chaos.enabled=true; never with profile prod): the commands cannot
 * switch it on.
 */
abstract class CamelChaosAction extends ActionBaseCommand {

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    protected CamelChaosAction(CamelJBangMain main) {
        super(main);
    }

    /**
     * Sends the chaos command to one integration, and returns its answer. When the integration refuses it (such as a
     * rule that is not valid, with the reason) or does not answer, it throws the CliException of
     * {@link ActionBaseCommand#executeAction}.
     */
    protected JsonObject chaos(CamelProcess process, String command, Consumer<JsonObject> options) {
        return executeAction(process, "chaos", root -> {
            root.put("command", command);
            if (options != null) {
                options.accept(root);
            }
        });
    }

    /**
     * The integrations a command without a name acts on: those that have chaos switched on, as their status says (the
     * CLI connector puts the chaos dev console in it), so the others are not asked. With a name, the integrations that
     * match it, which then answer for themselves (such as "chaos is off").
     */
    protected List<CamelProcess> findChaosProcesses(String name) {
        if (name != null && !name.isBlank() && !"*".equals(name)) {
            return findProcesses(name);
        }
        List<CamelProcess> running = ProcessFinder.find("*");
        List<CamelProcess> answer = running.stream().filter(this::chaosOn).collect(Collectors.toList());
        if (running.isEmpty()) {
            printer().printDiagnostic("No running Camel integrations");
        } else if (answer.isEmpty()) {
            printer().printDiagnostic("No running Camel integration has chaos switched on");
        }
        return answer;
    }

    private boolean chaosOn(CamelProcess process) {
        return ProcessFinder.chaosOn(process);
    }

    /**
     * The one integration that chaos-add, chaos-change and chaos-remove act on: the one the name or pid matches, else
     * the only one running, else the only one with chaos switched on (a rule id is known only in its integration). It
     * fails with exit code 3 when the name matches none or several, and with exit code 2 without a name when it is not
     * clear which one, with the list of the running integrations (see {@link ProcessFinder#requireOneForChaos}).
     */
    protected CamelProcess findChaosTarget(String name) {
        return ProcessFinder.requireOneForChaos(name);
    }

    /**
     * Sends the chaos command to each integration, and prints the answer of each: a line per integration that took it,
     * and the reason on stderr for each one that did not.
     *
     * @return the exit code: 0 when every integration took it, else the code of the first one that did not
     */
    protected int chaosOnEach(List<CamelProcess> processes, String command, Consumer<JsonObject> options) {
        int exit = CliExitCode.OK;
        for (CamelProcess process : processes) {
            ActionResult result = ActionClient.execute(process, "chaos", root -> {
                root.put("command", command);
                if (options != null) {
                    options.accept(root);
                }
            }, timeout());
            String prefix = processes.size() > 1 ? process.name() + " (pid " + process.pid() + "): " : "";
            if (result.isOk()) {
                printer().println(prefix + describe(result.reply()));
            } else {
                printer().printErr(prefix + result.error());
                if (exit == CliExitCode.OK) {
                    exit = result.exitCode();
                }
            }
        }
        return exit;
    }

    /**
     * The line printed for the answer of an integration that took the command.
     */
    protected abstract String describe(JsonObject answer);

    /**
     * The rule of the answer in plain words, after a colon (empty when the integration does not send it, as before
     * Camel 4.24).
     */
    protected static String inWords(JsonObject answer) {
        String words = answer.getString("description");
        return words != null && !words.isBlank() ? ": " + words : "";
    }

    /**
     * The warnings of the answer, one per line after the line of the answer: the rules that match nothing in the
     * integration now (often a typo), which were added anyway.
     */
    protected static String warnings(JsonObject answer) {
        List<Object> warnings = answer.getCollection("warnings");
        if (warnings == null || warnings.isEmpty()) {
            return "";
        }
        return warnings.stream().map(w -> "\n  Warning: " + w).collect(Collectors.joining());
    }
}
