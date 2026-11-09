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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tools of a chaos exploration (CAMEL-26786) against an integration that answers their actions: the exploration
 * starts and finishes, and an experiment runs in one call, with its traffic and the wait for its verdict in the status.
 */
class ChaosExploreToolsTest {

    private static final long PID = 4713L;

    @TempDir
    Path home;

    private Path previousHome;
    private FakeIntegration integration;

    @BeforeEach
    void start() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        integration = new FakeIntegration(PID, ChaosExploreToolsTest::answer);
    }

    @AfterEach
    void stop() throws Exception {
        integration.close();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    /** The answers of an integration with chaos on, to the chaos commands and the send. */
    private static Object answer(JsonObject action) {
        if ("send".equals(action.getString("action"))) {
            return Map.of("status", "failed", "endpoint", "direct://refunds", "messages", 10, "sent", 10, "failed", 2);
        }
        return switch (action.getString("command")) {
            case "explore-start" -> Map.of("running", true, "budget", 300_000, "focus", "refunds");
            case "explore-finish" -> Map.of("running", false, "finishedBy", "finished", "rules", List.of("r1"));
            case "experiment-begin" -> experiment("PENDING", true);
            case "experiment-end" -> experiment("FAIL", false);
            default -> Map.of("status", "failed", "error", "Unknown chaos command");
        };
    }

    private static Map<String, Object> experiment(String verdict, boolean open) {
        return Map.of("number", 1, "hypothesis", "a refund never fails", "verdict", verdict, "open", open,
                "rules", List.of("r1"), "expectations",
                List.of(Map.of("expect", "refunds:failed<=0", "state", open ? "pending" : "unmet", "actual", 2)));
    }

    private static Object call(String tool, Map<String, String> args) {
        ToolContext ctx = new ToolContext();
        ctx.selectProcess(PID);
        return ToolRegistry.execute(tool, ctx, new HashMap<>(args));
    }

    private JsonObject lastChaosCommand(String command) {
        return integration.actions().stream().filter(a -> command.equals(a.getString("command"))).reduce((a, b) -> b)
                .orElseThrow();
    }

    @Test
    void anExplorationStartsAndFinishes() throws Exception {
        String started = call("chaos_explore_start", Map.of("budget", "5m", "focus", "refunds")).toString();

        assertThat(started).contains("\"running\":true");
        JsonObject action = lastChaosCommand("explore-start");
        assertThat(action.getString("action")).isEqualTo("chaos");
        assertThat(action.getString("budget")).isEqualTo("5m");
        assertThat(action.getString("focus")).isEqualTo("refunds");

        assertThat(call("chaos_explore_finish", Map.of()).toString()).contains("\"finishedBy\":\"finished\"");
    }

    @Test
    void anExperimentInOneCall() throws Exception {
        // the status shows the verdict decided: the wait ends at the first look
        JsonObject exploration = new JsonObject(
                Map.of("running", true, "timeLeft", 240_000,
                        "experiments", List.of(experiment("FAIL", true))));
        JsonObject status = new JsonObject(
                Map.of("chaos",
                        Map.of("enabled", true, "report", Map.of("exploration", exploration))));
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"), status.toJson());

        String answer = call("chaos_experiment", Map.of(
                "hypothesis", "a refund never fails",
                "rule", "route=refunds error rate=every:5 expect=refunds:failed<=0 within=20s",
                "feed", "refunds", "body", "R-1", "repeat", "10", "interval", "100ms",
                "note", "no error handler")).toString();

        JsonObject result = (JsonObject) Jsoner.deserialize(answer);
        assertThat(result.getString("verdict")).isEqualTo("FAIL");
        assertThat(result.getLong("timeLeft")).isEqualTo(240_000);
        assertThat(((JsonObject) result.get("traffic")).getLong("sent")).isEqualTo(10);
        // begin, the messages, and the end with the note
        assertThat(integration.actions()).extracting(a -> a.getString("command") != null
                ? a.getString("command") : a.getString("action"))
                .containsExactly("experiment-begin", "send", "experiment-end");
        JsonObject begin = lastChaosCommand("experiment-begin");
        assertThat(begin.getString("hypothesis")).isEqualTo("a refund never fails");
        assertThat(begin.getString("rule")).startsWith("route=refunds error");
        JsonObject send = integration.actions().get(1);
        assertThat(send.getString("endpoint")).isEqualTo("refunds");
        assertThat(send.getLong("repeat")).isEqualTo(10);
        assertThat(send.getLong("interval")).isEqualTo(100);
        assertThat(lastChaosCommand("experiment-end").getString("note")).isEqualTo("no error handler");
    }
}
