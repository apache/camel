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
import org.apache.camel.dsl.jbang.core.common.PreviousRun;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-25784: the AI tools read the log of the previous run of an integration that is not running, such as one that
 * died at startup, and camel_run and camel_control restart wait until Camel has started.
 */
class PreviousRunToolsTest {

    // a pid no process has (above the highest pid of Linux and macOS)
    private static final long GONE_PID = 999_999_991L;

    @TempDir
    Path home;

    private Path previousHome;

    @BeforeEach
    void useHome() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void theLogOfAnIntegrationThatIsNotRunningIsTheLogOfItsPreviousRun() throws Exception {
        keepPreviousRun("orders", GONE_PID);

        JsonObject log = call("camel_get_log", new ToolContext(), Map.of("name", "orders"));

        assertThat(log.getString("message"))
                .startsWith("orders is not running. This is the log of its previous run (pid " + GONE_PID
                            + "), which ended at ")
                .endsWith(" with exit code 70: Failed to start route: route1 because: boom");
        JsonObject previous = log.getMap("previous");
        assertThat(previous.getString("name")).isEqualTo("orders");
        assertThat(previous.getInteger("exitCode")).isEqualTo(70);
        assertThat(previous.getString("failure")).isEqualTo("Failed to start route: route1 because: boom");
        assertThat(log.getString("file")).endsWith("orders-previous.log");
        // newest first
        JsonObject newest = (JsonObject) log.getCollection("lines").iterator().next();
        assertThat(newest.getString("level")).isEqualTo("ERROR");
        assertThat(newest.getString("message")).contains("Failed to start route route1");
    }

    @Test
    void theLogOfThePreviousRunCanBeAskedFor() throws Exception {
        keepPreviousRun("orders", GONE_PID);

        JsonObject log = call("camel_get_log", new ToolContext(), Map.of("name", "orders", "previous", "true"));

        assertThat(log.getString("message"))
                .startsWith("This is the log of its previous run (pid " + GONE_PID + "), which ended at ");
    }

    @Test
    void theIntegrationSelectedBeforeThatIsGoneHasTheLogOfItsPreviousRun() throws Exception {
        keepPreviousRun("orders", GONE_PID);
        ToolContext ctx = new ToolContext();
        // such as the pid camel_run answered with, of an integration that then died at startup
        ctx.selectProcess(GONE_PID);

        JsonObject log = call("camel_get_log", ctx, Map.of());

        assertThat(log.getString("message"))
                .startsWith("This is the log of its previous run (pid " + GONE_PID + "), which ended at ");
    }

    @Test
    void anIntegrationWithoutPreviousRunIsNotRunning() throws Exception {
        keepPreviousRun("orders", GONE_PID);

        assertThatThrownBy(() -> call("camel_get_log", new ToolContext(), Map.of("name", "payments")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("No running Camel process matches 'payments'; list_processes shows what runs");
        assertThatThrownBy(() -> call("camel_get_log", new ToolContext(), Map.of("name", "payments", "previous", "true")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("No previous run matches 'payments'");
    }

    @Test
    void theErrorsOfAnIntegrationThatIsNotRunningSayHowItsPreviousRunEnded() throws Exception {
        keepPreviousRun("orders", GONE_PID);

        assertThatThrownBy(() -> ToolRegistry.execute("camel_get_errors", new ToolContext(), Map.of("name", "orders")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageStartingWith("No running Camel process matches 'orders'; list_processes shows what runs."
                                        + " Its previous run (pid " + GONE_PID + ") ended at ")
                .hasMessageEndingWith(
                        " with exit code 70: Failed to start route: route1 because: boom. camel_get_log shows its log.");
    }

    @Test
    void anIntegrationHasStartedWhenItsCamelContextHas() throws Exception {
        // the CLI connector writes the status file while Camel starts, before the routes start
        status(GONE_PID, "Starting");
        assertThat(IntegrationLauncher.started(GONE_PID)).isFalse();
        status(GONE_PID, "Started");
        assertThat(IntegrationLauncher.started(GONE_PID)).isTrue();
        // an older Camel without the state
        status(GONE_PID, null);
        assertThat(IntegrationLauncher.started(GONE_PID)).isTrue();
        // no status file yet
        assertThat(IntegrationLauncher.started(GONE_PID + 1)).isFalse();
    }

    // a run of the name that failed to start
    private static void keepPreviousRun(String name, long pid) throws Exception {
        String record = " " + pid + " --- [           main] org.apache.camel.main.MainSupport   : ";
        Path log = CommandLineHelper.getCamelDir().resolve("4242.log");
        Files.write(log, List.of("2026-10-19 12:47:11.498  INFO" + record + "Apache Camel 4.24.0 is starting",
                "2026-10-19 12:47:11.901 ERROR" + record + "Failed to start route route1"));
        PreviousRun.keep(log, name, pid, 70, "Failed to start route: route1 because: boom");
    }

    private static void status(long pid, String state) throws Exception {
        JsonObject context = new JsonObject();
        context.put("name", "orders");
        if (state != null) {
            context.put("state", state);
        }
        JsonObject root = new JsonObject();
        root.put("context", context);
        Files.writeString(CommandLineHelper.getCamelDir().resolve(pid + "-status.json"), root.toJson());
    }

    private static JsonObject call(String tool, ToolContext ctx, Map<String, String> args) throws Exception {
        return (JsonObject) Jsoner.deserialize(String.valueOf(ToolRegistry.execute(tool, ctx, new HashMap<>(args))));
    }
}
