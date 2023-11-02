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

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.action.MessageTableHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;
import static org.apache.camel.util.IOHelper.buffered;

@Command(name = "debug", description = "Debug local Camel integration", sortOptions = false)
public class Debug extends Run {

    // TODO: faster (no refresh)
    // TODO: Multiple hit breakpoints (select starting, or fail and tell user to select a specific route/node)
    // TODO: fake "first"
    // TODO: current route id

    @CommandLine.Option(names = { "--breakpoint" },
                        description = "To set breakpoint at the given node id (Multiple ids can be separated by comma). If no breakpoint is set, then the first route is automatic selected.")
    String breakpoint;

    @CommandLine.Option(names = { "--stop-on-exit" }, defaultValue = "true",
                        description = "Whether to stop the running Camel on exit")
    boolean stopOnExit = true;

    @CommandLine.Option(names = { "--timestamp" }, defaultValue = "true",
                        description = "Print timestamp.")
    boolean timestamp = true;

    @CommandLine.Option(names = { "--ago" },
                        description = "Use ago instead of yyyy-MM-dd HH:mm:ss in timestamp.")
    boolean ago;

    @CommandLine.Option(names = { "--mask" },
                        description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @CommandLine.Option(names = { "--source" },
                        description = "Prefer to display source filename/code instead of IDs")
    boolean source;

    @CommandLine.Option(names = { "--show-exchange-properties" }, defaultValue = "false",
                        description = "Show exchange properties in traced messages")
    boolean showExchangeProperties;

    @CommandLine.Option(names = { "--show-headers" }, defaultValue = "true",
                        description = "Show message headers in traced messages")
    boolean showHeaders = true;

    @CommandLine.Option(names = { "--show-body" }, defaultValue = "true",
                        description = "Show message body in traced messages")
    boolean showBody = true;

    @CommandLine.Option(names = { "--show-exception" }, defaultValue = "true",
                        description = "Show exception and stacktrace for failed messages")
    boolean showException = true;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    private MessageTableHelper tableHelper;

    // status
    private InputStream spawnOutput;
    private InputStream spawnError;
    private List<String> logBuffer = new ArrayList<>(100);
    private Row contextRow = new Row();
    private SuspendedRow suspendedRow = new SuspendedRow();

    public Debug(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (!silentRun) {
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

        // read input
        final AtomicBoolean quit = new AtomicBoolean();
        final Console c = System.console();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                do {
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
                            } else {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                } while (!quit.get());
            }
        }, "ReadLog");
        t.start();
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                do {
                    String line = c.readLine();
                    if (line != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            // continue breakpoint
                            if (suspendedRow != null) {
                                sendDebugCommand(spawnPid, "step", suspendedRow.nodeId);
                            } else {
                                sendDebugCommand(spawnPid, "step", null);
                            }
                        } else if ("quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
                            quit.set(true);
                        }
                    }
                } while (!quit.get());
            }
        }, "ReadInput");
        t2.start();

        // give time for process to start
        Thread.sleep(250);

        do {
            exit = doWatch();
            if (exit == -1) {
                // maybe failed on startup, so dump log buffer
                for (String line : logBuffer) {
                    System.out.println(line);
                }
                // and any error
                String text = IOHelper.loadText(spawnError);
                System.out.println(text);
                return -1;
            }
            if (exit == 0) {
                // use half-sec delay to refresh
                Thread.sleep(500);
            }
        } while (exit == 0 && !quit.get());

        return 0;
    }

    @Override
    protected int runDebug(KameletMain main) throws Exception {
        List<String> cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());

        // debug should be run
        cmds.remove(0);
        cmds.add(0, "run");

        cmds.remove("--background=true");
        cmds.remove("--background");

        // remove args from debug that are not supported by run
        removeDebugOnlyOptions(cmds);

        // enable light-weight debugger (not camel-debug JAR that is for IDEA/VSCode tooling with remote JMX)
        cmds.add("--prop=camel.debug.enabled=true");
        cmds.add("--prop=camel.debug.breakpoints=FIRST_ROUTES");
        cmds.add("--prop=camel.debug.loggingLevel=DEBUG");
        cmds.add("--prop=camel.debug.singleStepLast=true");

        cmds.add(0, "camel");

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmds);

        Process p = pb.start();
        this.spawnOutput = p.getInputStream();
        this.spawnError = p.getErrorStream();
        this.spawnPid = p.pid();
        System.out.println("Debugging Camel integration: " + name + " with PID: " + p.pid());
        return 0;
    }

    private void removeDebugOnlyOptions(List<String> cmds) {
        ReflectionHelper.doWithFields(Debug.class, fc -> {
            cmds.removeIf(c -> {
                String n1 = "--" + fc.getName();
                String n2 = "--" + StringHelper.camelCaseToDash(fc.getName());
                return c.startsWith(n1) || c.startsWith(n2);
            });
        });
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

        // buffer before writing to screen
        StringWriter sw = new StringWriter();
        // log last 10 lines
        int start = Math.max(logBuffer.size() - 10, 0);
        for (int i = start; i < start + 10; i++) {
            String line = "";
            if (i < logBuffer.size()) {
                line = logBuffer.get(i);
            }
            sw.write(line);
            sw.write(System.lineSeparator());
        }
        sw.write(System.lineSeparator());
        //        updateContextStatus(spawnPid);
        //        sw.append(getContextStatusTable());
        //        sw.write(System.lineSeparator());
        printDebugStatus(spawnPid, sw);

        return 0;
    }

    private void sendDebugCommand(long pid, String command, String breakpoint) {
        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "debug");
        if (command != null) {
            root.put("command", command);
        }
        if (breakpoint != null) {
            root.put("breakpoint", breakpoint);
        }
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }
    }

    private void printDebugStatus(long pid, StringWriter buffer) {
        JsonObject jo = loadDebug(pid);
        if (jo != null) {
            List<SuspendedRow> rows = new ArrayList<>();
            JsonArray arr = jo.getCollection("suspended");
            if (arr != null) {
                for (Object o : arr) {
                    SuspendedRow row = new SuspendedRow();
                    row.pid = String.valueOf(pid);
                    row.name = "TODO";//pid.name;
                    jo = (JsonObject) o;
                    row.uid = jo.getLong("uid");
                    row.first = jo.getBoolean("first");
                    row.last = jo.getBoolean("last");
                    row.location = jo.getString("location");
                    row.routeId = jo.getString("routeId");
                    row.nodeId = jo.getString("nodeId");
                    String uri = jo.getString("endpointUri");
                    if (uri != null) {
                        row.endpoint = new JsonObject();
                        if (mask) {
                            uri = URISupport.sanitizeUri(uri);
                        }
                        row.endpoint.put("endpoint", uri);
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
                    rows.add(row);
                }
            }

            clearScreen();
            // top header
            System.out.println(buffer.toString());
            // suspended breakpoints
            for (SuspendedRow row : rows) {
                printSuspendedRow(row);
                this.suspendedRow = row;
            }
        }
    }

    private void printSuspendedRow(SuspendedRow row) {
        if (!row.code.isEmpty()) {
            String loc = LoggerHelper.stripSourceLocationLineNumber(row.location);
            System.out.printf("Source: %s%n", loc);
            System.out.println("--------------------------------------------------------------------------------");
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
                if (loggingColor && code.match) {
                    Ansi.Color col = row.last ? Ansi.Color.GREEN : Ansi.Color.RED;
                    AnsiConsole.out().println(Ansi.ansi().bg(col).a(Ansi.Attribute.INTENSITY_BOLD).a(msg).reset());
                } else {
                    System.out.println(msg);
                }
            }
            for (int i = row.code.size(); i < 11; i++) {
                // empty lines so source code has same height
                System.out.println();
            }
            System.out.println();
        }

        if (timestamp) {
            String ts;
            if (ago) {
                ts = String.format("%12s", TimeUtils.printSince(row.timestamp) + " ago");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                ts = sdf.format(new Date(row.timestamp));
            }
            if (loggingColor) {
                AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(ts).reset());
            } else {
                System.out.print(ts);
            }
            System.out.print("  ");
        }
        // pid
        String p = String.format("%5.5s", row.pid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(p).reset());
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(" --- ").reset());
        } else {
            System.out.print(p);
            System.out.print(" --- ");
        }
        // thread name
        String tn = row.threadName;
        if (tn.length() > 25) {
            tn = tn.substring(tn.length() - 25);
        }
        tn = String.format("[%25.25s]", tn);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(tn).reset());
        } else {
            System.out.print(tn);
        }
        System.out.print(" ");
        // node ids or source location
        String ids;
        if (source) {
            ids = row.location;
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
            System.out.print(ids);
        }
        System.out.print(" : ");
        // uuid
        String u = String.format("%5.5s", row.uid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(u).reset());
        } else {
            System.out.print(u);
        }
        System.out.print(" - ");
        // status
        System.out.print(getStatus(row));
        // elapsed
        String e = getElapsed(row);
        if (e != null) {
            if (loggingColor) {
                AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(" (" + e + ")").reset());
            } else {
                System.out.print("(" + e + ")");
            }
        }
        System.out.println();
        System.out.println(getDataAsTable(row));
        System.out.println();
    }

    private String getContextStatusTable() {
        return AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(contextRow), Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("CAMEL").dataAlign(HorizontalAlign.LEFT).with(r -> r.camelVersion),
                new Column().header("READY").dataAlign(HorizontalAlign.CENTER).with(r -> r.ready),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> extractState(r.state)),
                new Column().header("RELOAD").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.reloaded),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("ROUTE").with(this::getRoutes),
                new Column().header("MSG/S").with(this::getThroughput),
                new Column().header("TOTAL").with(r -> r.total),
                new Column().header("FAIL").with(r -> r.failed),
                new Column().header("INFLIGHT").with(r -> r.inflight),
                new Column().header("LAST").with(r -> r.last),
                new Column().header("DELTA").with(this::getDelta),
                new Column().header("SINCE-LAST").with(this::getSinceLast)));
    }

    private void clearScreen() {
        AnsiConsole.out().print(Ansi.ansi().eraseScreen().cursor(1, 1));
    }

    private void handleHangup() {
        if (spawnPid > 0) {
            File dir = new File(System.getProperty("user.home"), ".camel");
            File pidFile = new File(dir, Long.toString(spawnPid));
            if (pidFile.exists()) {
                System.out.println("Shutting down Camel integration (PID: " + spawnPid + ")");
                FileUtil.deleteFile(pidFile);
            }
        }
    }

    private void installHangupInterceptor() {
        Thread task = new Thread(this::handleHangup);
        task.setName(ThreadHelper.resolveThreadName(null, "CamelHangupInterceptor"));
        Runtime.getRuntime().addShutdownHook(task);
    }

    JsonObject loadStatus(long pid) {
        try {
            File f = getStatusFile(Long.toString(pid));
            if (f != null && f.exists()) {
                FileInputStream fis = new FileInputStream(f);
                String text = IOHelper.loadText(fis);
                IOHelper.close(fis);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Throwable e) {
            // ignore
        }
        return null;
    }

    JsonObject loadDebug(long pid) {
        try {
            File f = getDebugFile(Long.toString(pid));
            if (f != null && f.exists()) {
                FileInputStream fis = new FileInputStream(f);
                String text = IOHelper.loadText(fis);
                IOHelper.close(fis);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Throwable e) {
            // ignore
        }
        return null;
    }

    private void updateContextStatus(long pid) {
        JsonObject root = loadStatus(pid);
        // there must be a status file for the running Camel integration
        if (root != null) {
            JsonObject context = (JsonObject) root.get("context");
            if (context == null) {
                return;
            }
            contextRow.name = context.getString("name");
            if ("CamelJBang".equals(contextRow.name)) {
                contextRow.name = ProcessHelper.extractName(root, null);
            }
            contextRow.pid = Long.toString(pid);
            contextRow.uptime = extractSince();
            contextRow.age = TimeUtils.printSince(contextRow.uptime);
            JsonObject runtime = (JsonObject) root.get("runtime");
            contextRow.platform = extractPlatform(null, runtime);
            contextRow.state = context.getInteger("phase");
            contextRow.camelVersion = context.getString("version");
            Map<String, ?> stats = context.getMap("statistics");
            if (stats != null) {
                Object thp = stats.get("exchangesThroughput");
                if (thp != null) {
                    contextRow.throughput = thp.toString();
                }
                contextRow.total = stats.get("exchangesTotal").toString();
                contextRow.inflight = stats.get("exchangesInflight").toString();
                contextRow.failed = stats.get("exchangesFailed").toString();
                contextRow.reloaded = stats.get("reloaded").toString();
                Object last = stats.get("lastProcessingTime");
                if (last != null) {
                    contextRow.last = last.toString();
                }
                last = stats.get("deltaProcessingTime");
                if (last != null) {
                    contextRow.delta = last.toString();
                }
                last = stats.get("sinceLastCreatedExchange");
                if (last != null) {
                    contextRow.sinceLastStarted = last.toString();
                }
                last = stats.get("sinceLastCompletedExchange");
                if (last != null) {
                    contextRow.sinceLastCompleted = last.toString();
                }
                last = stats.get("sinceLastFailedExchange");
                if (last != null) {
                    contextRow.sinceLastFailed = last.toString();
                }
            }
            JsonArray array = (JsonArray) root.get("routes");
            contextRow.routeStarted = 0;
            contextRow.routeTotal = 0;
            for (int i = 0; i < array.size(); i++) {
                JsonObject o = (JsonObject) array.get(i);
                String state = o.getString("state");
                contextRow.routeTotal++;
                if ("Started".equals(state)) {
                    contextRow.routeStarted++;
                }
            }

            JsonObject hc = (JsonObject) root.get("healthChecks");
            boolean rdy = hc != null && hc.getBoolean("ready");
            if (rdy) {
                contextRow.ready = "1/1";
            } else {
                contextRow.ready = "0/1";
            }
        }
    }

    private String extractPlatform(ProcessHandle ph, JsonObject runtime) {
        String answer = runtime != null ? runtime.getString("platform") : null;
        if ("Camel".equals(answer)) {
            // generic camel, we need to check if we run in JBang
            String cl = ph != null ? ph.info().commandLine().orElse("") : "";
            if (cl.contains("main.CamelJBang run")) {
                answer = "JBang";
            }
        }
        return answer;
    }

    private static class Row {
        String pid;
        String platform;
        String camelVersion;
        String name;
        String ready;
        int routeStarted;
        int routeTotal;
        int state;
        String reloaded;
        String age;
        long uptime;
        String throughput;
        String total;
        String failed;
        String inflight;
        String last;
        String delta;
        String sinceLastStarted;
        String sinceLastCompleted;
        String sinceLastFailed;
    }

    protected String getSinceLast(Row r) {
        String s1 = r.sinceLastStarted != null ? r.sinceLastStarted : "-";
        String s2 = r.sinceLastCompleted != null ? r.sinceLastCompleted : "-";
        String s3 = r.sinceLastFailed != null ? r.sinceLastFailed : "-";
        return s1 + "/" + s2 + "/" + s3;
    }

    protected String getThroughput(Row r) {
        String s = r.throughput;
        if (s == null || s.isEmpty()) {
            s = "";
        }
        return s;
    }

    protected String getRoutes(Row r) {
        return r.routeStarted + "/" + r.routeTotal;
    }

    protected String getDelta(Row r) {
        if (r.delta != null) {
            if (r.delta.startsWith("-")) {
                return r.delta;
            } else if (!"0".equals(r.delta)) {
                // use plus sign to denote slower when positive
                return "+" + r.delta;
            }
        }
        return r.delta;
    }

    public static long extractSince() {
        ProcessHandle ph = ProcessHandle.current();
        long since = 0;
        if (ph.info().startInstant().isPresent()) {
            since = ph.info().startInstant().get().toEpochMilli();
        }
        return since;
    }

    private String getDataAsTable(SuspendedRow r) {
        return tableHelper.getDataAsTable(r.exchangeId, r.exchangePattern, r.endpoint, r.message, r.exception);
    }

    private String getStatus(SuspendedRow r) {
        if (r.first) {
            String s = "Created";
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
            } else {
                return "Input";
            }
        } else if (r.last) {
            String done = r.exception != null ? "Completed (exception)" : "Completed (success)";
            if (loggingColor) {
                return Ansi.ansi().fg(r.failed ? Ansi.Color.RED : Ansi.Color.GREEN).a(done).reset().toString();
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
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a("Processed").reset().toString();
            } else {
                return "Processed";
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

    private static class SuspendedRow {
        String pid;
        String name;
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
        JsonObject endpoint;
        JsonObject message;
        JsonObject exception;
        List<Code> code = new ArrayList<>();
    }

    private static class Code {
        int line;
        String code;
        boolean match;
    }

}
