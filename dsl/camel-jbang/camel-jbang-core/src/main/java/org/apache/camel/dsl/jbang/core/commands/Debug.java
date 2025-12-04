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

import static org.apache.camel.dsl.jbang.core.common.CommandLineHelper.CAMEL_JBANG_WORK_DIR;
import static org.apache.camel.util.IOHelper.buffered;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.dsl.jbang.core.commands.action.MessageTableHelper;
import org.apache.camel.dsl.jbang.core.common.CamelCommandHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "debug", description = "Debug local Camel integration", sortOptions = false, showDefaultValues = true)
public class Debug extends Run {

    @CommandLine.Option(
            names = {"--breakpoint"},
            description =
                    "To set breakpoint at the given node id (Multiple ids can be separated by comma). If no breakpoint is set, then the first route is automatic selected.")
    String breakpoint;

    @CommandLine.Option(
            names = {"--output"},
            description =
                    "File to store the current message body (will override). This allows for manual inspecting the message later.")
    String output;

    @CommandLine.Option(
            names = {"--stop-on-exit"},
            defaultValue = "true",
            description = "Whether to stop the running Camel on exit")
    boolean stopOnExit = true;

    @CommandLine.Option(
            names = {"--log-lines"},
            defaultValue = "10",
            description = "Number of log lines to display on top of screen")
    int logLines = 10;

    @CommandLine.Option(
            names = {"--timestamp"},
            defaultValue = "true",
            description = "Print timestamp.")
    boolean timestamp = true;

    @CommandLine.Option(
            names = {"--ago"},
            description = "Use ago instead of yyyy-MM-dd HH:mm:ss in timestamp.")
    boolean ago;

    @CommandLine.Option(
            names = {"--mask"},
            description =
                    "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @CommandLine.Option(
            names = {"--source"},
            description = "Prefer to display source filename/code instead of IDs")
    boolean source;

    @CommandLine.Option(
            names = {"--show-exchange-properties"},
            defaultValue = "false",
            description = "Show exchange properties in debug messages")
    boolean showExchangeProperties;

    @CommandLine.Option(
            names = {"--show-exchange-variables"},
            defaultValue = "true",
            description = "Show exchange variables in debug messages")
    boolean showExchangeVariables = true;

    @CommandLine.Option(
            names = {"--show-headers"},
            defaultValue = "true",
            description = "Show message headers in debug messages")
    boolean showHeaders = true;

    @CommandLine.Option(
            names = {"--show-body"},
            defaultValue = "true",
            description = "Show message body in debug messages")
    boolean showBody = true;

    @CommandLine.Option(
            names = {"--show-exception"},
            defaultValue = "true",
            description = "Show exception and stacktrace for failed messages")
    boolean showException = true;

