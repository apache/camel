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

import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.QUARKUS_LOG_FILE_FORMAT;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.addDependency;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.existingQuarkusJvmArgs;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.exportedRunJvmArgs;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.keepLeftoverRunLog;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.keepRunLog;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.once;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.readPom;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.resolveExistingProjectAppName;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.resolveExportedAppName;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.runExportRequest;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.runLogFile;
import static org.apache.camel.dsl.jbang.export.run.MavenRunSupport.writeRunPom;

/**
 * Runs with Quarkus: the files are exported to a Quarkus project in a directory of the run, which is run with mvn
 * quarkus:run (quarkus:dev in dev mode); an existing Quarkus project is run the same way.
 */
final class QuarkusRunner {

    private final ProjectRunner runner;
    private final RunRequest request;

    QuarkusRunner(ProjectRunner runner, RunRequest request) {
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

        // export to hidden folder
        ExportRequest eq = runExportRequest(request, runDirPath, request.files);
        eq.javaLiveReload = request.dev;
        eq.symbolicLink = request.dev;
        eq.runtime = RuntimeType.quarkus;

        printer().println("Running using Quarkus (preparing and downloading files)");

        // run export
        int exit = runner.export(eq.baseDir, eq);
        if (exit != 0) {
            return new EndedIntegration(exit);
        }

        // the exported project may have derived the application name from the source files, and the log file
        // must be named after the name the application reports (which is what camel log and the TUI look for)
        String appName = resolveExportedAppName(runDirPath, eq.name);
        appNameRef.set(appName);
        keepLeftoverRunLog(appName);

        // prepare quarkus for logging to file
        Path appProps = Paths.get(eq.exportDir, "src/main/resources/application.properties");
        if (Files.exists(appProps)) {
            String content = Files.readString(appProps);
            // in the Camel directory, where camel log looks for it (as for an existing Quarkus project)
            String logFile = runLogFile(appName).toAbsolutePath().toString().replace("\\", "/");
            content += "\n# logging to file\n"
                       + "quarkus.log.file.enabled=true\n"
                       + "quarkus.log.file.path=" + logFile + "\n"
                       + "quarkus.log.file.format=" + QUARKUS_LOG_FILE_FORMAT + "\n";
            Files.writeString(appProps, content);
        }

        // run quarkus via maven
        List<String> mvnCmd = new ArrayList<>();
        mvnCmd.add(MavenCommand.wrapper(runDirPath).toString());
        mvnCmd.add("--quiet");
        mvnCmd.add("--file");
        mvnCmd.add(runDirPath.toRealPath().resolve("pom.xml").toString());
        String quarkusRunJvmArgs = exportedRunJvmArgs(request, printer());
        if (quarkusRunJvmArgs != null) {
            mvnCmd.add("-Djvm.args=" + quarkusRunJvmArgs);
        }
        if (request.console) {
            // the console extension only exposes the console in dev and test mode, and camel run without --dev is
            // quarkus:run (prod mode). This is build-time configuration, and is set on the build of this temporary
            // project only, so an exported project does not have the console exposed in prod mode
            mvnCmd.add("-Dquarkus.camel.console.exposure-mode=ALL");
        }
        mvnCmd.add("package");
        mvnCmd.add("quarkus:" + (request.dev ? "dev" : "run"));

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

        // read the pom.xml and add camel-quarkus-cli-connector dependency
        Model model = readPom(pomPath);
        addDependency(model, "org.apache.camel.quarkus", "camel-quarkus-cli-connector");
        if (request.jfrEnabled()) {
            addDependency(model, "org.apache.camel.quarkus", "camel-quarkus-jfr");
        }
        // write temporary pom with cli-connector injected
        Path tempPom = writeRunPom(projectDir, model);

        // log to console and to file in ~/.camel (so camel log and the TUI can tail the logs)
        String appName = resolveExistingProjectAppName(projectDir, model);
        keepLeftoverRunLog(appName);

        // the process (and those it started) stops with this JVM, then the files of the run are removed
        ChildProcess.Cleanup cleanup = once(() -> {
            Files.deleteIfExists(tempPom);
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
        cmd.add("-Djvm.args=" + String.join(" ", existingQuarkusJvmArgs(request, appName, printer())));
        cmd.add("-DskipTests");
        cmd.add("package");
        cmd.add("quarkus:" + (request.dev ? "dev" : "run"));

        printer().println("Running Quarkus project: " + projectDir);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(projectDir.toFile());
        pb.inheritIO();
        Process p = runner.getContext().startProcess(pb);
        processRef.set(p);
        return new ProcessIntegration(p, cleanup);
    }

    private Printer printer() {
        return runner.getContext().getPrinter();
    }
}
