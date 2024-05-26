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
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "address", description = "Get usage of Camel service addresses (hosted and external)", sortOptions = false)
public class ListAddress extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or total", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter addresses by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter addresses")
    String filter;

    @CommandLine.Option(names = { "--filter-direction" },
                        description = "Filter by direction (in or out)")
    String filterDirection;

    @CommandLine.Option(names = { "--wide-uri" },
                        description = "List endpoint URI in full details")
    boolean wideUri;

    // TODO: Hosted is on consumer

    public ListAddress(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        // make it easier to filter
        if (filter != null && !filter.endsWith("*")) {
            filter += "*";
        }

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        JsonObject context = (JsonObject) root.get("context");
                        JsonObject jo = (JsonObject) root.get("endpoints");
                        if (context != null && jo != null) {
                            JsonArray array = (JsonArray) jo.get("endpoints");
                            for (int i = 0; i < array.size(); i++) {
                                JsonObject o = (JsonObject) array.get(i);
                                Row row = new Row();
                                row.name = context.getString("name");
                                if ("CamelJBang".equals(row.name)) {
                                    row.name = ProcessHelper.extractName(root, ph);
                                }
                                row.pid = Long.toString(ph.pid());
                                row.endpoint = o.getString("uri");
                                JsonObject ro = (JsonObject) o.get("location");
                                if (ro != null) {
                                    row.address = ro;
                                }
                                row.direction = o.getString("direction");
                                row.total = o.getString("hits");
                                row.uptime = extractSince(ph);
                                row.age = TimeUtils.printSince(row.uptime);
                                boolean add = true;
                                if (filterDirection != null && !filterDirection.equals(row.direction)) {
                                    add = false;
                                }
                                if (row.address == null) {
                                    add = false;
                                }
                                if (filter != null) {
                                    String f = filter;
                                    boolean negate = filter.startsWith("-");
                                    if (negate) {
                                        f = f.substring(1);
                                    }
                                    boolean match = PatternHelper.matchPattern(row.endpoint, f);
                                    if (negate) {
                                        match = !match;
                                    }
                                    if (!match) {
                                        add = false;
                                    }
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
            printTable(rows);
        }

        return 0;
    }

    protected void printTable(List<Row> rows) {
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("DIR").with(this::getDirection),
                new Column().header("TOTAL").with(r -> r.total),
                // new Column().header("HOSTED").dataAlign(HorizontalAlign.CENTER).with(this::getHosted),
                new Column().header("URI").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(90, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getUri),
                new Column().header("URI").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(140, OverflowBehaviour.NEWLINE)
                        .with(this::getUri),
                new Column().header("ADDRESS").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(90, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getAddress))));
    }

    private String getUri(Row r) {
        String u = r.endpoint;
        if (!wideUri) {
            int pos = u.indexOf('?');
            if (pos > 0) {
                u = u.substring(0, pos);
            }
        }
        return u;
    }

    private String getDirection(Row r) {
        String dir = r.direction;
        if (dir == null || dir.isEmpty()) {
            // hosted is always in
            dir = "x".equals(getHosted(r)) ? "in" : "out";
        }
        return dir;
    }

    private String getAddress(Row r) {
        String a = "";
        if (r.address != null) {
            a = r.address.getString("address");
            if (r.address.size() > 1) {
                StringJoiner sj = new StringJoiner(" ");
                r.address.forEach((k, v) -> {
                    if (!"address".equals(k) && !"hosted".equals(k) && !"remote".equals(k)) {
                        sj.add(k + "=" + v);
                    }
                });
                if (sj.length() > 0) {
                    a = a + " (" + sj + ")";
                }
            }
        }
        return a;
    }

    private String getHosted(Row r) {
        boolean hosted = false;
        if (r.address != null) {
            hosted = r.address.getBooleanOrDefault("hosted", false);
        }
        return hosted ? "x" : "";
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
        String endpoint;
        String direction;
        String total;
        JsonObject address;
    }

}
