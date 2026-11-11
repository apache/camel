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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel cmd chaos-explore (CAMEL-26787): camel ask gets the playbook and the options, and the journal is printed when
 * the AI is done.
 */
class ChaosExploreTest {

    @Test
    void camelAskGetsThePlaybookAndTheOptions() {
        ChaosExplore command = new ChaosExplore(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "shop", "--budget=30m", "--focus=refunds", "--model=qwen3.6",
                "--api-type=ollama", "--tools=core", "--show-tools");

        Ask ask = command.ask(new CamelProcess(4711, "shop.camel.yaml", null), "shop");

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
        // the exploration runs with the budget of the user: the AI does not get the tools to start another one
        assertThat(ask.hiddenTools).containsExactlyInAnyOrder("chaos_explore_start", "cli_exec");
    }

    @Test
    void theIntegrationIsNamedAsCamelGetChaosNamesIt() {
        // camel run * names the process after its files, and the CamelContext by --name
        JsonObject status = new JsonObject(Map.of("context", new JsonObject(Map.of("name", "shop"))));
        assertThat(ChaosExplore.displayName(new CamelProcess(1, "chaos.camel.yaml parked.camel.yaml", status)))
                .isEqualTo("shop");
        JsonObject unnamed = new JsonObject(Map.of("context", new JsonObject(Map.of("name", "CamelJBang"))));
        assertThat(ChaosExplore.displayName(new CamelProcess(1, "shop.camel.yaml", unnamed))).isEqualTo("shop.camel.yaml");
        assertThat(ChaosExplore.displayName(new CamelProcess(1, "shop.camel.yaml", null))).isEqualTo("shop.camel.yaml");
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

    @Test
    void whatTheExplorationFoundIsSaved(@TempDir Path dir) throws Exception {
        JsonObject failed = new JsonObject(
                Map.of("number", 1, "verdict", "FAIL", "open", false, "endedBy", "ended",
                        "hypothesis", "nothing is lost after the hand-off", "rules", new JsonArray(List.of("r1", "r2")),
                        "ruleTexts", new JsonArray(
                                List.of("route=shipping error rate=0.2 until=30s"
                                        + " expect=orders->shipping:lost<=0 within=45s",
                                        "endpoint=seda:dlq error at=dlq until=30s")),
                        "expectations", new JsonArray(
                                List.of(new JsonObject(
                                        Map.of("expect", "orders->shipping:lost<=0", "state", "unmet", "value", "11 lost")))),
                        "note", "the seda hand-off has no error handling"));
        JsonObject held = new JsonObject(
                Map.of("number", 2, "verdict", "PASS", "open", false,
                        "rules", new JsonArray(List.of("r3")), "ruleTexts", new JsonArray(List.of("route=refunds delay"))));
        JsonObject exploration = new JsonObject(
                Map.of("running", false, "finishedBy", "finished",
                        "experiments", new JsonArray(List.of(failed, held))));
        ChaosExplore.Run run = new ChaosExplore.Run("shop", "10m", "orders", "qwen3.6 (ollama)", -42L);

        ChaosExplore.Saved saved = ChaosExplore.save(dir, LocalDateTime.of(2026, 11, 9, 14, 2, 30), run, exploration,
                "## Summary\n1 broke", List.of("Chaos exploration finished: 2 experiments, 1 failed"));

        assertThat(saved.report()).isEqualTo(dir.resolve("reports/explore-20261109-140230.md"));
        assertThat(Files.readString(saved.report())).contains("# Chaos exploration: shop",
                "2026-11-09 14:02 · budget 10m · focus orders · qwen3.6 (ollama) · seed -42",
                "## Report\n\n## Summary\n1 broke", "Chaos exploration finished: 2 experiments, 1 failed",
                "## Scenarios", "camel run * --chaos=" + dir.resolve("explore-20261109-140230-1.chaos"));
        JsonObject journal = (JsonObject) Jsoner.deserialize(Files.readString(saved.journal()));
        assertThat(journal.getString("integration")).isEqualTo("shop");
        assertThat(journal.getLong("seed")).isEqualTo(-42);
        assertThat(journal.get("exploration")).isInstanceOf(Map.class);
        // only the experiment that failed is a scenario: comments, the seed, and its rules together
        assertThat(saved.scenarios()).containsExactly(dir.resolve("explore-20261109-140230-1.chaos"));
        assertThat(Files.readString(saved.scenarios().get(0))).isEqualTo(
                "# From the chaos exploration of shop on 2026-11-09 14:02, experiment 1: FAIL\n"
                                                                         + "# hypothesis: nothing is lost after the hand-off\n"
                                                                         + "# expect orders->shipping:lost<=0: unmet, 11 lost\n"
                                                                         + "# note: the seda hand-off has no error handling\n"
                                                                         + "# replay: camel run * --chaos="
                                                                         + dir.resolve("explore-20261109-140230-1.chaos")
                                                                         + " (and send it messages as the experiment did,"
                                                                         + " when its routes do not feed themselves)\n"
                                                                         + "seed=-42\n"
                                                                         + "route=shipping error rate=0.2 until=30s"
                                                                         + " expect=orders->shipping:lost<=0 within=45s\n"
                                                                         + "endpoint=seda:dlq error at=dlq until=30s\n");
    }

    @Test
    void saveIsOnByDefault() {
        ChaosExplore command = new ChaosExplore(new CamelJBangMain());
        CommandLineTestHelper.parse(command);
        assertThat(command.save).isTrue();
        CommandLineTestHelper.parse(command, "--no-save");
        assertThat(command.save).isFalse();
    }
}
