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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
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
 * CAMEL-26397: the AI tools read the whole command line of an integration, also when the JDK gives none of its
 * arguments: on Linux the JDK reads one page (4 KB) of /proc/&lt;pid&gt;/cmdline, and camel run started through jbang
 * has a longer command line with its classpath alone; on Windows it gives no arguments of any process. The integrations
 * here have an empty argument, of which the JDK gives no arguments on any OS, and their /proc/&lt;pid&gt;/cmdline is in
 * a directory of the test.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "the integrations are shell scripts")
class IntegrationLauncherCommandLineTest {

    // an integration: it writes its arguments, its pid file and its status file, as the CLI connector does, and runs
    // until its pid file is removed, as camel stop does
    private static final String RUNNING = "d=DIR; echo \"$@\" > \"$d/$$-args\"; touch \"$d/$$\";"
                                          + " echo '{\"context\":{\"name\":\"orders\",\"phase\":5,\"state\":\"Started\"}}'"
                                          + " > \"$d/$$-status.json\";"
                                          + " while [ -f \"$d/$$\" ]; do sleep 0.1; done; rm -f \"$d/$$-status.json\"";

    @TempDir
    Path home;
    @TempDir
    Path project;
    @TempDir
    Path proc;

    private Path previousHome;
    private Supplier<List<String>> previousCommand;
    private Path camelDir;
    private final List<Process> started = new ArrayList<>();

    @BeforeEach
    void useHome() throws IOException {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        camelDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(camelDir);
        previousCommand = IntegrationLauncher.camelCommand;
        // no /proc/<pid>/cmdline, as on macOS and Windows, unless a test writes one
        ProcessHelper.useProcRoot(proc);
    }

    @AfterEach
    void restore() throws IOException {
        started.forEach(Process::destroyForcibly);
        // the integrations the launcher started have a pid file
        try (Stream<Path> files = Files.list(camelDir)) {
            files.map(f -> f.getFileName().toString()).filter(n -> n.matches("\\d+"))
                    .forEach(n -> ProcessHandle.of(Long.parseLong(n)).ifPresent(ProcessHandle::destroyForcibly));
        }
        ProcessHelper.useProcRoot(null);
        IntegrationLauncher.camelCommand = previousCommand;
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void anIntegrationThatCamelRunStartedIsRestartedWithTheCommandItWasStartedWith() {
        String body = RUNNING.replace("DIR", "'" + camelDir + "'");
        IntegrationLauncher.camelCommand = () -> List.of("sh", "-c", body, "sh");
        JsonObject result = IntegrationLauncher.run(project, List.of("orders.camel.yaml"), "orders", false, List.of(""));
        assertThat(result.getString("status")).isEqualTo("started");
        long pid = result.getLong("pid");

        long restarted = restarted(IntegrationLauncher.restart(pid));

        assertThat(restarted).isNotEqualTo(pid);
        assertThat(camelDir.resolve(restarted + "-args")).content().startsWith("run orders.camel.yaml --name=orders");
        // and again: the command of the process the restart started is kept as well
        long again = restarted(IntegrationLauncher.restart(restarted));
        assertThat(camelDir.resolve(again + "-args")).content().startsWith("run orders.camel.yaml --name=orders");
    }

    @Test
    void anIntegrationStartedElsewhereIsRestartedWithItsWholeCommandLine() throws Exception {
        String body = RUNNING.replace("DIR", "'" + camelDir + "'");
        long pid = integration(List.of("sh", "-c", body, "sh", "run", "orders.camel.yaml", "--name=orders", ""));
        await().atMost(Duration.ofSeconds(10)).until(() -> IntegrationLauncher.started(pid));

        long restarted = restarted(IntegrationLauncher.restart(pid));

        assertThat(camelDir.resolve(restarted + "-args")).content().startsWith("run orders.camel.yaml --name=orders");
    }

    @Test
    void anIntegrationInDevModeThatWatchesItsDirectory() throws Exception {
        long pid = runningFromTheProject("run", "--source-dir=.", "--dev", "");

        assertThat(IntegrationLauncher.devModeFrom(project)).extracting(RuntimeHelper.ProcessInfo::pid).isEqualTo(pid);
        assertThat(IntegrationLauncher.watchesFile(pid, "templates/letter.ftl")).isTrue();
        assertThat(alreadyRunning()).contains("In dev mode a changed or added file is reloaded");
    }

    @Test
    void anIntegrationInDevModeThatWatchesItsOwnFiles() throws Exception {
        long pid = runningFromTheProject("run", "orders.camel.yaml", "--dev", "");

        assertThat(IntegrationLauncher.devModeFrom(project)).extracting(RuntimeHelper.ProcessInfo::pid).isEqualTo(pid);
        assertThat(IntegrationLauncher.watchesFile(pid, "orders.camel.yaml")).isTrue();
        assertThat(IntegrationLauncher.watchesFile(pid, "templates/letter.ftl")).isFalse();
        assertThat(alreadyRunning()).contains("It was started with its own files");
    }

    @Test
    void anIntegrationNotInDevMode() throws Exception {
        runningFromTheProject("run", "--source-dir=.", "");

        assertThat(IntegrationLauncher.devModeFrom(project)).isNull();
        assertThat(alreadyRunning()).contains("It is not in dev mode");
    }

    // an integration that this JVM did not start, running from the project directory with these arguments
    private long runningFromTheProject(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("sh", "-c", "sleep 60; exit 0", "sh"));
        cmd.addAll(List.of(args));
        long pid = integration(cmd);
        // its status file, as the CLI connector writes it, with its working directory
        JsonObject runtime = new JsonObject();
        runtime.put("directory", project.toRealPath().toString());
        JsonObject context = new JsonObject();
        context.put("name", "orders");
        context.put("phase", 5);
        context.put("state", "Started");
        JsonObject status = new JsonObject();
        status.put("runtime", runtime);
        status.put("context", context);
        Files.writeString(camelDir.resolve(pid + "-status.json"), status.toJson());
        return pid;
    }

    // starts the command, and puts its command line where Linux has it: /proc/<pid>/cmdline, each argument ending
    // with a NUL byte
    private long integration(List<String> cmd) throws IOException {
        Process p = new ProcessBuilder(cmd).start();
        started.add(p);
        Path dir = Files.createDirectories(proc.resolve(Long.toString(p.pid())));
        Files.writeString(dir.resolve("cmdline"), String.join("\0", cmd) + "\0");
        return p.pid();
    }

    private String alreadyRunning() {
        JsonObject result = new JsonObject();
        assertThat(IntegrationLauncher.alreadyRunning(project, result)).isTrue();
        return result.getString("message");
    }

    // the pid of the process a restart started
    private static long restarted(String answer) {
        assertThat(answer).startsWith("Restarted orders as pid ");
        return Long.parseLong(answer.substring(answer.lastIndexOf(' ') + 1));
    }
}
