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
import java.util.List;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "processor", aliases = { "processor", "processors" }, description = "Get status of Camel processors")
public class CamelProcessorStatus extends ProcessBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid or name", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--source" },
                        description = "Prefer to display source filename instead of processor IDs")
    boolean source;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter routes by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter-mean" },
                        description = "Filter processors that must be slower than the given time (ms)")
    long mean;

    public CamelProcessorStatus(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        JsonObject context = (JsonObject) root.get("context");
                        JsonArray array = (JsonArray) root.get("routes");
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject o = (JsonObject) array.get(i);
                            Row row = new Row();
                            row.name = context.getString("name");
                            if ("CamelJBang".equals(row.name)) {
                                row.name = extractName(root, ph);
                            }
                            row.pid = "" + ph.pid();
                            row.routeId = o.getString("routeId");
                            row.processor = o.getString("from");
                            row.source = o.getString("source");
                            row.state = o.getString("state");
                            Map<String, ?> stats = o.getMap("statistics");
                            if (stats != null) {
                                row.total = stats.get("exchangesTotal").toString();
                                row.inflight = stats.get("exchangesInflight").toString();
                                row.failed = stats.get("exchangesFailed").toString();
                                row.mean = stats.get("meanProcessingTime").toString();
                                if ("-1".equals(row.mean)) {
                                    row.mean = null;
                                }
                                row.max = stats.get("maxProcessingTime").toString();
                                row.min = stats.get("minProcessingTime").toString();
                                Object last = stats.get("sinceLastCreatedExchange");
                                if (last != null) {
                                    row.sinceLastStarted = last.toString();
                                }
                                last = stats.get("sinceLastCompletedExchange");
                                if (last != null) {
                                    row.sinceLastCompleted = last.toString();
                                }
                                last = stats.get("sinceLastFailedExchange");
                                if (last != null) {
                                    row.sinceLastFailed = last.toString();
                                }
                            }

                            boolean add = true;
                            if (mean > 0 && row.mean != null && Long.parseLong(row.mean) < mean) {
                                add = false;
                            }
                            if (limit > 0 && rows.size() >= limit) {
                                add = false;
                            }
                            if (add) {
                                rows.add(row);
                                List<JsonObject> list = o.getCollection("processors");
                                addProcessors(row, rows, list);
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

    private void addProcessors(Row route, List<Row> rows, List<JsonObject> processors) {
        for (JsonObject o : processors) {
            Row row = new Row();
            row.pid = route.pid;
            row.name = route.name;
            row.routeId = route.routeId;
            rows.add(row);
            row.processorId = o.getString("id");
            row.processor = o.getString("processor");
            row.level = o.getIntegerOrDefault("level", 0);
            row.source = o.getString("source");
            Map<String, ?> stats = o.getMap("statistics");
            if (stats != null) {
                row.total = stats.get("exchangesTotal").toString();
                row.inflight = stats.get("exchangesInflight").toString();
                row.failed = stats.get("exchangesFailed").toString();
                row.mean = stats.get("meanProcessingTime").toString();
                if ("-1".equals(row.mean)) {
                    row.mean = null;
                }
                row.max = stats.get("maxProcessingTime").toString();
                row.min = stats.get("minProcessingTime").toString();
                Object last = stats.get("sinceLastCompletedExchange");
                if (last != null) {
                    row.sinceLastCompleted = last.toString();
                }
                last = stats.get("sinceLastFailedExchange");
                if (last != null) {
                    row.sinceLastFailed = last.toString();
                }
            }
        }
    }

    protected void printTable(List<Row> rows) {
        // need to flatten list
        System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(this::getPid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getName),
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getId),
                new Column().header("PROCESSOR").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getProcessor),
                new Column().header("TOTAL").with(r -> r.total),
                new Column().header("FAIL").with(r -> r.failed),
                new Column().header("INFLIGHT").with(r -> r.inflight),
                new Column().header("MEAN").with(r -> r.mean),
                new Column().header("MIN").with(r -> r.min),
                new Column().header("MAX").with(r -> r.max),
                new Column().header("SINCE-LAST").with(this::getSinceLast))));
    }

    protected int sortRow(Row o1, Row o2) {
        switch (sort) {
            case "pid":
                return Long.compare(Long.parseLong(o1.pid), Long.parseLong(o2.pid));
            case "name":
                return o1.name.compareToIgnoreCase(o2.name);
            case "age":
                return Long.compare(o1.uptime, o2.uptime);
            default:
                return 0;
        }
    }

    protected String getSinceLast(Row r) {
        String s1 = r.sinceLastCompleted != null ? r.sinceLastCompleted : "-";
        String s2 = r.sinceLastFailed != null ? r.sinceLastFailed : "-";
        return s1 + "/" + s2;
    }

    protected String getName(Row r) {
        return r.processorId == null ? r.name : "";
    }

    protected String getId(Row r) {
        String answer;
        if (source && r.source != null) {
            answer = sourceLocLine(r.source);
        } else {
            answer = r.processorId != null ? r.processorId : r.routeId;
        }
        if (r.processorId != null) {
            String pad = StringHelper.padString(r.level, 1);
            answer = pad + answer;
        }
        return answer;
    }

    protected String getPid(Row r) {
        if (r.processorId == null) {
            return r.pid;
        } else {
            return "";
        }
    }

    protected String getProcessor(Row r) {
        String pad = StringHelper.padString(r.level, 1);
        return pad + r.processor;
    }

    static class Row {
        String pid;
        String name;
        long uptime;
        String routeId;
        String processorId;
        String processor;
        int level;
        String source;
        String state;
        String total;
        String failed;
        String inflight;
        String mean;
        String max;
        String min;
        String sinceLastStarted;
        String sinceLastCompleted;
        String sinceLastFailed;
    }

}
