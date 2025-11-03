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
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "producer", description = "Get status of Camel producers", sortOptions = false, showDefaultValues = true)
public class ListProducer extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter producers by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter producers by URI")
    String filter;

    @CommandLine.Option(names = { "--short-uri" },
                        description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @CommandLine.Option(names = { "--wide-uri" },
                        description = "List endpoint URI in full details")
    boolean wideUri;

    public ListProducer(CamelJBangMain main) {
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
                        JsonObject jo = (JsonObject) root.get("producers");
                        if (context != null && jo != null) {
                            JsonArray array = (JsonArray) jo.get("producers");
                            for (int i = 0; i < array.size(); i++) {
                                JsonObject o = (JsonObject) array.get(i);
                                Row row = new Row();
                                row.name = context.getString("name");
                                if ("CamelJBang".equals(row.name)) {
                                    row.name = ProcessHelper.extractName(root, ph);
                                }
                                row.pid = Long.toString(ph.pid());
                                row.uri = o.getString("uri");
                                row.id = o.getString("routeId");
                                row.state = o.getString("state");
                                row.className = o.getString("class");
                                row.singleton = o.getBoolean("singleton");
                                row.remote = o.getBoolean("remote");
                                row.uptime = extractSince(ph);
                                row.age = TimeUtils.printSince(row.uptime);
                                boolean add = true;
                                if (filter != null) {
                                    String f = filter;
                                    boolean negate = filter.startsWith("-");
                                    if (negate) {
                                        f = f.substring(1);
                                    }
                                    boolean match = PatternHelper.matchPattern(row.uri, f);
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
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.id),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER).with(this::getState),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getType),
                new Column().header("URI").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(90, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getUri),
                new Column().header("URI").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(140, OverflowBehaviour.NEWLINE)
                        .with(this::getUri))));
    }

    private String getUri(Row r) {
        String u = r.uri;
        if (shortUri) {
            int pos = u.indexOf('?');
            if (pos > 0) {
                u = u.substring(0, pos);
            }
        }
        return u;
    }

    private String getState(Row r) {
        return r.state;
    }

    private String getType(Row r) {
        String s = r.className;
        if (s.endsWith("Producer")) {
            s = s.substring(0, s.length() - 8);
        }
        return s;
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
        String id;
        String uri;
        String state;
        String className;
        boolean singleton;
        boolean remote;
    }

}
