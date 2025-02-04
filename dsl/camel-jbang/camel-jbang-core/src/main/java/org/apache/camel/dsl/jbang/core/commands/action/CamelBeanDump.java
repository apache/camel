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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "bean", description = "List beans in a running Camel integration", sortOptions = false,
         showDefaultValues = true)
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

    @CommandLine.Option(names = { "--properties" },
                        description = "Show bean properties", defaultValue = "true")
    boolean properties;

    @CommandLine.Option(names = { "--nulls" },
                        description = "Include null values", defaultValue = "true")
    boolean nulls;

    @CommandLine.Option(names = { "--internal" },
                        description = "Include internal Camel beans", defaultValue = "false")
    boolean internal;

    @CommandLine.Option(names = { "--dsl" },
                        description = "Include only beans from YAML or XML DSL", defaultValue = "false")
    boolean dsl;

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
        if (!"all".equals(filter)) {
            root.put("filter", filter);
        }
        root.put("properties", properties);
        root.put("nulls", nulls);
        root.put("internal", internal);
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);

        if (jo != null) {
            JsonObject beans;
            if (dsl) {
                beans = (JsonObject) jo.get("bean-models");
            } else {
                beans = (JsonObject) jo.get("beans");
            }
            for (String name : beans.keySet()) {
                JsonObject jt = (JsonObject) beans.get(name);
                Row row = new Row();
                row.name = jt.getString("name");
                row.type = jt.getString("type");
                JsonArray arr = jt.getCollection("properties");
                JsonArray arr2 = jt.getCollection("modelProperties");
                if (arr != null) {
                    row.properties = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        PropertyRow pr = new PropertyRow();
                        row.properties.add(pr);
                        JsonObject p = (JsonObject) arr.get(i);
                        pr.name = p.getString("name");
                        pr.type = p.getString("type");
                        pr.value = p.get("value");
                        if (arr2 != null) {
                            JsonObject p2 = (JsonObject) arr2.get(i);
                            if (p2 != null) {
                                pr.configValue = p2.getString("value");
                            }
                        }
                    }
                }
                rows.add(row);
            }
        } else {
            printer().printErr("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        // sort rows
        rows.sort(this::sortRow);
        if (properties) {
            for (Row row : rows) {
                String line = "BEAN: " + row.name + " (" + row.type + "):";
                printer().println(line);
                printer().println("-".repeat(line.length()));
                if (row.properties != null) {
                    propertiesTable(row.properties);
                }
                printer().println();
            }
        } else {
            singleTable(rows);
        }

        // delete output file after use
        FileUtil.deleteFile(outputFile);

        return 0;
    }

    protected void singleTable(List<Row> rows) {
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(60, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(100, OverflowBehaviour.CLIP_LEFT)
                        .with(r -> r.type))));
    }

    protected void propertiesTable(List<PropertyRow> rows) {
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PROPERTY").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.type),
                new Column().header("CONFIGURATION").visible(dsl).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(80, OverflowBehaviour.NEWLINE)
                        .with(r -> r.configValue),
                new Column().header("VALUE").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                        .with(this::getValue))));
    }

    private String getValue(PropertyRow r) {
        if (r.value != null) {
            return r.value.toString();
        }
        return "null";
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
        List<PropertyRow> properties;
    }

    private static class PropertyRow {
        String name;
        String type;
        Object value;
        String configValue;
    }

}
