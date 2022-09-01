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
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "integration", aliases = { "int", "integration", "integrations", "context" },
         description = "Get status of Camel integrations")
public class CamelContextStatus extends ProcessBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public CamelContextStatus(CamelJBangMain main) {
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
                    // there must be a status file for the running Camel integration
                    if (root != null) {
                        Row row = new Row();
                        JsonObject context = (JsonObject) root.get("context");
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = extractName(root, ph);
                        }
                        row.pid = "" + ph.pid();
                        row.uptime = extractSince(ph);
                        row.ago = TimeUtils.printSince(row.uptime);
                        JsonObject runtime = (JsonObject) root.get("runtime");
                        row.platform = extractPlatform(ph, runtime);
                        row.platformVersion = runtime != null ? runtime.getString("platformVersion") : null;
                        row.state = context.getInteger("phase");
                        row.camelVersion = context.getString("version");
                        Map<String, ?> stats = context.getMap("statistics");
                        if (stats != null) {
                            row.total = stats.get("exchangesTotal").toString();
                            row.inflight = stats.get("exchangesInflight").toString();
                            row.failed = stats.get("exchangesFailed").toString();
                            Object last = stats.get("sinceLastExchange");
                            if (last != null) {
                                row.sinceLast = last.toString();
                            }
                        }
                        JsonArray array = (JsonArray) root.get("routes");
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject o = (JsonObject) array.get(i);
                            String state = o.getString("state");
                            row.routeTotal++;
                            if ("Started".equals(state)) {
                                row.routeStarted++;
                            }
                        }

                        JsonObject hc = (JsonObject) root.get("healthChecks");
                        boolean rdy = hc != null && hc.getBoolean("ready");
                        if (rdy) {
                            row.ready = "1/1";
                        } else {
                            row.ready = "0/1";
                        }
                        rows.add(row);
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS)
                            .with(r -> r.name),
                    new Column().header("CAMEL").dataAlign(HorizontalAlign.LEFT).with(r -> r.camelVersion),
                    new Column().header("PLATFORM").dataAlign(HorizontalAlign.LEFT).with(this::getPlatform),
                    new Column().header("READY").dataAlign(HorizontalAlign.CENTER).with(r -> r.ready),
                    new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                            .with(r -> extractState(r.state)),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.ago),
                    new Column().header("ROUTE").with(this::getRoutes),
                    new Column().header("TOTAL").with(r -> r.total),
                    new Column().header("FAILED").with(r -> r.failed),
                    new Column().header("INFLIGHT").with(r -> r.inflight),
                    new Column().header("SINCE-LAST").with(r -> r.sinceLast))));
        }

        return 0;
    }

    private String extractPlatform(ProcessHandle ph, JsonObject runtime) {
        String answer = runtime != null ? runtime.getString("platform") : null;
        if ("Camel".equals(answer)) {
            // generic camel, we need to check if we run in JBang
            String cl = ph.info().commandLine().orElse("");
            if (cl.contains("main.CamelJBang run")) {
                answer = "JBang";
            }
        }
        return answer;
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

    private String getPlatform(Row r) {
        if (r.platformVersion != null) {
            return r.platform + " v" + r.platformVersion;
        } else {
            return r.platform;
        }
    }

    private String getRoutes(Row r) {
        return r.routeStarted + "/" + r.routeTotal;
    }

    private static class Row {
        String pid;
        String platform;
        String platformVersion;
        String camelVersion;
        String name;
        String ready;
        int routeStarted;
        int routeTotal;
        int state;
        String ago;
        long uptime;
        String total;
        String failed;
        String inflight;
        String sinceLast;
    }

}
