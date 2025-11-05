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
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "groovy", description = "Groovy Sources used of Camel integrations", sortOptions = false,
         showDefaultValues = true)
public class ListGroovy extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = CamelProcessorStatus.PidNameCompletionCandidates.class,
                        description = "Sort by pid or name", defaultValue = "pid")
    String sort;

    public ListGroovy(CamelJBangMain main) {
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

                        JsonObject jo = (JsonObject) root.get("groovy");
                        if (jo != null) {
                            jo = (JsonObject) jo.get("compiler");
                        }
                        if (jo != null) {
                            row = row.copy();
                            row.compiledCounter = jo.getInteger("compiledCounter");
                            row.preloaddCounter = jo.getInteger("preloadedCounter");
                            row.classesSize = jo.getInteger("classesSize");
                            row.time = jo.getLong("compiledTime");
                            row.last = jo.getLong("lastCompilationTimestamp");
                            row.compiledClasses.clear();
                            JsonArray arr = jo.getCollection("classes");
                            for (int i = 0; arr != null && i < arr.size(); i++) {
                                row.compiledClasses.add(arr.getString(i));
                            }
                            rows.add(row);
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
                    new Column().header("PRELOAD").headerAlign(HorizontalAlign.CENTER).with(r -> "" + r.preloaddCounter),
                    new Column().header("COMPILE").headerAlign(HorizontalAlign.CENTER).with(r -> "" + r.compiledCounter),
                    new Column().header("TIME").headerAlign(HorizontalAlign.CENTER).with(this::getTime),
                    new Column().header("SINCE").headerAlign(HorizontalAlign.CENTER).with(this::getLast),
                    new Column().header("CLASSES").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(60, OverflowBehaviour.ELLIPSIS_LEFT).with(this::getClasses))));
        }

        return 0;
    }

    private String getTime(Row r) {
        return TimeUtils.printDuration(r.time, true);
    }

    private String getLast(Row r) {
        if (r.last > 0) {
            return TimeUtils.printSince(r.last);
        }
        return null;
    }

    private String getClasses(Row r) {
        if (r.compiledClasses.isEmpty()) {
            return null;
        }
        return String.join("\n", r.compiledClasses);
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
            default:
                return 0;
        }
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        int compiledCounter;
        int preloaddCounter;
        int classesSize;
        long time;
        long last;
        List<String> compiledClasses = new ArrayList<>();

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
