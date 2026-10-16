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
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A command run in this JVM gives what a script running camel gets: the exit code, stdout and stderr apart, and a
 * timeout.
 */
class CliRunnerTest {

    private static final long PID = ProcessHandle.current().pid();

    @TempDir
    Path home;

    private Path previousHome;
    private StringPrinter printer;
    private TestMain main;

    @BeforeEach
    void setup() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        printer = new StringPrinter();
        main = new TestMain();
        main.withPrinter(printer);
        main.setDiscoverPlugins(false);
        // builds the command line of the CLI (its output goes to the printer)
        main.execute("ps", "--json");
    }

    @AfterEach
    void restore() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void outputAndExitCode() {
        CliRunner.CliResult result = CliRunner.run(List.of("ps", "--json"));

        assertThat(result.exitCode()).isEqualTo(CliExitCode.OK);
        assertThat(result.stdout()).isEqualTo("[]");
        assertThat(result.stderr()).isEqualTo("No running Camel integrations");
        assertThat(result.timedOut()).isFalse();
        // the printer of the CLI is put back
        assertThat(main.getOut()).isSameAs(printer);
    }

    @Test
    void notFound() {
        CliRunner.CliResult result = CliRunner.run(List.of("cmd", "reload", "no-such-integration"));

        assertThat(result.exitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).isEqualTo("ERROR: No running Camel integration matches: no-such-integration");
    }

    @Test
    void usageErrorAndHelpOfPicocli() {
        CliRunner.CliResult usage = CliRunner.run(List.of("ps", "--no-such-option"));
        assertThat(usage.exitCode()).isEqualTo(CliExitCode.USAGE);
        assertThat(usage.stderr()).contains("Unknown option: '--no-such-option'");

        CliRunner.CliResult help = CliRunner.run(List.of("ps", "--help"));
        assertThat(help.exitCode()).isEqualTo(CliExitCode.OK);
        assertThat(help.stdout()).contains("Usage: camel ps");
    }

    @Test
    void commandThatDoesNotFinishInTimeIsInterrupted() throws Exception {
        // an integration that never answers
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"),
                "{\"context\":{\"name\":\"test\",\"version\":\"4.24.0\",\"phase\":5}}");

        CliRunner.CliResult result = CliRunner.run(List.of("cmd", "gc", Long.toString(PID), "--timeout=2000"), 300);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(CliExitCode.TIMEOUT);
        assertThat(result.stderr()).startsWith("ERROR: camel cmd gc " + PID + " --timeout=2000 did not finish within 300");
    }

    @Test
    void resultAsJson() {
        JsonObject json = new CliRunner.CliResult(3, "out", "x".repeat(10), 12, false).toJson(5);

        assertThat(json.getInteger("exitCode")).isEqualTo(3);
        assertThat(json.getString("stdout")).isEqualTo("out");
        assertThat(json.getString("stderr")).isEqualTo("xxxxx\n... (truncated)");
        assertThat(json.getLong("elapsedMs")).isEqualTo(12L);
        assertThat(json.getBoolean("timedOut")).isFalse();
    }

    private static class TestMain extends CamelJBangMain {
        @Override
        public void postAddCommands(CommandLine commandLine, String[] args) {
            // the commands of the CLI only
        }

        @Override
        public void quit(int exitCode) {
            // in the test
        }
    }
}
