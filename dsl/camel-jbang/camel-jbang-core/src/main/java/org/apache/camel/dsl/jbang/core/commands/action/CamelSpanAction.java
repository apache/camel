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
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "span",
                     description = "Display OpenTelemetry spans from running Camel integrations",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel cmd span",
                             "  camel cmd span --limit=50",
                             "  camel cmd span --filter=direct" })
public class CamelSpanAction extends ActionBaseCommand {

    public static class SortCompletionCandidates implements Iterable<String> {

        public SortCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("name", "duration", "status").iterator();
        }
    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--limit" }, defaultValue = "100",
                        description = "Maximum number of spans to display")
    int limit = 100;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter spans by name (substring match)")
    String filter;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = SortCompletionCandidates.class,
                        description = "Sort by name, duration, or status")
    String sort;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true",
                        description = "Use colored logging")
    boolean loggingColor = true;

    private volatile long pid;

    public CamelSpanAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
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
        root.put("action", "span");
        root.put("dump", "true");
        root.put("limit", Integer.toString(limit));

        Path f = getActionFile(Long.toString(pid));
        try {
            PathUtils.writeTextSafely(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = getJsonObject(outputFile);
        if (jo != null) {
            Boolean enabled = jo.getBoolean("enabled");
            if (enabled == null || !enabled) {
                printer().println(
                        "OpenTelemetry in-memory exporter is not enabled. Use --observe flag when running the integration.");
                PathUtils.deleteFile(outputFile);
                return 0;
            }

            JsonArray arr = jo.getCollection("spans");
            if (arr == null || arr.isEmpty()) {
                printer().println("No spans captured yet.");
                PathUtils.deleteFile(outputFile);
                return 0;
            }

            List<Row> rows = new ArrayList<>();
            root = loadStatus(this.pid);
            String integrationName = null;
            String ago = null;
            if (root != null) {
                JsonObject context = (JsonObject) root.get("context");
                if (context != null) {
                    integrationName = context.getString("name");
                    if ("CamelJBang".equals(integrationName)) {
                        ProcessHandle ph = ProcessHandle.of(this.pid).orElse(null);
                        integrationName = ProcessHelper.extractName(root, ph);
                    }
                }
                ProcessHandle ph = ProcessHandle.of(this.pid).orElse(null);
                long uptime = extractSince(ph);
                ago = TimeUtils.printSince(uptime);
            }

            for (int i = 0; i < arr.size(); i++) {
                JsonObject span = (JsonObject) arr.get(i);
                Row row = new Row();
                row.pid = Long.toString(this.pid);
                row.name = integrationName;
                row.ago = ago;
                row.traceId = span.getString("traceId");
                row.spanId = span.getString("spanId");
                row.parentSpanId = span.getString("parentSpanId");
                row.spanName = span.getString("name");
                row.kind = span.getString("kind");
                row.status = span.getString("status");
                Long durationMs = span.getLong("durationMs");
                row.durationMs = durationMs != null ? durationMs : 0;

                if (filter != null && !matchesFilter(row.spanName, filter)) {
                    continue;
                }

                rows.add(row);
            }

            if (sort != null) {
                rows.sort(this::sortRow);
            }

            tableSpans(rows);
        } else {
            printer().printErr("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        PathUtils.deleteFile(outputFile);
        return 0;
    }

    private boolean matchesFilter(String spanName, String pattern) {
        if (spanName == null) {
            return false;
        }
        return spanName.toLowerCase().contains(pattern.toLowerCase());
    }

    protected void tableSpans(List<Row> rows) {
        int tw = terminalWidth();
        int fixedWidth = 10 + 10 + 10 + 12 + 8 + 10;
        int borderOverhead = TerminalWidthHelper.noBorderOverhead(7);
        int nameWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth, borderOverhead, 15, 60);

        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("TRACE-ID").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> shortId(r.traceId)),
                new Column().header("SPAN-ID").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> shortId(r.spanId)),
                new Column().header("PARENT").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> shortId(r.parentSpanId)),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(nameWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.spanName),
                new Column().header("KIND").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.kind),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.status),
                new Column().header("DURATION").headerAlign(HorizontalAlign.RIGHT)
                        .dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.durationMs + "ms"))));
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
                return compareNullSafe(o1.spanName, o2.spanName) * negate;
            case "duration":
                return Long.compare(o1.durationMs, o2.durationMs) * negate;
            case "status":
                return compareNullSafe(o1.status, o2.status) * negate;
            default:
                return 0;
        }
    }

    private static int compareNullSafe(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        if (id.length() > 8) {
            return id.substring(0, 8);
        }
        return id;
    }

    private static class Row {
        String pid;
        String name;
        String ago;
        String traceId;
        String spanId;
        String parentSpanId;
        String spanName;
        String kind;
        String status;
        long durationMs;
    }

}
