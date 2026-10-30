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
package org.apache.camel.dsl.jbang.export.run;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.export.common.CamelHome;
import org.apache.camel.dsl.jbang.export.common.StringPrinter;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how {@code camel run pom.xml} runs an existing Maven project: the application logs to a file in
 * {@code ~/.camel} that {@code camel log} and the TUI can read, and the options are passed to the right JVM for each
 * runtime. Isolated, as it changes the home directory of the JVM, which the other tests use.
 */
@Isolated
class RunExistingProjectTest {

    @TempDir
    Path home;

    private String originalHome;

    @BeforeEach
    void useTempHome() {
        originalHome = CamelHome.getHomeDir().toString();
        CamelHome.useHomeDir(home.toString());
    }

    @AfterEach
    void restoreHome() {
        CamelHome.useHomeDir(originalHome);
    }

    private final StringPrinter printer = new StringPrinter();

    /**
     * The request of camel run pom.xml with the dev profile, the default of camel run.
     */
    private static RunRequest request() {
        RunRequest request = new RunRequest();
        request.files.add("pom.xml");
        request.profile = "dev";
        return request;
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

        assertThat(MavenRunSupport.resolveExistingProjectAppName(project, model("my-artifact"))).isEqualTo("my-app");
    }

    @Test
    void appNameFallsBackToArtifactIdThenDirectoryName(@TempDir Path project) {
        assertThat(MavenRunSupport.resolveExistingProjectAppName(project, model("my-artifact"))).isEqualTo("my-artifact");
        assertThat(MavenRunSupport.resolveExistingProjectAppName(project, new Model()))
                .isEqualTo(project.getFileName().toString());
        assertThat(MavenRunSupport.resolveExistingProjectAppName(project, null)).isEqualTo(project.getFileName().toString());
    }

    @Test
    void camelMainLogsToFileViaLog4j2ConfigurationOnTheMavenCommandLine(@TempDir Path project) throws Exception {
        Path logConfig = project.resolve("target").resolve(MavenRunSupport.CAMEL_MAIN_RUN_LOG_CONFIG);
        Path logFile = CamelHome.getCamelDir().resolve("my-app.log");
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, "stale output of a previous run");

        MavenRunSupport.writeCamelMainRunLogConfig(logConfig, "my-app", "debug");

        // the log of the last run is kept as its previous run (CAMEL-25832), so the readers do not show stale lines
        assertThat(logFile).doesNotExist();
        assertThat(CamelHome.getCamelDir().resolve("my-app-previous.log")).hasContent("stale output of a previous run");
        String content = Files.readString(logConfig);
        assertThat(content).contains("appender.file.fileName = " + logFile.toAbsolutePath().toString().replace("\\", "/"));
        assertThat(content).contains("rootLogger.level = debug");
        assertThat(content).doesNotContain("{{");

        RunRequest request = request();
        request.port = 9090;
        request.maxSeconds = 30;
        request.maxMessages = 5;
        request.maxIdleSeconds = 10;
        request.applicationProperties = new String[] { "foo=bar", "-Dbar=baz" };
        List<String> args = MavenRunSupport.existingCamelMainSystemProperties(request, "my-app", logConfig);
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
        Path logConfig = project.resolve("target").resolve(MavenRunSupport.CAMEL_MAIN_RUN_LOG_CONFIG);
        MavenRunSupport.writeCamelMainRunLogConfig(logConfig, "my-app", null);
        assertThat(Files.readString(logConfig)).contains("rootLogger.level = info");

        // the default profile (dev) is passed, prod is not as it is the default of a Maven project
        List<String> args = MavenRunSupport.existingCamelMainSystemProperties(request(), "my-app", logConfig);
        assertThat(args).containsExactly(
                "-Dlog4j2.configurationFile=" + logConfig.toAbsolutePath(),
                "-Dcamel.main.profile=dev",
                "-Dcamel.main.name=my-app");

        RunRequest prod = request();
        prod.profile = "prod";
        args = MavenRunSupport.existingCamelMainSystemProperties(prod, "my-app", logConfig);
        assertThat(args).containsExactly(
                "-Dlog4j2.configurationFile=" + logConfig.toAbsolutePath(),
                "-Dcamel.main.name=my-app");
    }

    @Test
    void quarkusLogsToFileViaJvmArgs() {
        String logFile = CamelHome.getCamelDir().resolve("my-app.log").toAbsolutePath().toString()
                .replace("\\", "/");

        List<String> args = MavenRunSupport.existingQuarkusJvmArgs(options(), "my-app", printer);

        assertThat(args).containsExactly(
                "-Dquarkus.log.file.enabled=true",
                "-Dquarkus.log.file.path=" + logFile,
                "-Dquarkus.log.file.format=\"" + MavenRunSupport.QUARKUS_LOG_FILE_FORMAT + "\"",
                "-Dcamel.main.profile=dev",
                "-Dcamel.main.name=my-app",
                "-Dquarkus.http.port=9090",
                "-Dfoo=bar",
                "-Xmx512m");
    }

    @Test
    void springBootLogsToFileViaLogbackConfiguration() {
        String logFile = CamelHome.getCamelDir().resolve("my-app.log").toAbsolutePath().toString()
                .replace("\\", "/");

        List<String> args = MavenRunSupport.existingSpringBootJvmArgs(options(), "my-app",
                SpringBootRunner.logbackConfig("my-app"), printer);

        // the logback configuration is a temp file: the project needs no src/main/resources and is not changed; it logs
        // to <name>.log, as the other runtimes do, so the log is kept as the previous run (CAMEL-25832)
        assertThat(args.get(0)).startsWith("-Dlogging.config=file:").endsWith(".xml");
        assertThat(Path.of(URI.create(args.get(0).substring("-Dlogging.config=".length())))).exists()
                .content().contains("<configuration").contains("value=\"" + logFile + "\"").doesNotContain("{{");
        // the name is passed, so the app reports the name of its log file
        assertThat(args.subList(1, args.size())).containsExactly(
                "-Dcamel.main.profile=dev",
                "-Dcamel.main.name=my-app",
                "-Dserver.port=9090",
                "-Dfoo=bar",
                "-Xmx512m");
    }

    @Test
    void jfrIsPassedAsJvmArgument() {
        RunRequest request = request();
        request.jfr = true;
        List<String> args = MavenRunSupport.existingQuarkusJvmArgs(request, "my-app", printer);
        assertThat(args).anyMatch(a -> a.startsWith("-XX:StartFlightRecording"));

        args = MavenRunSupport.existingSpringBootJvmArgs(request, "my-app", null, printer);
        assertThat(args).anyMatch(a -> a.startsWith("-XX:StartFlightRecording"));
    }

    /**
     * The options of camel run --profile=dev --port=9090 --prop=foo=bar --jvm-args=-Xmx512m pom.xml.
     */
    private static RunRequest options() {
        RunRequest request = request();
        request.port = 9090;
        request.applicationProperties = new String[] { "foo=bar" };
        request.jvmArgs = "-Xmx512m";
        return request;
    }
}
