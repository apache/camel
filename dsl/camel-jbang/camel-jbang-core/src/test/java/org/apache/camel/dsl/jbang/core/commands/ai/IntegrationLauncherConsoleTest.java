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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * CAMEL-25818: the console output of an integration that camel_run or camel_control restart started goes to
 * <tt>~/.camel/&lt;pid&gt;-run.log</tt>, as for camel run --background, and is removed when the integration ends. It
 * stayed in a file of its own until this JVM exited.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "the integrations are shell scripts")
class IntegrationLauncherConsoleTest {

    // an integration: it writes its pid file and status file, as the CLI connector does, and runs until its pid file is
    // removed, as camel stop does
    private static final String RUNNING = "d=DIR; touch \"$d/$$\"; echo started;"
                                          + " echo '{\"context\":{\"name\":\"orders\",\"phase\":5,\"state\":\"Started\"}}'"
                                          + " > \"$d/$$-status.json\";"
                                          + " while [ -f \"$d/$$\" ]; do sleep 0.1; done; rm -f \"$d/$$-status.json\"";

    @TempDir
    Path home;
    @TempDir
    Path project;

    private Path previousHome;
    private Supplier<List<String>> previousCommand;
    private Path camelDir;

    @BeforeEach
    void useHome() throws IOException {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        camelDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(camelDir);
        previousCommand = IntegrationLauncher.camelCommand;
    }

    @AfterEach
    void restore() {
        IntegrationLauncher.camelCommand = previousCommand;
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void theOutputOfAStartThatFailedIsInTheAnswerAndThenRemoved() throws Exception {
        integration("echo 'ERROR: Failed to start route route1'; exit 1");

        JsonObject result = run();

        assertThat(result.getString("status")).isEqualTo("failed");
        assertThat(result.getString("cause")).isEqualTo("Failed to start route route1");
        assertThat(result.getString("output")).contains("ERROR: Failed to start route route1");
        assertThat(logFiles()).isEmpty();
    }

    @Test
    void theOutputOfAnIntegrationThatStartedIsRemovedWhenItEnds() throws Exception {
        integration(RUNNING);

        JsonObject result = run();

        assertThat(result.getString("status")).isEqualTo("started");
        long pid = result.getLong("pid");
        Path output = camelDir.resolve(pid + "-run.log");
        // while it runs, as for camel run --background
        assertThat(output).content().contains("started");
        assertThat(logFiles()).containsExactly(pid + "-run.log");

        stop(pid);

        await().atMost(Duration.ofSeconds(10)).until(() -> !Files.exists(output));
    }

    @Test
    void aRestartRemovesTheOutputOfTheProcessThatStopped() throws Exception {
        integration(RUNNING);
        long pid = run().getLong("pid");

        String answer = IntegrationLauncher.restart(pid);

        assertThat(answer).startsWith("Restarted ");
        long restarted = Long.parseLong(answer.substring(answer.lastIndexOf(' ') + 1));
        assertThat(camelDir.resolve(pid + "-run.log")).doesNotExist();
        Path output = camelDir.resolve(restarted + "-run.log");
        assertThat(output).exists();

        stop(restarted);

        await().atMost(Duration.ofSeconds(10)).until(() -> !Files.exists(output));
    }

    private JsonObject run() {
        return IntegrationLauncher.run(project, List.of("orders.camel.yaml"), "orders", false, List.of());
    }

    // the arguments that camel run gets are the arguments of the script ($1, $2, ...), which it does not use
    private void integration(String script) {
        String body = script.replace("DIR", "'" + camelDir + "'");
        IntegrationLauncher.camelCommand = () -> List.of("sh", "-c", body, "sh");
    }

    // as camel stop does
    private void stop(long pid) throws IOException {
        Files.deleteIfExists(camelDir.resolve(Long.toString(pid)));
    }

    private List<String> logFiles() throws IOException {
        try (Stream<Path> files = Files.list(camelDir)) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".log")).toList();
        }
    }
}
