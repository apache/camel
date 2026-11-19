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

import org.aesh.command.CommandDefinition;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.main.download.DownloadException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.StdErr;
import org.junitpioneer.jupiter.StdIo;

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
                CliExitCode.FAILURE, CliExitCode.USAGE, CliExitCode.NOT_FOUND, CliExitCode.FILE_NOT_FOUND,
                CliExitCode.TIMEOUT }) {
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
        assertThat(run("test-missing-file")).isEqualTo(CliExitCode.FILE_NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: File not found: routes/missing.yaml");
    }

    @Test
    void dependencyThatCannotBeDownloadedIsFailure() {
        assertThat(run("test-download")).isEqualTo(CliExitCode.FAILURE);
        // the message of the download failure, without the stack trace
        assertThat(printer.getStderr()).isEqualTo(
                "ERROR: Cannot download dependency: com.example:missing:1.0 due to: Could not find artifact");
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
        assertThat(printer.getStderr().lines().findFirst()).hasValue("ERROR: The option --no-such-option is unknown.");
    }

    @Test
    @StdIo
    void unknownCommandIsUsageError(StdErr err) {
        assertThat(run("no-such-command")).isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr().lines().findFirst().orElse(""))
                .startsWith("ERROR: 'camel no-such-command' is not a subcommand of 'camel'.");
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
    void rootHelpListsTheExitCodes() throws Exception {
        CommandLineParser<CommandInvocation> parser = new CommandTree(new CamelJBangMain()).parser();
        parser.updateAnsiMode(false);
        String help = parser.printHelp();
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
        assertThat(printer.getStderr()).contains("Installed plugin: kubernetes")
                .contains("Run the command again");
    }

    @Test
    void aMissingArgumentIsNamedByItsLabel() {
        // CAMEL-26931: without a label the error held the description of the argument ("Service name are required")
        assertThat(run("infra", "run")).isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr()).contains("ERROR: Missing argument: Service name", "<service>")
                .doesNotContain("are required");
        assertThat(run("config", "get")).isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr()).contains("ERROR: Missing argument: Configuration key", "<key>");
    }

    private int run(String... args) {
        main.execute(args);
        return main.exitCode;
    }

    private static class TestMain extends CamelJBangMain {
        int exitCode = -1;

        @Override
        public void postAddCommands(CommandTree commandLine, String[] args) {
            commandLine.addSubcommand("test-ok", new CommandTree(command(new OkCommand())));
            commandLine.addSubcommand("test-fail", new CommandTree(command(new FailCommand())));
            commandLine.addSubcommand("test-crash", new CommandTree(command(new CrashCommand())));
            commandLine.addSubcommand("test-error", new CommandTree(command(new ErrorCommand())));
            commandLine.addSubcommand("test-missing-file", new CommandTree(command(new MissingFileCommand())));
            commandLine.addSubcommand("test-download", new CommandTree(command(new DownloadCommand())));
            commandLine.addSubcommand("test-parent", new CommandTree(command(new ParentCommand())));
        }

        @Override
        public void quit(int exitCode) {
            this.exitCode = exitCode;
        }
    }

    @CommandDefinition(name = "test-ok", description = "", generateHelp = true, sortOptions = true)
    static class OkCommand extends CamelCommand {
        @Override
        public Integer doCall() {
            printer().println("ok");
            return CliExitCode.OK;
        }
    }

    @CommandDefinition(name = "test-fail", description = "", generateHelp = true, sortOptions = true)
    static class FailCommand extends CamelCommand {
        @Option(name = "code")
        int code;

        @Option(name = "message")
        String message = "failed";

        @Option(name = "verbose", hasValue = false)
        boolean verbose;

        @Override
        public Integer doCall() {
            throw new CliException(code, message);
        }
    }

    @CommandDefinition(name = "test-crash", description = "", generateHelp = true, sortOptions = true)
    static class CrashCommand extends CamelCommand {
        @Override
        public Integer doCall() {
            throw new IllegalStateException("Boom");
        }
    }

    @CommandDefinition(name = "test-error", description = "", generateHelp = true, sortOptions = true)
    static class ErrorCommand extends CamelCommand {
        @Override
        public Integer doCall() {
            throw new NoClassDefFoundError("org/acme/Missing");
        }
    }

    @CommandDefinition(name = "test-missing-file", description = "", generateHelp = true, sortOptions = true)
    static class MissingFileCommand extends CamelCommand {
        @Override
        public Integer doCall() throws Exception {
            throw new RuntimeException("Cannot load the routes", new NoSuchFileException("routes/missing.yaml"));
        }
    }

    @CommandDefinition(name = "test-download", description = "", generateHelp = true, sortOptions = true)
    static class DownloadCommand extends CamelCommand {
        @Override
        public Integer doCall() {
            // as camel run fails to start, when a dependency it needs cannot be downloaded
            throw new RuntimeCamelException(
                    new DownloadException(
                            "Cannot download dependency: com.example:missing:1.0 due to: Could not find artifact"));
        }
    }

    @CommandDefinition(name = "test-parent", description = "", generateHelp = true, sortOptions = true)
    static class ParentCommand extends CamelCommand {
        @Override
        public Integer doCall() {
            FailCommand child = getMain().command(new FailCommand());
            return runDefault(child, "--code=3", "--message=from the default sub-command");
        }
    }
}
