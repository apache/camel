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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "endpoint", description = "Get usage of Camel endpoints", sortOptions = false, showDefaultValues = true)
public class ListEndpoint extends ProcessWatchCommand {

    public static class PidNameAgeTotalCompletionCandidates implements Iterable<String> {

        public PidNameAgeTotalCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name", "age", "total").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeTotalCompletionCandidates.class,
                        description = "Sort by pid, name, age or total", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter endpoints by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter endpoints by URI")
    String filter;

    @CommandLine.Option(names = { "--filter-direction" },
                        description = "Filter by direction (in or out)")
    String filterDirection;

    @CommandLine.Option(names = { "--filter-total" },
                        description = "Filter endpoints that must be higher than the given usage")
    long filterTotal;

    @CommandLine.Option(names = { "--short-uri" },
                        description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @CommandLine.Option(names = { "--wide-uri" },
                        description = "List endpoint URI in full details")
    boolean wideUri;

    public ListEndpoint(CamelJBangMain main) {
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
                                row.stub = o.getBooleanOrDefault("stub", false);
                                row.remote = o.getBooleanOrDefault("remote", true);
                                row.direction = o.getString("direction");
                                row.total = o.getString("hits");
                                row.uptime = extractSince(ph);
                                row.age = TimeUtils.printSince(row.uptime);
                                boolean add = true;
                                if (filterTotal > 0 && (row.total == null || Long.parseLong(row.total) < filterTotal)) {
                                    add = false;
                                }
                                if (filterDirection != null && !filterDirection.equals(row.direction)) {
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
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = new JsonObject();
                jo.put("pid", r.pid);
                jo.put("name", r.name);
                jo.put("age", r.age);
                jo.put("direction", r.direction);
                jo.put("total", r.total);
                jo.put("stub", r.stub);
                jo.put("remote", r.remote);
                jo.put("uri", r.endpoint);
                return jo;
            }).collect(Collectors.toList())));
            return;
        }
        // Flexible column: URI (90/140)
        // Fixed columns: PID(8)+NAME(30)+AGE(8)+DIR(3)+TOTAL(5)+STUB(4)+REMOTE(6) ~= 64
        int tw = terminalWidth();
        int uriW = TerminalWidthHelper.flexWidth(tw, 64, TerminalWidthHelper.noBorderOverhead(9), 20, 90);
        int uriWideW = TerminalWidthHelper.flexWidth(tw, 64, TerminalWidthHelper.noBorderOverhead(9), 20, 140);
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("DIR").with(r -> r.direction),
                new Column().header("TOTAL").with(r -> r.total),
                new Column().header("STUB").dataAlign(HorizontalAlign.CENTER).with(r -> r.stub ? "x" : ""),
                new Column().header("REMOTE").dataAlign(HorizontalAlign.CENTER).with(r -> r.remote ? "x" : ""),
                new Column().header("URI").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(uriW, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getUri),
                new Column().header("URI").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(uriWideW, OverflowBehaviour.NEWLINE)
                        .with(this::getUri))));
    }

    private String getUri(Row r) {
        String u = r.endpoint;
        if (shortUri) {
            int pos = u.indexOf('?');
            if (pos > 0) {
                u = u.substring(0, pos);
            }
        }
        return u;
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
            case "total":
                return Long.compare(Long.parseLong(o1.total), Long.parseLong(o2.total)) * negate;
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
        boolean stub;
        boolean remote;
    }

}
