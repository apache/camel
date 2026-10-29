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
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-26396: the process that runs the tools (the MCP server, camel ask, the TUI) is never taken for an integration.
 * The scratch context of a local evaluation ran with the CLI connector, which wrote the status file of this process and
 * sometimes left it behind: the MCP server then listed itself, evaluated the next expression in itself (waiting 10 s
 * for its own answer), and camel_control kill of its pid failed with an internal error.
 */
class ThisProcessIsNoIntegrationTest {

    private static final long PID = ProcessHandle.current().pid();
    private static final String THIS_PROCESS = "pid " + PID + " is the process that runs these tools";

    @TempDir
    Path home;

    private Path previousHome;

    @BeforeEach
    void aStatusFileOfThisProcess() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Path camelDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(camelDir);
        // as the scratch context left it: named after the context, which had not stopped yet
        JsonObject context = new JsonObject();
        context.put("name", "camel-14");
        context.put("phase", 4);
        context.put("state", "Starting");
        JsonObject status = new JsonObject();
        status.put("context", context);
        Files.writeString(camelDir.resolve(PID + "-status.json"), status.toJson());
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void thisProcessIsNotListed() {
        assertThat(RuntimeHelper.discoverProcesses()).isEmpty();
        assertThat(RuntimeHelper.findProcess(null)).isNull();
        assertThat(RuntimeHelper.findProcess(String.valueOf(PID))).isNull();
        assertThat(ToolRegistry.execute("list_processes", new ToolContext(), Map.of()))
                .asString().startsWith("No running Camel processes found");

        ToolContext ctx = new ToolContext();
        assertThat(ctx.selectSingleProcessIfNone()).isFalse();
        assertThat(ctx.hasProcess()).isFalse();
    }

    @Test
    void aCallThatNamesThisProcessIsRefused() {
        ToolContext ctx = new ToolContext();
        assertThatThrownBy(() -> ctx.selectProcess(String.valueOf(PID)))
                .isInstanceOf(ToolExecutionException.class).hasMessageStartingWith(THIS_PROCESS);
        assertThat(ctx.hasProcess()).isFalse();

        assertThatThrownBy(() -> ToolRegistry.execute("select_process", ctx, Map.of("name", String.valueOf(PID))))
                .isInstanceOf(ToolExecutionException.class).hasMessageStartingWith(THIS_PROCESS);
        assertThatThrownBy(() -> ToolRegistry.execute("camel_eval_expression", ctx,
                Map.of("expression", "${body}", "name", String.valueOf(PID))))
                .isInstanceOf(ToolExecutionException.class).hasMessageStartingWith(THIS_PROCESS);
        assertThatThrownBy(() -> ToolRegistry.execute("camel_control", ctx,
                Map.of("action", "kill", "name", String.valueOf(PID))))
                .isInstanceOf(ToolExecutionException.class).hasMessageStartingWith(THIS_PROCESS);
    }

    @Test
    void anExpressionWithoutANameIsEvaluatedLocally() {
        JsonObject result = Jsoner.deserialize(String.valueOf(ToolRegistry.execute("camel_eval_expression",
                new ToolContext(), Map.of("expression", "Hello ${body}", "body", "Camel"))), (JsonObject) null);
        assertThat(result.getString("evaluatedIn")).isEqualTo("a local scratch context (no integration selected)");
        assertThat(result.getString("status")).isEqualTo("ok");
        assertThat(result.getString("result")).isEqualTo("Hello Camel");
    }

    @Test
    void theScratchContextRunsWithoutTheCliConnector(@TempDir Path userHome) {
        String previousUserHome = System.getProperty("user.home");
        // where the CLI connector writes the files of this process while a context runs
        System.setProperty("user.home", userHome.toString());
        try {
            JsonObject result = ExpressionEvaluator.evaluate(new ToolContext(), "simple", "Hello ${body}", "Camel");
            assertThat(result.getString("status")).isEqualTo("ok");
        } finally {
            System.setProperty("user.home", previousUserHome);
        }
        assertThat(userHome.resolve(".camel")).doesNotExist();
    }

    @Test
    void noActionControlsThisProcess() {
        ToolContext ctx = new ToolContext();
        // selected anyway, as a caller that has a pid of its own can
        ctx.selectProcess(PID);
        // kill first: the JDK refuses to kill this process, and restart would start a copy of it
        for (String action : List.of("kill", "stop", "restart", "reload", "stop-routes", "start-routes", "reset-stats")) {
            assertThatThrownBy(() -> IntegrationLauncher.control(ctx, action)).as(action)
                    .isInstanceOf(ToolExecutionException.class).hasMessageStartingWith(THIS_PROCESS);
        }
    }
}
