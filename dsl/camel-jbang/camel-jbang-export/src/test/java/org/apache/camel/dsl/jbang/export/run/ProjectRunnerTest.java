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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.export.DependencyResolver;
import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.camel.dsl.jbang.export.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                if (pb.command().contains("package")) {
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
            assertThat(project.getParent().getFileName()).hasToString(MavenRunSupport.RUN_PLATFORM_DIR);
            assertThat(project.resolve("pom.xml")).exists();
            assertThat(Files.readString(project.resolve("pom.xml"))).contains("camel-cli-connector");

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
    void theOtherRuntimesAreNotRunYet() {
        RunRequest request = request();
        request.runtime = RuntimeType.quarkus;

        assertThatThrownBy(() -> new ProjectRunner(context).start(request))
                .isInstanceOf(IllegalArgumentException.class);
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
