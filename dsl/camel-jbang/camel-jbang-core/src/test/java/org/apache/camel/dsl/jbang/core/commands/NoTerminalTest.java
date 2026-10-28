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
import java.time.Duration;

import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Without an interactive terminal, as in a script, a CI job or an AI agent, no command waits for input that never
 * comes: the commands that need a terminal fail fast with exit code 2, and the commands that watch or follow show their
 * output once, or for the --duration. Runs the CLI the way {@code camel} does, through {@link CamelJBangMain#execute}.
 */
class NoTerminalTest {

    private static final String RUN_ONCE
            = "WARN: --watch needs an interactive terminal or --duration, so the output is shown once";

    @TempDir
    Path home;

    private Path previousHome;
    private StringPrinter printer;
    private TestMain main;

    @BeforeEach
    void setup() throws Exception {
        assumeFalse(EnvironmentHelper.isInteractiveTerminal(), "runs without an interactive terminal");
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        printer = new StringPrinter();
        main = new TestMain();
        main.withPrinter(printer);
        main.setDiscoverPlugins(false);
    }

    @AfterEach
    void restore() {
        if (previousHome != null) {
            CommandLineHelper.useHomeDir(previousHome.toString());
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "debug hello.yaml         | camel debug",
            "nano hello.yaml          | camel nano",
            "shell                    | camel shell",
            "ask                      | camel ask without a question",
            "run hello.yaml --prompt  | camel run --prompt",
            "get history --it         | camel get history --it" })
    void interactiveCommandFailsFast(String args, String what) {
        assertThat(run(args.trim().split(" +"))).isEqualTo(CliExitCode.USAGE);

        assertThat(printer.getStderr()).isEqualTo("ERROR: " + what + " requires an interactive terminal");
    }

    @Test
    void watchShowsTheOutputOnce() {
        assertThat(run("ps", "--watch")).isEqualTo(CliExitCode.OK);

        assertThat(printer.getStderr()).startsWith(RUN_ONCE);
    }

    @Test
    void watchOfAnActionShowsTheOutputOnce() {
        // nothing runs, which the one time says
        assertThat(run("cmd", "thread-dump", "--watch")).isEqualTo(CliExitCode.NOT_FOUND);

        assertThat(printer.getStderr()).startsWith(RUN_ONCE);
    }

    @Test
    void watchForTheDuration() {
        StopWatch watch = new StopWatch();

        assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> assertThat(run("ps", "--watch", "--duration=1s")).isEqualTo(CliExitCode.OK));

        assertThat(watch.taken()).isGreaterThanOrEqualTo(1000);
        assertThat(printer.getStderr()).doesNotContain("WARN");
    }

    @Test
    void logDoesNotFollow() {
        assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> assertThat(run("log")).isEqualTo(CliExitCode.OK));

        assertThat(printer.getOutput()).doesNotContain("Waiting for logs");
    }

    @Test
    void logFollowsForTheDuration() {
        StopWatch watch = new StopWatch();

        assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> assertThat(run("log", "--duration=500")).isEqualTo(CliExitCode.OK));

        assertThat(watch.taken()).isGreaterThanOrEqualTo(500);
        assertThat(printer.getOutput()).contains("Waiting for logs");
    }

    @Test
    void readConsoleTaskStopsAtTheEndOfTheInput() {
        // there is no console here, which reads as the end of the input
        boolean[] stopped = new boolean[1];

        new CommandHelper.ReadConsoleTask(() -> stopped[0] = true).run();

        assertThat(stopped[0]).isTrue();
    }

    @Test
    void runUntilTheDuration() throws Exception {
        CommandHelper.RunUntil until = CommandHelper.RunUntil.start(200);
        assertThat(until.isRunning()).isTrue();

        StopWatch watch = new StopWatch();
        while (until.isRunning() && watch.taken() < 10000) {
            Thread.onSpinWait();
        }
        assertThat(watch.taken()).isLessThan(10000);

        CommandHelper.RunUntil forever = CommandHelper.RunUntil.start(0);
        assertThat(forever.isRunning()).isTrue();
        forever.stop();
        assertThat(forever.isRunning()).isFalse();
    }

    private int run(String... args) {
        main.execute(args);
        return main.exitCode;
    }

    private static class TestMain extends CamelJBangMain {
        int exitCode = -1;

        @Override
        public void postAddCommands(CommandLine commandLine, String[] args) {
            // the commands of the CLI only
        }

        @Override
        public void quit(int exitCode) {
            this.exitCode = exitCode;
        }
    }
}
