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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "logger",
                     description = "List or change logging levels", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel cmd logger",
                             "  camel cmd logger --json",
                             "  camel cmd logger --logging-level=DEBUG --logger=org.apache.camel" })
public class LoggerAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--logging-level" }, completionCandidates = LoggingLevelCompletionCandidates.class,
                        description = "To change logging level (${COMPLETION-CANDIDATES})")
    String loggingLevel;

    @CommandLine.Option(names = { "--logger" },
                        description = "The logger name", defaultValue = "root")
    String logger;

    @CommandLine.Option(names = { "--json" }, description = "Output the logging levels in JSON Format")
    boolean jsonOutput;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    public LoggerAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (loggingLevel == null) {
            return callList();
        }
        if (name == null) {
            name = "*";
        }
        return callChangeLoggingLevel();
    }

    protected Integer callChangeLoggingLevel() throws Exception {
        return sendAction(findProcesses(name), "logger", root -> {
            root.put("command", "set-logging-level");
            root.put("logger-name", logger);
            root.put("logging-level", loggingLevel);
        });
    }

    protected Integer callList() {
        List<Row> rows = new ArrayList<>();

        for (CamelProcess process : findProcesses(name)) {
            JsonObject root = process.status();
            Row row = new Row();
            row.pid = Long.toString(process.pid());
            row.uptime = extractSince(ProcessHandle.of(process.pid()).orElse(null));
            row.ago = TimeUtils.printSince(row.uptime);
            row.name = process.name();
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

        // sort rows
        rows.sort(this::sortRow);

        if (jsonOutput) {
            JsonOutput.printList(printer(), rows.stream().map(r -> {
                JsonObject jo = new JsonObject();
                jo.put("pid", r.pid);
                jo.put("name", r.name);
                jo.put("age", r.ago);
                jo.put("logger", r.logger);
                jo.put("level", r.level);
                return jo;
            }).toList());
        } else if (!rows.isEmpty()) {
            int tw = terminalWidth();
            int fixedWidth = 10 + 10 + 10; // PID + AGE + LEVEL (approx)
            int borderOverhead = TerminalWidthHelper.noBorderOverhead(5);
            int nameWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth + 60, borderOverhead, 15, 40);
            int loggerWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth + nameWidth, borderOverhead, 20, 60);

            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(nameWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.ago),
                    new Column().header("LOGGER").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(loggerWidth, OverflowBehaviour.ELLIPSIS_RIGHT).with(r -> r.logger),
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
                throw new RuntimeException(e);
            }
        }
    }
}
