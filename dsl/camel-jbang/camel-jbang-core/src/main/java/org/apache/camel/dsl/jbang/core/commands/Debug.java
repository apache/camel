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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.dsl.jbang.core.commands.action.MessageTableHelper;
import org.apache.camel.dsl.jbang.core.common.CamelCommandHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.main.KameletMain;
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

import static org.apache.camel.util.IOHelper.buffered;

@Command(name = "debug", description = "Debug local Camel integration", sortOptions = false)
public class Debug extends Run {

    @CommandLine.Option(names = { "--breakpoint" },
                        description = "To set breakpoint at the given node id (Multiple ids can be separated by comma). If no breakpoint is set, then the first route is automatic selected.")
    String breakpoint;

    @CommandLine.Option(names = { "--output" },
                        description = "File to store the current message body (will override). This allows for manual inspecting the message later.")
    String output;

    @CommandLine.Option(names = { "--stop-on-exit" }, defaultValue = "true",
                        description = "Whether to stop the running Camel on exit")
    boolean stopOnExit = true;

    @CommandLine.Option(names = { "--log-lines" }, defaultValue = "10",
                        description = "Number of log lines to display on top of screen")
    int logLines = 10;

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

    @CommandLine.Option(names = { "--show-exchange-variables" }, defaultValue = "false",
                        description = "Show exchange variables in traced messages")
    boolean showExchangeVariables;

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
        tableHelper.setShowExchangeVariables(showExchangeVariables);

        // read log input
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
                                logUpdated.set(true);
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

        // read CLI input from user
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                do {
                    String line = c.readLine();
                    if (line != null) {
                        line = line.trim();
                        if ("quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
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
                            sendDebugCommand(spawnPid, "step", null);
                        }
                        // user have pressed ENTER so continue
                        waitForUser.set(false);
                    }
                } while (!quit.get());
            }
        }, "ReadCommand");
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
                String text = IOHelper.loadText(spawnError);
                printer().println(text);
                return -1;
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
        if (breakpoint == null) {
            cmds.add("--prop=camel.debug.breakpoints=_all_routes_");
        } else {
            cmds.add("--prop=camel.debug.breakpoints=" + breakpoint);
        }
        cmds.add("--prop=camel.debug.loggingLevel=DEBUG");
        cmds.add("--prop=camel.debug.singleStepIncludeStartEnd=true");

        cmds.add(0, "camel");

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

            // only read if expecting new data
            long cnt = jo.getLongOrDefault("debugCounter", 0);
            if (cnt > debugCounter.get()) {
                JsonArray arr = jo.getCollection("suspended");
                if (arr != null) {

                    if (arr.size() > 1) {
                        printer().println(
                                "WARN: Multiple suspended breakpoints is not supported (You can use --breakpoint option to specify a starting breakpoint)");
                        return;
                    }

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
                        lines = jo.getCollection("history");
                        if (lines != null) {
                            for (JsonObject line : lines) {
                                History history = new History();
                                history.routeId = line.getString("routeId");
                                history.nodeId = line.getString("nodeId");
                                history.elapsed = line.getLongOrDefault("elapsed", 0);
                                history.location = line.getString("location");
                                history.line = line.getIntegerOrDefault("line", -1);
                                history.code = line.getString("code");
                                row.history.add(history);
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
                                    File f = new File(output);
                                    IOHelper.writeText(b, f);
                                } catch (IOException e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }

                String msg = "    Breakpoint suspended. Press ENTER to continue.";
                if (loggingColor) {
                    AnsiConsole.out().println(Ansi.ansi().a(Ansi.Attribute.INTENSITY_BOLD).a(msg).reset());
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
            if (loc.length() < 72) {
                loc = loc + " ".repeat(72 - loc.length());
            }
            panel.add(Panel.withCode("Source: " + loc).andHistory("History"));
            panel.add(Panel.withCode("-".repeat(80))
                    .andHistory("-".repeat(90)));

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
                    msg = Ansi.ansi().bg(col).a(Ansi.Attribute.INTENSITY_BOLD).a(msg).reset().toString();
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

                    String c = "";
                    if (h.code != null) {
                        c = Jsoner.unescape(h.code);
                        c = c.trim();
                    }

                    String fids = String.format("%-30.30s", ids);
                    String msg = String.format("%s %10.10s %4d:  %s", fids, elapsed, h.line, c);
                    int len = msg.length();
                    if (loggingColor) {
                        fids = String.format("%-30.30s", ids);
                        fids = Ansi.ansi().fgCyan().a(fids).reset().toString();
                        msg = String.format("%s %10.10s %4d:   %s", fids, elapsed, h.line, c);
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
                AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(ts).reset());
            } else {
                printer().print(ts);
            }
            printer().print("  ");
        }
        // pid
        String p = String.format("%5.5s", row.pid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(p).reset());
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(" --- ").reset());
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
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(tn).reset());
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
                AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(" (" + e + ")").reset());
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
            File pidFile = new File(CommandLineHelper.getCamelDir(), Long.toString(spawnPid));
            if (pidFile.exists()) {
                printer().println("Shutting down Camel integration (PID: " + spawnPid + ")");
                FileUtil.deleteFile(pidFile);
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
            File f = getDebugFile(Long.toString(pid));
            if (f != null && f.exists()) {
                FileInputStream fis = new FileInputStream(f);
                String text = IOHelper.loadText(fis);
                IOHelper.close(fis);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
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
        List<History> history = new ArrayList<>();
    }

    private static class Code {
        int line;
        String code;
        boolean match;
    }

    private static class History {
        String routeId;
        String nodeId;
        long elapsed;
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
