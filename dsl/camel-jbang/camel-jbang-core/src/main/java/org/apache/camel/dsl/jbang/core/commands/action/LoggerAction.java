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
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CopyableRow;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "logger", description = "List or change logging levels", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel cmd logger",
                           "  camel cmd logger --json",
                           "  camel cmd logger --logging-level=DEBUG --logger=org.apache.camel" })
public class LoggerAction extends ActionBaseCommand {

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(r -> r.pid, r -> r.name, r -> r.uptime);

    @Argument(description = "Name or pid of running Camel integration")
    String name = "*";

    @Option(name = "sort", description = "Sort by pid, name or age", defaultValue = "pid",
            completer = PidNameAgeCompletionCandidates.class)
    String sort;

    // the levels of the logging, as the completion offers them, and OFF
    private static final List<String> LEVELS = List.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE", "OFF");

    @Option(name = "logging-level", description = "To change logging level (${COMPLETION-CANDIDATES})",
            completer = LoggingLevelCompletionCandidates.class)
    String loggingLevel;

    @Option(name = "logger", description = "The logger name", defaultValue = "root")
    String logger;

    @Option(name = "json", hasValue = false, description = "Output the logging levels in JSON Format")
    boolean jsonOutput;

    @Mixin
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
        // an unknown level was set as ERROR, with exit code 0 (CAMEL-26929)
        if (LEVELS.stream().noneMatch(l -> l.equalsIgnoreCase(loggingLevel))) {
            throw CliException.usage("Unknown logging level: " + loggingLevel + " (" + String.join(", ", LEVELS) + ")");
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
        rows.sort(ORDER.comparator(sort));

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

    private static class Row extends CopyableRow<Row> {
        String pid;
        String name;
        String ago;
        long uptime;
        String logger;
        String level;
    }
}
