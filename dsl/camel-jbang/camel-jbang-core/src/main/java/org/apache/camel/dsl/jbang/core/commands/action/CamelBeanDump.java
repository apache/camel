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
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "bean", description = "List beans in a running Camel integration", sortOptions = false)
public class CamelBeanDump extends ActionBaseCommand {

    public static class NameTypeCompletionCandidates implements Iterable<String> {

        public NameTypeCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("name", "type").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = NameTypeCompletionCandidates.class,
                        description = "Sort by name or type", defaultValue = "name")
    String sort;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter beans names (use all to include all beans)", defaultValue = "all")
    String filter;

    @CommandLine.Option(names = { "--nulls" },
                        description = "Include null values", defaultValue = "true")
    boolean nulls;

    private volatile long pid;

    public CamelBeanDump(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
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
        root.put("action", "bean");
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            JsonArray arr = (JsonArray) jo.get("beans");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject jt = (JsonObject) arr.get(i);

                Row row = new Row();
                row.name = jt.getString("name");

                // filter
                boolean match
                        = "all".equals(filter) || row.name.contains(filter) || PatternHelper.matchPattern(row.name, filter);
                if (!match) {
                    continue;
                }

                row.type = jt.getString("type");
                row.properties = jt.getCollection("properties");
                rows.add(row);
            }
        } else {
            printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        // sort rows
        rows.sort(this::sortRow);
        singleTable(rows);

        // delete output file after use
        FileUtil.deleteFile(outputFile);

        return 0;
    }

    protected void singleTable(List<Row> rows) {
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(60, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(60, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.type))));
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            default:
                return 0;
        }
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        return getJsonObject(outputFile);
    }

    private static class Row {
        String name;
        String type;
        JsonArray properties;
    }

}
