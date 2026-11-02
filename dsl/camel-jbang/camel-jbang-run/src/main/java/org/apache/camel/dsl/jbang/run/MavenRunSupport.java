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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.dsl.jbang.run.common.CamelHome;
import org.apache.camel.dsl.jbang.run.common.ChildProcess;
import org.apache.camel.dsl.jbang.run.common.LogFileReader;
import org.apache.camel.dsl.jbang.run.common.PreviousRun;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.export.ExportRequest;
import org.apache.camel.util.json.JsonObject;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

/**
 * What the runs with Maven (Camel Main, Quarkus and Spring Boot) share: the directory of the run, the name of the
 * application, the JVM arguments of the options of the run, and the log of the run, which is kept as the previous run
 * of the application when it ends.
 */
final class MavenRunSupport {

    /**
     * The file with the log4j2 configuration that an existing Camel Main project is run with.
     */
    static final String CAMEL_MAIN_RUN_LOG_CONFIG = "camel-jbang-run-log4j2.properties";

    /**
     * The format of the log file of a Quarkus run: the same layout as the camel-jbang console logging, so the log file
     * reads the same for every runtime.
     */
    static final String QUARKUS_LOG_FILE_FORMAT
            = "%d{yyyy-MM-dd HH:mm:ss.SSS} %5p %i --- [%15.15t] %-40.40c{3.} : %s%e%n";

    /**
     * At most this many records at the end of the log are looked at for why a run failed.
     */
    private static final int FAILURE_RECORDS = 20;

    private MavenRunSupport() {
    }

