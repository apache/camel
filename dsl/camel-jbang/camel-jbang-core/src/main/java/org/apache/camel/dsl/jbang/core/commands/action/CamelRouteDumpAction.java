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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.support.LoggerHelper.stripSourceLocationLineNumber;

@Command(name = "route-dump", description = "Dump Camel route in XML, YAML, or Java DSL format", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel cmd route-dump",
                 "  camel cmd route-dump --format=yaml",
                 "  camel cmd route-dump --json" })
public class CamelRouteDumpAction extends ActionBaseCommand {

    public static class NameIdCompletionCandidates implements Iterable<String> {

        public NameIdCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("name", "id").iterator();
        }

    }

    public static class FormatCompletionCandidates implements Iterable<String> {

        public FormatCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("yaml", "xml", "java").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--format" }, completionCandidates = FormatCompletionCandidates.class,
                        description = "Output format (xml, yaml, or java)", defaultValue = "yaml")
    String format;

    @CommandLine.Option(names = { "--raw" },
                        description = "To output raw without metadata")
    boolean raw;

    @CommandLine.Option(names = { "--uri-as-parameters" },
                        description = "Whether to expand URIs into separated key/value parameters (only in use for YAML format)",
                        defaultValue = "true")
    boolean uriAsParameters;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter route by filename or route id (multiple names can be separated by comma)")
    String filter;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = NameIdCompletionCandidates.class,
                        description = "Sort route by name or id", defaultValue = "name")
    String sort;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    private volatile long pid;

    public CamelRouteDumpAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        CamelProcess process = findOne(name);
        this.pid = process.pid();

        JsonObject jo = executeAction(process, "route-dump", root -> {
            root.put("filter", "*");
            root.put("format", format);
            root.put("uriAsParameters", uriAsParameters);
        });
        JsonArray arr = (JsonArray) jo.get("routes");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            Row row = new Row();
            row.location = extractSourceName(o.getString("source"));
            row.routeId = o.getString("routeId");
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
                if (filter != null && !matchesFilter(filter, row.location, row.routeId)) {
                    add = false;
                }
                if (add) {
                    rows.add(row);
                }
            }
        }

        // sort rows
        rows.sort(this::sortRow);

        if (rows.isEmpty() && filter != null) {
            throw CliException.notFound("No route matching: " + filter);
        }
        if (jsonOutput) {
            JsonOutput.printList(printer(), rows.stream().map(r -> {
                JsonObject route = new JsonObject();
                route.put("routeId", r.routeId);
                route.put("location", r.location);
                route.put("format", format);
                route.put("code", r.code.stream().map(c -> {
                    JsonObject line = new JsonObject();
                    line.put("line", c.line);
                    line.put("code", c.code);
                    return line;
                }).toList());
                return route;
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
                return o1.location.compareToIgnoreCase(o2.location) * negate;
            case "id":
                return o1.routeId.compareToIgnoreCase(o2.routeId) * negate;
            default:
                return 0;
        }
    }

    protected void printSource(List<Row> rows) {
        for (Row row : rows) {
            printer().println();
            if (!raw) {
                printer().printf("Source: %s%n", row.location);
                printer().println("--------------------------------------------------------------------------------");
            }
            for (int i = 0; i < row.code.size(); i++) {
                Code code = row.code.get(i);
                String c = Jsoner.unescape(code.code);
                if (raw) {
                    printer().printf("%s%n", c);
                } else {
                    if (code.line != -1) {
                        printer().printf("%4d: %s%n", code.line, c);
                    } else {
                        printer().printf("      %s%n", c);
                    }
                }
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
        return loc != null ? loc : "";
    }

    private static class Row {
        String location;
        String routeId;
        List<Code> code = new ArrayList<>();

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            Row row = (Row) o;

            if (!Objects.equals(location, row.location))
                return false;
            return routeId.equals(row.routeId);
        }

        @Override
        public int hashCode() {
            return routeId.hashCode();
        }
    }

    private static class Code {
        int line;
        String code;
    }

}
