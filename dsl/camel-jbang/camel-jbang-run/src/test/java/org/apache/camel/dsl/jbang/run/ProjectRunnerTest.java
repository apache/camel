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
package org.apache.camel.dsl.jbang.run;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.dsl.jbang.run.export.DependencyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs through the Java API, without the CLI: the processes of the run are recorded instead of started, and the
 * dependencies come from a resolver that writes the run settings.
 */
class ProjectRunnerTest {

    @TempDir
    Path dir;

    private final StringPrinter printer = new StringPrinter();
    private final List<ProcessBuilder> started = new ArrayList<>();
    private int buildExitCode;

    private final DependencyResolver resolver = request -> {
        Path settings = request.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE);
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, """
                camel.main.routesIncludePattern=file:%s
                dependency=camel:timer
                dependency=camel:log
                """.formatted(request.files.get(0)));
        return 0;
    };

    private final RunContext context = new RunContext()
            .withPrinter(printer)
            .withDependencyResolver(resolver)
            .withJavaHome(Path.of("/opt/jdk"))
            .withProcessStarter(pb -> {
                started.add(pb);
                if (pb.command().contains("package")
                        && pb.command().stream().noneMatch(a -> a.startsWith("quarkus:") || a.startsWith("spring-boot:"))) {
                    // the runner JAR that the build of a Camel Main project makes
                    Path project = Path.of(pb.command().get(pb.command().indexOf("--file") + 1)).getParent();
                    Files.createDirectories(project.resolve("target"));
                    Files.writeString(project.resolve("target/runner.jar"), "");
                    return new EndedProcess(0, buildExitCode);
                }
                return new EndedProcess(4242, 7);
            });

    @Test
    void aCamelMainRunExportsAndBuildsTheProjectAndStartsItsJar() throws Exception {
        RunRequest request = request();
        request.profile = "dev";
        request.jvmArgs = "-Xmx256m";
        request.jvmDebugPort = 5005;
        request.maxSeconds = 30;

        RunningIntegration integration = new ProjectRunner(context).start(request);
        try {
            assertThat(started).hasSize(2);
            List<String> build = started.get(0).command();
            assertThat(build).endsWith("--quiet", "--file", build.get(3), "-DskipTests", "package");
            Path project = Path.of(build.get(3)).getParent();
            assertThat(project.getParent().getFileName()).hasToString(ProjectRunner.RUN_PLATFORM_DIR);
            assertThat(project.resolve("pom.xml")).exists();
            assertThat(Files.readString(project.resolve("pom.xml"))).contains("camel-cli-connector")
                    .doesNotContain("camel-chaos");

            assertThat(started.get(1).command()).containsExactly(
                    Path.of("/opt/jdk", "bin", "java").toString(),
                    "-Xmx256m",
                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
                    "-Dcamel.main.profile=dev",
                    "-Dcamel.main.durationMaxSeconds=30",
                    "-jar",
                    project.resolve("target/runner.jar").toAbsolutePath().toString());

            // the handle is the one of the JVM of the application
            assertThat(integration.pid()).isEqualTo(4242);
            assertThat(integration.waitFor()).isEqualTo(7);
            // the files of the run are kept until it is stopped
            assertThat(project).exists();
            integration.stop();
            assertThat(project).doesNotExist();
        } finally {
            integration.stop();
        }
    }

    @Test
    void runStartsAndWaitsForTheIntegration() throws Exception {
        RunRequest request = request();

        int exit = new ProjectRunner(context).run(request);

        assertThat(exit).isEqualTo(7);
        assertThat(started).hasSize(2);
    }

    @Test
    void aBuildThatFailsEndsTheRun() throws Exception {
        buildExitCode = 1;

        RunningIntegration integration = new ProjectRunner(context).start(request());

        assertThat(started).hasSize(1);
        assertThat(integration.pid()).isZero();
        assertThat(integration.waitFor()).isEqualTo(1);
        assertThat(printer.getOutput()).contains("Failed to build Camel Main project in: ");
    }

    @Test
    void anOptionThatCamelMainCannotRunEndsTheRun() throws Exception {
        RunRequest request = request();
        request.openapi = "api.yaml";

        RunningIntegration integration = new ProjectRunner(context).start(request);

        assertThat(started).isEmpty();
        assertThat(integration.waitFor()).isEqualTo(1);
        assertThat(printer.getOutput()).contains("Run Camel Main with --open-api is not supported");
    }

    @Test
    void theRequestIsNotChanged() throws Exception {
        RunRequest request = request();
        List<String> dependencies = new ArrayList<>(request.dependencies);

        new ProjectRunner(context).run(request);

        assertThat(request.dependencies).isEqualTo(dependencies);
        assertThat(request.gav).isNull();
    }

    @Test
    void aSpringBootRunExportsTheProjectAndRunsItWithMaven() throws Exception {
        RunRequest request = request();
        request.runtime = RuntimeType.springBoot;
        request.jvmArgs = "-Xmx256m";
        request.maxSeconds = 30;

        RunningIntegration integration = new ProjectRunner(context).start(request);
        try {
            // spring-boot:run builds and runs the application: the process of the integration is Maven
            assertThat(started).hasSize(1);
            List<String> cmd = started.get(0).command();
            Path project = Path.of(cmd.get(3)).getParent();
            assertThat(project.getParent().getFileName()).hasToString(ProjectRunner.RUN_PLATFORM_DIR);
            assertThat(cmd.subList(1, cmd.size())).containsExactly(
                    "--quiet", "--file", cmd.get(3),
                    "-Dspring-boot.run.jvmArguments=-Xmx256m -Dcamel.main.durationMaxSeconds=30",
                    "-Dspring-boot.run.workingDirectory=" + Path.of(".").toAbsolutePath().normalize(),
                    "spring-boot:run");
            assertThat(project.resolve("pom.xml")).content().contains("camel-cli-connector-starter");
            // the application logs to the log of its name, which is kept as its previous run
            assertThat(project.resolve("src/main/resources/logback.xml")).exists();
            assertThat(printer.getOutput()).contains("Running using Spring Boot (preparing and downloading files)");

            assertThat(integration.pid()).isEqualTo(4242);
            assertThat(integration.waitFor()).isEqualTo(7);
            integration.stop();
            assertThat(project).doesNotExist();
        } finally {
            integration.stop();
        }
    }

    @Test
    void anExistingQuarkusProjectRunsWithMaven() throws Exception {
        RunRequest request = existingProject(RuntimeType.quarkus);
        request.dev = true;
        request.port = 9090;
        request.jvmArgs = "-Xmx256m";

        RunningIntegration integration = new ProjectRunner(context).start(request);
        try {
            assertThat(started).hasSize(1);
            ProcessBuilder pb = started.get(0);
            Path project = dir.resolve("project");
            assertThat(pb.directory()).isEqualTo(project.toFile());
            List<String> cmd = pb.command();
            assertThat(cmd.subList(1, 4)).containsExactly("--quiet", "--file",
                    project.resolve("camel-jbang-run-pom.xml").toString());
            assertThat(cmd.get(4)).startsWith("-Djvm.args=-Dquarkus.log.file.enabled=true ")
                    .contains("-Dcamel.main.name=orders", "-Dquarkus.http.port=9090").endsWith(" -Xmx256m");
            assertThat(cmd.subList(5, cmd.size())).containsExactly("-DskipTests", "package", "quarkus:dev");
            // the project is run with a pom of its own, which adds the CLI connector
            assertThat(project.resolve("camel-jbang-run-pom.xml")).content().contains("camel-quarkus-cli-connector");
            assertThat(printer.getOutput()).contains("Running Quarkus project: " + project);

            assertThat(integration.pid()).isEqualTo(4242);
            integration.stop();
            assertThat(project.resolve("camel-jbang-run-pom.xml")).doesNotExist();
            assertThat(project.resolve("pom.xml")).exists();
        } finally {
            integration.stop();
        }
    }

    @Test
    void anExistingSpringBootProjectRunsWithMaven() throws Exception {
        RunRequest request = existingProject(RuntimeType.springBoot);
        request.jfr = true;

        RunningIntegration integration = new ProjectRunner(context).start(request);
        try {
            assertThat(started).hasSize(1);
            Path project = dir.resolve("project");
            List<String> cmd = started.get(0).command();
            assertThat(cmd.get(4)).startsWith("-Dspring-boot.run.jvmArguments=-Dlogging.config=file:")
                    .contains("-Dcamel.main.name=orders", "-XX:StartFlightRecording=filename=CamelJBang.jfr");
            assertThat(cmd.subList(5, cmd.size())).containsExactly("-DskipTests", "package", "spring-boot:run");
            assertThat(project.resolve("camel-jbang-run-pom.xml")).content()
                    .contains("camel-cli-connector-starter", "camel-jfr-starter");
            assertThat(printer.getOutput()).contains("Running Spring Boot project: " + project);

            integration.stop();
            assertThat(project.resolve("camel-jbang-run-pom.xml")).doesNotExist();
        } finally {
            integration.stop();
        }
    }

    @Test
    void aCamelMainRunWithChaosAddsCamelChaos() throws Exception {
        RunRequest request = request();
        request.chaos = true;

        RunningIntegration integration = new ProjectRunner(context).start(request);
        try {
            Path project = Path.of(started.get(0).command().get(3)).getParent();
            assertThat(Files.readString(project.resolve("pom.xml"))).contains("<artifactId>camel-chaos</artifactId>");
        } finally {
            integration.stop();
        }
    }

    @Test
    void anExistingCamelMainProjectRunsWithChaos() throws Exception {
        RunRequest request = existingProject(RuntimeType.main);
        request.chaos = true;

        RunningIntegration integration = new ProjectRunner(context).start(request);
        try {
            Path project = dir.resolve("project");
            assertThat(project.resolve("camel-jbang-run-pom.xml")).content()
                    .contains("<artifactId>camel-cli-connector</artifactId>", "<artifactId>camel-chaos</artifactId>");
        } finally {
            integration.stop();
        }
    }

    private RunRequest existingProject(RuntimeType runtime) throws Exception {
        Path project = dir.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>orders</artifactId>
                  <version>1.0</version>
                </project>
                """);
        RunRequest request = new RunRequest();
        request.files.add(project.resolve("pom.xml").toString());
        request.runtime = runtime;
        request.workDir = dir.resolve("work");
        return request;
    }

    private RunRequest request() {
        RunRequest request = new RunRequest();
        Path route = dir.resolve("hello.camel.yaml");
        try {
            Files.writeString(route, """
                    - from:
                        uri: timer:hello
                        steps:
                          - to: log:hello
                    """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        request.files.add(route.toString());
        request.runtime = RuntimeType.main;
        request.workDir = dir.resolve("work");
        return request;
    }

    /**
     * A process that has ended with the given exit code.
     */
    private static final class EndedProcess extends Process {

        private final long pid;
        private final int exitCode;

        EndedProcess(long pid, int exitCode) {
            this.pid = pid;
            this.exitCode = exitCode;
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            // ended
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return Stream.empty();
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    }
}
