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
import java.util.Iterator;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "startup-recorder",
                     description = "Display startup recording", sortOptions = false)
public class CamelStartupRecorderAction extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = DurationTypeCompletionCandidates.class,
                        description = "Sort by duration, or type")
    String sort;

    private volatile long pid;

    public CamelStartupRecorderAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        this.pid = pids.get(0);

        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "startup-recorder");
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
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
        }

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("DURATION").dataAlign(HorizontalAlign.RIGHT).with(this::getDuration),
                    new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(r -> r.type),
                    new Column().header("STEP (END)").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(this::getStep))));
        }

        // delete output file after use
        FileUtil.deleteFile(outputFile);

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
            return "" + r.duration;
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

    protected JsonObject waitForOutputFile(File outputFile) {
        return getJsonObject(outputFile);
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
