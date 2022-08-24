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

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route", aliases = { "route", "routes" }, description = "Get status of Camel routes")
public class CamelRouteStatus extends ProcessBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter routes by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter-mean" },
                        description = "Filter routes that must be slower than the given time (ms)")
    long mean;

    public CamelRouteStatus(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .sorted((o1, o2) -> {
                    switch (sort) {
                        case "pid":
                            return Long.compare(o1.pid(), o2.pid());
                        case "name":
                            return extractName(o1).compareTo(extractName(o2));
                        case "age":
                            // we want newest in top
                            return Long.compare(extractSince(o1), extractSince(o2)) * -1;
                        default:
                            return 0;
                    }
                })
                .forEach(ph -> {
                    String name = extractName(ph);
                    if (ObjectHelper.isNotEmpty(name)) {
                        JsonObject status = loadStatus(ph.pid());
                        if (status != null) {
                            JsonArray array = (JsonArray) status.get("routes");
                            for (int i = 0; i < array.size(); i++) {
                                JsonObject o = (JsonObject) array.get(i);
                                Row row = new Row();
                                row.pid = "" + ph.pid();
                                row.name = name;
                                row.routeId = o.getString("routeId");
                                row.from = o.getString("from");
                                row.source = o.getString("source");
                                row.state = o.getString("state").toLowerCase(Locale.ROOT);
                                row.uptime = o.getString("uptime");
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
                                    Object last = stats.get("sinceLastExchange");
                                    if (last != null) {
                                        row.sinceLast = last.toString();
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
                                }
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            System.out.println(AsciiTable.getTable(AsciiTable.BASIC_ASCII_NO_DATA_SEPARATORS, rows, Arrays.asList(
                    new Column().header("PID").with(r -> r.pid),
                    new Column().header("Name").dataAlign(HorizontalAlign.LEFT).maxColumnWidth(30)
                            .with(r -> maxWidth(r.name, 28)),
                    new Column().header("Route ID").dataAlign(HorizontalAlign.LEFT).maxColumnWidth(30)
                            .with(r -> maxWidth(r.routeId, 28)),
                    new Column().header("From").dataAlign(HorizontalAlign.LEFT).maxColumnWidth(40)
                            .with(r -> maxWidth(r.from, 38)),
                    new Column().header("State").with(r -> r.state),
                    new Column().header("Age").with(r -> r.uptime),
                    new Column().header("Total").headerAlign(HorizontalAlign.CENTER).with(r -> r.total),
                    new Column().header("Failed").headerAlign(HorizontalAlign.CENTER).maxColumnWidth(8).with(r -> r.failed),
                    new Column().header("Inflight").headerAlign(HorizontalAlign.CENTER).maxColumnWidth(10)
                            .with(r -> r.inflight),
                    new Column().header("Mean").headerAlign(HorizontalAlign.CENTER).maxColumnWidth(8).with(r -> r.mean),
                    new Column().header("Max").headerAlign(HorizontalAlign.CENTER).maxColumnWidth(8).with(r -> r.max),
                    new Column().header("Min").headerAlign(HorizontalAlign.CENTER).maxColumnWidth(8).with(r -> r.min),
                    new Column().header("Last Ago").headerAlign(HorizontalAlign.CENTER).maxColumnWidth(10)
                            .with(r -> r.sinceLast))));
        }

        return 0;
    }

    private JsonObject loadStatus(long pid) {
        try {
            File f = getStatusFile("" + pid);
            if (f != null) {
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

    protected int sortRow(Row o1, Row o2) {
        // no sort by default
        return 0;
    }

    static class Row {
        String pid;
        String name;
        String routeId;
        String from;
        String source;
        String uptime;
        String state;
        String total;
        String failed;
        String inflight;
        String mean;
        String max;
        String min;
        String sinceLast;
    }

}
