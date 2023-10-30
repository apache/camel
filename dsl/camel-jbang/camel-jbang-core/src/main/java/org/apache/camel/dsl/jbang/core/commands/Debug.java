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

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

@Command(name = "debug", description = "Debug local Camel integration", sortOptions = false)
public class Debug extends Run {

    @CommandLine.Option(names = { "--breakpoint" },
                        description = "To set breakpoint at the given node id (Multiple ids can be separated by comma). If no breakpoint is set, then the first route is automatic selected.")
    String breakpoint;

    @CommandLine.Option(names = { "--stop-on-exit" }, defaultValue = "true",
                        description = "Whether to stop the running Camel on exit")
    boolean stopOnExit = true;

    // context status
    private Row contextRow = new Row();

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

        do {
            clearScreen();
            exit = doWatch();
            if (exit == 0) {
                // use 2-sec delay to refresh
                Thread.sleep(2000);
            }
        } while (exit == 0);

        return 0;
    }

    protected int doWatch() {
        if (spawnPid == 0) {
            return 0;
        }

        updateContextStatus(spawnPid);
        printContextStatus();

        // empty line
        System.out.println();

        // show debug status
        printDebugStatus(spawnPid);

        return 0;
    }

    private void printDebugStatus(long pid) {
        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "debug");
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            // print details on screen
        }
    }

    private void printContextStatus() {
        System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(contextRow), Arrays.asList(
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
                new Column().header("SINCE-LAST").with(this::getSinceLast))));
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

    protected JsonObject waitForOutputFile(File outputFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                // give time for response to be ready
                Thread.sleep(100);

                if (outputFile.exists()) {
                    FileInputStream fis = new FileInputStream(outputFile);
                    String text = IOHelper.loadText(fis);
                    IOHelper.close(fis);
                    return (JsonObject) Jsoner.deserialize(text);
                }

            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

}
