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

import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.KameletMain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldDefaultToJBangRuntime() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "route.yaml");

        assertThat(command.runtime).isEqualTo(RuntimeType.jbang);
    }

    @Test
    public void shouldParseRuntimeOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--runtime=main", "route.yaml");
        assertThat(command.runtime).isEqualTo(RuntimeType.main);

        command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--runtime=camel-main", "route.yaml");
        assertThat(command.runtime).isEqualTo(RuntimeType.main);

        command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--runtime=jbang", "route.yaml");
        assertThat(command.runtime).isEqualTo(RuntimeType.jbang);

        command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--runtime=spring-boot", "route.yaml");
        assertThat(command.runtime).isEqualTo(RuntimeType.springBoot);
    }

    @Test
    public void shouldParseJavaVersionOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=17", "route.yaml");

        Assertions.assertEquals("17", command.javaVersion);
    }

    @Test
    public void shouldUseDefaultJavaVersion() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "route.yaml");

        Assertions.assertEquals("21", command.javaVersion);
    }

    @Test
    public void shouldParseJavaVersion11() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=11", "route.yaml");

        Assertions.assertEquals("11", command.javaVersion);
    }

    @Test
    public void shouldParseJavaVersion21() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=21", "route.yaml");

        Assertions.assertEquals("21", command.javaVersion);
    }

    @Test
    public void shouldExpectRoutes() {
        KameletMain main = mock(KameletMain.class);
        when(main.getCamelContext()).thenReturn(new DefaultCamelContext());

        Run command = new Run(new CamelJBangMain());
        assertThat(command.hasNoRoutes(main)).isTrue();

        // files can be added or changed later
        command.dev = true;
        assertThat(command.hasNoRoutes(main)).isFalse();
        command = new Run(new CamelJBangMain());
        command.sourceDir = "src";
        assertThat(command.hasNoRoutes(main)).isFalse();
        // or no files are wanted
        command = new Run(new CamelJBangMain());
        command.empty = true;
        assertThat(command.hasNoRoutes(main)).isFalse();
    }

    @Test
    void aWholeDirectoryIsNamedAfterTheDirectory(@TempDir Path dir) {
        // CAMEL-25830
        Path project = dir.resolve("orders-app");
        assertThat(Run.directoryName(project.toString())).isEqualTo("orders-app");
        // the folders of a project layout say nothing about the app: the project does
        assertThat(Run.directoryName(project.resolve("src/main/resources/camel").toString())).isEqualTo("orders-app");
        assertThat(Run.directoryName(project.resolve("src/main/java").toString())).isEqualTo("orders-app");
        assertThat(Run.directoryName(project.resolve("routes").toString())).isEqualTo("orders-app");
        // the working directory
        assertThat(Run.directoryName(".")).isEqualTo(Path.of("").toAbsolutePath().getFileName().toString());
        // no name to give
        assertThat(Run.directoryName("/src")).isNull();
    }

    @Test
    public void shouldListExamples() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "";
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Examples: "));
        Assertions.assertTrue(output.contains("circuit-breaker"));
        Assertions.assertTrue(output.contains("groovy"));
        Assertions.assertTrue(output.contains("routes"));
    }

    @Test
    public void shouldRejectUnknownExample() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "nonexistent";
        int exit = command.doCall();

        Assertions.assertEquals(1, exit);
    }

    @Test
    public void shouldListOneGroupInFull() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "route";
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Route:"));
        Assertions.assertTrue(output.contains("aggregator"));
        Assertions.assertTrue(output.contains("eips: "));
        Assertions.assertFalse(output.contains("timer-log"), "only the group asked for is listed");
    }

    @Test
    public void shouldSuggestSimilarExample() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "fail-well/circuit-brake";
        int exit = command.doCall();

        Assertions.assertEquals(1, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Did you mean"));
    }

    @Test
    public void shouldParseExampleOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--example=circuit-breaker");

        Assertions.assertEquals("circuit-breaker", command.example);
    }

    @Test
    public void shouldParseExampleListOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--example");

        Assertions.assertNotNull(command.example);
    }

    @Test
    void jfrDisabledByDefaultBuildsNoJvmArgs() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "route.yaml");

        assertThat(command.jfrEnabled()).isFalse();
        assertThat(command.buildJfrJvmArgs()).isNull();
    }

    @Test
    void jfrFlagBuildsStartFlightRecordingArg() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
        assertThat(command.buildJfrJvmArgs())
                .isEqualTo("-XX:StartFlightRecording=filename=CamelJBang.jfr "
                           + "-Dcamel.main.startupRecorderRuntimeEnabled=true");
    }

    @Test
    void jfrProfileImpliesJfrAndIsAppendedAsSettings() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr-profile=profile", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
        assertThat(command.buildJfrJvmArgs())
                .isEqualTo("-XX:StartFlightRecording=filename=CamelJBang.jfr,settings=profile "
                           + "-Dcamel.main.startupRecorderRuntimeEnabled=true");
    }

    @Test
    void explicitStartFlightRecordingInJvmArgsKeepsRuntimeRecorderEnabled() throws Exception {
        // the explicit recording wins, but --jfr must still enable Camel runtime events in forked projects
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr",
                "--jvm-args=-XX:StartFlightRecording=filename=mine.jfr", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
        assertThat(command.buildJfrJvmArgs())
                .isEqualTo("-Dcamel.main.startupRecorderRuntimeEnabled=true");
    }

    @Test
    void unknownJfrProfileFailsFastWithTheSupportedProfiles() throws Exception {
        // running without a recording after the user asked for one would look like JFR simply produced nothing
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr-profile=does-not-exist", "route.yaml");

        assertThatThrownBy(command::startJfrRecording)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist")
                .hasMessageContaining("default");
    }

    @Test
    void jfrRecordingIsNotStartedWhenJfrIsOff() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "route.yaml");

        assertThat(command.startJfrRecording()).isNull();
    }

    @Test
    void jfrFileNameUsesAppName() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr", "--name=my-app", "route.yaml");

        assertThat(command.jfrFileName()).isEqualTo("my-app.jfr");
    }

    @Test
    void jfrFileNameFallsBackToCamelWhenNameIsNull() {
        Run command = new Run(new CamelJBangMain());
        command.name = null;

        assertThat(command.jfrFileName()).isEqualTo("camel.jfr");
    }

    @Test
    void mergeJvmArgsReturnsExistingWhenExtraIsNull() {
        assertThat(Run.mergeJvmArgs("-Xmx256m", null)).isEqualTo("-Xmx256m");
        assertThat(Run.mergeJvmArgs(null, null)).isNull();
    }

    @Test
    void mergeJvmArgsReturnsExtraWhenExistingIsBlank() {
        assertThat(Run.mergeJvmArgs(null, "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-XX:StartFlightRecording=filename=camel.jfr");
        assertThat(Run.mergeJvmArgs("  ", "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-XX:StartFlightRecording=filename=camel.jfr");
    }

    @Test
    void mergeJvmArgsConcatenatesBothWithSpace() {
        assertThat(Run.mergeJvmArgs("-Xmx256m", "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-Xmx256m -XX:StartFlightRecording=filename=camel.jfr");
    }

    @Test
    public void shouldParseDataDirOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--source-dir=shop", "--data-dir=inbox,archive");

        assertThat(command.dataDir).isEqualTo("inbox,archive");
    }

    @Test
    void sourceDirReloadPatternLeavesTheDataDirsOut() {
        // CAMEL-25670: the routes read and write the files of a data folder, which must not reload them
        assertThat(Run.sourceDirReloadPattern(null)).isEqualTo("*");
        assertThat(Run.sourceDirReloadPattern("inbox")).isEqualTo("*,!inbox/**");
        assertThat(Run.sourceDirReloadPattern(" inbox , ./archive/ ,data\\in,"))
                .isEqualTo("*,!inbox/**,!archive/**,!data/in/**");
    }
}
