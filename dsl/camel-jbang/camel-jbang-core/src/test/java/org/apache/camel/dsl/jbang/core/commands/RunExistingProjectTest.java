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

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how {@code camel run pom.xml} runs an existing Maven project: the application logs to a file in
 * {@code ~/.camel} that {@code camel log} and the TUI can read, and the options are passed to the right JVM for each
 * runtime.
 */
@Isolated
class RunExistingProjectTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path home;

    private String originalHome;

    @BeforeEach
    void useTempHome() {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(originalHome);
    }

    private static Run run(String... args) {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, args);
        return command;
    }

    private static Model model(String artifactId) {
        Model model = new Model();
        model.setArtifactId(artifactId);
        return model;
    }

    @Test
    void appNameIsCamelMainNameFromApplicationProperties(@TempDir Path project) throws Exception {
        Path props = project.resolve("src/main/resources/application.properties");
        Files.createDirectories(props.getParent());
        Files.writeString(props, "camel.main.name = my-app\n");

        assertThat(Run.resolveExistingProjectAppName(project, model("my-artifact"))).isEqualTo("my-app");
    }

    @Test
    void appNameFallsBackToArtifactIdThenDirectoryName(@TempDir Path project) {
        assertThat(Run.resolveExistingProjectAppName(project, model("my-artifact"))).isEqualTo("my-artifact");
        assertThat(Run.resolveExistingProjectAppName(project, new Model()))
                .isEqualTo(project.getFileName().toString());
        assertThat(Run.resolveExistingProjectAppName(project, null)).isEqualTo(project.getFileName().toString());
    }

    @Test
    void camelMainLogsToFileViaLog4j2ConfigurationOnTheMavenCommandLine(@TempDir Path project) throws Exception {
        Path logConfig = project.resolve("target").resolve(Run.CAMEL_MAIN_RUN_LOG_CONFIG);
        Path logFile = CommandLineHelper.getCamelDir().resolve("my-app.log");
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, "stale output of a previous run");

        Run.writeCamelMainRunLogConfig(logConfig, "my-app", "debug");

        // the previous log is removed so the readers do not show stale lines
        assertThat(logFile).doesNotExist();
        String content = Files.readString(logConfig);
        assertThat(content).contains("appender.file.fileName = " + logFile.toAbsolutePath().toString().replace("\\", "/"));
        assertThat(content).contains("rootLogger.level = debug");
        assertThat(content).doesNotContain("{{");

        List<String> args = run("--profile=dev", "--port=9090", "--max-seconds=30", "--max-messages=5",
                "--max-idle-seconds=10", "--prop=foo=bar", "--prop=-Dbar=baz", "pom.xml")
                .buildExistingCamelMainSystemProperties("my-app", logConfig);
        assertThat(args).containsExactly(
                "-Dlog4j2.configurationFile=" + logConfig.toAbsolutePath(),
                "-Dcamel.main.profile=dev",
                "-Dcamel.main.name=my-app",
                "-Dcamel.server.port=9090",
                "-Dcamel.main.durationMaxSeconds=30",
                "-Dcamel.main.durationMaxMessages=5",
                "-Dcamel.main.durationMaxIdleSeconds=10",
                "-Dfoo=bar",
                "-Dbar=baz");
    }

    @Test
    void camelMainDefaultsToInfoLevelAndNoProfileOrPort(@TempDir Path project) throws Exception {
        Path logConfig = project.resolve("target").resolve(Run.CAMEL_MAIN_RUN_LOG_CONFIG);
        Run.writeCamelMainRunLogConfig(logConfig, "my-app", null);
        assertThat(Files.readString(logConfig)).contains("rootLogger.level = info");

        // the default profile (dev) is passed, prod is not as it is the default of a Maven project
        List<String> args = run("pom.xml").buildExistingCamelMainSystemProperties("my-app", logConfig);
        assertThat(args).containsExactly(
                "-Dlog4j2.configurationFile=" + logConfig.toAbsolutePath(),
                "-Dcamel.main.profile=dev",
                "-Dcamel.main.name=my-app");

        args = run("--profile=prod", "pom.xml").buildExistingCamelMainSystemProperties("my-app", logConfig);
        assertThat(args).containsExactly(
                "-Dlog4j2.configurationFile=" + logConfig.toAbsolutePath(),
                "-Dcamel.main.name=my-app");
    }

    @Test
    void quarkusLogsToFileViaJvmArgs() {
        String logFile = CommandLineHelper.getCamelDir().resolve("my-app.log").toAbsolutePath().toString()
                .replace("\\", "/");

        List<String> args = run("--profile=dev", "--port=9090", "--prop=foo=bar", "--jvm-args=-Xmx512m", "pom.xml")
                .buildExistingQuarkusJvmArgs("my-app");

        assertThat(args).containsExactly(
                "-Dquarkus.log.file.enabled=true",
                "-Dquarkus.log.file.path=" + logFile,
                "-Dquarkus.log.file.format=\"" + Run.QUARKUS_LOG_FILE_FORMAT + "\"",
                "-Dcamel.main.profile=dev",
                "-Dcamel.main.name=my-app",
                "-Dquarkus.http.port=9090",
                "-Dfoo=bar",
                "-Xmx512m");
    }

    @Test
    void springBootLogsToFileViaLogbackConfiguration() {
        List<String> args = run("--profile=dev", "--port=9090", "--prop=foo=bar", "--jvm-args=-Xmx512m", "pom.xml")
                .buildExistingSpringBootJvmArgs();

        assertThat(args).containsExactly(
                "-Dlogging.config=classpath:logback-camel-jbang.xml",
                "-Dcamel.main.profile=dev",
                "-Dserver.port=9090",
                "-Dfoo=bar",
                "-Xmx512m");
    }

    @Test
    void jfrIsPassedAsJvmArgument() {
        List<String> args = run("--jfr", "pom.xml").buildExistingQuarkusJvmArgs("my-app");
        assertThat(args).anyMatch(a -> a.startsWith("-XX:StartFlightRecording"));

        args = run("--jfr", "pom.xml").buildExistingSpringBootJvmArgs();
        assertThat(args).anyMatch(a -> a.startsWith("-XX:StartFlightRecording"));
    }

}