    @CommandLine.Option(
            names = {"--pretty"},
            description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    private MessageTableHelper tableHelper;

    // state from spawned debugging process
    private InputStream spawnOutput;
    private InputStream spawnError;
    private final List<String> logBuffer = new ArrayList<>(100);
    private final AtomicBoolean logUpdated = new AtomicBoolean();
    private SuspendedRow suspendedRow = new SuspendedRow();
    private final AtomicBoolean waitForUser = new AtomicBoolean();
    private final AtomicLong debugCounter = new AtomicLong();

    public Debug(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (!exportRun) {
            printConfigurationValues("Debugging integration with the following configuration:");
        }

        Integer exit = runDebug();
        if (exit != null && exit != 0) {
            return exit;
        }

        // to shut down debug process if ctrl + c
        if (stopOnExit) {
            installHangupInterceptor();
        }

        tableHelper = new MessageTableHelper();
        tableHelper.setPretty(pretty);
        tableHelper.setLoggingColor(loggingColor);
        tableHelper.setShowExchangeProperties(showExchangeProperties);
        tableHelper.setShowExchangeVariables(showExchangeVariables);

        // read log input
        final AtomicBoolean quit = new AtomicBoolean();
        final Console c = System.console();
        Thread t = new Thread(
                () -> {
                    doReadLog(quit);
                },
                "ReadLog");
        t.start();

        // read CLI input from user
        Thread t2 = new Thread(() -> doRead(c, quit), "ReadCommand");
        t2.start();

        do {
            exit = doWatch();
            if (exit == 0) {
                // wait a little bit before loading again
                Thread.sleep(100);
            } else if (exit == -1) {
                // maybe failed on startup, so dump log buffer
                for (String line : logBuffer) {
                    printer().println(line);
                }
                // and any error
                if (spawnError != null) {
                    try {
                        String text = IOHelper.loadText(spawnError);
                        printer().println(text);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                return -1;
            }
        } while (exit == 0 && !quit.get());

        return 0;
    }

    private void doReadLog(AtomicBoolean quit) {
        do {
            if (spawnOutput != null) {
                InputStreamReader isr = new InputStreamReader(spawnOutput);
                try {
                    BufferedReader reader = buffered(isr);
                    while (true) {
                        String line = reader.readLine();
                        if (line != null) {
                            while (logBuffer.size() >= 100) {
                                logBuffer.remove(0);
                            }
                            logBuffer.add(line);
                            logUpdated.set(true);
                        } else {
                            break;
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        } while (!quit.get());
    }

    private void doRead(Console c, AtomicBoolean quit) {
        do {
            String line = c.readLine();
            if (line != null) {
                line = line.trim();
                if ("q".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
                    quit.set(true);
                } else {
                    // continue breakpoint
                    if (suspendedRow != null) {
                        // step to exit because it was the last
                        if (suspendedRow.last) {
                            // we need to clear screen so fool by saying log is updated
                            logUpdated.set(true);
                        }
                    }
                    String cmd = "step";
                    int position = lineIsNumber(line);
                    if (line.equalsIgnoreCase("o") || line.equalsIgnoreCase("over")) {
                        cmd = "stepover";
                    } else if (line.equalsIgnoreCase("s") || line.equalsIgnoreCase("skip")) {
                        cmd = "skipover";
                    }
                    sendDebugCommand(spawnPid, cmd, null, position);
                }
                // user have pressed ENTER so continue
                waitForUser.set(false);
            }
        } while (!quit.get());
    }

    @Override
    protected int runDebug(KameletMain main) throws Exception {
        File pom = new File("pom.xml");
        if (pom.isFile() && pom.exists()) {
            try (InputStream is = new FileInputStream(pom)) {
                String text = IOHelper.loadText(is);
                if (text.contains("camel-spring-boot-bom")) {
                    return doRunDebugSpringBoot(main);
                } else if (text.contains("org.apache.camel.quarkus")) {
                    return doRunDebugQuarkus(main);
                }
            }
        }
        return doRunDebug(main);
    }

    private int doRunDebugSpringBoot(KameletMain main) throws Exception {
        Path pom = Paths.get("pom.xml");
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        try (Reader reader = Files.newBufferedReader(pom)) {
            Model model = mavenReader.read(reader);

            // include camel-debug dependency
            Dependency d = new Dependency();
            d.setGroupId("org.apache.camel.springboot");
            d.setArtifactId("camel-debug-starter");
            model.getDependencies().add(d);
            d = new Dependency();
            d.setGroupId("org.apache.camel.springboot");
            d.setArtifactId("camel-cli-connector-starter");
            model.getDependencies().add(d);

            Profile mp = new Profile();
            model.addProfile(mp);
            mp.setId("camel-debug");
            Activation a = new Activation();
            a.setActiveByDefault(true);
            mp.setActivation(a);

            Build b = new Build();
            mp.setBuild(b);

            Plugin pi = new Plugin();
            b.addPlugin(pi);
            pi.setGroupId("org.springframework.boot");
            pi.setArtifactId("spring-boot-maven-plugin");
            pi.setVersion("${spring-boot-version}");
            PluginExecution pe = new PluginExecution();
            pe.addGoal("repackage");
            pi.addExecution(pe);
            Xpp3Dom cfg = new Xpp3Dom("finalName");
            cfg.setValue("camel-jbang-debug");
            Xpp3Dom root = new Xpp3Dom("configuration");
            root.addChild(cfg);
            pi.setConfiguration(root);

            MavenXpp3Writer w = new MavenXpp3Writer();
            FileOutputStream fos = new FileOutputStream("camel-jbang-debug-pom.xml", false);
            w.write(fos, model);
            IOHelper.close(fos);

            printer().println("Preparing Camel Spring Boot for debugging ...");

            // use maven wrapper if present
            String mvnw = "/mvnw";
            if (FileUtil.isWindows()) {
                mvnw = "/mvnw.cmd";
            }
            if (!new File(mvnw).exists()) {
                mvnw = "mvn";
            }
            // use maven to build the JAR and then run the JAR after-wards
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(
                    mvnw,
                    "-Dmaven.test.skip",
                    "--file",
                    "camel-jbang-debug-pom.xml",
                    "package",
                    "spring-boot:repackage");
            Process p = pb.start();

            if (p.waitFor(30, TimeUnit.SECONDS)) {
                AtomicReference<Process> processRef = new AtomicReference<>();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        // We need to wait for the process to exit before doing any cleanup
                        Process process = processRef.get();
                        if (process != null) {
                            process.destroy();
                            for (int i = 0; i < 30; i++) {
                                if (!process.isAlive()) {
                                    break;
                                }
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                        removeDir(Paths.get(RUN_PLATFORM_DIR));
                        removeDir(Paths.get(CAMEL_JBANG_WORK_DIR));
                        Files.deleteIfExists(Paths.get("camel-jbang-debug-pom.xml"));
                    } catch (Exception e) {
                        // Ignore
                    }
                }));

                // okay build is complete then run java
                pb = new ProcessBuilder();
                pb.command(
                        "java",
                        "-Dcamel.debug.enabled=true",
                        (breakpoint == null
                                ? "-Dcamel.debug.breakpoints=_all_routes_"
                                : "-Dcamel.debug.breakpoints=" + breakpoint),
                        "-Dcamel.debug.loggingLevel=DEBUG",
                        "-Dcamel.debug.singleStepIncludeStartEnd=true",
                        loggingColor ? "-Dspring.output.ansi.enabled=ALWAYS" : "-Dspring.output.ansi.enabled=NEVER",
                        "-jar",
                        "target/camel-jbang-debug.jar");

                p = pb.start();
                processRef.set(p);
                this.spawnOutput = p.getInputStream();
                this.spawnError = p.getErrorStream();
                this.spawnPid = p.pid();
                printer().println("Debugging Camel Spring Boot integration: " + name + " with PID: " + p.pid());
            } else {
                printer().printErr("Timed out preparing Camel Spring Boot for debugging");
                this.spawnError = p.getErrorStream();
                this.spawnPid = p.pid();
                p.destroy();
                return -1;
            }
        }

        return 0;
    }

    private int doRunDebugQuarkus(KameletMain main) throws Exception {
        Path pom = Paths.get("pom.xml");
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        try (Reader reader = Files.newBufferedReader(pom)) {
            Model model = mavenReader.read(reader);

            // include camel-debug dependency
            Dependency d = new Dependency();
            d.setGroupId("org.apache.camel.quarkus");
            d.setArtifactId("camel-quarkus-debug");
            model.getDependencies().add(d);
            d = new Dependency();
            d.setGroupId("org.apache.camel.quarkus");
            d.setArtifactId("camel-quarkus-cli-connector");
            model.getDependencies().add(d);

            Profile mp = new Profile();
            model.addProfile(mp);
            mp.setId("camel-debug");
            Activation a = new Activation();
            a.setActiveByDefault(true);
            mp.setActivation(a);

            Build b = new Build();
            mp.setBuild(b);

            Plugin pi = new Plugin();
            b.addPlugin(pi);
            pi.setGroupId(quarkusGroupId);
            pi.setArtifactId("quarkus-maven-plugin");
            pi.setVersion(quarkusVersion);
            PluginExecution pe = new PluginExecution();
            pe.addGoal("build");
            pi.addExecution(pe);
            Xpp3Dom cfg = new Xpp3Dom("finalName");
            cfg.setValue("camel-jbang-debug");
            Xpp3Dom root = new Xpp3Dom("configuration");
            root.addChild(cfg);
            pi.setConfiguration(root);

            MavenXpp3Writer w = new MavenXpp3Writer();
            FileOutputStream fos = new FileOutputStream("camel-jbang-debug-pom.xml", false);
            w.write(fos, model);
            IOHelper.close(fos);

            printer().println("Preparing Camel Quarkus for debugging ...");

            // use maven wrapper if present
            String mvnw = "/mvnw";
            if (FileUtil.isWindows()) {
                mvnw = "/mvnw.cmd";
            }
            if (!new File(mvnw).exists()) {
                mvnw = "mvn";
            }
            // use maven to build the JAR and then run the JAR after-wards
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(mvnw, "-Dmaven.test.skip", "--file", "camel-jbang-debug-pom.xml", "package", "quarkus:build");
            Process p = pb.start();

            if (p.waitFor(30, TimeUnit.SECONDS)) {
                AtomicReference<Process> processRef = new AtomicReference<>();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        // We need to wait for the process to exit before doing any cleanup
                        Process process = processRef.get();
                        if (process != null) {
                            process.destroy();
                            for (int i = 0; i < 30; i++) {
                                if (!process.isAlive()) {
                                    break;
                                }
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                        removeDir(Paths.get(RUN_PLATFORM_DIR));
                        removeDir(Paths.get(CAMEL_JBANG_WORK_DIR));
                        Files.deleteIfExists(Paths.get("camel-jbang-debug-pom.xml"));
                    } catch (Exception e) {
                        // Ignore
                    }
                }));

                // okay build is complete then run java
                pb = new ProcessBuilder();
                pb.command(
                        "java",
                        "-Dcamel.debug.enabled=true",
                        (breakpoint == null
                                ? "-Dcamel.debug.breakpoints=_all_routes_"
                                : "-Dcamel.debug.breakpoints=" + breakpoint),
                        "-Dcamel.debug.loggingLevel=DEBUG",
                        "-Dcamel.debug.singleStepIncludeStartEnd=true",
                        "-Dcamel.main.sourceLocationEnabled=true",
                        "-jar",
                        "target/quarkus-app/quarkus-run.jar");

                p = pb.start();
                processRef.set(p);
                this.spawnOutput = p.getInputStream();
                this.spawnError = p.getErrorStream();
                this.spawnPid = p.pid();
                printer().println("Debugging Camel Quarkus integration: " + name + " with PID: " + p.pid());
            } else {
                printer().printErr("Timed out preparing Camel Quarkus for debugging");
                this.spawnError = p.getErrorStream();
                this.spawnPid = p.pid();
                p.destroy();
                return -1;
            }
        }

        return 0;
    }

    protected int doRunDebug(KameletMain main) throws Exception {
        List<String> cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());

        // debug should be run
        cmds.remove(0);
        cmds.add(0, "run");

        cmds.remove("--background=true");
        cmds.remove("--background");
        cmds.remove("--background-wait=true");
        cmds.remove("--background-wait=false");
        cmds.remove("--background-wait");

        // remove args from debug that are not supported by run
        removeDebugOnlyOptions(cmds);

        // enable light-weight debugger (not camel-debug JAR that is for IDEA/VSCode tooling with remote JMX)
        cmds.add("--prop=camel.debug.enabled=true");
        if (breakpoint == null) {
            cmds.add("--prop=camel.debug.breakpoints=_all_routes_");
        } else {
            cmds.add("--prop=camel.debug.breakpoints=" + breakpoint);
        }
        cmds.add("--prop=camel.debug.loggingLevel=DEBUG");
        cmds.add("--prop=camel.debug.singleStepIncludeStartEnd=true");

        RunHelper.addCamelJBangCommand(cmds);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmds);

        Process p = pb.start();
        this.spawnOutput = p.getInputStream();
        this.spawnError = p.getErrorStream();
        this.spawnPid = p.pid();
        printer().println("Debugging Camel integration: " + name + " with PID: " + p.pid());
        return 0;
    }

    private void removeDebugOnlyOptions(List<String> cmds) {
        // only check Debug.class (not super classes)
        RunHelper.doWithFields(
                Debug.class,
                fc -> cmds.removeIf(c -> {
                    String n1 = "--" + fc.getName();
                    String n2 = "--" + StringHelper.camelCaseToDash(fc.getName());
                    return c.startsWith(n1) || c.startsWith(n2);
                }));
    }

    protected int doWatch() {
        if (spawnPid == 0) {
            return -1;
        }

        // does the process still exists?
        ProcessHandle ph = ProcessHandle.of(spawnPid).orElse(null);
        if (ph == null || !ph.isAlive()) {
            return -1;
        }

        // suspended and waiting to continue
        if (waitForUser.get()) {
            return 0;
        }

        // buffer before writing to screen
        StringWriter sw = new StringWriter();

        if (logLines > 0) {
            // log last 10 lines
            int start = Math.max(logBuffer.size() - logLines, 0);
            for (int i = start; i < start + logLines; i++) {
                String line = "";
                if (i < logBuffer.size()) {
                    line = logBuffer.get(i);
                }
                sw.write(line);
                sw.write(System.lineSeparator());
            }
        }
        printDebugStatus(spawnPid, sw);

        return 0;
    }

    private void sendDebugCommand(long pid, String command, String breakpoint, int position) {
        // ensure output file is deleted before executing action
        Path outputFile = getOutputFile(Long.toString(pid));
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "debug");
        if (command != null) {
            root.put("command", command);
        }
        if (breakpoint != null) {
            root.put("breakpoint", breakpoint);
        }
        if (position > 0) {
            root.put("position", position);
        }
        Path f = getActionFile(Long.toString(pid));
        try {
            String text = root.toJson();
            Files.writeString(f, text);
        } catch (Exception e) {
            // ignore
        }
    }

