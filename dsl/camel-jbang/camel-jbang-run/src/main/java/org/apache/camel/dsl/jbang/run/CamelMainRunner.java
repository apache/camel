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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.run.common.ChildProcess;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.MavenCommand;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.export.ExportRequest;
import org.apache.camel.util.FileUtil;
import org.apache.maven.model.Model;

import static org.apache.camel.dsl.jbang.run.MavenRunSupport.CAMEL_MAIN_RUN_LOG_CONFIG;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.addDependency;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.durationLimitArgs;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.existingCamelMainSystemProperties;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.jfrJvmArgs;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.keepRunLog;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.mergeJvmArgs;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.once;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.readPom;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.resolveExistingProjectAppName;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.resolveExportedAppName;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.runExportRequest;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.writeCamelMainRunLogConfig;
import static org.apache.camel.dsl.jbang.run.MavenRunSupport.writeRunPom;

/**
 * Runs with Camel Main: the files are exported to a Camel Main project in a directory of the run, which is packaged
 * with Maven and run with java -jar; an existing Camel Main project is run with mvn camel:run.
 */
final class CamelMainRunner {

    private final ProjectRunner runner;
    private final RunRequest request;

    CamelMainRunner(ProjectRunner runner, RunRequest request) {
        this.runner = runner;
        this.request = request;
    }

    RunningIntegration start() throws Exception {
        // existing Maven project: run directly without export
        if (request.files != null && request.files.size() == 1 && request.files.get(0).endsWith("pom.xml")) {
            return startExistingProject();
        }

        String unsupported = null;
        if (request.openapi != null) {
            unsupported = "--open-api";
        } else if (request.mcpStdio) {
            unsupported = "--mcp-stdio";
        } else if (request.empty) {
            unsupported = "--empty";
        }
        if (unsupported != null) {
            printer().printErr("Run Camel Main with " + unsupported + " is not supported (use --runtime=jbang)");
            return new EndedIntegration(1);
        }

        List<String> files = new ArrayList<>(request.files);
        // source-dir is the directory with the files to run (and reload in dev mode)
        if (request.sourceDir != null) {
            if (files.isEmpty()) {
                ExportHelper.dirToFiles(request.sourceDir, files);
            }
            if (files.isEmpty()) {
                printer().printErr("No files to run in source-dir: " + request.sourceDir);
                return new EndedIntegration(1);
            }
        }

        AtomicReference<Process> processRef = new AtomicReference<>();
        AtomicReference<String> appNameRef = new AtomicReference<>();
        // the JVM of the app, once the project is built
        AtomicReference<Process> appRef = new AtomicReference<>();

        // create temp run dir
        Path runDirPath = Paths.get(ProjectRunner.RUN_PLATFORM_DIR, Long.toString(System.currentTimeMillis()));
        // the process (and those it started) stops with this JVM, then the files of the run are removed
        ChildProcess.Cleanup cleanup = once(() -> {
            RunPipeline.removeDir(runDirPath);
            // the log is kept as the previous run of the app (CAMEL-25832); a build that failed has no log
            if (appRef.get() != null) {
                keepRunLog(appNameRef.get(), appRef.get(), true);
            }
        });
        ChildProcess.stopOnExit(processRef::get, cleanup);
        Files.createDirectories(runDirPath);

        // export to hidden folder
        ExportRequest eq = runExportRequest(request, runDirPath, files);
        eq.javaLiveReload = false;
        eq.symbolicLink = false;
        eq.runtime = RuntimeType.main;

        printer().println("Running using Camel Main (preparing and downloading files)");

        // run export
        int exit = runner.export(eq.baseDir, eq);
        if (exit != 0) {
            return new EndedIntegration(exit);
        }
        // the exported project may have derived the application name from the source files
        String appName = resolveExportedAppName(runDirPath, eq.name);
        appNameRef.set(appName);

        // log to console and to file in ~/.camel (so camel log and the TUI can tail the logs)
        writeCamelMainRunLogConfig(runDirPath.resolve("src/main/resources/log4j2.properties"), appName,
                request.loggingLevel);

        // package via maven (the runner JAR contains all dependencies)
        List<String> mvnCmd = new ArrayList<>();
        mvnCmd.add(MavenCommand.wrapper(runDirPath).toString());
        mvnCmd.add("--quiet");
        mvnCmd.add("--file");
        mvnCmd.add(runDirPath.toRealPath().resolve("pom.xml").toString());
        mvnCmd.add("-DskipTests");
        mvnCmd.add("package");

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(mvnCmd);
        pb.inheritIO();
        Process p = runner.getContext().startProcess(pb);
        processRef.set(p);
        exit = p.waitFor();
        if (exit != 0) {
            printer().printErr("Failed to build Camel Main project in: " + runDirPath);
            return new EndedIntegration(exit);
        }

        Path jar = findRunnerJar(runDirPath.resolve("target"));
        if (jar == null) {
            printer().printErr("Cannot find runner JAR in: " + runDirPath.resolve("target"));
            return new EndedIntegration(1);
        }

        // run in a plain JVM
        List<String> javaCmd = new ArrayList<>();
        javaCmd.add(runner.getContext().getJavaHome().resolve("bin").resolve("java").toString());
        String runJvmArgs = mergeJvmArgs(request.jvmArgs, jfrJvmArgs(request, printer()));
        if (runJvmArgs != null && !runJvmArgs.isBlank()) {
            javaCmd.addAll(ExportHelper.splitArguments(runJvmArgs));
        }
        if (request.jvmDebugPort > 0) {
            javaCmd.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:" + request.jvmDebugPort);
        }
        if (request.profile != null) {
            javaCmd.add("-Dcamel.main.profile=" + request.profile);
        }
        if (request.dev) {
            javaCmd.addAll(reloadArgs(files));
        }
        javaCmd.addAll(durationLimitArgs(request));
        javaCmd.add("-jar");
        javaCmd.add(jar.toAbsolutePath().toString());

        pb = new ProcessBuilder();
        pb.command(javaCmd);
        // run in the current directory (not the export folder), so the routes read and write files relative to
        // where camel run is started, as with JBang: file:orders in an example reads its orders folder (CAMEL-25423)
        pb.inheritIO(); // run in foreground (with IO so logs are visible)
        p = runner.getContext().startProcess(pb);
        processRef.set(p);
        appRef.set(p);
        return new ProcessIntegration(p, cleanup);
    }

