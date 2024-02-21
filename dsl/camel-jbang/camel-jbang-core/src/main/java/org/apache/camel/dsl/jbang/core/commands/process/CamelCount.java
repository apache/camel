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
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "count",
         description = "Get total and failed exchanges for running integrations",
         sortOptions = false)
public class CamelCount extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--total" },
                        description = "Get the total exchanges from a running integration")
    boolean total;

    @CommandLine.Option(names = { "--fail" },
                        description = "Get the failed exchanges from a running integration")
    boolean fail;

    public CamelCount(CamelJBangMain main) {
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
                        row.age = TimeUtils.printSince(extractSince(ph));
                        Map<String, ?> stats = context.getMap("statistics");
                        if (stats != null) {
                            row.total = stats.get("exchangesTotal").toString();
                            row.failed = stats.get("exchangesFailed").toString();
                        }
                        rows.add(row);
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!total && !fail) {
            if (!rows.isEmpty()) {
                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                        new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.name),
                        new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                        new Column().header("TOTAL").with(r -> r.total),
                        new Column().header("FAIL").with(r -> r.failed))));
            }
        } else {
            StringBuilder builder = new StringBuilder();
            if (!rows.isEmpty()) {
                int index = 0;
                for (Row r : rows) {
                    if (rows.size() > 1) {
                        builder.append(r.name).append(",");
                    }
                    if (total) {
                        builder.append(r.total);
                    }
                    if (fail) {
                        if (total) {
                            builder.append(",");
                        }
                        builder.append(r.failed);
                    }
                    if (index < rows.size() - 1) {
                        builder.append(System.getProperty("line.separator"));
                    }
                    index++;
                }
            }
            printer().println(String.valueOf(builder));
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

    private static class Row {
        String pid;
        String name;
        String age;
        long uptime;
        String total;
        String failed;
    }

}
