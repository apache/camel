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
import java.util.Properties;

import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.IntegrationStartException;
import org.apache.camel.dsl.jbang.run.RunPipeline;
import org.apache.camel.dsl.jbang.run.RunRequest;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
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
    void citrusTestsGivenWithRoutesAreSkipped(@TempDir Path dir) throws Exception {
        Path route = Files.writeString(dir.resolve("orders.camel.yaml"), "- from:\n    uri: timer:tick\n");
        Path test = Files.writeString(dir.resolve("orders.citrus.it.yaml"), "name: orders-test\nactions: []\n");
        Path feature = Files.writeString(dir.resolve("orders.feature"), "Feature: orders\n");

        // camel run * of a folder with its Citrus tests next to the routes: the routes run as before
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        CommandLineTestHelper.parse(command, route.toString(), test.toString(), feature.toString());
        command.skipCitrusTests();

        assertThat(command.files).containsExactly(route.toString());
        assertThat(printer.getStderr()).isEqualTo(
                "Skipping " + test + ": a Citrus test, not a route (run it with camel test run " + test + ")\n"
                                                  + "Skipping " + feature
                                                  + ": a Citrus test, not a route (run it with camel test run " + feature
                                                  + ")");
        assertThat(printer.getStdout()).isEmpty();
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
    void theRequestKnowsTheOptionsGivenOnTheCommandLine() {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--trace", "--console", "--logging-level=DEBUG", "--camel-version=4.23.0",
                "route.yaml");

        RunRequest request = command.toRunRequest(Path.of("."));

        assertThat(request.givenOptions).contains("trace", "console", "logging-level", "camel-version")
                .doesNotContain("health", "metrics", "stub");
        assertThat(request.given("trace")).isTrue();
        assertThat(request.given("health")).isFalse();
    }

    @Test
    void theLoggingOptionsGivenOnTheCommandLineWinOverTheApplicationProperties() {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--logging-level=DEBUG", "route.yaml");
        Properties properties = new Properties();
        properties.setProperty("loggingLevel", "WARN");
        properties.setProperty("loggingJson", "true");

        command.applyProfileProperties(command.toRunRequest(null), properties);

        assertThat(command.loggingOptions.loggingLevel).isEqualTo("DEBUG");
        // not given: the file
        assertThat(command.loggingOptions.loggingJson).isTrue();
    }

    @Test
    void theBackgroundAndDebugPropertiesStartAnotherJvm() {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "route.yaml");
        command.spawned = false;

        command.applyProfileProperties(command.toRunRequest(null), backgroundAndDebugProperties());

        assertThat(command.background).isTrue();
        assertThat(command.backgroundWait).isEqualTo(5000);
        assertThat(command.jvmDebugPort).isEqualTo(4004);
    }

    @Test
    void aJvmThatARunStartedDoesNotStartAnotherFromTheProperties() {
        // the JVM in the background reads the same application.properties: before, it went to the background
        // again, and its own child too, without end (CAMEL-26930)
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "route.yaml");
        command.spawned = true;

        command.applyProfileProperties(command.toRunRequest(null), backgroundAndDebugProperties());

        assertThat(command.background).isFalse();
        assertThat(command.backgroundWait).isEqualTo(BackgroundWait.DEFAULT);
        assertThat(command.jvmDebugPort).isZero();
    }

    @Test
    void theJvmsARunStartsAreMarked() {
        ProcessBuilder pb = new ProcessBuilder("camel", "run", "route.yaml");

        Run.markSpawned(pb);

        assertThat(pb.environment()).containsEntry(Run.SPAWNED_RUN, "true");
    }

    @Test
    void theBackgroundAndDebugOptionsGivenOnTheCommandLineWinOverTheProperties() {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--background-wait=1000", "--jvm-debug=5005", "route.yaml");
        command.spawned = false;

        command.applyProfileProperties(command.toRunRequest(null), backgroundAndDebugProperties());

        assertThat(command.backgroundWait).isEqualTo(1000);
        assertThat(command.jvmDebugPort).isEqualTo(5005);
        // not given: the file
        assertThat(command.background).isTrue();
    }

    private static Properties backgroundAndDebugProperties() {
        Properties properties = new Properties();
        properties.setProperty("camel.jbang.background", "true");
        properties.setProperty("camel.jbang.backgroundWait", "5000");
        properties.setProperty("camel.jbang.jvmDebug", "true");
        return properties;
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
        // CAMEL-26494: nor must the test folder, with the Citrus tests of the project, which are not routes
        String ignored = "!*.log,!target/**,!.git/**,!.camel/**,!.camel-jbang/**";
        assertThat(RunPipeline.sourceDirReloadPattern(null)).isEqualTo("*,!test/**," + ignored);
        assertThat(RunPipeline.sourceDirReloadPattern("inbox")).isEqualTo("*,!test/**," + ignored + ",!inbox/**");
        assertThat(RunPipeline.sourceDirReloadPattern(" inbox , ./archive/ ,data\\in,"))
                .isEqualTo("*,!test/**," + ignored + ",!inbox/**,!archive/**,!data/in/**");
    }

    @Test
    void chaosWithAScenarioFileOrAFileToRun(@TempDir Path dir) throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--chaos", "route.yaml");
        assertThat(command.chaosEnabled).isTrue();
        assertThat(command.chaosScenario).isNull();
        assertThat(command.files).containsExactly("route.yaml");

        for (String[] args : new String[][] {
                { "--chaos=test/chaos/outage.chaos", "route.yaml" }, { "--chaos", "test/chaos/outage.chaos", "route.yaml" } }) {
            command = new Run(new CamelJBangMain());
            CommandLineTestHelper.parse(command, args);
            assertThat(command.chaosEnabled).isTrue();
            assertThat(command.chaosScenario).isEqualTo("test/chaos/outage.chaos");
            assertThat(command.files).containsExactly("route.yaml");
        }

        command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--chaos=false", "route.yaml");
        assertThat(command.chaosEnabled).isFalse();

        // the scenario must be there; it is passed on as an absolute path
        Path scenario = Files.writeString(dir.resolve("outage.chaos"), "endpoint=http:* error\n");
        command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--chaos=" + scenario, "--chaos-report=" + dir.resolve("chaos.json"),
                "route.yaml");
        command.prepareChaos();
        assertThat(command.toRunRequest(dir).chaosScenario).isEqualTo(scenario.toAbsolutePath().toString());
        assertThat(command.toRunRequest(dir).chaosReport).isEqualTo(dir.resolve("chaos.json").toString());

        Run missing = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(missing, "--chaos=" + dir.resolve("missing.chaos"), "route.yaml");
        CliException e = Assertions.assertThrows(CliException.class, missing::prepareChaos);
        assertThat(e.getExitCode()).isEqualTo(CliExitCode.FILE_NOT_FOUND);

        Run report = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(report, "--chaos-report=chaos.json", "route.yaml");
        e = Assertions.assertThrows(CliException.class, report::prepareChaos);
        assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE);
        assertThat(e).hasMessage("--chaos-report needs --chaos");
    }

    @Test
    void chaosWithProfileProdSaysThatChaosStaysOff() {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        CommandLineTestHelper.parse(command, "--chaos", "--profile=prod", "route.yaml");
        command.warnChaosOffWithProfileProd();
        assertThat(printer.getStderr()).contains("WARN: camel-chaos stays off with profile prod");

        // not with profile dev, nor without --chaos
        for (String[] args : new String[][] { { "--chaos", "route.yaml" }, { "--profile=prod", "route.yaml" } }) {
            StringPrinter quiet = new StringPrinter();
            command = new Run(new CamelJBangMain().withPrinter(quiet));
            CommandLineTestHelper.parse(command, args);
            command.warnChaosOffWithProfileProd();
            assertThat(quiet.getOutput()).isEmpty();
        }
    }

    @Test
    void sanitizerWithProfileProdSaysThatTheSanitizerStaysOff() {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        CommandLineTestHelper.parse(command, "--sanitizer", "--profile=prod", "route.yaml");
        command.warnSanitizerOffWithProfileProd();
        assertThat(printer.getStderr()).contains("WARN: camel-sanitizer stays off with profile prod");

        // not with profile dev, nor without --sanitizer
        for (String[] args : new String[][] { { "--sanitizer", "route.yaml" }, { "--profile=prod", "route.yaml" } }) {
            StringPrinter quiet = new StringPrinter();
            command = new Run(new CamelJBangMain().withPrinter(quiet));
            CommandLineTestHelper.parse(command, args);
            command.warnSanitizerOffWithProfileProd();
            assertThat(quiet.getOutput()).isEmpty();
        }
    }

    @Test
    void theLogOfACommandLineRunIsKeptWithTheExitCodeOfTheCli() {
        // CAMEL-25784: camel run on the command line keeps its log as the previous log of the integration
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "route.yaml");
        assertThat(command.toRunRequest(Path.of(".")).keepPreviousLog).isTrue();
        // with the exit code camel run exits with for the exception
        assertThat(command.runContext().failureExitCode(
                new IntegrationStartException(new FailedToStartRouteException("route1", "boom"))))
                .isEqualTo(CliExitCode.FAILURE);

        // not a Camel that another command runs in its JVM (such as camel cmd send --local)
        assertThat(new Run(new CamelJBangMain()).toRunRequest(Path.of(".")).keepPreviousLog).isFalse();
        command.transformRun = true;
        assertThat(command.toRunRequest(Path.of(".")).keepPreviousLog).isFalse();
    }
}