    private RunningIntegration startExistingProject() throws Exception {
        AtomicReference<Process> processRef = new AtomicReference<>();
        Path pomPath = Path.of(request.files.get(0)).toAbsolutePath();
        Path projectDir = pomPath.getParent();

        // read the pom.xml and add camel-cli-connector dependency
        Model model = readPom(pomPath);
        addDependency(model, "org.apache.camel", "camel-cli-connector");
        if (request.jfrEnabled()) {
            addDependency(model, "org.apache.camel", "camel-jfr");
        }
        if (request.chaos) {
            addDependency(model, "org.apache.camel", "camel-chaos");
        }
        // write temporary pom with cli-connector injected
        Path tempPom = writeRunPom(projectDir, model);

        // log to console and to file in ~/.camel (so camel log and the TUI can tail the logs)
        String appName = resolveExistingProjectAppName(projectDir, model);
        Path logConfig = projectDir.resolve("target").resolve(CAMEL_MAIN_RUN_LOG_CONFIG);
        writeCamelMainRunLogConfig(logConfig, appName, request.loggingLevel);

        // the process (and those it started) stops with this JVM, then the files of the run are removed
        ChildProcess.Cleanup cleanup = once(() -> {
            Files.deleteIfExists(tempPom);
            Files.deleteIfExists(logConfig);
            // the log is kept as the previous run of the app, which ran in the Maven JVM (CAMEL-25832)
            keepRunLog(appName, processRef.get(), true);
        });
        ChildProcess.stopOnExit(processRef::get, cleanup);

        // find mvnw or fall back to mvn
        String mvnCmd = MavenCommand.of(projectDir);

        List<String> cmd = new ArrayList<>();
        cmd.add(mvnCmd);
        cmd.add("--quiet");
        cmd.add("--file");
        cmd.add(tempPom.toString());
        // camel:run runs Camel inside the Maven JVM, so the system properties go directly on the Maven command line
        cmd.addAll(existingCamelMainSystemProperties(request, appName, logConfig));
        cmd.add("-DskipTests");
        // camel:run does not compile the project itself
        cmd.add("compile");
        cmd.add("camel:run");

        printer().println("Running Camel Main project: " + projectDir);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
        pb.directory(projectDir.toFile());
        pb.inheritIO();
        // JVM options (--jvm-args and --jfr) must be set on the Maven JVM as Camel runs inside it
        String mavenOpts = mergeJvmArgs(request.jvmArgs, jfrJvmArgs(request, printer()));
        if (mavenOpts != null && !mavenOpts.isBlank()) {
            pb.environment().merge("MAVEN_OPTS", mavenOpts.trim(), (existing, extra) -> existing + " " + extra);
        }
        Process p = runner.getContext().startProcess(pb);
        processRef.set(p);
        return new ProcessIntegration(p, cleanup);
    }

    /**
     * Route reload (dev mode) for Camel Main watches the original source files, as the exported project only contains
     * copies of them.
     */
    private List<String> reloadArgs(List<String> files) {
        List<String> args = new ArrayList<>();
        String reloadDir;
        String pattern;
        if (request.sourceDir != null) {
            reloadDir = request.sourceDir;
            pattern = RunPipeline.sourceDirReloadPattern(request.dataDir);
        } else {
            // use the directory of the first file that is in another folder than the current dir
            reloadDir = ".";
            StringJoiner sj = new StringJoiner(",");
            for (String f : files) {
                String path = FileUtil.onlyPath(f);
                if (path != null && !path.equals(".camel-jbang") && ".".equals(reloadDir)) {
                    reloadDir = path;
                }
                sj.add(FileUtil.stripPath(f));
            }
            pattern = sj.toString();
        }
        args.add("-Dcamel.main.routesReloadEnabled=true");
        args.add("-Dcamel.main.routesReloadDirectory=" + Paths.get(reloadDir).toAbsolutePath().normalize());
        args.add("-Dcamel.main.routesReloadPattern=" + pattern);
        args.add("-Dcamel.main.routesReloadDirectoryRecursive=" + (request.sourceDir != null ? "true" : "false"));
        args.add("-Dcamel.main.routesReloadRemoveAllRoutes=true");
        return args;
    }

    private static Path findRunnerJar(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (Stream<Path> paths = Files.list(target)) {
            return paths
                    .filter(f -> f.getFileName().toString().endsWith(".jar"))
                    .filter(f -> !f.getFileName().toString().startsWith("original-"))
                    .findFirst().orElse(null);
        }
    }

    private Printer printer() {
        return runner.getContext().getPrinter();
    }
}
