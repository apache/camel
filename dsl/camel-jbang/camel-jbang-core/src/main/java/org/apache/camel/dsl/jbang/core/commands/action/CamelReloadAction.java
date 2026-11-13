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

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "reload", description = "Trigger reloading Camel", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel cmd reload",
                           "  camel cmd reload myapp --wait",
                           "  camel cmd reload myapp --wait --json" })
public class CamelReloadAction extends ActionBaseCommand {

    @Argument(description = "Name or pid of running Camel integration. (default selects all)")
    String name = "*";

    @Option(name = "wait", hasValue = false,
            description = "Reload the files changed since the last scan of dev mode now, and wait for the outcome: the"
                          + " files reloaded, or why the reload failed")
    boolean waitForOutcome;

    @Option(name = "json", hasValue = false, description = "The outcome of --wait as JSON")
    boolean jsonOutput;

    @Mixin
    ActionTimeoutMixin timeoutOption;

    public CamelReloadAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (!waitForOutcome) {
            if (jsonOutput) {
                throw CliException.usage("--json is the outcome of --wait: use both");
            }
            return sendAction(findProcesses(name), "reload", null);
        }
        CamelProcess process = findOne(name);
        String version = process.camelVersion();
        if (version != null && !ActionClient.isAtLeast(version, 4, 24)) {
            throw CliException.failure("The integration " + process.name() + " runs Camel " + version
                                       + ", which reloads the changed files at its next scan only: --wait needs Camel"
                                       + " 4.24 or later");
        }
        // a context reload rebuilds the routes from what was loaded: the changed files are what an edit needs
        ActionResult result = ActionClient.execute(process, "reload", o -> o.put("changed", true), timeout());
        JsonObject outcome = outcome(process, result);
        boolean failed = "failed".equals(outcome.getString("status"));
        if (jsonOutput) {
            JsonOutput.print(printer(), outcome);
            return failed ? CliExitCode.FAILURE : CliExitCode.OK;
        }
        if (failed) {
            throw CliException.failure("Reloading " + process.name() + " failed: " + outcome.getString("error"));
        }
        JsonArray files = outcome.getCollection("files");
        if (files.isEmpty()) {
            printer().println("No files of " + process.name() + " changed since they were last reloaded");
        } else {
            printer().println("Reloaded " + process.name() + ":");
            for (Object file : files) {
                printer().println("  " + file);
            }
        }
        return CliExitCode.OK;
    }

    /**
     * The outcome of a reload of the changed files: <tt>reloaded</tt> with the files, <tt>unchanged</tt> when no file
     * changed, or <tt>failed</tt> with the error and the files whose reload failed.
     *
     * @throws CliException when the integration did not answer in time, or stopped (see {@link ActionResult#orThrow()})
     */
    static JsonObject outcome(CamelProcess process, ActionResult result) {
        JsonObject outcome = new JsonObject();
        outcome.put("pid", process.pid());
        outcome.put("name", process.name());
        if (result.status() == ActionClient.Status.ERROR && !result.isNotFound()) {
            JsonObject partial = result.reply() != null && result.reply().get("result") instanceof JsonObject jo ? jo : null;
            outcome.put("status", "failed");
            outcome.put("files", partial != null && partial.getCollection("files") != null
                    ? partial.getCollection("files") : new JsonArray());
            outcome.put("error", result.error());
            return outcome;
        }
        JsonObject reply = result.orThrow();
        JsonArray files = reply != null ? reply.getCollection("files") : null;
        if (files == null) {
            // a context reload, by a connector of a Camel before this option
            throw CliException.failure("The integration " + process.name()
                                       + " does not reload the changed files on demand: it reloads them at its next"
                                       + " scan");
        }
        outcome.put("status", files.isEmpty() ? "unchanged" : "reloaded");
        outcome.put("files", files);
        return outcome;
    }

}
