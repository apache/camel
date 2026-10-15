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

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.StdErr;
import org.junitpioneer.jupiter.StdIo;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exit codes and output streams of the Camel CLI: scripts and AI agents rely on them, so they are a contract (see
 * {@link CliExitCode}). Runs the CLI the way {@code camel} does, through {@link CamelJBangMain#execute(String...)}.
 */
class ExitCodeContractTest {

    @TempDir
    Path home;

    private Path previousHome;
    private StringPrinter printer;
    private TestMain main;

    @BeforeEach
    void setup() {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
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
    void success() {
        assertThat(run("test-ok")).isEqualTo(CliExitCode.OK);
        assertThat(printer.getStdout()).isEqualTo("ok");
        assertThat(printer.getStderr()).isEmpty();
    }

    @Test
    void cliExceptionPrintsOneErrorLineOnStderr() {
        assertThat(run("test-fail", "--code=3", "--message=No running Camel integration matches: foo"))
                .isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: No running Camel integration matches: foo");
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void cliExceptionCodesArePassedOn() {
        for (int code : new int[] {
                CliExitCode.FAILURE, CliExitCode.USAGE, CliExitCode.NOT_FOUND, CliExitCode.TIMEOUT }) {
            assertThat(run("test-fail", "--code=" + code)).isEqualTo(code);
        }
    }

    @Test
    void verbosePrintsTheStackTrace() {
        assertThat(run("test-fail", "--code=1", "--message=Validation failed", "--verbose"))
                .isEqualTo(CliExitCode.FAILURE);
        assertThat(printer.getStderr()).startsWith("ERROR: Validation failed")
                .contains(CliException.class.getName())
                .contains("\tat ");
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithStackTrace() {
        assertThat(run("test-crash")).isEqualTo(CliExitCode.INTERNAL);
        assertThat(printer.getStderr()).startsWith("ERROR: Boom")
                .contains(IllegalStateException.class.getName() + ": Boom")
                .contains("\tat ");
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void errorIsInternalError() {
        assertThat(run("test-error")).isEqualTo(CliExitCode.INTERNAL);
        assertThat(printer.getStderr()).startsWith("ERROR: ")
                .contains("org/acme/Missing")
                .contains("\tat ");
    }

    @Test
    void missingFileIsNotFound() {
        assertThat(run("test-missing-file")).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: File not found: routes/missing.yaml");
    }

    @Test
    void noMatchingIntegrationIsNotFound() {
        assertThat(run("cmd", "memory-leak", "no-such-integration")).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: No running Camel integration matches: no-such-integration");
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    @StdIo
    void unknownOptionIsUsageError(StdErr err) {
        assertThat(run("test-ok", "--no-such-option")).isEqualTo(CliExitCode.USAGE);
        assertThat(err.capturedLines()[0]).isEqualTo("Unknown option: '--no-such-option'");
    }

    @Test
    @StdIo
    void unknownCommandIsUsageError(StdErr err) {
        assertThat(run("no-such-command")).isEqualTo(CliExitCode.USAGE);
        assertThat(err.capturedLines()[0]).isEqualTo("Unmatched argument at index 0: 'no-such-command'");
    }

    @Test
    void parentCommandHelpListsItsSubCommands() {
        assertThat(run("catalog")).isEqualTo(CliExitCode.OK);
        assertThat(printer.getStdout()).contains("Usage: camel catalog")
                .contains("component")
                .contains("kamelet");

        printer = new StringPrinter();
        main.withPrinter(printer);
        assertThat(run("cmd")).isEqualTo(CliExitCode.OK);
        assertThat(printer.getStdout()).contains("Usage: camel cmd").contains("send").contains("stop-route");
    }

    @Test
    void parentCommandPassesOnTheExitCodeOfItsDefault() {
        assertThat(run("test-parent")).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: from the default sub-command");
    }

    @Test
    void rootHelpListsTheExitCodes() {
        CommandLine cl = new CommandLine(new CamelJBangMain());
        String help = cl.getUsageMessage(CommandLine.Help.Ansi.OFF);
        assertThat(help).contains("Exit Codes:")
                .containsPattern("\\s0\\s+Success")
                .containsPattern("\\s3\\s+Not found")
                .containsPattern("\\s70\\s+Internal error")
                .containsPattern("124\\s+Timed out");
    }

    @Test
    @StdIo
    void installingAPluginIsNotASuccess(StdErr err) {
        // without plugin discovery (embedded use) the command did not run: it must not look like it succeeded
        PluginHelper.createPluginConfig();
        assertThat(run("kubernetes")).isEqualTo(CliExitCode.FAILURE);
        assertThat(String.join("\n", err.capturedLines())).contains("Installed plugin: kubernetes")
                .contains("Run the command again");
    }

    private int run(String... args) {
        main.execute(args);
        return main.exitCode;
    }

    private static class TestMain extends CamelJBangMain {
        int exitCode = -1;

        @Override
        public void postAddCommands(CommandLine commandLine, String[] args) {
            commandLine.addSubcommand("test-ok", new CommandLine(new OkCommand(this)));
            commandLine.addSubcommand("test-fail", new CommandLine(new FailCommand(this)));
            commandLine.addSubcommand("test-crash", new CommandLine(new CrashCommand(this)));
            commandLine.addSubcommand("test-error", new CommandLine(new ErrorCommand(this)));
            commandLine.addSubcommand("test-missing-file", new CommandLine(new MissingFileCommand(this)));
            commandLine.addSubcommand("test-parent", new CommandLine(new ParentCommand(this)));
        }

        @Override
        public void quit(int exitCode) {
            this.exitCode = exitCode;
        }
    }

    @CommandLine.Command(name = "test-ok")
    static class OkCommand extends CamelCommand {
        OkCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        public Integer doCall() {
            printer().println("ok");
            return CliExitCode.OK;
        }
    }

    @CommandLine.Command(name = "test-fail")
    static class FailCommand extends CamelCommand {
        @CommandLine.Option(names = "--code")
        int code;

        @CommandLine.Option(names = "--message")
        String message = "failed";

        @CommandLine.Option(names = "--verbose")
        boolean verbose;

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
        CrashCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        public Integer doCall() {
            throw new IllegalStateException("Boom");
        }
    }

    @CommandLine.Command(name = "test-error")
    static class ErrorCommand extends CamelCommand {
        ErrorCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        public Integer doCall() {
            throw new NoClassDefFoundError("org/acme/Missing");
        }
    }

    @CommandLine.Command(name = "test-missing-file")
    static class MissingFileCommand extends CamelCommand {
        MissingFileCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        public Integer doCall() throws Exception {
            throw new RuntimeException("Cannot load the routes", new NoSuchFileException("routes/missing.yaml"));
        }
    }

    @CommandLine.Command(name = "test-parent")
    static class ParentCommand extends CamelCommand {
        ParentCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        public Integer doCall() {
            FailCommand child = new FailCommand(getMain());
            return runDefault(child, "--code=3", "--message=from the default sub-command");
        }
    }
}
