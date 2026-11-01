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

import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.RunPipeline;
import org.apache.camel.dsl.jbang.run.RunRequest;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class RunTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldDefaultToJBangRuntime() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "route.yaml");

        assertThat(command.runtime).isEqualTo(RuntimeType.jbang);
    }

    @Test
    public void shouldParseRuntimeOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--runtime=main", "route.yaml");
        assertThat(command.runtime).isEqualTo(RuntimeType.main);

        command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--runtime=camel-main", "route.yaml");
        assertThat(command.runtime).isEqualTo(RuntimeType.main);

        command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--runtime=jbang", "route.yaml");
        assertThat(command.runtime).isEqualTo(RuntimeType.jbang);

        command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--runtime=spring-boot", "route.yaml");
        assertThat(command.runtime).isEqualTo(RuntimeType.springBoot);
    }

    @Test
    public void shouldParseJavaVersionOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--java-version=17", "route.yaml");

        Assertions.assertEquals("17", command.javaVersion);
    }

    @Test
    public void shouldUseDefaultJavaVersion() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "route.yaml");

        Assertions.assertEquals("21", command.javaVersion);
    }

    @Test
    public void shouldParseJavaVersion11() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--java-version=11", "route.yaml");

        Assertions.assertEquals("11", command.javaVersion);
    }

    @Test
    public void shouldParseJavaVersion21() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--java-version=21", "route.yaml");

        Assertions.assertEquals("21", command.javaVersion);
    }

    @Test
    void aWholeDirectoryIsNamedAfterTheDirectory(@TempDir Path dir) {
        // CAMEL-25830
        Path project = dir.resolve("orders-app");
        assertThat(RunPipeline.directoryName(project.toString())).isEqualTo("orders-app");
        // the folders of a project layout say nothing about the app: the project does
        assertThat(RunPipeline.directoryName(project.resolve("src/main/resources/camel").toString())).isEqualTo("orders-app");
        assertThat(RunPipeline.directoryName(project.resolve("src/main/java").toString())).isEqualTo("orders-app");
        assertThat(RunPipeline.directoryName(project.resolve("routes").toString())).isEqualTo("orders-app");
        // the working directory
        assertThat(RunPipeline.directoryName(".")).isEqualTo(Path.of("").toAbsolutePath().getFileName().toString());
        // no name to give
        assertThat(RunPipeline.directoryName("/src")).isNull();
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
        CommandLineTestHelper.parse(command, "--example=circuit-breaker");

        Assertions.assertEquals("circuit-breaker", command.example);
    }

    @Test
    public void shouldParseExampleListOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--example");

        Assertions.assertNotNull(command.example);
    }

    @Test
    void jfrIsDisabledByDefault() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "route.yaml");

        assertThat(command.jfrEnabled()).isFalse();
    }

    @Test
    void jfrFlagEnablesJfr() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--jfr", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
    }

    @Test
    void jfrProfileImpliesJfr() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--jfr-profile=profile", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
    }

    @Test
    void explicitStartFlightRecordingInJvmArgsKeepsJfrEnabled() throws Exception {
        // the explicit recording wins, but --jfr must still enable Camel runtime events in forked projects
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--jfr",
                "--jvm-args=-XX:StartFlightRecording=filename=mine.jfr", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
    }

    @Test
    void jfrFileNameUsesAppName() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--jfr", "--name=my-app", "route.yaml");

        assertThat(command.jfrFileName()).isEqualTo("my-app.jfr");
    }

    @Test
    void jfrFileNameFallsBackToCamelWhenNameIsNull() {
        Run command = new Run(new CamelJBangMain());
        command.name = null;

        assertThat(command.jfrFileName()).isEqualTo("camel.jfr");
    }

    @Test
    void theOptionsOfARunWithMavenAreInTheRequest() {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--runtime=spring-boot", "--camel-spring-boot-version=4.20.0",
                "--jvm-args=-Xmx512m", "--jvm-debug=5005", "--port=9090", "--max-seconds=30", "--prop=foo=bar",
                "--jfr", "--dev", "route.yaml");

        RunRequest request = command.toRunRequest(null);

        // the default profile of camel run is dev
        assertThat(request.profile).isEqualTo("dev");
        assertThat(request.runtime).isEqualTo(RuntimeType.springBoot);
        assertThat(request.camelSpringBootVersion).isEqualTo("4.20.0");
        assertThat(request.jvmArgs).isEqualTo("-Xmx512m");
        assertThat(request.jvmDebugPort).isEqualTo(5005);
        assertThat(request.port).isEqualTo(9090);
        assertThat(request.maxSeconds).isEqualTo(30);
        assertThat(request.applicationProperties).containsExactly("foo=bar");
        assertThat(request.jfrEnabled()).isTrue();
        assertThat(request.dev).isTrue();
    }

    @Test
    public void shouldParseDataDirOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--source-dir=shop", "--data-dir=inbox,archive");

        assertThat(command.dataDir).isEqualTo("inbox,archive");
    }

    @Test
    void sourceDirReloadPatternLeavesTheDataDirsOut() {
        // CAMEL-25670: the routes read and write the files of a data folder, which must not reload them
        assertThat(RunPipeline.sourceDirReloadPattern(null)).isEqualTo("*");
        assertThat(RunPipeline.sourceDirReloadPattern("inbox")).isEqualTo("*,!inbox/**");
        assertThat(RunPipeline.sourceDirReloadPattern(" inbox , ./archive/ ,data\\in,"))
                .isEqualTo("*,!inbox/**,!archive/**,!data/in/**");
    }

    @Test
    void theLogOfACommandLineRunIsKeptWithTheExitCodeOfTheCli() {
        // CAMEL-25784: camel run on the command line keeps its log as the previous log of the integration
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "route.yaml");
        assertThat(command.toRunRequest(Path.of(".")).keepPreviousLog).isTrue();
        // with the exit code camel run exits with for the exception
        assertThat(command.runContext().failureExitCode(new FailedToStartRouteException("route1", "boom")))
                .isEqualTo(CliExitCode.INTERNAL);

        // not a Camel that another command runs in its JVM (such as camel cmd send --local)
        assertThat(new Run(new CamelJBangMain()).toRunRequest(Path.of(".")).keepPreviousLog).isFalse();
        command.transformRun = true;
        assertThat(command.toRunRequest(Path.of(".")).keepPreviousLog).isFalse();
    }
}