    private static boolean isStepOverSupported(String version) {
        // step-over is Camel 4.8.3 or 4.10 or better (not in 4.9)
        if ("4.9.0".equals(version)) {
            return false;
        }
        return version == null || VersionHelper.isGE(version, "4.8.3");
    }

    private static boolean isSkipOverSupported(String version) {
        // skip-over is Camel 4.14 or better
        return version == null || VersionHelper.isGE(version, "4.14.0");
    }

    private void printDebugStatus(long pid, StringWriter buffer) {
        JsonObject jo = loadDebug(pid);
        if (jo != null) {
            List<SuspendedRow> rows = new ArrayList<>();

            // only read if expecting new data
            long cnt = jo.getLongOrDefault("debugCounter", 0);
            String version = jo.getString("version");
            // clip -SNAPSHOT from version
            if (version != null && version.endsWith("-SNAPSHOT")) {
                version = version.substring(0, version.length() - 9);
            }
            if (cnt > debugCounter.get()) {
                JsonArray arr = jo.getCollection("suspended");
                if (arr != null) {

                    if (arr.size() > 1) {
                        printer()
                                .println(
                                        "WARN: Multiple suspended breakpoints is not supported (You can use --breakpoint option to specify a starting breakpoint)");
                        return;
                    }

                    for (Object o : arr) {
                        SuspendedRow row = new SuspendedRow();
                        row.pid = String.valueOf(pid);
                        row.version = version;
                        jo = (JsonObject) o;
                        row.uid = jo.getLong("uid");
                        row.first = jo.getBoolean("first");
                        row.last = jo.getBoolean("last");
                        row.location = jo.getString("location");
                        row.routeId = jo.getString("routeId");
                        row.nodeId = jo.getString("nodeId");
                        if ("aggregate".equals(jo.getString("nodeShortName"))) {
                            row.aggregate = new JsonObject();
                            row.aggregate.put("nodeLabel", jo.getString("nodeLabel"));
                        }
                        String uri = jo.getString("endpointUri");
                        if (uri != null) {
                            row.endpoint = new JsonObject();
                            if (mask) {
                                uri = URISupport.sanitizeUri(uri);
                            }
                            row.endpoint.put("endpoint", uri);
                        }
                        JsonObject es = jo.getMap("endpointService");
                        if (es != null) {
                            row.endpointService = es;
                        }
                        Long ts = jo.getLong("timestamp");
                        if (ts != null) {
                            row.timestamp = ts;
                        }
                        row.elapsed = jo.getLong("elapsed");
                        row.failed = jo.getBoolean("failed");
                        row.done = jo.getBoolean("done");
                        row.threadName = jo.getString("threadName");
                        row.message = jo.getMap("message");
                        row.exception = jo.getMap("exception");
                        row.exchangeId = row.message.getString("exchangeId");
                        row.exchangePattern = row.message.getString("exchangePattern");
                        // we should exchangeId/pattern elsewhere
                        row.message.remove("exchangeId");
                        row.message.remove("exchangePattern");
                        if (!showExchangeVariables) {
                            row.message.remove("exchangeVariables");
                        }
                        if (!showExchangeProperties) {
                            row.message.remove("exchangeProperties");
                        }
                        if (!showHeaders) {
                            row.message.remove("headers");
                        }
                        if (!showBody) {
                            row.message.remove("body");
                        }
                        if (!showException) {
                            row.exception = null;
                        }
                        List<JsonObject> lines = jo.getCollection("code");
                        if (lines != null) {
                            for (JsonObject line : lines) {
                                Code code = new Code();
                                code.line = line.getInteger("line");
                                code.match = line.getBooleanOrDefault("match", false);
                                code.code = line.getString("code");
                                row.code.add(code);
                            }
                        }
                        lines = jo.getCollection("history");
                        if (lines != null) {
                            for (JsonObject line : lines) {
                                // only include if accepted for debugging
                                boolean accept = line.getBooleanOrDefault("acceptDebugger", true);
                                if (accept) {
                                    History history = new History();
                                    history.index = line.getIntegerOrDefault("index", 0);
                                    history.routeId = line.getString("routeId");
                                    history.nodeId = line.getString("nodeId");
                                    history.elapsed = line.getLongOrDefault("elapsed", 0);
                                    history.skipOver = line.getBooleanOrDefault("skipOver", false);
                                    history.location = line.getString("location");
                                    history.line = line.getIntegerOrDefault("line", -1);
                                    history.code = line.getString("code");
                                    row.history.add(history);
                                }
                            }
                        }
                        rows.add(row);
                    }
                }
            }

            // if no suspended breakpoint and no updated logs then do not refresh screen
            if (rows.isEmpty() && !logUpdated.get()) {
                return;
            }

            logUpdated.set(false);

            // okay there is some updates to the screen, so redraw
            clearScreen();
            // top header
            printer().println(buffer.toString());
            // suspended breakpoints
            for (SuspendedRow row : rows) {
                printSourceAndHistory(row);
                printSuspendedRow(row);
                this.suspendedRow = row;
                // suspended so wait for user and remember position
                this.debugCounter.set(cnt);
                this.waitForUser.set(true);
            }
            if (this.waitForUser.get()) {
                boolean first = this.suspendedRow != null && this.suspendedRow.first;
                boolean last = this.suspendedRow != null && this.suspendedRow.last;
                // save current message to file
                if (output != null && this.suspendedRow != null) {
                    JsonObject j = this.suspendedRow.message;
                    if (j != null) {
                        j = j.getMap("body");
                        if (j != null) {
                            String b = j.getString("value");
                            if (b != null) {
                                b = CamelCommandHelper.valueAsStringPretty(b, false);
                                try {
                                    Path f = Path.of(output);
                                    Files.writeString(f, b);
                                } catch (IOException e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }

                String msg;
                if (!last && !first && isSkipOverSupported(version)) {
                    msg =
                            "    Breakpoint suspended (i = step into (default), o = step over, s = skip over, N = step to index). Press ENTER to continue (q = quit).";
                } else if (!last && !first && isStepOverSupported(version)) {
                    msg =
                            "    Breakpoint suspended (i = step into (default), o = step over). Press ENTER to continue (q = quit).";
                } else {
                    msg = "    Breakpoint suspended. Press ENTER to continue (q = quit).";
                }
                if (loggingColor) {
                    AnsiConsole.out()
                            .println(Ansi.ansi()
                                    .a(Ansi.Attribute.INTENSITY_BOLD)
                                    .a(msg)
                                    .reset());
                } else {
                    printer().println(msg);
                }
                printer().println();
            }
        }
    }

    private void printSourceAndHistory(SuspendedRow row) {
        List<Panel> panel = new ArrayList<>();
        if (!row.code.isEmpty()) {
            String loc = StringHelper.beforeLast(row.location, ":", row.location);
            if (loc != null && loc.length() < 72) {
                loc = loc + " ".repeat(72 - loc.length());
            } else {
                loc = "";
            }
            panel.add(Panel.withCode("Source: " + loc).andHistory("History"));
            panel.add(Panel.withCode("-".repeat(80)).andHistory("-".repeat(90)));

            for (int i = 0; i < row.code.size(); i++) {
                Code code = row.code.get(i);
                String c = Jsoner.unescape(code.code);
                String arrow = "    ";
                if (code.match) {
                    if (row.first) {
                        arrow = "*-->";
                    } else if (row.last) {
                        arrow = "<--*";
                    } else {
                        arrow = "--->";
                    }
                }
                String msg = String.format("%4d: %s %s", code.line, arrow, c);
                if (msg.length() > 80) {
                    msg = msg.substring(0, 80);
                }
                int length = msg.length();
                if (loggingColor && code.match) {
                    Ansi.Color col = Ansi.Color.BLUE;
                    Ansi.Attribute it = Ansi.Attribute.INTENSITY_BOLD;
                    if (row.failed && row.last) {
                        col = Ansi.Color.RED;
                    } else if (row.last) {
                        col = Ansi.Color.GREEN;
                    }
                    // need to fill out entire line, so fill in spaces
                    if (length < 80) {
                        String extra = " ".repeat(80 - length);
                        msg = msg + extra;
                        length = 80;
                    }
                    msg = Ansi.ansi().bg(col).a(it).a(msg).reset().toString();
                } else {
                    // need to fill out entire line, so fill in spaces
                    if (length < 80) {
                        String extra = " ".repeat(80 - length);
                        msg = msg + extra;
                        length = 80;
                    }
                }
                panel.add(Panel.withCode(msg, length));
            }
            for (int i = row.code.size(); i < 11; i++) {
                // empty lines so source code has same height
                panel.add(Panel.withCode(" ".repeat(80)));
            }
        }
        if (!row.history.isEmpty()) {
            if (row.history.size() > (panel.size() - 4)) {
                // cut to only what we can display
                int pos = row.history.size() - (panel.size() - 4);
                if (row.history.size() > pos) {
                    row.history = row.history.subList(pos, row.history.size());
                }
            }
            for (int i = 2; panel.size() > 2 && i < 11; i++) {
                Panel p = panel.get(i);
                if (row.history.size() > (i - 2)) {
                    History h = row.history.get(i - 2);

                    // from camel 4.7 onwards then message history include current line as well
                    // so the history panel needs to output a bit different in this situation
                    boolean top = false;
                    if (row.version != null && VersionHelper.isGE(row.version, "4.7.0")) {
                        top = h == row.history.get(row.history.size() - 1);
                    }

                    String ids;
                    if (source) {
                        ids = locationAndLine(h.location, h.line);
                    } else {
                        ids = h.routeId + "/" + h.nodeId;
                    }
                    if (ids.length() > 30) {
                        ids = ids.substring(ids.length() - 30);
                    }

                    ids = String.format("%-30.30s", ids);
                    if (loggingColor) {
                        ids = Ansi.ansi().fgCyan().a(ids).reset().toString();
                    }
                    long e = i == 2 ? 0 : h.elapsed; // the pseudo from should have 0 as elapsed
                    String elapsed = "(" + e + "ms)";
                    if (h.skipOver) {
                        elapsed = "(skipped)";
                    }

                    String c = "";
                    if (h.code != null) {
                        c = Jsoner.unescape(h.code);
                        c = c.trim();
                    }

                    String fids = String.format("%-30.30s", ids);
                    String msg;
                    if (top && !row.last) {
                        msg = String.format("%2d %10.10s %s %4d:   %s", h.index, "--->", fids, h.line, c);
                    } else {
                        msg = String.format("%2d %10.10s %s %4d:   %s", h.index, elapsed, fids, h.line, c);
                    }
                    int len = msg.length();
                    if (loggingColor) {
                        fids = String.format("%-30.30s", ids);
                        fids = Ansi.ansi().fgCyan().a(fids).reset().toString();
                        if (top && !row.last) {
                            msg = String.format("%2d %10.10s %s %4d:   %s", h.index, "--->", fids, h.line, c);
                        } else {
                            msg = String.format("%2d %10.10s %s %4d:   %s", h.index, elapsed, fids, h.line, c);
                        }
                    }

                    p.history = msg;
                    p.historyLength = len;
                }
            }
        }

        // the ascii-table does not work well with color cells (https://github.com/freva/ascii-table/issues/26)
        for (Panel p : panel) {
            String c = p.code;
            String h = p.history;
            int len = p.historyLength;
            if (len > 90) {
                h = h.substring(0, 90);
            }
            String line = c + "    " + h;
            printer().println(line);
        }
    }

    private void printSuspendedRow(SuspendedRow row) {
        if (timestamp) {
            String ts;
            if (ago) {
                ts = String.format("%12s", TimeUtils.printSince(row.timestamp) + " ago");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                ts = sdf.format(new Date(row.timestamp));
            }
            if (loggingColor) {
                AnsiConsole.out()
                        .print(Ansi.ansi()
                                .fgBrightDefault()
                                .a(Ansi.Attribute.INTENSITY_FAINT)
                                .a(ts)
                                .reset());
            } else {
                printer().print(ts);
            }
            printer().print("  ");
        }
        // pid
        String p = String.format("%5.5s", row.pid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(p).reset());
            AnsiConsole.out()
                    .print(Ansi.ansi()
                            .fgBrightDefault()
                            .a(Ansi.Attribute.INTENSITY_FAINT)
                            .a(" --- ")
                            .reset());
        } else {
            printer().print(p);
            printer().print(" --- ");
        }
        // thread name
        String tn = row.threadName;
        if (tn.length() > 25) {
            tn = tn.substring(tn.length() - 25);
        }
        tn = String.format("[%25.25s]", tn);
        if (loggingColor) {
            AnsiConsole.out()
                    .print(Ansi.ansi()
                            .fgBrightDefault()
                            .a(Ansi.Attribute.INTENSITY_FAINT)
                            .a(tn)
                            .reset());
        } else {
            printer().print(tn);
        }
        printer().print(" ");
        // node ids or source location
        String ids;
        if (source) {
            ids = locationAndLine(row.location, -1);
        } else {
            ids = row.routeId + "/" + getId(row);
        }
        if (ids.length() > 40) {
            ids = ids.substring(ids.length() - 40);
        }
        ids = String.format("%40.40s", ids);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgCyan().a(ids).reset());
        } else {
            printer().print(ids);
        }
        printer().print(" : ");
        // uuid
        String u = String.format("%5.5s", row.uid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(u).reset());
        } else {
            printer().print(u);
        }
        printer().print(" - ");
        // status
        printer().print(getStatus(row));
        // elapsed
        String e = getElapsed(row);
        if (e != null) {
            if (loggingColor) {
                AnsiConsole.out()
                        .print(Ansi.ansi().fgBrightDefault().a(" (" + e + ")").reset());
            } else {
                printer().print("(" + e + ")");
            }
        }
        printer().println();
        printer().println(getDataAsTable(row));
        printer().println();
    }

