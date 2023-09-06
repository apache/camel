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
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "source", description = "List top processors (source) in a running Camel integration", sortOptions = false)
public class CamelSourceTop extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter processors by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter-mean" },
                        description = "Filter processors that must be slower than the given time (ms)")
    long mean;

    private volatile long pid;

    public CamelSourceTop(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            System.out.println("Name or pid " + name + " matches " + pids.size()
                               + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        this.pid = pids.get(0);

        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "top-processors");
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            JsonArray arr = (JsonArray) jo.get("processors");
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
        } else {
            System.out.println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        // sort rows
        rows.sort(this::sortRow);

        if (watch) {
            clearScreen();
        }
        if (!rows.isEmpty()) {
            printSource(rows);
        }

        // delete output file after use
        FileUtil.deleteFile(outputFile);

        return 0;
    }

    protected void printSource(List<Row> rows) {
        for (Row row : rows) {
            System.out.printf("Route: %s\tSource: %s Total: %s Mean: %s Max: %s Min: %s Last: %s%n", row.routeId, row.location,
                    row.total, row.mean != null ? row.mean : "", row.max,
                    row.min, row.last != null ? row.last : "");
            for (int i = 0; i < row.code.size(); i++) {
                Code code = row.code.get(i);
                String c = Jsoner.unescape(code.code);
                String arrow = code.match ? "-->" : "   ";
                System.out.printf("%4d: %s %s%n", code.line, arrow, c);
            }
            System.out.println();
        }
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                // give time for response to be ready
                Thread.sleep(100);

                if (outputFile.exists()) {
                    FileInputStream fis = new FileInputStream(outputFile);
                    String text = IOHelper.loadText(fis);
                    IOHelper.close(fis);
                    return (JsonObject) Jsoner.deserialize(text);
                }

            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    protected int sortRow(Row o1, Row o2) {
        // sort for highest mean value as we want the slowest in the top
        long m1 = o1.mean != null ? Long.parseLong(o1.mean) : 0;
        long m2 = o2.mean != null ? Long.parseLong(o2.mean) : 0;
        if (m1 < m2) {
            return 1;
        } else if (m1 > m2) {
            return -1;
        } else {
            return 0;
        }
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
