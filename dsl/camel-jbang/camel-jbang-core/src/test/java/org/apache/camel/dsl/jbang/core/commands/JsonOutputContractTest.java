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

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.StdErr;
import org.junitpioneer.jupiter.StdIo;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSON mode of the Camel CLI: with {@code --json}, stdout is exactly one JSON document, also when the command
 * fails, so a script or an AI agent can parse it. Runs the CLI the way {@code camel} does, through
 * {@link CamelJBangMain#execute(String...)}.
 */
class JsonOutputContractTest {

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
    }

    @AfterEach
    void restore() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void errorIsAJsonDocumentOnStdout() {
        assertThat(run("test-fail", "--json", "--code=3", "--message=No running Camel integration matches: foo"))
                .isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(stdoutObject()).containsEntry("status", "error")
                .containsEntry("message", "No running Camel integration matches: foo");
        assertThat(stdoutObject().getInteger("code")).isEqualTo(CliExitCode.NOT_FOUND);
        // the plain error is still on stderr
        assertThat(printer.getStderr()).isEqualTo("ERROR: No running Camel integration matches: foo");
    }

    @Test
    void errorWithoutJsonModeKeepsStdoutEmpty() {
        assertThat(run("test-fail", "--code=1")).isEqualTo(CliExitCode.FAILURE);
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void unexpectedErrorIsAJsonDocumentWithTheStackTraceOnStderr() {
        assertThat(run("test-crash", "--json")).isEqualTo(CliExitCode.INTERNAL);
        assertThat(stdoutObject()).containsEntry("message", "Boom");
        assertThat(stdoutObject().getInteger("code")).isEqualTo(CliExitCode.INTERNAL);
        assertThat(printer.getStderr()).startsWith("ERROR: Boom").contains("\tat ");
    }

    @Test
    @StdIo
    void usageErrorIsAJsonDocument(StdErr err) {
        assertThat(run("test-fail", "--json", "--no-such-option")).isEqualTo(CliExitCode.USAGE);
        assertThat(stdoutObject()).containsEntry("status", "error")
                .containsEntry("message", "Unknown option: '--no-such-option'");
        assertThat(stdoutObject().getInteger("code")).isEqualTo(CliExitCode.USAGE);
        // the usage is on stderr
        assertThat(err.capturedLines()[0]).isEqualTo("Unknown option: '--no-such-option'");
    }

    @Test
    @StdIo
    void usageErrorBeforeTheJsonOptionIsAJsonDocument(StdErr err) {
        assertThat(run("test-fail", "--code=oops", "--json")).isEqualTo(CliExitCode.USAGE);
        assertThat(stdoutObject().getInteger("code")).isEqualTo(CliExitCode.USAGE);
    }

    @Test
    void emptyListIsAnEmptyArray() {
        // no integrations are running in this home directory
        for (String[] args : new String[][] {
                { "ps", "--json" }, { "get", "route", "--json" }, { "get", "route", "--error", "--json" },
                { "top", "route", "--json" }, { "top", "group", "--json" }, { "get", "health", "--trace", "--json" },
                { "get", "count", "--total", "--json" }, { "infra", "get", "--json" } }) {
            printer = new StringPrinter();
            main.withPrinter(printer);
            assertThat(run(args)).as(String.join(" ", args)).isEqualTo(CliExitCode.OK);
            assertThat(printer.getStdout()).as(String.join(" ", args)).isEqualTo("[]");
        }
    }

    @Test
    void catalogWithoutMatchIsAnEmptyArrayAndTheHintOnStderr() {
        assertThat(run("catalog", "component", "--filter=no-such-component-xyz", "--json")).isEqualTo(CliExitCode.OK);
        assertThat(printer.getStdout()).isEqualTo("[]");
        assertThat(printer.getStderr()).contains("No results for filter: no-such-component-xyz");
    }

    @Test
    void infraServiceThatIsNotRunningIsNotFound() {
        assertThat(run("infra", "get", "kafka", "--json")).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(stdoutObject()).containsEntry("message", "No running infra service matches: kafka");
    }

    @Test
    void configGetAndList() throws Exception {
        // the user configuration of this home directory
        Files.writeString(home.resolve(CommandLineHelper.USER_CONFIG), "camel-version=4.24.0\nruntime=quarkus\n");

        assertThat(run("config", "get", "runtime", "--json")).isEqualTo(CliExitCode.OK);
        assertThat(stdoutObject()).containsEntry("key", "runtime").containsEntry("value", "quarkus");

        printer = new StringPrinter();
        main.withPrinter(printer);
        assertThat(run("config", "get", "no-such-key", "--json")).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(stdoutObject()).containsEntry("message", "no-such-key key not found");

        printer = new StringPrinter();
        main.withPrinter(printer);
        assertThat(run("config", "list", "--json")).isEqualTo(CliExitCode.OK);
        JsonArray rows = stdoutArray();
        assertThat(rows).hasSize(2);
        JsonObject first = rows.getMap(0);
        assertThat(first).containsEntry("key", "camel-version").containsEntry("value", "4.24.0")
                .containsEntry("scope", "global");
        JsonObject second = rows.getMap(1);
        assertThat(second).containsEntry("key", "runtime");
    }

    @Test
    void docOfAComponent() {
        assertThat(run("doc", "timer", "--json")).isEqualTo(CliExitCode.OK);
        assertThat(stdoutObject()).containsEntry("kind", "component").containsEntry("name", "timer");

        printer = new StringPrinter();
        main.withPrinter(printer);
        assertThat(run("doc", "component:no-such-component-xyz", "--json")).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(stdoutObject()).containsEntry("status", "error");
        assertThat(stdoutObject().getString("message")).startsWith("Camel component: no-such-component-xyz not found");
    }

    @Test
    void pluginList() {
        assertThat(run("plugin", "get", "--json")).isEqualTo(CliExitCode.OK);
        assertThat(printer.getStdout()).isEqualTo("[]");

        printer = new StringPrinter();
        main.withPrinter(printer);
        assertThat(run("plugin", "list", "--json")).isEqualTo(CliExitCode.OK);
        JsonArray rows = stdoutArray();
        assertThat(rows).isNotEmpty();
        assertThat(rows.stream().map(o -> ((JsonObject) o).getString("name"))).contains("kubernetes");
        JsonObject first = rows.getMap(0);
        assertThat(first).containsEntry("status", "available");
    }

    @Test
    void overviewSavesWithTheMessageOnStderr() throws Exception {
        Path project = home.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("hello.camel.yaml"), """
                - route:
                    id: hello
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: Hello
                """);

        assertThat(run("overview", project.toString(), "--format=json", "--save")).isEqualTo(CliExitCode.OK);
        assertThat(stdoutObject()).isNotEmpty();
        assertThat(printer.getStderr()).isEqualTo("Saved camel-summary.md");
        assertThat(project.resolve("camel-summary.md")).exists();
    }

    @Test
    void overviewWithoutRoutesIsAJsonError() throws Exception {
        Path project = home.resolve("empty");
        Files.createDirectories(project);

        assertThat(run("overview", project.toString(), "--format=json")).isEqualTo(CliExitCode.FAILURE);
        assertThat(stdoutObject().getString("message")).startsWith("No routes found in ");
    }

    private JsonObject stdoutObject() {
        Object doc = Jsoner.deserialize(printer.getStdout(), (JsonObject) null);
        assertThat(doc).as("stdout must be one JSON object: " + printer.getStdout()).isNotNull();
        return (JsonObject) doc;
    }

    private JsonArray stdoutArray() {
        Object doc = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        assertThat(doc).as("stdout must be one JSON array: " + printer.getStdout()).isNotNull();
        return (JsonArray) doc;
    }

    private int run(String... args) {
        main.execute(args);
        return main.exitCode;
    }

    private static class TestMain extends CamelJBangMain {
        int exitCode = -1;

        @Override
        public void postAddCommands(CommandLine commandLine, String[] args) {
            commandLine.addSubcommand("test-fail", new CommandLine(new FailCommand(this)));
            commandLine.addSubcommand("test-crash", new CommandLine(new CrashCommand(this)));
        }

        @Override
        public void quit(int exitCode) {
            this.exitCode = exitCode;
        }
    }

    @CommandLine.Command(name = "test-fail")
    static class FailCommand extends CamelCommand {
        @CommandLine.Option(names = "--code")
        int code;

        @CommandLine.Option(names = "--message")
        String message = "failed";

        @CommandLine.Option(names = "--json")
        boolean json;

        FailCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        public Integer doCall() {
            throw new CliException(code, message);
        }
    }

    @CommandLine.Command(name = "test-crash")
    static class CrashCommand extends CamelCommand {
        @CommandLine.Option(names = "--json")
        boolean json;

        CrashCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        public Integer doCall() {
            throw new IllegalStateException("Boom");
        }
    }
}
