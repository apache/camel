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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "logger",
                     description = "List or change logging levels", sortOptions = false)
public class LoggerAction extends ActionBaseCommand {

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name;

    @CommandLine.Option(names = { "--all" },
                        description = "To select all running Camel integrations")
    boolean all;

    @CommandLine.Option(names = { "--logging-level" }, completionCandidates = LoggingLevelCompletionCandidates.class,
                        description = "To change logging level")
    String loggingLevel;

    @CommandLine.Option(names = { "--logger" },
                        description = "The logger name", defaultValue = "root")
    String logger;

    public LoggerAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (loggingLevel == null) {
            return callList();
        }
        if (!all && name == null) {
            return 0;
        } else if (all) {
            name = "*";
        }
        return callChangeLoggingLevel();
    }

    protected Integer callChangeLoggingLevel() throws Exception {
        List<Long> pids = findPids(name);

        for (long pid : pids) {
            JsonObject root = new JsonObject();
            root.put("action", "logger");
            File f = getActionFile(Long.toString(pid));
            root.put("command", "set-logging-level");
            root.put("logger-name", logger);
            root.put("logging-level", loggingLevel);
            IOHelper.writeText(root.toJson(), f);
        }

        return 0;
    }

    protected Integer callList() {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids("*");
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        Row row = new Row();
                        row.pid = Long.toString(ph.pid());
                        row.uptime = extractSince(ph);
                        row.ago = TimeUtils.printSince(row.uptime);
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        JsonObject jo = (JsonObject) root.get("logger");
                        if (jo != null) {
                            Map<String, String> levels = jo.getMap("levels");
                            levels.forEach((k, v) -> {
                                // create a copy for 2+ levels
                                Row cp = row.copy();
                                cp.logger = k;
                                cp.level = v;
                                rows.add(cp);
                            });
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.ago),
                    new Column().header("LOGGER").dataAlign(HorizontalAlign.LEFT).with(r -> r.logger),
                    new Column().header("LEVEL").dataAlign(HorizontalAlign.RIGHT).with(r -> r.level))));
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

    private static class Row implements Cloneable {
        String pid;
        String name;
        String ago;
        long uptime;
        String logger;
        String level;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }
}
