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
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

@Command(name = "ps", description = "List running Camel integrations", sortOptions = false)
public class ListProcess extends ProcessWatchCommand {

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--pid" },
                        description = "List only pid in the output")
    boolean pid;

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    public ListProcess(CamelJBangMain main) {
        super(main);
    }

    protected Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids("*");
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        Row row = new Row();
                        row.pid = Long.toString(ph.pid());
                        row.uptime = extractSince(ph);
                        row.ago = TimeUtils.printSince(row.uptime);
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        row.state = context.getInteger("phase");
                        JsonObject hc = (JsonObject) root.get("healthChecks");
                        boolean rdy = hc != null && hc.getBoolean("ready");
                        if (rdy) {
                            row.ready = "1/1";
                        } else {
                            row.ready = "0/1";
                        }
                        Map<String, ?> stats = context.getMap("statistics");
                        if (stats != null) {
                            row.total = stats.get("exchangesTotal").toString();
                            Object num = stats.get("remoteExchangesTotal");
                            if (num != null) {
                                row.totalRemote = num.toString();
                            }
                            row.failed = stats.get("exchangesFailed").toString();
                            num = stats.get("remoteExchangesFailed");
                            if (num != null) {
                                row.failedRemote = num.toString();
                            }
                            row.inflight = stats.get("exchangesInflight").toString();
                            num = stats.get("remoteExchangesInflight");
                            if (num != null) {
                                row.inflightRemote = num.toString();
                            }
                        }
                        rows.add(row);
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        // print rows
        if (!rows.isEmpty()) {
            if (pid) {
                if (jsonOutput) {
                    printer().println(
                            Jsoner.serialize(
                                    rows.stream().map(row -> row.pid).collect(Collectors.toList())));
                } else {
                    rows.forEach(r -> printer().println(r.pid));
                }
            } else {
                if (jsonOutput) {
                    printer().println(
                            Jsoner.serialize(
                                    rows.stream().map(row -> Map.of(
                                            "pid", row.pid,
                                            "name", row.name,
                                            "ready", row.ready,
                                            "status", extractState(row.state),
                                            "age", row.ago,
                                            "total", getTotal(row),
                                            "fail", getFailed(row),
                                            "inflight", getInflight(row))).collect(Collectors.toList())));
                } else {
                    printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                            new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                            new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                    .maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                                    .with(r -> r.name),
                            new Column().header("READY").dataAlign(HorizontalAlign.CENTER).with(r -> r.ready),
                            new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                                    .with(r -> extractState(r.state)),
                            new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.ago),
                            new Column().header("TOTAL").with(this::getTotal),
                            new Column().header("FAIL").with(this::getFailed),
                            new Column().header("INFLIGHT").with(this::getInflight))));
                }
            }
        }

        return 0;
    }

    private String getTotal(Row r) {
        if (r.totalRemote != null) {
            return r.totalRemote + "/" + r.total;
        }
        return r.total;
    }

    private String getFailed(Row r) {
        if (r.failedRemote != null) {
            return r.failedRemote + "/" + r.failed;
        }
        return r.failed;
    }

    private String getInflight(Row r) {
        if (r.inflightRemote != null) {
            return r.inflightRemote + "/" + r.inflight;
        }
        return r.inflight;
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

    private static class Row {
        String pid;
        String name;
        String ready;
        int state;
        String ago;
        long uptime;
        String total;
        String totalRemote;
        String failed;
        String failedRemote;
        String inflight;
        String inflightRemote;
    }

}
