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
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "source", description = "List top processors (source) in a running Camel integration", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel top source",
                 "  camel top source --limit=5",
                 "  camel top source --json" })
public class CamelSourceTop extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter processors by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter-mean" },
                        description = "Filter processors that must be slower than the given time (ms)")
    long mean;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    private volatile long pid;

    public CamelSourceTop(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        CamelProcess process = findOne(name);
        this.pid = process.pid();

        JsonObject jo = executeAction(process, "top-processors", null);
        JsonArray arr = (JsonArray) jo.get("processors");
        if (arr == null) {
            throw CliException.failure("Source top data not available from the running integration");
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            Row row = new Row();
            row.id = o.getString("processorId");
            row.routeId = o.getString("routeId");
            row.location = o.getString("location");
            Map<String, ?> stats = o.getMap("statistics");
            if (stats != null) {
                row.total = stats.get("exchangesTotal").toString();
                row.mean = stats.get("meanProcessingTime").toString();
                if ("-1".equals(row.mean)) {
                    row.mean = null;
                }
                row.max = stats.get("maxProcessingTime").toString();
                row.min = stats.get("minProcessingTime").toString();
                Object last = stats.get("lastProcessingTime");
                if (last != null) {
                    row.last = last.toString();
                    if ("-1".equals(row.last)) {
                        row.last = null;
                    }
                } else {
                    row.last = null;
                }
            }
            List<JsonObject> lines = o.getCollection("code");
            if (lines != null) {
                for (JsonObject line : lines) {
                    Code code = new Code();
                    code.line = line.getInteger("line");
                    code.code = line.getString("code");
                    if (line.getBooleanOrDefault("match", false)) {
                        code.match = true;
                    }
                    row.code.add(code);
                }
            }

            boolean add = true;
            if (mean > 0 && (row.mean == null || Long.parseLong(row.mean) < mean)) {
                add = false;
            }
            if (limit > 0 && rows.size() >= limit) {
                add = false;
            }
            if (add) {
                rows.add(row);
            }
        }

        // sort rows
        rows.sort(this::sortRow);

        if (jsonOutput) {
            JsonOutput.printList(printer(), rows.stream().map(r -> {
                JsonObject row = new JsonObject();
                row.put("id", r.id);
                row.put("routeId", r.routeId);
                row.put("location", r.location);
                row.put("total", r.total);
                row.put("mean", r.mean);
                row.put("min", r.min);
                row.put("max", r.max);
                row.put("last", r.last);
                row.put("code", r.code.stream().map(c -> {
                    JsonObject line = new JsonObject();
                    line.put("line", c.line);
                    line.put("code", c.code);
                    if (c.match) {
                        line.put("match", true);
                    }
                    return line;
                }).toList());
                return row;
            }).toList());
            return 0;
        }
        if (watch) {
            clearScreen();
        }
        if (!rows.isEmpty()) {
            printSource(rows);
        }

        return 0;
    }

    protected void printSource(List<Row> rows) {
        for (Row row : rows) {
            printer().printf("Route: %s\tSource: %s Total: %s Mean: %s Max: %s Min: %s Last: %s%n", row.routeId, row.location,
                    row.total, row.mean != null ? row.mean : "", row.max,
                    row.min, row.last != null ? row.last : "");
            for (int i = 0; i < row.code.size(); i++) {
                Code code = row.code.get(i);
                String c = Jsoner.unescape(code.code);
                String arrow = code.match ? "-->" : "   ";
                printer().printf("%4d: %s %s%n", code.line, arrow, c);
            }
            printer().println();
        }
    }

    protected int sortRow(Row o1, Row o2) {
        // sort for highest mean value as we want the slowest in the top
        long m1 = o1.mean != null ? Long.parseLong(o1.mean) : 0;
        long m2 = o2.mean != null ? Long.parseLong(o2.mean) : 0;
        return Long.compare(m2, m1);
    }

    private static class Row {
        String routeId;
        String id;
        String location;
        String total;
        String mean;
        String max;
        String min;
        String last;
        List<Code> code = new ArrayList<>();
    }

    private static class Code {
        int line;
        String code;
        boolean match;
    }

}