    /**
     * The application name as configured in the exported project ({@code camel.main.name}), which is what the running
     * application reports to the CLI and the TUI.
     */
    static String resolveExportedAppName(Path runDirPath, String fallback) {
        Path props = runDirPath.resolve("src/main/resources/application.properties");
        if (Files.exists(props)) {
            try (InputStream is = Files.newInputStream(props)) {
                Properties p = new Properties();
                p.load(is);
                String name = p.getProperty("camel.main.name");
                if (name != null && !name.isBlank()) {
                    return name.trim();
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return fallback;
    }

    /**
     * The application name of an existing Maven project: {@code camel.main.name} from its application.properties, or
     * else the Maven artifactId (or the project directory name). The name is also passed to the application as
     * {@code camel.main.name}, so the name it reports to the CLI and the TUI matches the {@code <name>.log} file that
     * {@code camel log} and the TUI read when there is no {@code <pid>.log}.
     */
    static String resolveExistingProjectAppName(Path projectDir, Model model) {
        String name = resolveExportedAppName(projectDir, null);
        if (name == null && model != null && model.getArtifactId() != null && !model.getArtifactId().isBlank()) {
            name = model.getArtifactId().trim();
        }
        if (name == null) {
            name = projectDir.getFileName().toString();
        }
        return name;
    }

    /**
     * The JFR JVM arguments for runtimes that fork a subprocess, or {@code null} when JFR was not requested.
     */
    static String jfrJvmArgs(RunRequest request, Printer printer) {
        if (!request.jfrEnabled()) {
            return null;
        }
        StringBuilder arg = new StringBuilder();
        if (request.jvmArgs != null && request.jvmArgs.contains("-XX:StartFlightRecording")) {
            // the explicit JVM argument wins, as two recordings would otherwise compete for the same file
            printer.printWarn(
                    "--jvm-args already starts a flight recording, skipping -XX:StartFlightRecording from --jfr"
                              + " (Camel runtime instrumentation is still enabled)");
        } else {
            arg.append("-XX:StartFlightRecording=filename=").append(request.jfrFileName());
            if (request.jfrProfile != null) {
                arg.append(",settings=").append(request.jfrProfile);
            }
        }
        if (!arg.isEmpty()) {
            arg.append(" ");
        }
        arg.append("-Dcamel.main.startupRecorderRuntimeEnabled=true");
        return arg.toString();
    }

    static String mergeJvmArgs(String existing, String extra) {
        if (extra == null) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return extra;
        }
        return existing.trim() + " " + extra;
    }

    /**
     * The execution limits ({@code --max-seconds}, {@code --max-messages} and {@code --max-idle-seconds}) as
     * {@code camel.main.durationMax*} system properties, which all the runtimes honour.
     */
    static List<String> durationLimitArgs(RunRequest request) {
        List<String> args = new ArrayList<>();
        if (request.maxSeconds > 0) {
            args.add("-Dcamel.main.durationMaxSeconds=" + request.maxSeconds);
        }
        if (request.maxMessages > 0) {
            args.add("-Dcamel.main.durationMaxMessages=" + request.maxMessages);
        }
        if (request.maxIdleSeconds > 0) {
            args.add("-Dcamel.main.durationMaxIdleSeconds=" + request.maxIdleSeconds);
        }
        return args;
    }

    /**
     * The JVM arguments the exported Quarkus or Spring Boot project is run with ({@code jvm.args} and
     * {@code spring-boot.run.jvmArguments}): {@code --jvm-args}, the flight recording and the execution limits. Returns
     * null when there are none.
     */
    static String exportedRunJvmArgs(RunRequest request, Printer printer) {
        String args = mergeJvmArgs(request.jvmArgs, jfrJvmArgs(request, printer));
        List<String> limits = durationLimitArgs(request);
        if (!limits.isEmpty()) {
            args = mergeJvmArgs(args, String.join(" ", limits));
        }
        return args != null && !args.isBlank() ? args.trim() : null;
    }

    /**
     * The {@code -D} system properties an existing Maven project is run with: the HTTP port ({@code --port}, as the
     * given property), the execution limits ({@code --max-seconds}, {@code --max-messages} and
     * {@code --max-idle-seconds}) and the additional properties ({@code --prop}).
     */
    static List<String> existingProjectSystemProperties(RunRequest request, String portKey) {
        List<String> args = new ArrayList<>();
        if (request.port != -1) {
            args.add("-D" + portKey + "=" + request.port);
        }
        args.addAll(durationLimitArgs(request));
        if (request.applicationProperties != null) {
            for (String p : request.applicationProperties) {
                String s = p.trim();
                if (!s.isEmpty()) {
                    args.add(s.startsWith("-D") ? s : "-D" + s);
                }
            }
        }
        return args;
    }

    /**
     * The {@code -D} system properties an existing Camel Main project is run with (Camel runs inside the Maven JVM so
     * they go on the Maven command line): the log4j2 configuration, the profile, the name, the port and properties.
     */
    static List<String> existingCamelMainSystemProperties(RunRequest request, String appName, Path logConfig) {
        List<String> args = new ArrayList<>();
        args.add("-Dlog4j2.configurationFile=" + logConfig.toAbsolutePath());
        if (request.profile != null && !"prod".equals(request.profile)) {
            args.add("-Dcamel.main.profile=" + request.profile);
        }
        args.add("-Dcamel.main.name=" + appName);
        args.addAll(existingProjectSystemProperties(request, "camel.server.port"));
        return args;
    }

    /**
     * Writes the log4j2 configuration used by {@code camel run --runtime=main} to the given file. It logs to the
     * console, and to the {@code <name>.log} file in the {@code ~/.camel} directory. A log a run of that name left
     * behind is kept as its previous run first, so the new log does not start with the output of that run.
     */
    static void writeCamelMainRunLogConfig(Path target, String appName, String loggingLevel) throws IOException {
        Path logFile = runLogFile(appName);
        keepLeftoverRunLog(appName);
        String fileName = logFile.toAbsolutePath().toString().replace("\\", "/");
        try (InputStream is = MavenRunSupport.class.getClassLoader()
                .getResourceAsStream("camel-main-run-log4j2.properties")) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("{{logFile}}", fileName)
                    .replace("{{level}}", loggingLevel != null ? loggingLevel : "info");
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
        }
    }

    /**
     * The log a run on a Maven runtime writes in the Camel directory: {@code <name>.log}, which {@code camel log} and
     * the TUI read when there is no {@code <pid>.log}.
     */
    static Path runLogFile(String appName) {
        return CamelHome.getCamelDir().resolve(appName + ".log");
    }

    /**
     * A log that a run of this name left behind, such as a run that was killed so it could not keep its log, is kept as
     * the previous run of the name before the new run writes its own (CAMEL-25832).
     */
    static void keepLeftoverRunLog(String appName) {
        PreviousRun.keepLeftover(runLogFile(appName), appName);
    }

    /**
     * When a run on a Maven runtime ends, its log is kept as the previous run of its name, as camel run does for the
     * default runtime (CAMEL-25784), so camel log --previous and the AI tools can read why the app stopped, such as an
     * app that failed to start (CAMEL-25832).
     *
     * @param appName the name of the app, or null when the run did not get that far
     * @param process the last process of the run, which has stopped
     * @param app     whether the process is the app itself (java -jar, or mvn camel:run, where Camel runs in the Maven
     *                JVM), or Maven that runs the app in a process of its own (quarkus:run, quarkus:dev,
     *                spring-boot:run), whose pid and exit code are not those of the app
     */
    static void keepRunLog(String appName, Process process, boolean app) {
        Integer code = process != null && !process.isAlive() ? process.exitValue() : null;
        keepRunLog(appName, app && process != null ? process.pid() : 0, code, app);
    }

    static Path keepRunLog(String appName, long pid, Integer code, boolean app) {
        if (appName == null) {
            return null;
        }
        String signal = code != null ? signalOf(code) : null;
        // the exit code of Maven is not the one of the app
        Integer exitCode = app || signal != null ? code : null;
        String failure = null;
        if (code != null && code != 0 && signal == null) {
            // why it failed, as the log of the app says, as camel run says it for the default runtime; else that Maven
            // failed, as the exit code of Maven is not given
            failure = failureInLog(runLogFile(appName));
            if (failure == null && !app) {
                failure = "Maven ended with exit code " + code;
            }
        }
        return PreviousRun.keep(runLogFile(appName), appName, pid, exitCode, signal, failure);
    }

    /**
     * Why a run that failed ended, as its log says: the first ERROR record among the last records of the log, with the
     * cause of its stack trace, such as "Application run failed: java.lang.IllegalArgumentException: Invalid time
     * pattern: notanumber" (Spring Boot), "Failed to start application: ..." (Quarkus) or "Error starting Camel: ..."
     * (Camel Main). The first, as an error that follows it is about the same failure in other words: Quarkus logs the
     * failure of Camel, then that Quarkus failed to start. Null when the end of the log has no ERROR.
     */
    static String failureInLog(Path log) {
        if (!Files.isRegularFile(log)) {
            return null;
        }
        String answer = null;
        if (LogFileReader.read(log, FAILURE_RECORDS, null, null, false).get("lines") instanceof List<?> lines) {
            // newest first: the last ERROR found is the first one logged
            for (Object o : lines) {
                if (o instanceof JsonObject r && "ERROR".equalsIgnoreCase(r.getString("level"))) {
                    String message = r.getStringOrDefault("message", "").strip();
                    String cause = r.getString("cause");
                    answer = cause != null && !message.contains(cause) ? message + ": " + cause : message;
                }
            }
        }
        return answer;
    }

    /**
     * The signal a process stopped by it exits with, as 128 and the number of the signal: 130 for SIGINT (Ctrl+C), 137
     * for SIGKILL and 143 for SIGTERM; null for another exit code.
     */
    static String signalOf(int exitCode) {
        return switch (exitCode) {
            case 130 -> "SIGINT";
            case 137 -> "SIGKILL";
            case 143 -> "SIGTERM";
            default -> null;
        };
    }

    /**
     * The request of the export of the project that a run builds and runs, to the given directory of the run: the
     * options of the run, the CLI connector (and the flight recorder) for the tools, and no logging of the export.
     */
    static ExportRequest runExportRequest(RunRequest request, Path runDir, List<String> files) {
        ExportRequest eq = new ExportRequest(request);
        eq.files = files;
        eq.mavenWrapper = true;
        eq.exportDir = runDir.toString();
        if (eq.gav == null) {
            if (eq.name == null) {
                eq.name = "jbang-run-dummy";
            }
            eq.gav = "org.example.project:" + eq.name + ":1.0-SNAPSHOT";
        }
        eq.dependencies = new ArrayList<>(request.dependencies);
        eq.dependencies.add("camel:cli-connector");
        if (request.jfrEnabled()) {
            eq.dependencies.add("camel:jfr");
        }
        if (request.chaos) {
            // camel run --chaos with the Camel Main runtime (Spring Boot and Quarkus refuse it, see Run)
            eq.dependencies.add("camel:chaos");
        }
        eq.quiet = true;
        eq.logging = false;
        eq.loggingLevel = "off";
        eq.consoleForRun = true;
        return eq;
    }

    /**
     * Reads the pom.xml of an existing Maven project.
     */
    static Model readPom(Path pom) throws Exception {
        try (Reader reader = Files.newBufferedReader(pom)) {
            return new MavenXpp3Reader().read(reader);
        }
    }

    /**
     * Adds the dependency to the project, unless it has it already.
     */
    static void addDependency(Model model, String groupId, String artifactId) {
        boolean found = model.getDependencies().stream().anyMatch(d -> artifactId.equals(d.getArtifactId()));
        if (!found) {
            Dependency d = new Dependency();
            d.setGroupId(groupId);
            d.setArtifactId(artifactId);
            model.getDependencies().add(d);
        }
    }

    /**
     * Writes the pom.xml that an existing Maven project is run with, next to its own, which is not changed.
     */
    static Path writeRunPom(Path projectDir, Model model) throws IOException {
        Path tempPom = projectDir.resolve("camel-jbang-run-pom.xml");
        try (FileOutputStream fos = new FileOutputStream(tempPom.toFile())) {
            new MavenXpp3Writer().write(fos, model);
        }
        return tempPom;
    }

    /**
     * The Quarkus logging properties that make an existing Quarkus project also log to the {@code <name>.log} file in
     * the Camel directory (the same as the exported project does via its application.properties).
     */
    static List<String> quarkusLogFileJvmArgs(String appName) {
        String fileName = runLogFile(appName).toAbsolutePath().toString().replace("\\", "/");
        List<String> args = new ArrayList<>();
        args.add("-Dquarkus.log.file.enabled=true");
        args.add("-Dquarkus.log.file.path=" + fileName);
        // quoted as the format has spaces (quarkus:dev parses jvm.args as a command line)
        args.add("-Dquarkus.log.file.format=\"" + QUARKUS_LOG_FILE_FORMAT + "\"");
        return args;
    }

    /**
     * The JVM arguments ({@code jvm.args}) an existing Quarkus project is run with: logging to file, the profile, the
     * name, the port and properties, the flight recording and {@code --jvm-args}.
     */
    static List<String> existingQuarkusJvmArgs(RunRequest request, String appName, Printer printer) {
        List<String> args = new ArrayList<>(quarkusLogFileJvmArgs(appName));
        args.addAll(existingProjectJvmArgs(request, appName, "quarkus.http.port", printer));
        return args;
    }

    /**
     * The JVM arguments ({@code spring-boot.run.jvmArguments}) an existing Spring Boot project is run with: logging to
     * file (the given logback configuration, unless null), the profile, the name (so the app reports the name of its
     * log file), the port and properties, the flight recording and {@code --jvm-args}.
     */
    static List<String> existingSpringBootJvmArgs(RunRequest request, String appName, Path logback, Printer printer) {
        List<String> args = new ArrayList<>();
        if (logback != null) {
            args.add("-Dlogging.config=" + logback.toUri());
        }
        args.addAll(existingProjectJvmArgs(request, appName, "server.port", printer));
        return args;
    }

    private static List<String> existingProjectJvmArgs(
            RunRequest request, String appName, String portKey, Printer printer) {
        List<String> args = new ArrayList<>();
        if (request.profile != null && !"prod".equals(request.profile)) {
            args.add("-Dcamel.main.profile=" + request.profile);
        }
        args.add("-Dcamel.main.name=" + appName);
        args.addAll(existingProjectSystemProperties(request, portKey));
        String jfrArg = jfrJvmArgs(request, printer);
        if (jfrArg != null) {
            args.add(jfrArg);
        }
        if (request.jvmArgs != null && !request.jvmArgs.isBlank()) {
            args.add(request.jvmArgs.trim());
        }
        return args;
    }

    /**
     * Writes the logback configuration used by {@code camel run --runtime=spring-boot} to the given file. It logs to
     * the console, and to the {@code <name>.log} file in the Camel directory, as the other runtimes do, so the log can
     * be kept as the previous run of the app (CAMEL-25832).
     */
    static void writeSpringBootLogbackConfig(Path target, String appName) throws IOException {
        String fileName = runLogFile(appName).toAbsolutePath().toString().replace("\\", "/")
                .replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
        try (InputStream is = MavenRunSupport.class.getClassLoader().getResourceAsStream("spring-boot-logback.xml")) {
            if (is == null) {
                throw new IOException("spring-boot-logback.xml not found");
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8).replace("{{logFile}}", fileName);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
        }
    }

    /**
     * The cleanup runs once: when the integration is stopped, or when this JVM exits.
     */
    static ChildProcess.Cleanup once(ChildProcess.Cleanup cleanup) {
        AtomicBoolean done = new AtomicBoolean();
        return () -> {
            if (done.compareAndSet(false, true)) {
                cleanup.run();
            }
        };
    }
}
