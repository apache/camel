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

import org.apache.camel.dsl.jbang.core.commands.action.EvalExpressionCommand;
import org.apache.camel.dsl.jbang.core.commands.action.TransformMessageAction;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A command called wrong exits with 2 (usage error), and a file or name that does not exist exits with 3 (not found).
 * Either way the command prints one ERROR line on stderr, nothing on stdout, and stops before it does any work.
 */
class CommandInputErrorsTest {

    @TempDir
    Path dir;

    private Path previousHome;
    private StringPrinter printer;
    private CamelJBangMain main;

    @BeforeEach
    void setup() {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(dir.toString());
        printer = new StringPrinter();
        main = new CamelJBangMain().withPrinter(printer);
    }

    @AfterEach
    void restore() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void explainChecksTheFilesBeforeTheLlm() throws Exception {
        Path missing = dir.resolve("missing.yaml");

        assertThat(call(new Explain(main), missing.toString())).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: File not found: " + missing);
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void hardenChecksTheFilesBeforeTheLlm() throws Exception {
        Path missing = dir.resolve("missing.yaml");

        assertThat(call(new Harden(main), missing.toString())).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: File not found: " + missing);
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void evalExpressionNeedsATemplate() throws Exception {
        // picocli requires --template, so only a blank value reaches the command
        assertThat(call(new EvalExpressionCommand(main), "--template=")).isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr()).isEqualTo("ERROR: Template is required");
    }

    @Test
    void evalExpressionWithAMissingTemplateFile() throws Exception {
        Path missing = dir.resolve("missing.txt");

        assertThat(call(new EvalExpressionCommand(main), "--template=file:" + missing)).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: Template file does not exist: " + missing);
    }

    @Test
    void transformMessageNeedsASourceOrATemplate() throws Exception {
        assertThat(call(new TransformMessageAction(main), "--body=Hello")).isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr())
                .isEqualTo("ERROR: Either source or template and one of language/component must be configured");
    }

    @Test
    void transformMessageWithAMissingSourceFile() throws Exception {
        Path missing = dir.resolve("missing.yaml");

        assertThat(call(new TransformMessageAction(main), "--body=Hello", "--source=file:" + missing))
                .isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: Source file does not exist: " + missing);
    }

    @Test
    void dependencyUpdateRejectsCleanWithScanRoutes() throws Exception {
        Path pom = dir.resolve("pom.xml");

        assertThat(call(new DependencyUpdate(main), "--clean", "--scan-routes", pom.toString()))
                .isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr()).isEqualTo("ERROR: Options --clean and --scan-routes are mutually exclusive");
    }

    @Test
    void dependencyUpdateWithAMissingFile() throws Exception {
        Path missing = dir.resolve("missing/pom.xml");

        assertThat(call(new DependencyUpdate(main), missing.toString())).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).isEqualTo("ERROR: Source file does not exist: " + missing);
    }

    @Test
    void initFromKameletNeedsAKameletFile() throws Exception {
        Path file = dir.resolve("orders.yaml");

        assertThat(call(new Init(main), file.toString(), "--from-kamelet=timer-source")).isEqualTo(CliExitCode.USAGE);
        assertThat(printer.getStderr())
                .isEqualTo("ERROR: When extending from an existing Kamelet then file must have extension .kamelet.yaml");
        assertThat(file).doesNotExist();
    }

    @Test
    void debugNeedsATerminalBeforeAttaching() {
        // nothing is attached to (or spawned) when the debugger cannot read its commands; finding the integration to
        // attach to is ProcessFinder.requireOne
        assumeFalse(EnvironmentHelper.isInteractiveTerminal(), "runs without an interactive terminal");

        assertThatThrownBy(() -> call(new Debug(main), "--remote-attach", "--name=no-such-integration"))
                .isInstanceOf(CliException.class)
                .hasMessage("camel debug requires an interactive terminal")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "missing.yaml", "missing.xml", "missing.java", "file:missing.camel.yaml" })
    void runAFileThatDoesNotExist(String file) {
        // every extension, before anything is prepared or printed
        assertThatThrownBy(() -> call(new Run(main), file))
                .isInstanceOf(CliException.class)
                .hasMessage("File does not exist: " + file.replace("file:", ""))
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getOutput()).isEmpty();
    }

    @Test
    void runFilesAndSourceDir() throws Exception {
        Path file = Files.writeString(dir.resolve("orders.yaml"), "- from:\n    uri: timer:x\n");

        assertThatThrownBy(() -> call(new Run(main), file.toString(), "--source-dir=" + dir))
                .isInstanceOf(CliException.class)
                .hasMessage("Cannot specify both file(s) and source-dir at the same time.")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    @Test
    void runQuarkusInTheBackground() throws Exception {
        Path file = Files.writeString(dir.resolve("orders.yaml"), "- from:\n    uri: timer:x\n");

        assertThatThrownBy(() -> call(new Run(main), file.toString(), "--runtime=quarkus", "--background"))
                .isInstanceOf(CliException.class)
                .hasMessage("Run Camel Quarkus with --background is not supported")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    private static int call(CamelCommand command, String... args) throws Exception {
        CommandLine.populateCommand(command, args);
        return command.doCall();
    }
}
