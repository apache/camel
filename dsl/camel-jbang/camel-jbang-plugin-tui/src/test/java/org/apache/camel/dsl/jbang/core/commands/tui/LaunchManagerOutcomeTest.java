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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A launch has started when the status file of its integration says so (or of a process it started, as a Maven launch
 * runs the app in a child), and failed when it ends with an exit code other than 0. A restart is watched the same way,
 * and camel_control waits for how it went.
 */
@DisabledOnOs(OS.WINDOWS)
class LaunchManagerOutcomeTest {

    @TempDir
    Path home;

    private Path previousHome;
    private final LaunchManager lm = new LaunchManager(List::of);

    @BeforeEach
    void setup() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
    }

    @AfterEach
    void restore() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void startedWhenTheStatusFileSaysSo() throws Exception {
        Process process = new ProcessBuilder("sleep", "30").start();
        try {
            lm.addPendingLaunch("orders", process, home.resolve("orders.log"));
            lm.tick(0);
            assertThat(lm.outcome("orders")).isNull();

            // what the CLI connector writes once Camel has started
            Files.writeString(CommandLineHelper.getCamelDir().resolve(process.pid() + "-status.json"),
                    "{\"context\":{\"name\":\"orders\",\"phase\":5}}");
            lm.tick(0);

            assertThat(lm.outcome("orders").ok()).isTrue();
            assertThat(McpFacade.awaitRestart(lm, "orders")).isEqualTo("Restarted orders");
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void failedWhenItEndsWithAnErrorBeforeItStarted() throws Exception {
        Path log = home.resolve("orders.log");
        Process process = new ProcessBuilder("sh", "-c", "echo Port 8080 is already in use; exit 3")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        lm.addPendingLaunch("orders", process, log);
        lm.tick(0);

        assertThat(lm.outcome("orders").ok()).isFalse();
        // camel_control restart is an error of the tool, with the end of the log
        assertThatThrownBy(() -> McpFacade.awaitRestart(lm, "orders"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageStartingWith("Restart of orders failed: it stopped while starting")
                .hasMessageContaining("Port 8080 is already in use");
    }

    @Test
    void phasesOfAStartedCamel() {
        assertThat(LaunchManager.PendingLaunch.isStarted(status(4))).isFalse();
        assertThat(LaunchManager.PendingLaunch.isStarted(status(5))).isTrue();
        assertThat(LaunchManager.PendingLaunch.isStarted(status(7))).isTrue();
        assertThat(LaunchManager.PendingLaunch.isStarted(status(9))).isFalse();
        assertThat(LaunchManager.PendingLaunch.isStarted(null)).isFalse();
    }

    private static JsonObject status(int phase) {
        JsonObject context = new JsonObject();
        context.put("phase", phase);
        JsonObject root = new JsonObject();
        root.put("context", context);
        return root;
    }
}
