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
import java.util.Locale;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
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
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        String name = extractName(root, ph);
                        JsonArray array = (JsonArray) root.get("routes");
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject o = (JsonObject) array.get(i);
                            Row row = new Row();
                            row.name = name;
                            row.pid = "" + ph.pid();
                            row.routeId = o.getString("routeId");
                            row.from = o.getString("from");
                            row.source = o.getString("source");
                            row.state = o.getString("state").toLowerCase(Locale.ROOT);
                            row.age = o.getString("uptime");
                            row.uptime = row.age != null ? TimeUtils.toMilliSeconds(row.age) : 0;
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
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            boolean sources = rows.stream().noneMatch(r -> r.source == null);
            System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header(sources ? "SOURCE" : "NAME").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(30, OverflowBehaviour.CLIP)
                            .with(r -> sourceLocLine(sources ? r.source : r.name)),
                    new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS)
                            .with(r -> r.routeId),
                    new Column().header("FROM").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS)
                            .with(r -> r.from),
                    new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                            .with(r -> extractState(r.state)),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                    new Column().header("TOTAL").with(r -> r.total),
                    new Column().header("FAILED").with(r -> r.failed),
                    new Column().header("INFLIGHT").with(r -> r.inflight),
                    new Column().header("MEAN").with(r -> r.mean),
                    new Column().header("MIN").with(r -> r.min),
                    new Column().header("MAX").with(r -> r.max),
                    new Column().header("SINCE-LAST").with(r -> r.sinceLast))));
        }

        return 0;
    }

    protected String sourceLocLine(String location) {
        while (StringHelper.countChar(location, ':') > 1) {
            location = location.substring(location.indexOf(':') + 1);
        }
        int pos = location.indexOf(':');
        // is the colon as scheme or line number
        String last = location.substring(pos + 1);
        boolean digits = last.matches("\\d+");
        if (!digits) {
            // it must be scheme so clip that
            location = location.substring(pos + 1);
        }
        location = FileUtil.stripPath(location);
        return location;
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

    static class Row {
        String pid;
        String name;
        long uptime;
        String routeId;
        String from;
        String source;
        String state;
        String age;
        String total;
        String failed;
        String inflight;
        String mean;
        String max;
        String min;
        String sinceLast;
    }

}
