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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "heap-histogram", description = "Display class-level heap memory usage in a running Camel integration",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel cmd heap-histogram",
                 "  camel cmd heap-histogram --sort instances",
                 "  camel cmd heap-histogram --filter camel --top 30" })
public class CamelHeapHistogram extends ActionWatchCommand {

    public static class SortCompletionCandidates implements Iterable<String> {

        public SortCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("bytes", "instances", "className").iterator();
        }
    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = SortCompletionCandidates.class,
                        description = "Sort by bytes, instances, or className", defaultValue = "bytes")
    String sort;

    @CommandLine.Option(names = { "--top" },
                        description = "Show only the top N classes", defaultValue = "50")
    int top;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter class names (use all to include all classes)", defaultValue = "all")
    String filter;

    private volatile long pid;

    public CamelHeapHistogram(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 1;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        }

        this.pid = pids.get(0);

        Path outputFile = getOutputFile(Long.toString(pid));
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "heap-histogram");
        Path f = getActionFile(Long.toString(pid));
        try {
            Files.writeString(f, root.toJson());
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            long totalInstances = jo.getLongOrDefault("totalInstances", 0);
            long totalBytes = jo.getLongOrDefault("totalBytes", 0);

            JsonArray arr = (JsonArray) jo.get("classes");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject jc = (JsonObject) arr.get(i);

                    Row row = new Row();
                    row.num = jc.getIntegerOrDefault("num", 0);
                    row.className = jc.getString("className");
                    row.instances = jc.getLongOrDefault("instances", 0);
                    row.bytes = jc.getLongOrDefault("bytes", 0);

                    if (!matchesFilter(row)) {
                        continue;
                    }

                    rows.add(row);
                }
            }

            rows.sort(this::sortRow);

            if (top > 0 && rows.size() > top) {
                rows = rows.subList(0, top);
            }

            if (watch) {
                clearScreen();
            }
            if (!rows.isEmpty()) {
                printer().printf("PID: %s\tClasses: %d\tTotal Instances: %s\tTotal Bytes: %s\tDisplay: %d%n",
                        pid, arr != null ? arr.size() : 0,
                        formatNumber(totalInstances), formatBytes(totalBytes), rows.size());
                printTable(rows);
            }
        } else {
            printer().println("Response from running Camel with PID " + pid + " not received within 10 seconds");
            return 1;
        }

        PathUtils.deleteFile(outputFile);

        return 0;
    }

    private boolean matchesFilter(Row row) {
        if ("all".equalsIgnoreCase(filter)) {
            return true;
        }
        if (row.className == null) {
            return false;
        }
        if ("non-jdk".equalsIgnoreCase(filter)) {
            return !row.className.startsWith("java.")
                    && !row.className.startsWith("javax.")
                    && !row.className.startsWith("jdk.")
                    && !row.className.startsWith("sun.")
                    && !row.className.startsWith("com.sun.")
                    && !row.className.startsWith("[");
        }
        return row.className.toLowerCase().contains(filter.toLowerCase());
    }

    protected void printTable(List<Row> rows) {
        int tw = terminalWidth();
        int fixedWidth = 6 + 14 + 14;
        int borderOverhead = TerminalWidthHelper.noBorderOverhead(4);
        int nameWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth, borderOverhead, 30, 80);

        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("#").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Integer.toString(r.num)),
                new Column().header("CLASS NAME").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(nameWidth, OverflowBehaviour.ELLIPSIS_LEFT)
                        .with(r -> r.className),
                new Column().header("INSTANCES").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> formatNumber(r.instances)),
                new Column().header("BYTES").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> formatBytes(r.bytes)))));
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "instances":
                return Long.compare(o2.instances, o1.instances) * negate;
            case "className":
                return (o1.className != null
                        ? o1.className.compareToIgnoreCase(o2.className != null ? o2.className : "")
                        : 0)
                       * negate;
            default:
                return Long.compare(o2.bytes, o1.bytes) * negate;
        }
    }

    protected JsonObject waitForOutputFile(Path outputFile) {
        return getJsonObject(outputFile);
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    static String formatNumber(long num) {
        return String.format("%,d", num);
    }

    private static class Row {
        int num;
        String className;
        long instances;
        long bytes;
    }
}
