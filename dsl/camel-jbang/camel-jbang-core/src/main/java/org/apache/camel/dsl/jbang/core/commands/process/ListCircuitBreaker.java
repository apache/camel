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

@Command(name = "circuit-breaker",
         description = "Get status of Circuit Breaker EIPs", sortOptions = false)
public class ListCircuitBreaker extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public ListCircuitBreaker(CamelJBangMain main) {
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

                        JsonObject mo = (JsonObject) root.get("resilience4j");
                        if (mo != null) {
                            JsonArray arr = (JsonArray) mo.get("circuitBreakers");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    row = baseRow.copy();
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.component = "resilience4j";
                                    row.id = jo.getString("id");
                                    row.routeId = jo.getString("routeId");
                                    row.state = jo.getString("state");
                                    row.bufferedCalls = jo.getInteger("bufferedCalls");
                                    row.successfulCalls = jo.getInteger("successfulCalls");
                                    row.failedCalls = jo.getInteger("failedCalls");
                                    row.notPermittedCalls = jo.getLong("notPermittedCalls");
                                    row.failureRate = jo.getDouble("failureRate");
                                    rows.add(row);
                                }
                            }
                        }
                        mo = (JsonObject) root.get("fault-tolerance");
                        if (mo != null) {
                            JsonArray arr = (JsonArray) mo.get("circuitBreakers");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    row = baseRow.copy();
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.component = "fault-tolerance";
                                    row.id = jo.getString("id");
                                    row.routeId = jo.getString("routeId");
                                    row.state = jo.getString("state");
                                    rows.add(row);
                                }
                            }
                        }
                        mo = (JsonObject) root.get("route-circuit-breaker");
                        if (mo != null) {
                            JsonArray arr = (JsonArray) mo.get("circuitBreakers");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    row = baseRow.copy();
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.component = "core";
                                    row.id = jo.getString("routeId");
                                    row.routeId = jo.getString("routeId");
                                    row.state = jo.getString("state");
                                    row.successfulCalls = jo.getInteger("successfulCalls");
                                    row.failedCalls = jo.getInteger("failedCalls");
                                    rows.add(row);
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
                    new Column().header("COMPONENT").dataAlign(HorizontalAlign.LEFT).with(r -> r.component),
                    new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT).with(r -> r.routeId),
                    new Column().header("ID").dataAlign(HorizontalAlign.LEFT).with(r -> r.id),
                    new Column().header("STATE").dataAlign(HorizontalAlign.LEFT).with(r -> r.state),
                    new Column().header("PENDING").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(this::getPending),
                    new Column().header("SUCCESS").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(this::getSuccess),
                    new Column().header("FAIL").headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT)
                            .with(this::getFailure),
                    new Column().header("REJECT").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(this::getReject))));
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

    private String getFailure(Row r) {
        if (r.failedCalls <= 0) {
            return "";
        } else if (r.failureRate > 0) {
            return +r.failedCalls + " (" + String.format("%.0f", r.failureRate) + "%)";
        } else {
            return Integer.toString(r.failedCalls);
        }
    }

    private String getPending(Row r) {
        if ("resilience4j".equals(r.component)) {
            return Integer.toString(r.bufferedCalls);
        }
        return "";
    }

    private String getSuccess(Row r) {
        if ("resilience4j".equals(r.component) || "core".equals(r.component)) {
            return Integer.toString(r.successfulCalls);
        }
        return "";
    }

    private String getReject(Row r) {
        if ("resilience4j".equals(r.component)) {
            return Long.toString(r.notPermittedCalls);
        }
        return "";
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        String age;
        long uptime;
        String component;
        String id;
        String routeId;
        String state;
        int bufferedCalls;
        int successfulCalls;
        int failedCalls;
        long notPermittedCalls;
        double failureRate;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }

}
