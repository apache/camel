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

@Command(name = "health", description = "Get health check status of running Camel integrations")
public class ListHealth extends ProcessBaseCommand {

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--level" },
                        description = "Level of details: full, oneline, or default", defaultValue = "default")
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

    public ListHealth(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        List<Row> rows = new ArrayList<>();

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
                            row.pid = "" + ph.pid();
                            row.uptime = extractSince(ph);
                            row.ago = TimeUtils.printSince(row.uptime);
                            row.name = context.getString("name");
                            if ("CamelJBang".equals(row.name)) {
                                row.name = extractName(root, ph);
                            }
                            row.id = o.getString("id");
                            row.group = o.getString("group");
                            row.state = o.getString("state");
                            row.readiness = o.getBoolean("readiness");
                            row.liveness = o.getBoolean("liveness");
                            row.message = o.getString("message");

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
                                        row.since = TimeUtils.printAge(delta);
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
                            if (add) {
                                rows.add(row);
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
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
                    new Column().header("SINCE").headerAlign(HorizontalAlign.RIGHT)
                            .dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> r.since),
                    new Column().header("TOTAL").headerAlign(HorizontalAlign.RIGHT).with(r -> r.total),
                    new Column().header("OK/KO").headerAlign(HorizontalAlign.RIGHT).with(this::getRate),
                    new Column().header("MESSAGE").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(r -> r.message))));
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
        String s1 = r.success != null && !"0".equals(r.success) ? r.success : "-";
        String s2 = r.failure != null && !"0".equals(r.failure) ? r.failure : "-";
        return s1 + "/" + s2;
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
        String since;
        String message;
    }

}
