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

import java.nio.file.Files;
import java.nio.file.Path;
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

@Command(name = "activity", description = "Get recent completed exchange activity",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel get activity",
                 "  camel get activity --filter=route1",
                 "  camel get activity --watch" })
public class ListActivity extends ProcessWatchCommand {

    public static class PidNameAgeElapsedSinceCandidates implements Iterable<String> {

        public PidNameAgeElapsedSinceCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name", "age", "elapsed", "since").iterator();
        }
    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeElapsedSinceCandidates.class,
                        description = "Sort by pid, name, age, elapsed, or since", defaultValue = "since")
    String sort;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter activity by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter activity by route ID")
    String filter;

    @CommandLine.Option(names = { "--short-uri" },
                        description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @CommandLine.Option(names = { "--wide-uri" },
                        description = "List endpoint URI in full details")
    boolean wideUri;

    public ListActivity(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

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
                        if (context != null) {
                            String integrationName = context.getString("name");
                            if ("CamelJBang".equals(integrationName)) {
                                integrationName = ProcessHelper.extractName(root, ph);
                            }
                            long uptime = extractSince(ph);
                            String age = TimeUtils.printSince(uptime);

                            JsonObject activity = loadActivityFile(ph.pid());
                            if (activity != null) {
                                JsonArray array = (JsonArray) activity.get("activity");
                                if (array != null) {
                                    for (int i = 0; i < array.size(); i++) {
                                        JsonObject o = (JsonObject) array.get(i);
                                        Row row = new Row();
                                        row.pid = Long.toString(ph.pid());
                                        row.name = integrationName;
                                        row.uptime = uptime;
                                        row.age = age;
                                        row.exchangeId = o.getString("exchangeId");
                                        row.routeId = o.getString("routeId");
                                        row.failed = o.getBooleanOrDefault("failed", false);
                                        row.status = row.failed ? "FAILED" : "OK";
                                        row.elapsed = o.getLongOrDefault("elapsed", 0);
                                        row.timestamp = o.getLongOrDefault("timestamp", 0);
                                        row.endpointUri = o.getString("endpointUri");
                                        if (row.timestamp > 0) {
                                            long ago = System.currentTimeMillis() - row.timestamp;
                                            row.since = TimeUtils.printSince(ago);
                                        } else {
                                            row.since = "";
                                        }

                                        boolean add = true;
                                        if (filter != null) {
                                            String f = filter;
                                            boolean negate = filter.startsWith("-");
                                            if (negate) {
                                                f = f.substring(1);
                                            }
                                            boolean match = PatternHelper.matchPattern(row.routeId, f);
                                            if (negate) {
                                                match = !match;
                                            }
                                            if (!match) {
                                                add = false;
                                            }
                                        }
                                        if (add) {
                                            rows.add(row);
                                        }
                                    }
                                }
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (limit > 0 && rows.size() > limit) {
            rows.subList(limit, rows.size()).clear();
        }

        if (!rows.isEmpty()) {
            printTable(rows);
        }

        return 0;
    }

    private JsonObject loadActivityFile(long pid) {
        try {
            Path f = getActivityFile(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                if (text != null && !text.isBlank()) {
                    return (JsonObject) Jsoner.deserialize(text);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    protected void printTable(List<Row> rows) {
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = new JsonObject();
                jo.put("pid", r.pid);
                jo.put("name", r.name);
                jo.put("age", r.age);
                jo.put("exchangeId", r.exchangeId);
                jo.put("routeId", r.routeId);
                jo.put("status", r.status);
                jo.put("elapsed", r.elapsed);
                jo.put("since", r.since);
                if (r.endpointUri != null) {
                    jo.put("endpointUri", r.endpointUri);
                }
                return jo;
            }).collect(Collectors.toList())));
            return;
        }

        // Flexible column: ENDPOINT (40/140)
        // Fixed columns: PID(8)+NAME(30)+AGE(8)+EXCHANGE(40)+ROUTE(25)+STATUS(6)+ELAPSED(8)+SINCE(8) ~= 133
        int fixedW = 133;
        int numCols = 9;
        int tw = terminalWidth();
        int uriW = TerminalWidthHelper.flexWidth(tw, fixedW, TerminalWidthHelper.noBorderOverhead(numCols), 20, 40);
        int uriWideW = TerminalWidthHelper.flexWidth(tw, fixedW, TerminalWidthHelper.noBorderOverhead(numCols), 20, 140);
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("EXCHANGE").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.exchangeId),
                new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.routeId),
                new Column().header("STATUS").dataAlign(HorizontalAlign.CENTER)
                        .with(r -> r.status),
                new Column().header("ELAPSED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.elapsed + "ms"),
                new Column().header("SINCE").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.since),
                new Column().header("ENDPOINT").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(uriW, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getUri),
                new Column().header("ENDPOINT").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(uriWideW, OverflowBehaviour.NEWLINE)
                        .with(this::getUri))));
    }

    private String getUri(Row r) {
        String u = r.endpointUri;
        if (u == null) {
            return "";
        }
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
            case "elapsed":
                return Long.compare(o1.elapsed, o2.elapsed) * negate;
            case "since":
                return Long.compare(o2.timestamp, o1.timestamp) * negate;
            default:
                return 0;
        }
    }

    static class Row {
        String pid;
        String name;
        long uptime;
        String age;
        String exchangeId;
        String routeId;
        boolean failed;
        String status;
        long elapsed;
        long timestamp;
        String since;
        String endpointUri;
    }

}
