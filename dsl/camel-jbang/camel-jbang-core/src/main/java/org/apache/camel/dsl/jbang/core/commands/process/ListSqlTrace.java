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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "sql-trace", description = "Get SQL query trace data", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel get sql-trace",
                 "  camel get sql-trace --watch" })
public class ListSqlTrace extends ProcessWatchCommand {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public ListSqlTrace(CamelJBangMain main) {
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
                    if (root != null) {
                        JsonObject context = (JsonObject) root.get("context");
                        JsonObject sqlTrace = (JsonObject) root.get("sqlTrace");
                        if (context != null && sqlTrace != null) {
                            JsonArray array = (JsonArray) sqlTrace.get("statements");
                            if (array != null) {
                                for (int i = 0; i < array.size(); i++) {
                                    JsonObject o = (JsonObject) array.get(i);
                                    Row row = new Row();
                                    row.name = context.getString("name");
                                    if ("CamelJBang".equals(row.name)) {
                                        row.name = ProcessHelper.extractName(root, ph);
                                    }
                                    row.pid = Long.toString(ph.pid());
                                    row.uptime = extractSince(ph);
                                    row.age = TimeUtils.printSince(row.uptime);
                                    row.timestamp = o.getLongOrDefault("timestamp", 0);
                                    row.exchangeId = o.getString("exchangeId");
                                    row.routeId = o.getString("routeId");
                                    row.query = o.getString("query");
                                    row.category = o.getString("category");
                                    row.endpoint = o.getString("endpoint");
                                    row.duration = o.getLongOrDefault("duration", 0);
                                    row.rowCount = o.getIntegerOrDefault("rowCount", 0);
                                    row.updateCount = o.getIntegerOrDefault("updateCount", 0);
                                    row.failed = o.getBooleanOrDefault("failed", false);
                                    rows.add(row);
                                }
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
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = new JsonObject();
                jo.put("pid", r.pid);
                jo.put("name", r.name);
                jo.put("age", r.age);
                jo.put("timestamp", r.timestamp);
                jo.put("exchangeId", r.exchangeId);
                jo.put("routeId", r.routeId);
                jo.put("query", r.query);
                jo.put("category", r.category);
                jo.put("endpoint", r.endpoint);
                jo.put("duration", r.duration);
                jo.put("rowCount", r.rowCount);
                jo.put("updateCount", r.updateCount);
                jo.put("failed", r.failed);
                return jo;
            }).collect(Collectors.toList())));
            return;
        }
        int tw = terminalWidth();
        int sqlW = Math.max(20, Math.min(80, tw - 100));
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("TIME").headerAlign(HorizontalAlign.CENTER).with(this::formatTime),
                new Column().header("CAT").dataAlign(HorizontalAlign.LEFT).with(r -> r.category != null ? r.category : ""),
                new Column().header("SQL").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(sqlW, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.query != null ? r.query : ""),
                new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.routeId != null ? r.routeId : ""),
                new Column().header("DURATION").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.duration + " ms"),
                new Column().header("ROWS").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(this::formatRows),
                new Column().header("STATUS").dataAlign(HorizontalAlign.CENTER)
                        .with(r -> r.failed ? "FAIL" : "OK"))));
    }

    private String formatTime(Row r) {
        if (r.timestamp > 0) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(r.timestamp), ZoneId.systemDefault())
                    .format(TIME_FMT);
        }
        return "";
    }

    private String formatRows(Row r) {
        if (r.rowCount > 0) {
            return Integer.toString(r.rowCount);
        } else if (r.updateCount > 0) {
            return Integer.toString(r.updateCount);
        }
        return "";
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

    static class Row {
        String pid;
        String name;
        long uptime;
        String age;
        long timestamp;
        String exchangeId;
        String routeId;
        String query;
        String category;
        String endpoint;
        long duration;
        int rowCount;
        int updateCount;
        boolean failed;
    }
}
