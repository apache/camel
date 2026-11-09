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

import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel cmd chaos-explore (CAMEL-26787): camel ask gets the playbook and the options, and the journal is printed when
 * the AI is done.
 */
class ChaosExploreTest {

    @Test
    void camelAskGetsThePlaybookAndTheOptions() {
        ChaosExplore command = new ChaosExplore(new CamelJBangMain());
        CommandLine.populateCommand(command, "shop", "--budget=30m", "--focus=refunds", "--model=qwen3.6",
                "--api-type=ollama", "--tools=core", "--show-tools");

        Ask ask = command.ask(new CamelProcess(4711, "shop", null));

        assertThat(ask.question).singleElement().asString()
                .contains("explore how the integration shop copes", "The exploration is running (30m)", "chaos_experiment");
        assertThat(ask.nameOrPid).isEqualTo("4711");
        assertThat(ask.model).isEqualTo("qwen3.6");
        assertThat(ask.apiType).isEqualTo(LlmClient.ApiType.ollama);
        assertThat(ask.tools).isEqualTo(Ask.ToolSet.core);
        assertThat(ask.showTools).isTrue();
        // an experiment can take minutes, and a run many rounds
        assertThat(ask.timeout).isEqualTo(300);
        assertThat(ask.maxIterations).isEqualTo(100);
        assertThat(ask.yes).isFalse();
        // the exploration runs with the budget of the user: the AI does not get the tool to start another one
        assertThat(ask.hiddenTools).containsExactly("chaos_explore_start");
    }

    @Test
    void theJournalWhenTheAiIsDone() {
        JsonObject failed = new JsonObject(
                Map.of("number", 1, "verdict", "FAIL", "open", false, "endedBy", "ended",
                        "elapsed", 31_000L, "hypothesis", "a refund never fails", "rules", new JsonArray(List.of("r1")),
                        "expectations", new JsonArray(
                                List.of(new JsonObject(
                                        Map.of("expect", "refunds:failed<=0", "state", "unmet", "value", "4 failed"))))));
        JsonObject held = new JsonObject(
                Map.of("number", 2, "verdict", "PASS", "open", false, "endedBy", "ended",
                        "elapsed", 12_000L, "rules", new JsonArray(List.of("r2"))));
        JsonObject exploration = new JsonObject(
                Map.of("running", false, "finishedBy", "deadline",
                        "startedAt", 1_000_000L, "finishedAt", 1_180_000L, "experiments",
                        new JsonArray(List.of(failed, held))));

        assertThat(ChaosExplore.journal(exploration)).containsExactly(
                "Chaos exploration finished at its deadline after 3m0s: 2 experiments, 1 failed",
                "  1 FAIL  a refund never fails (r1, ended after 31s)",
                "    expect refunds:failed<=0: unmet, 4 failed",
                "  2 PASS (r2, ended after 12s)");
        assertThat(ChaosExplore.journal(null)).containsExactly(
                "No journal of the exploration in the status of the integration");
    }
}
