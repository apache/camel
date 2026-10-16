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
import java.util.List;
import java.util.Objects;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.support.LoggerHelper.stripSourceLocationLineNumber;

@Command(name = "source", description = "Display Camel route source code", sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel get source",
                 "  camel get source --filter=myRoute",
                 "  camel get source --json" })
public class CamelSourceAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter source by filename (multiple names can be separated by comma)")
    String filter;

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort source by name", defaultValue = "name")
    String sort;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    private volatile long pid;

    public CamelSourceAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        CamelProcess process = findOne(name);
        this.pid = process.pid();

        JsonObject jo = executeAction(process, "source", root -> {
            root.put("filter", "*");
        });
        JsonArray arr = jo.get("routes") instanceof JsonArray routes ? routes : new JsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            Row row = new Row();
            row.location = extractSourceName(o.getString("source"));
            // if there are 2+ routes in the same source then we would have duplicates
            if (!rows.contains(row)) {
                List<JsonObject> lines = o.getCollection("code");
                if (lines != null) {
                    for (JsonObject line : lines) {
                        Code code = new Code();
                        code.line = line.getInteger("line");
                        code.code = line.getString("code");
                        row.code.add(code);
                    }
                }
                boolean add = true;
                if (filter != null && !matchesFilter(filter, row.location)) {
                    add = false;
                }
                if (add) {
                    rows.add(row);
                }
            }
        }

        // sort rows
        rows.sort(this::sortRow);

        if (jsonOutput) {
            JsonOutput.printList(printer(), rows.stream().map(r -> {
                JsonObject jo2 = new JsonObject();
                jo2.put("location", r.location);
                jo2.put("code", r.code.stream().map(c -> {
                    JsonObject line = new JsonObject();
                    line.put("line", c.line);
                    line.put("code", c.code);
                    return line;
                }).toList());
                return jo2;
            }).toList());
        } else if (!rows.isEmpty()) {
            printSource(rows);
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
            case "name":
                // a route without a source location sorts first
                return (o1.location != null ? o1.location : "").compareToIgnoreCase(o2.location != null ? o2.location : "")
                       * negate;
            default:
                return 0;
        }
    }

    protected void printSource(List<Row> rows) {
        for (Row row : rows) {
            printer().println();
            printer().printf("Source: %s%n", row.location);
            printer().println("--------------------------------------------------------------------------------");
            for (int i = 0; i < row.code.size(); i++) {
                Code code = row.code.get(i);
                String c = Jsoner.unescape(code.code);
                printer().printf("%4d: %s%n", code.line, c);
            }
            printer().println();
        }
    }

    public static String extractSourceName(String loc) {
        loc = stripSourceLocationLineNumber(loc);
        if (loc != null) {
            if (loc.contains(":")) {
                // strip prefix
                loc = loc.substring(loc.indexOf(':') + 1);
                // file based such as xml and yaml
                loc = FileUtil.stripPath(loc);
            }
        }
        return loc;
    }

    private static class Row {
        String location;
        List<Code> code = new ArrayList<>();

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Row row = (Row) o;

            return Objects.equals(location, row.location);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(location);
        }
    }

    private static class Code {
        int line;
        String code;
    }

}
