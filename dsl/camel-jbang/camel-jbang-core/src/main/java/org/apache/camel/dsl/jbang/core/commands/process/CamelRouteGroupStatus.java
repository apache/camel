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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route-group", description = "Get status of Camel route groups",
         sortOptions = false, showDefaultValues = true)
public class CamelRouteGroupStatus extends ProcessWatchCommand {

    public static class PidNameAgeGroupCompletionCandidates implements Iterable<String> {

        public PidNameAgeGroupCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name", "age", "group").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeGroupCompletionCandidates.class,
                        description = "Sort by pid, name, age or group", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter groups by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter-mean" },
                        description = "Filter groups that must be slower than the given time (ms)")
    long mean;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter groups by name")
    String[] filter;

    public CamelRouteGroupStatus(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        AtomicBoolean remoteVisible = new AtomicBoolean();
        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        JsonArray array = (JsonArray) root.get("routeGroups");
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject o = (JsonObject) array.get(i);
                            Row row = new Row();
                            row.name = context.getString("name");
                            if ("CamelJBang".equals(row.name)) {
                                row.name = ProcessHelper.extractName(root, ph);
                            }
                            row.pid = Long.toString(ph.pid());
                            row.group = o.getString("group");
                            row.size = o.getInteger("size");
                            row.routeIds = o.getCollection("routeIds");
                            row.state = o.getString("state");
                            row.age = o.getString("uptime");
                            row.uptime = row.age != null ? TimeUtils.toMilliSeconds(row.age) : 0;
                            Map<String, ?> stats = o.getMap("statistics");
                            if (stats != null) {
                                Object load = stats.get("load01");
                                if (load != null) {
                                    row.load01 = load.toString();
                                }
                                load = stats.get("load05");
                                if (load != null) {
                                    row.load05 = load.toString();
                                }
                                load = stats.get("load15");
                                if (load != null) {
                                    row.load15 = load.toString();
                                }
                                Object thp = stats.get("exchangesThroughput");
                                if (thp != null) {
                                    row.throughput = thp.toString();
                                }
                                Object coverage = stats.get("coverage");
                                if (coverage != null) {
                                    row.coverage = coverage.toString();
                                }
                                row.total = stats.get("exchangesTotal").toString();
                                row.inflight = stats.get("exchangesInflight").toString();
                                row.failed = stats.get("exchangesFailed").toString();
                                row.mean = stats.get("meanProcessingTime").toString();
                                if ("-1".equals(row.mean)) {
                                    row.mean = null;
                                }
                                row.max = stats.get("maxProcessingTime").toString();
                                row.min = stats.get("minProcessingTime").toString();
                                Object last = stats.get("lastProcessingTime");
                                if (last != null) {
                                    row.last = last.toString();
                                }
                                last = stats.get("deltaProcessingTime");
                                if (last != null) {
                                    row.delta = last.toString();
                                }
                                last = stats.get("lastCreatedExchangeTimestamp");
                                if (last != null) {
                                    long time = Long.parseLong(last.toString());
                                    row.sinceLastStarted = TimeUtils.printSince(time);
                                }
                                last = stats.get("lastCompletedExchangeTimestamp");
                                if (last != null) {
                                    long time = Long.parseLong(last.toString());
                                    row.sinceLastCompleted = TimeUtils.printSince(time);
                                }
                                last = stats.get("lastFailedExchangeTimestamp");
                                if (last != null) {
                                    long time = Long.parseLong(last.toString());
                                    row.sinceLastFailed = TimeUtils.printSince(time);
                                }
                            }

                            boolean add = true;
                            if (mean > 0 && (row.mean == null || Long.parseLong(row.mean) < mean)) {
                                add = false;
                            }
                            if (limit > 0 && rows.size() >= limit) {
                                add = false;
                            }
                            if (add && filter != null) {
                                boolean match = false;
                                for (String f : filter) {
                                    if (!match) {
                                        match = PatternHelper.matchPattern(row.group, f);
                                    }
                                }
                                if (!match) {
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
            printTable(rows);
        }

        return 0;
    }

    protected void printTable(List<Row> rows) {
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("GROUP").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getGroup),
                new Column().header("ROUTES").dataAlign(HorizontalAlign.RIGHT).headerAlign(HorizontalAlign.CENTER)
                        .with(r -> "" + r.size),
                new Column().header("STATUS").dataAlign(HorizontalAlign.LEFT).headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.state),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("COVER").with(this::getCoverage),
                new Column().header("MSG/S").with(this::getThroughput),
                new Column().header("TOTAL").with(this::getTotal),
                new Column().header("FAIL").with(this::getFailed),
                new Column().header("INFLIGHT").with(this::getInflight),
                new Column().header("MEAN").with(r -> r.mean),
                new Column().header("MIN").with(r -> r.min),
                new Column().header("MAX").with(r -> r.max),
                new Column().header("LAST").with(r -> r.last),
                new Column().header("DELTA").with(this::getDelta),
                new Column().header("SINCE-LAST").with(this::getSinceLast))));
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
            case "group":
                return o1.name.compareToIgnoreCase(o2.group) * negate;
            case "age":
                return Long.compare(o1.uptime, o2.uptime) * negate;
            default:
                return 0;
        }
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

    protected String getCoverage(Row r) {
        String s = r.coverage;
        if (s == null || s.isEmpty()) {
            s = "";
        }
        return s;
    }

    protected String getGroup(Row r) {
        return r.group;
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

    protected String getTotal(Row r) {
        return r.total;
    }

    protected String getFailed(Row r) {
        return r.failed;
    }

    protected String getInflight(Row r) {
        return r.inflight;
    }

    static class Row {
        String pid;
        String name;
        long uptime;
        String group;
        int size;
        List<String> routeIds;
        String state;
        String age;
        String coverage;
        String throughput;
        String total;
        String failed;
        String inflight;
        String mean;
        String max;
        String min;
        String last;
        String delta;
        String sinceLastStarted;
        String sinceLastCompleted;
        String sinceLastFailed;
        String load01;
        String load05;
        String load15;
    }

}
