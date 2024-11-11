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

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "properties", description = "List configuration properties", sortOptions = false)
public class ListProperties extends ProcessWatchCommand {

    public static class PidNameKeyCompletionCandidates implements Iterable<String> {

        public PidNameKeyCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name", "key").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameKeyCompletionCandidates.class,
                        description = "Sort by pid, name or key", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--startup" }, description = "List only startup configuration")
    boolean startup;

    @CommandLine.Option(names = { "--verbose" }, description = "Whether to include more details")
    boolean verbose;

    @CommandLine.Option(names = { "--internal" }, description = "Whether to include internal configuration")
    boolean internal;

    @CommandLine.Option(names = { "--mask" },
                        description = "Whether to mask configuration values to avoid printing sensitive information such as password or access keys",
                        defaultValue = "true")
    boolean mask = true;

    public ListProperties(CamelJBangMain main) {
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

                        JsonArray arr;
                        if (startup) {
                            JsonObject jv = (JsonObject) root.get("main-configuration");
                            arr = jv.getCollectionOrDefault("configurations", null);
                        } else {
                            JsonObject jv = (JsonObject) root.get("properties");
                            arr = jv.getCollectionOrDefault("properties", null);
                        }
                        for (int i = 0; arr != null && i < arr.size(); i++) {
                            row = row.copy();
                            JsonObject jo = (JsonObject) arr.get(i);
                            row.key = jo.getString("key");
                            String value = jo.getString("value");
                            if (mask && SensitiveUtils.containsSensitive(row.key)) {
                                value = "xxxxxx";
                            }
                            row.value = value;
                            value = jo.getString("originalValue");
                            if (mask && SensitiveUtils.containsSensitive(row.key)) {
                                value = "xxxxxx";
                            }
                            row.originalValue = value;
                            row.internalLoc = jo.getBooleanOrDefault("internal", false);
                            row.source = jo.getString("source");
                            row.loc = sanitizeLocation(jo.getString("location"));
                            boolean accept = internal || !row.internalLoc;
                            if (accept) {
                                rows.add(row);
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
                    new Column().header("LOCATION").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(r -> r.loc),
                    new Column().header("KEY").dataAlign(HorizontalAlign.LEFT).maxWidth(50, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.key),
                    new Column().header("VALUE").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(r -> "" + r.value),
                    new Column().header("FUNCTION").visible(verbose).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(50, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(this::getFunction),
                    new Column().header("ORIGINAL VALUE").visible(verbose).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(r -> "" + r.originalValue))));
        }

        return 0;
    }

    protected String getFunction(Row r) {
        return StringHelper.before(r.source, ":", r.source);
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
            case "key":
                return o1.key.compareToIgnoreCase(o2.key) * negate;
            default:
                return 0;
        }
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        String key;
        Object value;
        Object originalValue;
        String source;
        String loc;
        boolean internalLoc;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }

    private static String sanitizeLocation(String loc) {
        if (loc == null) {
            return "";
        }
        switch (loc) {
            case "initial", "override" -> loc = "camel-main";
            case "SYS" -> loc = "JVM System Property";
            case "ENV", "env" -> loc = "OS Environment Variable";
            case "arguments", "CLI" -> loc = "Command Line";
        }
        return loc;
    }

}
