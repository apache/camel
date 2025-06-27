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
import java.util.StringJoiner;

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

@Command(name = "rest",
         description = "Get REST services of Camel integrations", sortOptions = false, showDefaultValues = true)
public class ListRest extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--verbose" },
                        description = "Show more details")
    boolean verbose;

    public ListRest(CamelJBangMain main) {
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

                        JsonObject jo = (JsonObject) root.get("rests");
                        if (jo != null) {
                            JsonArray arr = (JsonArray) jo.get("rests");
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    row = row.copy();
                                    jo = (JsonObject) arr.get(i);
                                    row.url = jo.getString("url");
                                    row.method = jo.getString("method").toUpperCase(Locale.ROOT);
                                    row.consumes = jo.getString("consumes");
                                    row.produces = jo.getString("produces");
                                    row.description = jo.getString("description");
                                    row.contractFirst = jo.getBooleanOrDefault("contractFirst", false);
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
                    new Column().header("URL").dataAlign(HorizontalAlign.LEFT).with(r -> r.url),
                    new Column().header("METHOD").dataAlign(HorizontalAlign.LEFT).with(r -> r.method),
                    new Column().header("FIRST").visible(verbose).dataAlign(HorizontalAlign.LEFT).with(this::getKind),
                    new Column().header("DESCRIPTION").visible(verbose).maxWidth(40, OverflowBehaviour.NEWLINE)
                            .dataAlign(HorizontalAlign.LEFT).with(r -> r.description),
                    new Column().header("CONTENT-TYPE").dataAlign(HorizontalAlign.LEFT).with(this::getContent))));
        }

        return 0;
    }

    private String getKind(Row r) {
        return r.contractFirst ? "Contract" : "Code";
    }

    private String getContent(Row r) {
        StringJoiner sj = new StringJoiner("   ");
        if (r.consumes != null || r.produces != null) {
            if (r.consumes != null) {
                sj.add("accept: " + r.consumes);
            }
            if (r.produces != null) {
                sj.add("produces: " + r.produces);
            }
        }
        if (sj.length() > 0) {
            return sj.toString();
        }
        return "";
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

    private static class Row implements Cloneable {
        String pid;
        String name;
        String age;
        long uptime;
        String url;
        String method;
        String consumes;
        String produces;
        String description;
        boolean contractFirst;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }

}