    private static String locationAndLine(String loc, int line) {
        // shorten path as there is no much space
        loc = FileUtil.stripPath(loc);
        return line == -1 ? loc : loc + ":" + line;
    }

    private void clearScreen() {
        AnsiConsole.out().print(Ansi.ansi().eraseScreen().cursor(1, 1));
    }

    private void handleHangup() {
        if (spawnPid > 0) {
            Path pidPath = CommandLineHelper.getCamelDir().resolve(Long.toString(spawnPid));
            if (Files.exists(pidPath)) {
                printer().println("Shutting down Camel integration (PID: " + spawnPid + ")");
                PathUtils.deleteFile(pidPath);
            }
        }
    }

    private void installHangupInterceptor() {
        Thread task = new Thread(this::handleHangup);
        task.setName(ThreadHelper.resolveThreadName(null, "CamelHangupInterceptor"));
        Runtime.getRuntime().addShutdownHook(task);
    }

    JsonObject loadDebug(long pid) {
        try {
            Path p = getDebugFile(Long.toString(pid));
            if (p != null && Files.exists(p)) {
                String text = Files.readString(p);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private String getDataAsTable(SuspendedRow r) {
        return tableHelper.getDataAsTable(
                r.exchangeId, r.exchangePattern, r.aggregate, r.endpoint, r.endpointService, r.message, r.exception);
    }

    private String getStatus(SuspendedRow r) {
        boolean remote = r.endpoint != null && r.endpoint.getBooleanOrDefault("remote", false);

        if (r.first) {
            String s = "Created";
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
            } else {
                return s;
            }
        } else if (r.last) {
            String done = r.exception != null ? "Completed (exception)" : "Completed (success)";
            if (loggingColor) {
                return Ansi.ansi()
                        .fg(r.failed ? Ansi.Color.RED : Ansi.Color.GREEN)
                        .a(done)
                        .reset()
                        .toString();
            } else {
                return done;
            }
        }
        if (!r.done) {
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.BLUE).a("Breakpoint").reset().toString();
            } else {
                return "Breakpoint";
            }
        } else if (r.failed) {
            String fail = r.exception != null ? "Exception" : "Failed";
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.RED).a(fail).reset().toString();
            } else {
                return fail;
            }
        } else {
            String s = remote ? "Sent" : "Processed";
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
            } else {
                return s;
            }
        }
    }

    private String getId(SuspendedRow r) {
        if (r.first) {
            return "*-->";
        } else if (r.last) {
            return "*<--";
        } else {
            return r.nodeId;
        }
    }

    private String getElapsed(SuspendedRow r) {
        if (!r.first) {
            return TimeUtils.printDuration(r.elapsed, true);
        }
        return null;
    }

    private static int lineIsNumber(String line) {
        try {
            return Integer.parseInt(line);
        } catch (Exception e) {
            return 0;
        }
    }

    private static class SuspendedRow {
        String pid;
        String version;
        boolean first;
        boolean last;
        long uid;
        String exchangeId;
        String exchangePattern;
        String threadName;
        String location;
        String routeId;
        String nodeId;
        long timestamp;
        long elapsed;
        boolean done;
        boolean failed;
        JsonObject aggregate;
        JsonObject endpoint;
        JsonObject endpointService;
        JsonObject message;
        JsonObject exception;
        List<Code> code = new ArrayList<>();
        List<History> history = new ArrayList<>();
    }

    private static class Code {
        int line;
        String code;
        boolean match;
    }

    private static class History {
        int index;
        String routeId;
        String nodeId;
        long elapsed;
        boolean skipOver;
        String location;
        int line;
        String code;
    }

    private static class Panel {
        String code = "";
        String history = "";
        int codeLength;
        int historyLength;

        static Panel withCode(String code) {
            return withCode(code, code.length());
        }

        static Panel withCode(String code, int length) {
            Panel p = new Panel();
            p.code = code;
            p.codeLength = length;
            return p;
        }

        Panel andHistory(String history) {
            return andHistory(history, history.length());
        }

        Panel andHistory(String history, int length) {
            this.history = history;
            this.historyLength = length;
            return this;
        }
    }
}
