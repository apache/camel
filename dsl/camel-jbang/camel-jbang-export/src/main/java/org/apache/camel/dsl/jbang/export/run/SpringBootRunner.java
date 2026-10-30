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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.dsl.jbang.export.ExportRequest;
import org.apache.camel.dsl.jbang.export.common.ChildProcess;
import org.apache.camel.dsl.jbang.export.common.MavenCommand;
import org.apache.camel.dsl.jbang.export.common.Printer;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.maven.model.Model;

import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.addDependency;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.existingSpringBootJvmArgs;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.exportedRunJvmArgs;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.keepLeftoverRunLog;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.keepRunLog;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.once;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.readPom;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.resolveExistingProjectAppName;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.resolveExportedAppName;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.runExportRequest;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.writeRunPom;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.writeSpringBootLogbackConfig;

/**
 * Runs with Spring Boot: the files are exported to a Spring Boot project in a directory of the run, which is run with
 * mvn spring-boot:run; an existing Spring Boot project is run the same way.
 */
final class SpringBootRunner {

    private final ProjectRunner runner;
    private final RunRequest request;

    SpringBootRunner(ProjectRunner runner, RunRequest request) {
        this.runner = runner;
        this.request = request;
    }

    RunningIntegration start() throws Exception {
        // existing Maven project: run directly without export
        if (request.files != null && request.files.size() == 1 && request.files.get(0).endsWith("pom.xml")) {
            return startExistingProject();
        }

        AtomicReference<Process> processRef = new AtomicReference<>();
        AtomicReference<String> appNameRef = new AtomicReference<>();

        // create temp run dir
        Path runDirPath = Paths.get(ProjectRunner.RUN_PLATFORM_DIR, Long.toString(System.currentTimeMillis()));
        // the process (and those it started) stops with this JVM, then the files of the run are removed
        ChildProcess.Cleanup cleanup = once(() -> {
            RunPipeline.removeDir(runDirPath);
            // the log is kept as the previous run of the app (CAMEL-25832)
            keepRunLog(appNameRef.get(), processRef.get(), false);
        });
        ChildProcess.stopOnExit(processRef::get, cleanup);
        Files.createDirectories(runDirPath);

        // the code reload is not supported, since we use symlink, spring-boot devtools doesn't support symlink
        if (request.dev) {
            printer().printWarn("Code reload is not supported with Spring Boot.");
        }

        // export to hidden folder
        ExportRequest eq = runExportRequest(request, runDirPath, request.files);
        eq.javaLiveReload = false;
        eq.symbolicLink = request.dev;
        eq.runtime = RuntimeType.springBoot;
        eq.camelSpringBootVersion
                = request.camelSpringBootVersion != null ? request.camelSpringBootVersion : request.camelVersion;
        if (request.dev) {
            // hot-reload of spring-boot
            eq.dependencies.add("mvn:org.springframework.boot:spring-boot-devtools");
        }

        printer().println("Running using Spring Boot (preparing and downloading files)");

        // run export
        int exit = runner.export(eq.baseDir, eq);
        if (exit != 0) {
            return new EndedIntegration(exit);
        }

        // the exported project may have derived the application name from the source files, and the log file is
        // named after the name the application reports (what camel log and the TUI look for)
        String appName = resolveExportedAppName(runDirPath, eq.name);
        appNameRef.set(appName);
        keepLeftoverRunLog(appName);

        // prepare spring-boot for logging to file
        writeSpringBootLogbackConfig(Paths.get(eq.exportDir, "src/main/resources/logback.xml"), appName);

        // run spring-boot via maven
        List<String> mvnCmd = new ArrayList<>();
        mvnCmd.add(MavenCommand.wrapper(runDirPath).toString());
        mvnCmd.add("--quiet");
        mvnCmd.add("--file");
        mvnCmd.add(runDirPath.toRealPath().resolve("pom.xml").toString());
        String springBootRunJvmArgs = exportedRunJvmArgs(request, printer());
        if (springBootRunJvmArgs != null) {
            mvnCmd.add("-Dspring-boot.run.jvmArguments=" + springBootRunJvmArgs);
        }
        // run in the current directory, not the export folder, so the routes read and write files relative to where
        // camel run is started (CAMEL-25423)
        mvnCmd.add("-Dspring-boot.run.workingDirectory=" + Paths.get(".").toAbsolutePath().normalize());
        mvnCmd.add("spring-boot:run");

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(mvnCmd);
        pb.inheritIO(); // run in foreground (with IO so logs are visible)
        Process p = runner.getContext().startProcess(pb);
        processRef.set(p);
        return new ProcessIntegration(p, cleanup);
    }

    private RunningIntegration startExistingProject() throws Exception {
        AtomicReference<Process> processRef = new AtomicReference<>();
        Path pomPath = Path.of(request.files.get(0)).toAbsolutePath();
        Path projectDir = pomPath.getParent();

        // read the pom.xml and add camel-cli-connector-starter dependency
        Model model = readPom(pomPath);
        addDependency(model, "org.apache.camel.springboot", "camel-cli-connector-starter");
        if (request.jfrEnabled()) {
            addDependency(model, "org.apache.camel.springboot", "camel-jfr-starter");
        }
        // write temporary pom with cli-connector injected
        Path tempPom = writeRunPom(projectDir, model);

        // log to console and to file in ~/.camel (so camel log and the TUI can tail the logs)
        String appName = resolveExistingProjectAppName(projectDir, model);
        keepLeftoverRunLog(appName);
        Path logback = logbackConfig(appName);

        // the process (and those it started) stops with this JVM, then the files of the run are removed
        ChildProcess.Cleanup cleanup = once(() -> {
            Files.deleteIfExists(tempPom);
            if (logback != null) {
                Files.deleteIfExists(logback);
            }
            // the log is kept as the previous run of the app (CAMEL-25832)
            keepRunLog(appName, processRef.get(), false);
        });
        ChildProcess.stopOnExit(processRef::get, cleanup);

        // find mvnw or fall back to mvn
        String mvnCmd = MavenCommand.of(projectDir);

        List<String> cmd = new ArrayList<>();
        cmd.add(mvnCmd);
        cmd.add("--quiet");
        cmd.add("--file");
        cmd.add(tempPom.toString());
        cmd.add("-Dspring-boot.run.jvmArguments="
                + String.join(" ", existingSpringBootJvmArgs(request, appName, logback, printer())));
        cmd.add("-DskipTests");
        cmd.add("package");
        cmd.add("spring-boot:run");

        printer().println("Running Spring Boot project: " + projectDir);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(projectDir.toFile());
        pb.inheritIO();
        Process p = runner.getContext().startProcess(pb);
        processRef.set(p);
        return new ProcessIntegration(p, cleanup);
    }

    /**
     * The logback configuration an existing Spring Boot project runs with, logging to the {@code <name>.log} file so
     * the TUI can read the log: a temp file, so the project is not changed and needs no src/main/resources. Null when
     * it cannot be written.
     */
    static Path logbackConfig(String appName) {
        try {
            Path file = Files.createTempFile("camel-jbang-logback-", ".xml");
            file.toFile().deleteOnExit();
            writeSpringBootLogbackConfig(file, appName);
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    private Printer printer() {
        return runner.getContext().getPrinter();
    }
}
