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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "health", description = "Get health check status of running Camel integrations", sortOptions = false)
public class ListHealth extends ProcessWatchCommand {

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--level" },
                        description = "Level of details: full, or default", defaultValue = "default")
    String level;

    @CommandLine.Option(names = { "--down" },
                        description = "Show only checks which are DOWN")
    boolean down;

    @CommandLine.Option(names = { "--ready" },
                        description = "Show only readiness checks")
    boolean ready;

    @CommandLine.Option(names = { "--live" },
                        description = "Show only liveness checks")
    boolean live;

    @CommandLine.Option(names = { "--trace" },
                        description = "Include stack-traces in error messages", defaultValue = "false")
    boolean trace;

    @CommandLine.Option(names = { "--depth" },
                        description = "Max depth of stack-trace", defaultValue = "1")
    int depth;

    public ListHealth(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        final List<Row> rows = new ArrayList<>();

        // include stack-traces
        if (trace && depth <= 1) {
            depth = Integer.MAX_VALUE;
        }

        List<Long> pids = findPids("*");
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        JsonObject hc = (JsonObject) root.get("healthChecks");
                        if (hc == null) {
                            return;
                        }
                        JsonArray array = (JsonArray) hc.get("checks");
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject o = (JsonObject) array.get(i);
                            Row row = new Row();
                            row.pid = Long.toString(ph.pid());
                            row.uptime = extractSince(ph);
                            row.ago = TimeUtils.printSince(row.uptime);
                            row.name = context.getString("name");
                            if ("CamelJBang".equals(row.name)) {
                                row.name = ProcessHelper.extractName(root, ph);
                            }
                            row.id = o.getString("id");
                            row.group = o.getString("group");
                            row.state = o.getString("state");
                            row.readiness = o.getBoolean("readiness");
                            row.liveness = o.getBoolean("liveness");
                            row.message = o.getString("message");
                            row.stackTrace = o.getCollection("stackTrace");

                            JsonObject d = (JsonObject) o.get("details");
                            if (d != null) {
                                row.total = d.getString("invocation.count");
                                row.success = d.getString("success.count");
                                row.failure = d.getString("failure.count");
                                String kind = d.getString("check.kind");
                                if ("READINESS".equals(kind)) {
                                    row.liveness = false;
                                } else if ("LIVENESS".equals(kind)) {
                                    row.readiness = false;
                                }
                                // calc how long time since the check was invoked
                                String time = d.getString("invocation.time");
                                if (time != null) {
                                    ZonedDateTime zdt = ZonedDateTime.parse(time);
                                    if (zdt != null) {
                                        long delta = Math.abs(ZonedDateTime.now().until(zdt, ChronoUnit.MILLIS));
                                        row.sinceLast = TimeUtils.printAge(delta);
                                    }
                                }
                                time = d.getString("success.start.time");
                                if (time != null) {
                                    ZonedDateTime zdt = ZonedDateTime.parse(time);
                                    if (zdt != null) {
                                        long delta = Math.abs(ZonedDateTime.now().until(zdt, ChronoUnit.MILLIS));
                                        row.sinceStartSuccess = TimeUtils.printAge(delta);
                                    }
                                }
                                time = d.getString("failure.start.time");
                                if (time != null) {
                                    ZonedDateTime zdt = ZonedDateTime.parse(time);
                                    if (zdt != null) {
                                        long delta = Math.abs(ZonedDateTime.now().until(zdt, ChronoUnit.MILLIS));
                                        row.sinceStartFailure = TimeUtils.printAge(delta);
                                    }
                                }
                                for (Map.Entry<String, Object> entry : d.entrySet()) {
                                    String k = entry.getKey();
                                    // gather custom details
                                    if (!HealthCheckHelper.isReservedKey(k)) {
                                        if (row.customMeta == null) {
                                            row.customMeta = new TreeMap<>();
                                        }
                                        row.customMeta.put(k, entry.getValue());
                                    }
                                }
                            }

                            boolean add = true;
                            if (live && !row.liveness) {
                                add = false;
                            }
                            if (ready && !row.readiness) {
                                add = false;
                            }
                            if (down && !row.state.equals("DOWN")) {
                                add = false;
                            }
                            if (level == null || "default".equals(level)) {
                                if (row.state.equals("UP") && "camel".equals(row.group)
                                        && (row.id.startsWith("route:") || row.id.startsWith("consumer:"))) {
                                    // skip camel/route: camel/consumer: checks when they are UP
                                    // as they are less relevant and is verbose
                                    add = false;
                                }
                            }
                            if (add) {
                                rows.add(row);
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.ago),
                    new Column().header("ID").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(this::getId),
                    new Column().header("RL").minWidth(4).maxWidth(4).with(this::getLR),
                    new Column().header("STATE").headerAlign(HorizontalAlign.CENTER)
                            .dataAlign(HorizontalAlign.CENTER)
                            .with(r -> r.state),
                    new Column().header("RATE").headerAlign(HorizontalAlign.CENTER)
                            .dataAlign(HorizontalAlign.RIGHT)
                            .with(this::getRate),
                    new Column().header("SINCE").headerAlign(HorizontalAlign.CENTER)
                            .dataAlign(HorizontalAlign.RIGHT)
                            .with(this::getSince),
                    new Column().header("MESSAGE").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(r -> r.message))));
        }
        if (trace) {
            var traces
                    = rows.stream().filter(r -> r.stackTrace != null && !r.stackTrace.isEmpty()).collect(Collectors.toList());
            if (!traces.isEmpty()) {
                for (Row row : traces) {
                    printer().println("\n");
                    printer().println(StringHelper.fillChars('-', 120));
                    printer().println(StringHelper.padString(1, 55) + "STACK-TRACE");
                    printer().println(StringHelper.fillChars('-', 120));
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("\tPID: %s%n", row.pid));
                    sb.append(String.format("\tNAME: %s%n", row.name));
                    sb.append(String.format("\tAGE: %s%n", row.ago));
                    sb.append(String.format("\tCHECK-ID: %s%n", getId(row)));
                    sb.append(String.format("\tSTATE: %s%n", row.state));
                    sb.append(String.format("\tRATE: %s%n", row.failure));
                    sb.append(String.format("\tSINCE: %s%n", row.sinceStartFailure));
                    if (row.customMeta != null) {
                        sb.append(String.format("\tMETADATA:%n"));
                        row.customMeta.forEach((k, v) -> {
                            sb.append(String.format("\t\t%s = %s%n", k, v));
                        });
                    }
                    sb.append(String.format("\tMESSAGE: %s%n", row.message));
                    for (int i = 0; i < depth && i < row.stackTrace.size(); i++) {
                        sb.append(String.format("\t%s%n", row.stackTrace.get(i)));
                    }
                    printer().println(sb.toString());
                }
            }
        }

        return 0;
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "pid":
                return Long.compare(Long.parseLong(o1.pid), Long.parseLong(o2.pid)) * negate;
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            case "age":
                return Long.compare(o1.uptime, o2.uptime) * negate;
            default:
                return 0;
        }
    }

    protected String getId(Row r) {
        if (r.group != null) {
            return r.group + "/" + r.id;
        } else {
            return r.id;
        }
    }

    protected String getLR(Row r) {
        if (r.readiness && r.liveness) {
            return "RL";
        } else if (r.readiness) {
            return "R";
        } else if (r.liveness) {
            return "L";
        }
        return "";
    }

    protected String getRate(Row r) {
        String s1 = r.total != null && !"0".equals(r.total) ? r.total : "-";
        String s2 = r.success != null && !"0".equals(r.success) ? r.success : "-";
        String s3 = r.failure != null && !"0".equals(r.failure) ? r.failure : "-";
        return s1 + "/" + s2 + "/" + s3;
    }

    protected String getSince(Row r) {
        String s1 = r.sinceLast != null ? r.sinceLast : "-";
        String s2 = r.sinceStartSuccess != null ? r.sinceStartSuccess : "-";
        String s3 = r.sinceStartFailure != null ? r.sinceStartFailure : "-";
        return s1 + "/" + s2 + "/" + s3;
    }

    private static class Row {
        String pid;
        String name;
        String ago;
        long uptime;
        String id;
        String group;
        String state;
        boolean readiness;
        boolean liveness;
        String total;
        String success;
        String failure;
        String sinceLast;
        String sinceStartSuccess;
        String sinceStartFailure;
        String message;
        List<String> stackTrace;
        Map<String, Object> customMeta;
    }

}
