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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route-controller", description = "List status of route controller in a running Camel integration",
         sortOptions = false)
public class RouteControllerAction extends ActionWatchCommand {

    public static class IdStateCompletionCandidates implements Iterable<String> {

        public IdStateCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("id", "state").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = IdStateCompletionCandidates.class,
                        description = "Sort by id, or state", defaultValue = "id")
    String sort;

    @CommandLine.Option(names = { "--header" },
                        description = "Include controller configuration details", defaultValue = "true")
    boolean header;

    @CommandLine.Option(names = { "--trace" },
                        description = "Include stack-traces in error messages", defaultValue = "true")
    boolean trace;

    @CommandLine.Option(names = { "--depth" },
                        description = "Max depth of stack-trace", defaultValue = "1")
    int depth;

    private volatile long pid;

    public RouteControllerAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        // include stack-traces
        if (trace && depth <= 1) {
            depth = Integer.MAX_VALUE;
        }

        this.pid = pids.get(0);

        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-controller");
        root.put("stacktrace", trace ? "true" : "false");
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        boolean supervising;
        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            supervising = "SupervisingRouteController".equals(jo.getString("controller"));

            JsonArray arr = (JsonArray) jo.get("routes");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject jt = (JsonObject) arr.get(i);

                Row row = new Row();
                row.routeId = jt.getString("routeId");
                row.uri = jt.getString("uri");
                row.status = jt.getString("status");

                if (supervising) {
                    row.attempts = jt.getLong("attempts");
                    long time = jt.getLong("lastAttempt");
                    if (time > 0) {
                        row.lastAttempt = TimeUtils.printSince(time);
                    }
                    time = jt.getLong("nextAttempt");
                    if (time > 0) {
                        row.nextAttempt = TimeUtils.printDuration(time);
                    }
                    time = jt.getLong("elapsed");
                    if (time > 0) {
                        row.elapsed = TimeUtils.printDuration(time);
                    }
                    row.supervising = jt.getString("supervising");
                    row.error = jt.getString("error");
                    row.stackTrace = jt.getCollection("stackTrace");
                }

                rows.add(row);
            }
        } else {
            printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        // sort rows
        rows.sort(this::sortRow);

        if (watch) {
            clearScreen();
        }
        if (!rows.isEmpty()) {
            if (supervising) {
                if (header) {
                    printer().println("Supervising Route Controller");
                    printer().printf("\tInitial Starting Routes: %b%n", jo.getBoolean("startingRoutes"));
                    printer().printf("\tUnhealthy Routes: %b%n", jo.getBoolean("unhealthyRoutes"));
                    printer().printf("\tRoutes Total: %s%n", jo.getInteger("totalRoutes"));
                    printer().printf("\tRoutes Started: %d%n", jo.getInteger("startedRoutes"));
                    printer().printf("\tRoutes Restarting: %d%n", jo.getInteger("restartingRoutes"));
                    printer().printf("\tRoutes Exhausted: %d%n", jo.getInteger("exhaustedRoutes"));
                    printer().printf("\tInitial Delay: %d%n", jo.getInteger("initialDelay"));
                    printer().printf("\tBackoff Delay: %d%n", jo.getInteger("backoffDelay"));
                    printer().printf("\tBackoff Max Delay: %d%n", jo.getInteger("backoffMaxDelay"));
                    printer().printf("\tBackoff Max Elapsed Time: %d%n", jo.getInteger("backoffMaxElapsedTime"));
                    printer().printf("\tBackoff Max Attempts: %d%n", jo.getInteger("backoffMaxAttempts"));
                    printer().printf("\tThread Pool Size: %d%n", jo.getInteger("threadPoolSize"));
                    printer().printf("\tUnhealthy On Restarting: %b%n", jo.getBoolean("unhealthyOnRestarting"));
                    printer().printf("\tUnhealthy On Exhaust: %b%n", jo.getBoolean("unhealthyOnExhausted"));
                    printer().println("\n");
                }
                dumpTable(rows, true);
            } else {
                if (header) {
                    printer().println("Default Route Controller");
                    printer().printf("\tStarting Routes: %b%n", jo.getBoolean("startingRoutes"));
                    printer().printf("\tRoutes Total: %s%n", jo.getInteger("totalRoutes"));
                    printer().println("\n");
                }
                dumpTable(rows, false);
            }
        }

        // delete output file after use
        FileUtil.deleteFile(outputFile);

        return 0;
    }

    protected void dumpTable(List<Row> rows, boolean supervised) {
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.routeId),
                new Column().header("URI").dataAlign(HorizontalAlign.LEFT).maxWidth(60, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.uri),
                new Column().header("STATE").headerAlign(HorizontalAlign.RIGHT).with(this::getSupervising),
                new Column().visible(supervised).header("ATTEMPT").headerAlign(HorizontalAlign.CENTER)
                        .dataAlign(HorizontalAlign.CENTER).with(this::getAttempts),
                new Column().visible(supervised).header("ELAPSED").headerAlign(HorizontalAlign.CENTER).with(this::getElapsed),
                new Column().visible(supervised).header("LAST-AGO").headerAlign(HorizontalAlign.CENTER).with(this::getLast),
                new Column().visible(supervised).header("ERROR-MESSAGE").headerAlign(HorizontalAlign.LEFT)
                        .dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(80, OverflowBehaviour.ELLIPSIS_RIGHT).with(r -> r.error))));

        if (supervised && trace) {
            rows = rows.stream().filter(r -> r.error != null && !r.error.isEmpty()).collect(Collectors.toList());
            if (!rows.isEmpty()) {
                for (Row row : rows) {
                    printer().println("\n");
                    printer().println(StringHelper.fillChars('-', 120));
                    printer().println(StringHelper.padString(1, 55) + "STACK-TRACE");
                    printer().println(StringHelper.fillChars('-', 120));
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("\tID: %s%n", row.routeId));
                    sb.append(String.format("\tURI: %s%n", row.uri));
                    sb.append(String.format("\tSTATE: %s%n", getSupervising(row)));
                    for (int i = 0; i < depth && i < row.stackTrace.size(); i++) {
                        sb.append(String.format("\t%s%n", row.stackTrace.get(i)));
                    }
                    printer().println(String.valueOf(sb));
                }
            }
        }
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "id":
                return o1.routeId.compareToIgnoreCase(o2.routeId) * negate;
            case "state":
                return o1.status.compareToIgnoreCase(o2.status) * negate;
            default:
                return 0;
        }
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        return getJsonObject(outputFile);
    }

    protected String getSupervising(Row r) {
        if (r.supervising != null) {
            if ("Active".equals(r.supervising)) {
                if (r.attempts <= 1) {
                    return "Starting";
                } else {
                    return "Restarting";
                }
            }
            return r.supervising;
        }
        return r.status;
    }

    protected String getAttempts(Row r) {
        if (r.supervising != null) {
            return Long.toString(r.attempts);
        }
        return "";
    }

    protected String getLast(Row r) {
        if (r.lastAttempt != null && !r.lastAttempt.isEmpty()) {
            return r.lastAttempt;
        }
        return "";
    }

    protected String getElapsed(Row r) {
        if (r.elapsed != null && !r.elapsed.isEmpty()) {
            return r.elapsed;
        }
        return "";
    }

    private static class Row {
        String routeId;
        String status;
        String uri;
        long attempts;
        String lastAttempt;
        String nextAttempt;
        String elapsed;
        String supervising;
        String error;
        List<String> stackTrace;
    }

}
