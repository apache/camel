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
import java.util.StringJoiner;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "metric",
         description = "Get metrics (micrometer) of running Camel integrations", sortOptions = false)
public class ListMetric extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter metric by type or name")
    String filter;

    @CommandLine.Option(names = { "--tags" },
                        description = "Show metric tags", defaultValue = "false")
    boolean tags;

    @CommandLine.Option(names = { "--custom" },
                        description = "Only show custom metrics", defaultValue = "false")
    boolean custom;

    @CommandLine.Option(names = { "--all" },
                        description = "Whether to show all metrics (also unused with counter being 0)", defaultValue = "false")
    boolean all;

    public ListMetric(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    // there must be a status file for the running Camel integration
                    if (root != null) {
                        Row row = new Row();
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        row.pid = Long.toString(ph.pid());
                        row.uptime = extractSince(ph);
                        row.age = TimeUtils.printSince(row.uptime);
                        Row baseRow = row.copy();

                        JsonObject mo = (JsonObject) root.get("micrometer");
                        if (mo != null) {
                            JsonArray arr = (JsonArray) mo.get("counters");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    row = baseRow.copy();
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.type = "counter";
                                    row.metricName = jo.getString("name");
                                    row.metricDescription = jo.getString("description");
                                    row.metricId = extractId(jo);
                                    row.tags = extractTags(jo);
                                    row.count = jo.getDouble("count");

                                    if (custom && row.metricName.startsWith("Camel")) {
                                        continue; // skip internal camel metrics
                                    }
                                    if (!all && getNumber(row.count).isEmpty()) {
                                        continue;
                                    }
                                    if (filter == null || row.type.equals(filter) || row.metricName.contains(filter)) {
                                        rows.add(row);
                                    }
                                }
                            }
                            arr = (JsonArray) mo.get("timers");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    row = baseRow.copy();
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.type = "timer";
                                    row.metricName = jo.getString("name");
                                    row.metricDescription = jo.getString("description");
                                    row.metricId = extractId(jo);
                                    row.tags = extractTags(jo);
                                    row.count = jo.getDouble("count");
                                    row.mean = jo.getDouble("mean");
                                    row.max = jo.getDouble("max");
                                    row.total = jo.getDouble("total");

                                    if (custom && row.metricName.startsWith("Camel")) {
                                        continue; // skip internal camel metrics
                                    }
                                    if (!all && getNumber(row.count).isEmpty()) {
                                        continue;
                                    }
                                    if (filter == null || row.type.equals(filter) || row.metricName.contains(filter)) {
                                        rows.add(row);
                                    }
                                }
                            }
                            arr = (JsonArray) mo.get("gauges");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    row = baseRow.copy();
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.type = "gauge";
                                    row.metricName = jo.getString("name");
                                    row.metricDescription = jo.getString("description");
                                    row.metricId = extractId(jo);
                                    row.tags = extractTags(jo);
                                    row.count = jo.getDouble("value");

                                    if (custom && row.metricName.startsWith("Camel")) {
                                        continue; // skip internal camel metrics
                                    }
                                    if (!all && getNumber(row.count).isEmpty()) {
                                        continue;
                                    }
                                    if (filter == null || row.type.equals(filter) || row.metricName.contains(filter)) {
                                        rows.add(row);
                                    }
                                }
                            }
                            arr = (JsonArray) mo.get("distribution");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    row = baseRow.copy();
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.type = "distribution";
                                    row.metricName = jo.getString("name");
                                    row.metricDescription = jo.getString("description");
                                    row.metricId = extractId(jo);
                                    row.tags = extractTags(jo);
                                    row.count = jo.getDouble("value");
                                    row.mean = jo.getDouble("mean");
                                    row.max = jo.getDouble("max");
                                    row.total = jo.getDouble("totalAmount");

                                    if (custom && row.metricName.startsWith("Camel")) {
                                        continue; // skip internal camel metrics
                                    }
                                    if (!all && getNumber(row.count).isEmpty()) {
                                        continue;
                                    }
                                    if (filter == null || row.type.equals(filter) || row.metricName.contains(filter)) {
                                        rows.add(row);
                                    }
                                }
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(r -> r.type),
                    new Column().header("METRIC").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.metricName),
                    new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.metricId),
                    new Column().header("VALUE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> getNumber(r.count)),
                    new Column().header("MEAN").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> getNumber(r.mean)),
                    new Column().header("MAX").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> getNumber(r.max)),
                    new Column().header("TOTAL").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> getNumber(r.total)),
                    new Column().header("TAGS").visible(tags).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(60, OverflowBehaviour.NEWLINE)
                            .with(r -> r.tags))));
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

        int answer;
        switch (s) {
            case "pid":
                answer = Long.compare(Long.parseLong(o1.pid), Long.parseLong(o2.pid)) * negate;
                break;
            case "name":
                answer = o1.name.compareToIgnoreCase(o2.name) * negate;
                break;
            case "age":
                answer = Long.compare(o1.uptime, o2.uptime) * negate;
                break;
            default:
                answer = 0;
        }
        if (answer == 0) {
            // sort by metric/route
            answer = o1.metricName.compareToIgnoreCase(o2.metricName) * negate;
            if (answer == 0) {
                answer = o1.metricId.compareToIgnoreCase(o2.metricId) * negate;
            }
        }
        return answer;
    }

    private String getNumber(double d) {
        String s = Double.toString(d);
        if (s.equals("0.0") || s.equals("0,0")) {
            return "";
        } else if (s.endsWith(".0") || s.endsWith(",0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private String extractId(JsonObject jo) {
        List<JsonObject> tags = jo.getCollection("tags");
        String routeId = null;
        String nodeId = null;
        if (tags != null) {
            for (JsonObject t : tags) {
                String k = t.getString("key");
                if ("routeId".equals(k)) {
                    routeId = t.getString("value");
                } else if ("nodeId".equals(k)) {
                    nodeId = t.getString("value");
                }
            }
        }
        if (routeId != null && nodeId != null) {
            return routeId + "/" + nodeId;
        } else if (routeId != null) {
            return routeId;
        } else if (nodeId != null) {
            return nodeId;
        }
        return "";
    }

    private String extractTags(JsonObject jo) {
        StringJoiner sj = new StringJoiner(" ");
        List<JsonObject> tags = jo.getCollection("tags");
        if (tags != null) {
            for (JsonObject t : tags) {
                String k = t.getString("key");
                String v = t.getString("value");
                sj.add(k + "=" + v);
            }
        }
        return sj.toString();
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        String age;
        long uptime;
        String type;
        String metricName;
        String metricDescription;
        String metricId;
        String tags;
        double count;
        double mean;
        double max;
        double total;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }

}
