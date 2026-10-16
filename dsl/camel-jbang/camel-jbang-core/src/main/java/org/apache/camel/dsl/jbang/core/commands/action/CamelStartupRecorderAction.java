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
import java.util.Iterator;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "startup-recorder",
                     description = "Display startup recording", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel get startup-recorder",
                             "  camel get startup-recorder --json" })
public class CamelStartupRecorderAction extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = DurationTypeCompletionCandidates.class,
                        description = "Sort by duration, or type")
    String sort;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    private volatile long pid;

    public CamelStartupRecorderAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        CamelProcess process = findOne(name);
        this.pid = process.pid();

        JsonObject jo = executeAction(process, "startup-recorder", null);
        JsonArray arr = (JsonArray) jo.get("steps");
        for (int i = 0; arr != null && i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            Row row = new Row();
            row.id = o.getInteger("id");
            row.parentId = o.getInteger("parentId");
            row.level = o.getInteger("level");
            row.name = o.getString("name");
            row.type = o.getString("type");
            row.description = o.getString("description");
            row.duration = o.getLong("duration");
            rows.add(row);
        }

        // sort rows
        rows.sort(this::sortRow);

        if (jsonOutput) {
            JsonOutput.printList(printer(), rows.stream().map(r -> {
                JsonObject step = new JsonObject();
                step.put("id", r.id);
                step.put("parentId", r.parentId);
                step.put("level", r.level);
                step.put("name", r.name);
                step.put("type", r.type);
                step.put("description", r.description);
                step.put("duration", r.duration);
                return step;
            }).toList());
        } else if (!rows.isEmpty()) {
            int tw = terminalWidth();
            int fixedWidth = 10 + 15; // DURATION + TYPE (approx)
            int borderOverhead = TerminalWidthHelper.noBorderOverhead(3);
            int stepWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth, borderOverhead, 20, 80);

            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("DURATION").dataAlign(HorizontalAlign.RIGHT).with(this::getDuration),
                    new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(r -> r.type),
                    new Column().header("STEP (END)").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(stepWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(this::getStep))));
        }

        return 0;
    }

    private String getStep(Row r) {
        String pad = StringHelper.padString(r.level);
        String out = r.description;
        if (r.name != null && !r.name.equals("null")) {
            out = String.format("%s(%s)", r.description, r.name);
        }
        return pad + out;
    }

    private String getDuration(Row r) {
        if (r.duration > 0) {
            return Long.toString(r.duration);
        }
        return "";
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort != null ? sort : "";
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "duration":
                return Long.compare(o1.duration, o2.duration) * negate;
            case "type":
                return o1.type.compareToIgnoreCase(o2.type) * negate;
            default:
                return 0;
        }
    }

    private static class Row {
        int id;
        int parentId;
        int level;
        String name;
        String type;
        String description;
        long duration;
    }

    public static class DurationTypeCompletionCandidates implements Iterable<String> {

        public DurationTypeCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("duration", "type").iterator();
        }

    }

}
